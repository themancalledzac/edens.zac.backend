# Handoff: Admin-Images Filters + Schema Migration Cleanup (BE)

**Branch:** `0085-admin-hub`
**Session date:** 2026-05-05 → 2026-05-06
**Companion FE branch:** `0135-admin-page` (`edens.zac`)

This handoff covers commits added to the BE branch in this session. The branch already contained the admin-hub backend slice and the synthetic-collection resolver from prior sessions; this session adds two commits on top.

---

## Commits added this session (chronological, latest first)

```
a6db4c3  feat(admin-images): filter params on /api/admin/content/images
250cce6  fix(content): synthetic resolver IDs + locations visibility-enum migration cleanup + ASC ordering
```

---

## 1) `250cce6` — Three pre-existing bugs surfaced by the FE admin-images SSR work

All three bugs existed before this session. The new FE `/all-images` SSR fetch path is what brought them into view.

### a) `SyntheticCollectionResolver` — null content-table IDs

The resolver synthesizes a PARENT-shaped `CollectionModel` with `ContentModels.Collection` content blocks pointing to each child collection. The first parameter (the content-table id) was hardcoded to `null` because synthetic items don't have backing content rows.

**Symptom on the FE:** every synthetic item arrived with `id=null`. React row-key collisions (`row-IMAGE-null-IMAGE-null-IMAGE-null`) plus a `Map<id, size>` collision in `BoxRenderer` caused all tiles in a row to render at the SAME size — usually 344px each, totalling 1057.59px on a 985px viewport (visible overflow off the right edge).

**Fix:** pass `c.getId()` (the referenced collection's ID) instead of `null`. Synthetic items are now uniquely identifiable.

```java
private static ContentModels.Collection toCollectionContent(CollectionModel c) {
  return new ContentModels.Collection(
      c.getId(),                         // was null
      ContentType.COLLECTION,
      ...
      c.getId(),                         // referencedCollectionId (unchanged)
      c.getSlug(),
      c.getType(),
      c.getCoverImage());
}
```

The FE has a defensive fallback (`col.id ?? col.referencedCollectionId`), so either fix alone resolves the rendering bug; together they're belt-and-suspenders.

### b) `LocationRepository.findLocationsWithVisibleContent` — V20 visibility migration leftover

Migration V20 (`V20__visibility_3state.sql`, prior session) replaced `collection.visible BOOLEAN` with `collection.visibility VARCHAR(16) CHECK IN ('LISTED','UNLISTED','HIDDEN')` and dropped the `visible` column. This query was orphaned and still referenced `c.visible = true` and `c2.visible = true`.

**Symptom:** `/api/read/content/locations` returned a 500 (`PSQLException: ERROR: column c.visible does not exist`) the moment any caller touched it. Surfaced now because the FE all-images page SSRs `getAllLocations()` for the filter dropdown.

**Fix:** replace `c.visible = true` / `c2.visible = true` with `c.visibility = 'LISTED'` / `c2.visibility = 'LISTED'`. Matches V20's stated intent ("clients should not be in public lists" — V20 mapped `visible=true` CLIENT_GALLERY → UNLISTED, and `visible=true` other → LISTED). The `cc.visible` / `cc2.visible` references are unchanged because V20 did not touch the `collection_content` join table; that boolean is still the correct per-membership visibility flag.

### c) `ContentRepository.searchImages` — DESC ordering causing cross-page layout shifts

Search results were ordered `ci.capture_date DESC NULLS LAST, c.created_at DESC` (newest first). When the FE `CollectionPageClient` re-sorted the growing array under `displayMode='CHRONOLOGICAL'` (which sorts by `createdAt ASC`), each newly-loaded page was older than the previous and would insert above already-rendered content, pushing the user's scroll position down.

**Fix:** change the ORDER BY to `ci.capture_date ASC NULLS LAST, c.created_at ASC` so pages return oldest-first, matching the FE's CHRONOLOGICAL sort. New pages now append at the bottom of the list as expected.

This affects three endpoints (all of which feed the same FE rendering pipeline that expects oldest-first):
- `GET /api/read/content/images/search` (called from FE for `/location/[slug]`, `/people/[slug]`, `/tag/[slug]` pages)
- `GET /api/admin/content/images` (now also routed through `searchImages` — see commit a6db4c3)

---

## 2) `a6db4c3` — `/api/admin/content/images` accepts filter params

Routes the admin all-images endpoint through `ContentService.searchImages` instead of `ContentService.getAllImages(Pageable)`. The endpoint now accepts the same filter params as the prod search endpoint.

### Endpoint signature

Was:
```java
@GetMapping("/images")
public ResponseEntity<Page<ContentModels.Image>> getAllImages(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "50") int size)
```

Now:
```java
@GetMapping("/images")
public ResponseEntity<Page<ContentModels.Image>> getAllImages(
    @RequestParam(required = false) List<Long> personIds,
    @RequestParam(required = false) List<Long> tagIds,
    @RequestParam(required = false) Long cameraId,
    @RequestParam(required = false) Long locationId,
    @RequestParam(required = false) Long lensId,
    @RequestParam(required = false) Integer minRating,
    @RequestParam(required = false) Boolean isFilm,
    @RequestParam(required = false) Boolean blackAndWhite,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate captureStartDate,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate captureEndDate,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "50") int size)
```

### Behavior

- **No filters provided** → behaves identically to an unfiltered all-images fetch (returns every image, paginated).
- **Any filter provided** → returns page N of the *filtered* set (not "page N of all images, with non-matches dropped"). The DB-level filtering means even if matching images are sparsely scattered across millions of rows, the user sees 50 *matches* per page, not 50 unfiltered rows of which 3 happen to match.

### Response shape preserved

The endpoint still returns a Spring `Page<ContentModels.Image>` envelope so the FE's existing unwrap logic continues to work:
```java
ImageSearchResponse response = contentService.searchImages(request);
Pageable pageable = PageRequest.of(page, safeSize);
Page<ContentModels.Image> wrapped =
    new PageImpl<>(response.content(), pageable, response.totalElements());
```

`size` is clamped server-side to `[1, 200]` regardless of input — matches the prod search endpoint's `@Min(1) @Max(200)` constraint.

### Admin-tier visibility

Important: `searchImages` queries the `content_image` table directly. It does **not** apply collection-level visibility filtering. So admin sees images even when their containing collection is UNLISTED or HIDDEN. This is intentional for the admin tool (you want to see all your images, including hidden/unlisted ones).

### Test update

`ContentControllerDevTest` previously mocked `contentService.getAllImages(any(Pageable.class))`. Updated to mock `contentService.searchImages(any(ImageSearchRequest.class))` since that's the new code path.

`ContentService.getAllImages(Pageable)` is now vestigial (no callers) but kept to avoid widening the diff. Remove in a follow-up if you want.

---

## Verification status

- `mvn spotless:check` — passes (after two iterations of javadoc reflow and import cleanup the user reported via build output).
- `mvn clean package -DskipTests` — passes.
- Tests: `ContentControllerDevTest.getAllImages_*` updated and passing. No other tests touch the modified queries, but a manual smoke against the running container (with V20 applied) confirmed `/api/read/content/locations` and `/api/admin/content/images?page=0&size=50` return 200.

## Database expectations

- V20 must be applied (`collection.visibility` column present). The handoff commit `LocationRepository` change assumes this. If running against an older snapshot, the query will fail with `column c.visibility does not exist`.
- No new migrations in this session.

## FE coupling

The companion FE branch is `0135-admin-page` (`edens.zac`). The FE depends on:
- `searchImages` returning oldest-first so paginated pages append cleanly under CHRONOLOGICAL display.
- `/api/admin/content/images` accepting filter params (FE has the API client wired but currently only sends `page`/`size`; filter UI is a planned follow-up).
- Synthetic resolver returning unique IDs so the FE rendering pipeline doesn't collide on null keys.

The two branches should ship together (or BE first, then FE — never FE first).

## Follow-up work

1. **Drop the unused `ContentService.getAllImages(Pageable)` overload** — no callers remain after `a6db4c3`.
2. **Consider parameterizing sort direction** on `searchImages` if there's ever a use case for newest-first results. Currently the ASC change is global (affects search-by-location, search-by-tag, search-by-person too). Those pages also render under CHRONOLOGICAL and benefit from ASC, but if a future admin page wants reverse-chronological, an `Order` param on `ImageSearchRequest` would be needed.
3. **`ContentService.getAllImages(Pageable)` SQL** (`findAllImagesOrderByCreateDateDesc`) is also DESC and orphaned — if you decide to remove the unused overload, this method goes with it. Otherwise consider renaming to drop the `Desc` suffix and aligning the order.

## Files in this branch (this session)

```
src/main/java/edens/zac/portfolio/backend/controller/dev/ContentControllerDev.java   (modified)
src/main/java/edens/zac/portfolio/backend/dao/ContentRepository.java                 (modified)
src/main/java/edens/zac/portfolio/backend/dao/LocationRepository.java                (modified)
src/main/java/edens/zac/portfolio/backend/services/SyntheticCollectionResolver.java  (modified)
src/test/java/edens/zac/portfolio/backend/controller/dev/ContentControllerDevTest.java (modified)
docs/handoffs/handoff_pagination_filters_2026_05_06.md                               (new — this file)
```
