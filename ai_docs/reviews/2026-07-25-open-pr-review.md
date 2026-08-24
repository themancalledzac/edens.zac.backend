# Critical Review — Open PRs #132 (backend), #229 / #230 / #231 (frontend)

Date: 2026-07-25. Scope: full multi-agent review (14 specialist reviewers: code, type-design, tests, silent-failures, comments, plus a cross-repo contract/deploy-window pass). Every finding below was verified against actual code at each PR's head ref; items that could not be fully verified are marked `[UNVERIFIED: ...]`. This document is written for fixer agents: each item carries file:line (PR-head numbering), the defect, and the fix. Stable IDs (`132-B1`, `229-F2`, ...) for cross-referencing.

Repos:
- Backend: `/Users/themancalledzac/Code/edens.zac.backend` — PR #132, branch `feat/collection-debloat`
- Frontend: `/Users/themancalledzac/Code/edens.zac` — PR #229 `feat/collection-debloat`, #230 `0214-collections-showcase-and-polish`, #231 `claude/lcp-image-eager-loading-5d86eb`

---

## Verdicts

| PR | Verdict | Merge blockers |
|---|---|---|
| backend #132 | **BLOCKED** — 3 blockers, 8 should-fix | 132-B1 migration boot risk, 132-B2 ingest blogs never publish, 132-B3 empty gallery tiles |
| frontend #229 | **BLOCKED** — 3 blockers + 1 product decision | 229-F1 admin cannot set collection kind, 229-F2 client download UI loss, 229-F3 SEO fail-open |
| frontend #230 | **BLOCKED** — 2 blockers | 230-G1 stale shared cache on /collections, 230-G2 rating control lies |
| frontend #231 | **APPROVE** after 3 one-line comment restorations | none (zero logic change is *proven* — see §5) |

**Deploy order (verified by cross-repo contract review):**
1. Fix + merge + deploy **#132** first. Old FE against new BE is contract-safe (old FE still sends/reads `type`, BE accepts/emits it — test-pinned).
2. Then fix + merge + deploy **#229**. Deploying #229 first is a hard admin outage: against current prod BE, `createCollection`/`createChildCollection` return 400 ("Type is required" — `@NotNull` + `@Valid` at `AdminController.java:111-112`); all public badges vanish; SEO suppression goes inert.
3. **#230 is independently deployable** — verified on backend `main` today: `V43__add_collection_end_date.sql` present, end-date fields present across model/requests/ContentModels/repo/entity, and `PATCH /collections/{id}/rating` exists and matches the FE call.
4. **#231 any time.** No overlap with anything (single file).
5. **#229 x #230 have exactly one real merge conflict** (verified with `git merge-tree`; both branch from `e9b7047`): `app/utils/contentLayout.ts` `convertCollectionContentToParallax`. Whichever merges second must keep **all five** inserted fields — #229's `isClient`/`isBlog`/`tags` AND #230's `collectionDate`/`collectionEndDate` — and keep #230's JSDoc form (add one sentence: "Tags are carried through so the public card badge survives the conversion."), zero inline comments. Dropping #229's side kills badges/cover-strip on converted cards; dropping #230's kills showcase date labels.

**Process finding:** the frontend repo has **no CI on PRs** ("no checks reported" on all three branches). Every FE verification claim (tsc, 2,600+ tests, eslint) is local-only. Recommend a GitHub Actions workflow running `tsc --noEmit`, `jest`, `eslint` on PRs. Backend #132 CI is fully green (build, lint, tests, security scan).

**Still open from prior review:** no tracking issue exists for the phase-2 `type`-column drop. Phase 2 currently lives only as a comment in V50. See 132-C12 for the consolidated phase-2 comment; the issue itself still needs opening.

---

# 1. Backend PR #132 — isClient/isBlog storage truth + dual-compat

## 1.1 MERGE-BLOCKING

**132-B1. V50's targeted `ON CONFLICT (collection_id, tag_id)` runs against a pre-Flyway table whose unique constraint is unverified in prod — a failed migration blocks app boot.**
`src/main/resources/db/migration/V50__collection_client_blog_flags.sql:40,:47`
`collection_tags` predates Flyway (no CREATE TABLE in any migration; the only definition is `src/test/resources/db/test-base-schema.sql:149-153`, an explicit *reconstruction*). `ON CONFLICT (a,b)` requires a real unique index on those columns; if prod lacks it, Postgres errors, V50 aborts, Flyway fails the boot. The repo's own convention agrees: `TagRepository.java:391` writes this same table with **targetless** `ON CONFLICT DO NOTHING`; every *targeted* ON CONFLICT in the repo is against a Flyway-declared PK. Testcontainers passes only because the reconstruction script declares the PK.
**Fix:** rewrite both inserts constraint-independent (also strictly more idempotent):
```sql
INSERT INTO collection_tags (collection_id, tag_id)
SELECT c.id, t.id
FROM collection c
JOIN tag t ON t.slug = 'art-gallery'
WHERE c.type = 'ART_GALLERY'
  AND NOT EXISTS (SELECT 1 FROM collection_tags ct
                  WHERE ct.collection_id = c.id AND ct.tag_id = t.id);
```
(and the `portfolio` twin). Alternative: verify prod first — `SELECT conname, contype FROM pg_constraint WHERE conrelid = 'collection_tags'::regclass;`

**132-B2. Lightroom-ingest day-blogs now land UNLISTED and silently never publish.** (Triple-confirmed: code review, silent-failure hunt, contract review.)
`services/CollectionProcessingUtil.java:589` + `ImageUploadPipelineService.java:563-583`
On `main`, `toEntity` set HIDDEN and `applyTypeSpecificDefaults` promoted HIDDEN→LISTED for non-CLIENT_GALLERY types (verified at main's `CollectionProcessingUtil.java:890-897`). This PR sets UNLISTED at create and deletes the promotion. `getOrCreateBlogForDay` never sets visibility, so every ingested day lands UNLISTED: `findListedBlogsOrdered` (LISTED-only) and prod `/all-blogs` (`SyntheticCollectionResolver.java:93`, scope=[LISTED]) both exclude it. Every log line says success; dev masks it (dev scope includes all visibilities). The daily-blog pipeline changes from auto-publish to publish-never.
**Fix:** set the intended visibility explicitly in `getOrCreateBlogForDay` after create (or add `visibility` to `CollectionRequests.Create` and pass LISTED from the pipeline). Add a regression test asserting the ingested blog is reachable via `findListedBlogsOrdered`.

**132-B3. Derived parents are admitted by the listing query but stripped of their children by the render path — the common case produces an empty tile.**
`dao/CollectionRepository.java:474-497` vs `services/CollectionService.java:1419-1422`
The query now qualifies ANY collection with a visible `is_client` child (parent-side `type='PARENT'` filter dropped, deliberate and test-locked). But `isClientGalleryContext` still reads `type == CLIENT_GALLERY || (type == PARENT && children.anyMatch(CLIENT_GALLERY))`. A non-PARENT wrapper (MISC/PORTFOLIO folder — or the `staging` collection, which `ImageUploadPipelineService.java:812-820` links to EVERY new collection with `visible=true`) with UNLISTED client children (the *default* for client galleries after this PR) appears in `/all-client-galleries` and renders empty when opened. HOME is also newly admitted. No error, no log.
**Fix:** re-key the context flag-wise and drop the PARENT requirement: `entity.isClient() || children.stream().anyMatch(CollectionEntity::isClient)`. Add a test: non-PARENT wrapper with UNLISTED client children shows those children.

## 1.2 SHOULD-FIX (before or immediately with merge)

**132-B4. `resolve` inherits null flags from the legacy `type` column, not the entity's current booleans — the booleans are not actually storage truth on the update path.**
`services/CollectionTypeCompat.java:70-72` called from `CollectionProcessingUtil.java:634-636` (passes `entity.getType()`, never `entity.isClient()/isBlog()`).
On a drifted row (`type=MISC, is_blog=true` — the exact shape the PR's own integration test creates), `{"id":9,"isClient":false}` resolves base=MISC → `(MISC,false,false)`, silently clearing `is_blog`. Inert today (all writes funnel through resolve, so type/flags stay synced) but becomes an unconditional every-update demotion the moment phase 2 nulls `currentType`. Related: any update on a drifted row silently re-promotes it into flag-keyed listings (drift re-derivation).
**Fix:** add `currentIsClient`/`currentIsBlog` params; inherit from them, falling back to `deriveX(base)` only when unavailable.
**Test seam (independently found by the test reviewer):** no test drives a single-flag partial update through `applyBasicUpdates` — mutating `entity.getType()` → `null` survives all 1,134 tests and resurrects the prod demote bug. Add `applyBasicUpdates_partialIsBlogFalse_onClientGallery_doesNotDemote` (entity CLIENT_GALLERY + isClient=true; update `{isBlog:false}` only; assert type stays, isClient true, isBlog false) in `CollectionProcessingUtilTest`.

**132-B5. One checkbox silently destroys PARENT/HOME structural type.** (Found independently by code + type reviewers.)
`services/CollectionTypeCompat.java:60-66`
`resolve(true,null,null,HOME)` → `(CLIENT_GALLERY,true,false)`. Explicit true unconditionally overwrites the base; `isParentType()` gates ~8-11 call sites across services. One `{"isClient":true}` on the home singleton retypes it and re-opens it to non-collection content. Reachable today from the admin update endpoint.
**Fix:** in `resolve`, throw IAE (→400) when an explicit true flag arrives and the effective base type `isParentType()` — parent retypes stay deliberate via `type`. Add the missing PARENT/HOME case to `CollectionTypeCompatTest`'s `BooleansProvided` nest.

**132-B6. The mutual-exclusion invariant has exactly one enforcement point and no backstop.**
Enforcement lives solely in `CollectionTypeCompat.java:55-58`. No bean validation, no entity check, no DB CHECK — while this schema already uses CHECKs for this class of invariant (V20 visibility, V36 role). `CollectionEntity` is `@Data @Builder` with independently settable `type`/`isClient`/`isBlog` (`entity/CollectionEntity.java:17,33,39`) and `CollectionRepository.save` full-row-writes whatever the entity holds (`:319-320,:337-338`).
**Fix (two 5-line changes):**
1. V50: `ALTER TABLE collection ADD CONSTRAINT chk_collection_client_blog_excl CHECK (NOT (is_client AND is_blog));` — do NOT add a full type-sync CHECK (integration fixtures and the phase-2 window intentionally allow type/flag divergence).
2. `Resolved` compact constructor asserting `isClient == deriveIsClient(type) && isBlog == deriveIsBlog(type)` — its javadoc claims "always mutually consistent" but `new Resolved(BLOG, true, false)` currently compiles and flows into the entity setters.

**132-B7. A collection demoted via `isClient:false` keeps an enforced gallery password that no endpoint can clear.**
`services/CollectionService.java:1479-1480` — `updateGalleryAccess` refuses non-CLIENT_GALLERY/PARENT (`not-eligible-type`); the read gate keys on `getGalleryPassword() != null` (fail-closed, no bypass — good), but the demote path never nulls password/role grants.
**Fix:** clear `gallery_password`/`recipient_emails` on `isClient` true→false transition in `applyBasicUpdates`, or gate `updateGalleryAccess` on `getGalleryPassword() != null` in addition to type. (This is the known "retype doesn't reconcile defaults/password/grants" latent gap — this PR makes it reachable via checkbox.)

**132-B8. Tag seeding silently no-ops on a name-collision with a different slug.**
`V50:25-33` — `tag.tag_name` is NOT NULL UNIQUE. A pre-existing tag named `Portfolio` with a manually-edited slug ≠ `portfolio` passes the `WHERE NOT EXISTS (slug=...)` guard, hits the name unique constraint, `ON CONFLICT DO NOTHING` swallows it, and the subsequent slug-join attaches zero rows. Migration reports success; the grouping the design depends on is absent.
**Fix:** seed by `tag_name` conflict target with slug backfill, or add a post-insert `DO $$ ... RAISE EXCEPTION` assertion. Also add the V15-style pre-migration audit comment: `SELECT * FROM tag WHERE slug IN ('art-gallery','portfolio');` — a *converted* tag holding either slug would shadow the tag view entirely (`TagViewResolver.java:60-62` returns empty for converted tags). `[UNVERIFIED: prod tag state — run the audit]`

**132-B9. Every existing collection with `display_mode IS NULL` flips ORDERED → CHRONOLOGICAL on next read.**
`services/CollectionProcessingUtil.java:191-195` — fallback changed from `BLOG ? CHRONOLOGICAL : ORDERED` to unconditional CHRONOLOGICAL. `display_mode` is nullable and never backfilled; this is a production-visible reordering of every un-set non-blog collection, test-locked (intentional per D10) but undisclosed in the PR body, and no "D10" decision doc exists anywhere in the repo (grep-verified — the four `D10:` comment tags reference nothing).
**Fix:** before deploy run `SELECT count(*) FROM collection WHERE display_mode IS NULL AND type <> 'BLOG';` — if nonzero, either backfill `ORDERED` for those rows in V50 or disclose the reorder in the PR body. Drop the dangling `D10:` prefixes from the 4 comments (`CollectionProcessingUtil.java:189,598`; `CollectionProcessingUtilTest.java:369,374`).

**132-B10. The V50 backfill has never executed against a single row of data.**
CI migrates an empty table (fresh Testcontainers), so the backfill UPDATE and tag-attachment joins are untested against data; `migrationSeedsLabelTags` only proves the two tag rows exist.
**Fix:** dedicated-container test: programmatic Flyway `target=49` on a raw `PostgreSQLContainer`, insert one row per all 7 legacy types + a pre-existing `('Portfolio','portfolio')` tag, migrate to latest, assert per-type flags, tags attach only to ART_GALLERY/PORTFOLIO collections, exactly one `portfolio` tag row. Do NOT re-run V50 DML inside the shared harness (the global UPDATE rewrites sibling fixtures' divergent-flag rows → order-dependent failures).

**132-B11. Response flags are nullable `Boolean` where the wire contract is a required boolean.**
`model/CollectionModel.java:45,49`, `model/Records.java:81-82`, `model/ContentModels.java:244-245`. All construction sites are currently correct, but `CollectionList` retains two constructors that hard-write `null,null` (`Records.java:96-106`, now test-only), and the FE types these as required.
**Fix:** make the flags primitive `boolean` on the three response shapes (keep `@JsonProperty` — it also neutralizes the Lombok primitive is-getter rename); at minimum delete the two null-defaulting `CollectionList` overloads. Pairs with 229-F8 (make FE fields required on detail models).

## 1.3 FOLLOW-UP (non-blocking, track)

- **132-C1.** `/all-blogs` still keys on `type='BLOG'` while the admin blogs tile keys on `is_blog` — two competing definitions in one release; the PR's own test asserts `(type=MISC,is_blog=true)` IS a blog, and that row is invisible on `/all-blogs`. Port it or add an explicit `TODO(phase-2)` at the `Synthetic` catalog entry (`SyntheticCollectionResolver.java:43`; javadoc at `CollectionRepository.java:376-378` already acknowledges).
- **132-C2.** Drift audit for the compat window: startup or admin-endpoint check `SELECT count(*) FROM collection WHERE (type='CLIENT_GALLERY') <> is_client OR (type='BLOG') <> is_blog`, WARN with ids. Drift producers: rollback (old jar writes type-only), out-of-band SQL. Consequences verified: same row present in `/all-blogs` but absent from admin tile; **duplicate day-blogs** (drifted day invisible to `findBlogsByCollectionDate` → second blog created for the same date; the "using oldest" warning never fires); silent re-promotion on any update (see 132-B4). Document the V50 backfill UPDATE as the idempotent repair statement after rollback.
- **132-C3.** Multipart create is the only write surface where the MISC fold is unobservable in-band (`ImageUploadResult` doesn't echo type/flags/visibility; a typo'd `isclient=` param silently binds nothing). Cheap fix: INFO log in `toEntity` when a create lands MISC with neither type nor flags; and/or echo resolved type/flags in `ImageUploadResult`.
- **132-C4.** Log category transitions at INFO in `applyBasicUpdates` when `resolved.type() != entity.getType()` — makes demotions and drift re-promotions greppable during the window.
- **132-C5.** `TagAdminController.java:30` — `@RequestBody(required=false) SaveAsCollectionRequest` lacks `@Valid` (project rule); no-op today but the PR adds fields to that record.
- **132-C6.** `SyntheticCollectionResolver.java:47` — `Synthetic("Client Galleries", CLIENT_GALLERY)`'s typeFilter is dead (the `:89` branch bypasses it). Pass `null`.
- **132-C7.** Indexes: **no parity was lost** (the V9 `(type, collection_date)` index was partial `WHERE visible=true` and was dropped in V20 with the `visible` column — reconciled between reviewers; one reviewer's "loses index parity" claim does not survive V20). Optional if scale demands: `CREATE INDEX CONCURRENTLY ... ON collection (collection_date DESC) WHERE is_blog` / `WHERE is_client`.
- **132-C8.** Unknown-JSON-key policy: Boot default ignores unknown properties, so `{"isclient":true}` no-ops with 200. Tolerable (responses echo state; FE is name-coordinated) — make it a deliberate decision; option: fail-on-unknown for the admin API only.
- **132-C9.** Jackson lock hygiene: `CollectionFlagSerializationTest` uses bespoke `new ObjectMapper()` — equivalent today (no naming strategy in src/main); add a one-line comment pinning that assumption or use `@JsonTest`.
- **132-C10.** `CollectionFlagRepositoryIntegrationTest` inserts ~20 rows into the shared singleton container (harness truncates only auth tables) — future exact-count tests become class-order-dependent. Note it in the class javadoc.
- **132-C11.** V50 makes `/art-gallery` and `/portfolio` live public tag-view routes (TagViewResolver renders any tag at `/{slug}`). Visibility-scoped, no leak — just be deliberate.
- **132-C12.** Consolidate the phase-2 record in V50's header (it is the only record of phase 2): what gets dropped (collection.type), what gets deleted (CollectionTypeCompat, legacy type API field/params), and the one query that must be re-keyed (`findNonEmptyOrderedByVisibilityIn`'s typeFilter / synthetic catalog). **And open the tracking issue.**

## 1.4 Java optimizations / simplification

- `CollectionTypeCompat`: extract `private static final Resolved CLIENT / BLOG_RESOLVED` constants (each constructed twice at `:62/:74`, `:65/:77`); rewrite the legacy branch (`:88-92`) as an exhaustive switch expression; make `deriveIsClient`/`deriveIsBlog` (`:96-103`) private (zero external prod callers — grep-verified) and delete their direct tests (see §1.6); express the fold-set literal (`:81`) as `deriveIsClient(base) || deriveIsBlog(base)`.
- **Signature hardening (pick one):** (a) named entry points `forCreate(isClient,isBlog,requested)` / `forUpdate(isClient,isBlog,requested,currentEntity)` — removes the `null` literal at `CollectionProcessingUtil.java:575`; or (b) a tiny request interface (`CollectionType type(); Boolean isClient(); Boolean isBlog();`) implemented by Create/Update/SaveAsCollectionRequest (accessor names already match) → `resolve(request, currentType)`. Both kill the two-adjacent-Booleans transposition hazard and give phase-2 `isParent` a single extension point.
- Add `CollectionTypeCompat.apply(entity, resolved)` (or `Resolved.applyTo`) — the three-setter application is duplicated at `CollectionProcessingUtil.java:576-578` and `:637-639`; a future third site must not half-apply the triple.
- `applyTypeSpecificDefaults` (`CollectionProcessingUtil.java:906-918`) now has one caller and one behavior (pagination default). Inline into `toEntity` or rename `applyPaginationDefaults`; also rename its test (`applyTypeSpecificDefaults_setsPaginationOnly_leavesVisibilityUntouched`).

## 1.5 Duplication

- **Collection column list copy-pasted six times:** `CollectionRepository.java:42` (constant) + inline at `:161,:186,:255,:283` + `TagRepository.java:335`. This PR hand-edited all six; phase 2 does it again. Extract a shared `COLLECTION_COLUMNS` (with an `columns(String alias)` helper); the identical row mappers (`CollectionRepository.java:50-54`, `TagRepository.java:49-53`) become one constant.
- `new Records.CollectionList(...)` positional-with-nulls x3 (`CollectionRepository.java:522-529`, `CollectionProcessingUtil.java:507-514`, `CollectionService.java:744-751`) → one `CollectionList.from(CollectionEntity, String coverUrl)` factory. Also fixes the 4-constructor telescoping.
- `.isClient(false).isBlog(false)` triplicated at every synthetic builder (`SyntheticCollectionResolver.java:116-117`, `TagViewResolver.java:101-102`, `UserPageAssembler.java:96-97`) → `@Builder.Default` on the model or a shared `derivedParentBuilder(...)`.
- `Records.SiblingRow` 5-arg back-compat ctor (`Records.java:125`) defaults flags to `false` and exists only for tests — inline it away (and note its primitive flags carry no `@JsonProperty`).
- `CollectionRequests.Create` has 3 constructors; the 6-arg overload has exactly one production caller.

## 1.6 Tests — add / remove (backend)

**Add (ordered by value):**
1. `applyBasicUpdates_partialIsBlogFalse_onClientGallery_doesNotDemote` — the wiring-seam regression pin (see 132-B4). Criticality 9.
2. Backfill + seed-idempotency dedicated-container migration test (see 132-B10). Criticality 8.
3. `toEntity_bothFlagsTrue_isRejected` — the both-true 400 is pinned on update only; all three create surfaces funnel through `toEntity` (verified), one unit test covers all. Do NOT add a MockMvc variant (would re-test only the advice; `GlobalExceptionHandlerTest:47` covers IAE→400).
4. Dual-role dedup: extend `CollectionFlagRepositoryIntegrationTest` (~`:234`) with a collection that is itself `is_client` AND parent of a visible client child — appears once, `doesNotHaveDuplicates()`.
5. Ordering assertion for `findListedBlogsOrdered` (distinct ratings, `containsSubsequence`) — required only if removal 4 below is taken.
6. (Optional) multipart create with neither type nor flags → 201, captor type/flags all null.
7. PARENT/HOME explicit-true case in `CollectionTypeCompatTest` (pairs with 132-B5).
8. Ingest day-blog visibility regression test (pairs with 132-B2).
9. Non-PARENT derived-parent children-visible test (pairs with 132-B3).

**Remove (verified redundant — with surviving coverage):**
1. `CollectionTypeCompatTest.neitherTrue_requestedClientGalleryTypeFoldsToMisc` (`:76`) — every mutation it kills is killed by `:43`, `:66`, `:132`; mutation-checked that it cannot detect what its name implies.
2. `CollectionTypeCompatTest.DeriveHelpers` nested class (`:198-211`) — helpers have zero prod callers outside `resolve`; the `LegacyTypeOnly` nest pushes all 7 enum values through `resolve`. Pair with making the helpers private.
3. `CollectionProcessingUtilTest.toEntity_createWithoutVisibility_defaultsToUnlisted` (`:165`) — strict subset of `toEntity_unlistedDefaultAppliesToAllTypes` (`:177`).
4. `CollectionRepositoryTest.findListedBlogsOrderedSqlFiltersOnIsBlogAndOrdersByRatingThenDate` (`:157`) — SQL-substring pinning against a mock; real contract proven on Postgres by `findListedBlogsOrdered_keysOnIsBlogNotType`. Take add-5 first (ORDER BY is its only unique value). Judgment call: it updates a pre-existing house-pattern test.

**Do NOT remove** (each kills a unique mutation): `neitherTrue_currentlyBlog_foldsToMisc`; `explicitFalseOnTheEncodingFlagDemotesToMisc` and its `(false,false)` variant; both partial-inherit tests; the two legacy-payload deserialization tests; the per-shape serialization tests; `toEntity_legacyBlogType_derivesIsBlog`.

**Quality:** extract `typeFlagsUpdate(type,isClient,isBlog)` helper — the four new flag tests inline 18- and 23-arg constructors with mixed arities (`:484` vs `:499`); rename `bothTrue_isRejectedWith400Semantics` → `bothTrue_isRejectedAsIllegalArgument` (asserts IAE, not 400); `ContentControllerDevTest` actually tests `AdminController` (pre-existing drift, deepened — rename in a follow-up); `migrationSeedsLabelTags`/`saveRoundTripsFlags` don't follow `method_scenario_result` naming.

## 1.7 Comment debloat (backend) — all verified against code

**Headline: `CollectionTypeCompat`'s javadoc was verified line-by-line against `resolve()` and matches exactly** — trustworthy as the phase-2 contract. Keep it as-is.

**Wrong (fix):**
1. `CollectionProcessingUtilTest.java:262` — "Canonical 21-arg order:" lists 23 items → "23-arg".
2. `CollectionRequests.java:132-136` — compat-ctor doc says "those three set to null"; it now nulls five (isClient, isBlog added).
3. `CollectionProcessingUtil.java:609-615` — `applyBasicUpdates` javadoc is severely rotted (lists visible/priority/coverImageUrl/configJson/blocksPerPage/password-hashing — none exist; omits the compat resolution this PR added). Replacement text in review notes: partial-field updates list + "Null request fields leave the entity untouched."
4. `CollectionEntity.java:26` — `type` doc lists 4 of 7 enum values; rewrite as legacy/dual-compat/dropped-in-phase-2.
5. `CollectionEntity.java:59` — "New rows default HIDDEN" contradicts the PR's own UNLISTED create default; distinguish field default (fail-closed HIDDEN) from create-path override (UNLISTED).
6. `AbstractPostgresIntegrationTest.java:16` — "applies V2..V29" → version-agnostic "all V2+ migrations".
7. `CollectionRepository.java:533` — "INSERT writes all columns" — it omits `recipient_emails`; both password fields are owned by `saveGalleryAccess`.

**Condense:** Update-request tri-state docs → one-liners pointing at `CollectionTypeCompat` (`CollectionRequests.java:62-73`); `CollectionModel.java:40-47` flag docs → wire-name-pinning one-liners; `CollectionProcessingUtil.java:629-632` call-site comment → guard-behavior only; V50 header → consolidated phase-2 record (132-C12); drop the four dangling `D10:` prefixes; `TagService.java:68-71` second half; `Records.java:68-73` component enumeration (append flags or drop style); change-log-style test comments at `CollectionProcessingUtilTest.java:178,:374`; "drift was invisible in dev" → concrete dev-vs-prod scope sentence (`CollectionListReadRepositoryIntegrationTest.java:159`).

**Remove (only two):** `CollectionProcessingUtil.java:601` (narrates the next call); `CollectionProcessingUtilTest.java:157` (ancient change-log). Everything else earned KEEP — notably the negative-space comments (`CollectionProcessingUtil.java:898-901` "visibility deliberately not touched"; `CollectionRepository.java:376-377` deliberately-legacy filter) which prevent exactly the "helpful fix" regressions this refactor risks.

---

# 2. Frontend PR #229 — key public surfaces on isClient/isBlog

## 2.1 MERGE-BLOCKING

**229-F1. The frontend can no longer set a collection's kind — at create or update. The ordering error: the `type` strip shipped before any boolean writer exists.** (Confirmed by code review + contract review; grep-verified: zero `isClient`/`isBlog` setters in `app/` outside type definitions.)
- `CreateCollectionForm.tsx:30-33,49` renders a required "Collection Type *" Select, sets `createData.type` — `withoutLegacyType` (`app/lib/api/collections.ts:226-232`) strips it. Wire body: `{title}` → backend #132 lands **MISC + UNLISTED** regardless of the dropdown. No way to create a client gallery or blog from the UI, and (below) no way to fix it after.
- `createChildCollection` (`useCollectionEdit.tsx:1222-1226`): `{type:PORTFOLIO,title}` → `{title}` → MISC (was PORTFOLIO).
- **Drag-retype is an optimistic lie** (`useCollectionRetype.ts:44-50`): body strips to `{id}`, backend returns 200-unchanged, revert only fires on null/throw → the re-bucket sticks in the UI and snaps back on reload.
- **Edit-sheet type Select** (`InfoTab.tsx:100` → `buildUpdatePayload` → stripped at `collections.ts:252-256`) and **SaveAsCollectionModal** (`:36,67-77` → stripped at `:400-408`, backend defaults PORTFOLIO) are silently dead controls.
- `CollectionCreateRequest.isClient/isBlog` (`app/types/Collection.ts:115-116`) and `CollectionUpdateRequest` (`:221-222`) are dead type surface exercised only by tests that hand-write booleans no production code sends — the suite is green over a broken contract.
**Fix (smallest, one choke point):** make `withoutLegacyType` transitional — derive from the field it strips: `isClient: type === CLIENT_GALLERY || undefined`, `isBlog: type === BLOG || undefined` (repairs all four call sites at once). Or wire the form/modal/retype to booleans now. Either way pin with a test: `createCollection({type: CLIENT_GALLERY, title})` emits `{title, isClient: true}`; and either fix or disable the retype handler and dead Selects in THIS PR, not the follow-up.

**229-F2. Anonymous password-cookie clients — the primary client-gallery persona — permanently lose all download UI while the backend still authorizes their downloads.** (Triple-confirmed.)
`app/utils/galleryAccess.ts:39-45` deleted the `type===CLIENT_GALLERY` fast-path; role grant via `/api/auth/me` is now the sole mechanism. But backend `/auth/me` (`AuthController.java:94-108`) 401s without a session principal — the `gallery_access_*` cookie plays no part — while `ContentDownloadControllerProd.java:150-157` still authorizes downloads via `GalleryAccessCookies.hasValidAccess(...)`. A client who unlocks with the gallery password (no account) gets `me = null` → download UI hidden at all three sites (`CollectionPageClient.tsx:145,366-373`, `CollectionContentRenderer.tsx:379-381`, `FullScreenModal.tsx:339`) although the backend would happily presign the ZIP. The PR's own *deleted comment* documented this exact user; the replacement test comment's premise ("/me surfaces the cookie-backed grant") is false, and **the new galleryAccess test pins the broken behavior as correct** — it must be updated with the fix. #132 contains no AuthController/download changes, so this is permanent, not a deploy-window artifact.
**Fix:** restore a cookie-aware branch that doesn't depend on `me`: allow download when `collection.isClient === true && collection.isPasswordProtected === true && Array.isArray(collection.content)` — content present on a protected client gallery is by construction proof of a validated cookie (backend nulls content otherwise, `CollectionControllerProd.java:98-102`). Strictly narrower than the old fast-path and keeps the role path. (Bigger alternative — /me surfacing cookie pseudo-memberships — is backend work not in #132; don't assume it.)

**229-F3. SEO suppression and list-cover stripping fail OPEN when the booleans are absent — and for SEO covers, the FE suppression is the ONLY defense.**
- `app/[slug]/page.tsx:39-44` — `isClient === true && isPasswordProtected === true`; a stale CloudFront payload or deploy-order slip carries `type` but no `isClient` → `openGraph.images`/Twitter card get the private cover URL. Verified real exposure: backend nulls only `content`/`contentCount` on locked responses — **`coverImage` is retained by design** (backend tests literally named "retains coverImage"). Old code keyed on `type` (present in every payload) — no such window existed.
- `CollectionPage.tsx:47` — same pattern on the list-cover strip; its own comment says it exists for "a stale cache" — the exact case where it now no-ops. `[UNVERIFIED: no backend cover strip found in the list path — this FE strip may be primary, not defense-in-depth, for list entries]`
**Fix (choose):** (a) *recommended* — key suppression/stripping on `isPasswordProtected === true` alone (the gate already does; protected = private regardless of kind; removes the undefined hazard entirely); or (b) conservative — add the free window fallback `|| collection.type === CollectionType.CLIENT_GALLERY` plus fail-safe on `isClient === undefined`. Either way add a `logger.warn` when a protected collection arrives without booleans (makes the stale-cache window observable), and pin the undefined case in tests (currently only the three defined-boolean cells are covered).

**229-F4 (product decision required). The Gallery badge feature is inert on every real (non-synthetic) surface.**
- `CollectionModel.tags` (`CollectionModel.java:98`) is **never populated anywhere in backend src/main** (exhaustive grep) → the array-branch badge fix + `tagNameToSlug` mapping + the four new `CollectionPage.test.tsx` cases exercise dead code (no live route passes an array anyway — all three call sites pass a single model).
- `ContentModelConverter.buildCollectionRecord` hardcodes `tags = List.of()` on nested collection blocks → home page and every real parent lose the Gallery badge that `collectionType: ART_GALLERY` used to render. Only synthetic list views enrich tags (`SyntheticCollectionResolver.java:101,108`). FE tests pass because fixtures inject tags. ("Story"/isBlog is unaffected.)
**Fix:** either populate tags backend-side (batch `findTagsByCollectionIds` into `batchConvertToBasicModels` / `buildCollectionRecord` — the batch path already has a `tagsByContentId` map available) — this is #132-adjacent work — or accept and document the Gallery-badge loss on non-synthetic surfaces and delete the dead array branch (`CollectionPage.tsx:40-83,141`) plus its tests.

## 2.2 SHOULD-FIX

**229-F5. Unify the three "parent" notions — two failure modes, one of them access-control-shaped.**
`useCollectionEdit.tsx:597` (content-derived `hasChildCollectionContent`) vs `:698` and `:1318` (still `type === PARENT`).
- Today: an admin creating a new PARENT and opening manage before adding children loses the Gallery Access section (`InfoTab.tsx:75`) with no explanation (`:597` false; pre-PR true).
- At enum removal: `:698`'s password-propagate-to-children confirm silently never fires → admin saves a parent password believing it covers child galleries; children keep old/no passwords; clients locked out, no error.
**Fix:** one derivation used at all three sites — content-based OR-ed with the type check during the window (`hasChildCollectionContent(collection) || collection.type === PARENT`). Re-key `:698`/`:1318` in THIS PR (the collection is fully loaded in edit mode).

**229-F6. `hasChildCollectionContent` can read false for a true parent** — `CollectionPageWrapper:48` fetches only the first 500 items and `:54-61` filters child refs out via `excludeContentSlugs`; a parent whose refs fall past 500 (or are all excluded) reads false → Gallery Access hidden, `useCoverImageSelection.ts:52` picks the wrong pool. Prefer a backend-supplied `hasChildren` if #132 exposes one; otherwise document the truncation bound at the call site. Also wrap the call at `useCollectionEdit.tsx:597` in `useMemo(..., [collection.content])` (currently an up-to-500-item scan per render, replacing an O(1) enum compare).

**229-F7. Silent-failure hardening (cheap, from the hunt):**
- Selects contract-violation detector: when `findMembership(me, collection.id)` exists but `collection.isClient === undefined`, `logger.warn` — that combination IS "stale payload in flight" (`CollectionPageClient.tsx:135`, `CollectionPageWrapper.tsx:90-92`).
- `selects.ts:89-97` `listSelectIdsServer` bare `catch { return [] }` swallows 500s unlogged — adopt the sibling `emptyOnError` pattern (`personal.ts:93-100`).
- `Badge.tsx:54` — `TAG_PUBLIC_LABELS[slug]` on an object literal resolves prototype members (tag named "Constructor" → `constructor` → a function flows into the `<span>`). Use `Object.hasOwn` / `Map` / `Object.create(null)`. Iterate `TAG_PUBLIC_LABELS` keys (not tag array order) so multi-tag precedence is declared.
- Null-tolerant tags: `Badge.tsx:53` (`tag.slug` TypeErrors on a null entry), `CollectionPage.tsx:66` (unguarded `.toLowerCase()`); one `filter(t => t != null)` at the conversion boundary degrades to "no badge" instead of crashing SSR.
- `app/[slug]/page.tsx:62-64` pre-existing bare catch turns transient API failures into "Not Found" metadata — add `logger.warn` while in the file.
- `ClientGalleryDownload.tsx:49-52` doc comment now false (control is mounted on role-based canDownload) — update alongside 229-F2's fix.

**229-F8. Type tightening:** declare `isClient: boolean; isBlog: boolean` (required) on `CollectionModel`/`CollectionPageDTO` — V50 makes the columns NOT NULL, backend emission is total (verified: every builder/ctor/row-mapper carries them), and the deleted `type` left these types with no required kind discriminator. Keep them optional only on `CollectionBaseModel` (synthetic builds). The compiler then flags every `=== true` fail-open site. Pairs with 132-B11. Narrow `withoutLegacyType<T extends { type?: unknown }>` to the two request types (currently deletes `type` from anything).

**229-F9. `isCollectionCard` fragility (verified safe today):** only `ContentParallaxImageModel` (optional slug, same interface as plain parallax images) and `ContentCollectionModel` carry top-level `slug` — no photo can misclassify now, but any future slug-stamping on a parallax image silently promotes it to rating 4. Prefer `contentType === 'COLLECTION'`-style discriminants where available, or add a `@see isCollectionCard` warning on `ContentParallaxImageModel.slug`. Also: the slug discriminant is written twice — import `isCollectionCard` at `contentRendererUtils.ts:243` instead of restating it.

## 2.3 Duplication

Four copies of the parallax-card shape: `collectionToContentModel` (`CollectionPage.tsx:40-83`), `convertCollectionContentToParallax` (`contentLayout.ts:184-220`) — this PR added the SAME carry block to both — plus `meContentBlock.ts` and `allCollectionsContentBlock.ts`. One shared `toParallaxCard(fields)` builder collapses all four (do after the #229/#230 five-field merge, see deploy plan #5).

## 2.4 Tests — add / remove (#229)

**Add:** 1) `createChildCollection` strips `type` (the ONLY unwrapped wire-contract site of four — `tests/lib/api/collections.test.ts` imports the other three only); 2) `editMode: true` bypasses the gate (zero coverage; blast radius grew this PR); 3) `isClient: undefined` pinned at both privacy sites (direction per 229-F3 decision); 4) `convertCollectionContentToParallax` carries tags/isClient/isBlog (the PR's own bug class re-enters through this door — only dimensions are tested); 5) synthetic tiles rate 4 (disclosed side effect, unpinned); 6) childless legacy-PARENT → `isParent === false`; 7) selects seeding degradation on undefined isClient; 8) `tagNameToSlug('Café Sessions')` → `'caf-sessions'` + slugless-tag tolerance. **Parity caveat (do not pin):** JS `\s` matches NBSP, Java's default `\s` does not — `'Art Gallery'` yields `art-gallery` (FE) vs `artgallery` (backend `SlugUtil`, verified); consider normalizing NBSP first.
**Remove:** `CollectionPageWrapper.test.tsx:174` (locked PARENT ≡ line 159 modulo inert type) and `:205` (non-protected PARENT ≡ `:145`/`:132`). **Rename** `:190` to drop dead PARENT vocabulary; reword the file header (`:2-13`) and describe (`:84`) from "CLIENT_GALLERY routing" to "password-protection routing".
**Quality:** badge test pins synthetic `id: 0` (use `objectContaining({name, slug})`); saved-ids test's anonymous phase relies on module-level mock defaults (make `meServer.mockResolvedValueOnce(null)` explicit). Fixture note: `useCollectionEdit.handlers.test.tsx:87` type-only fixtures are currently *correct* (those paths still read type) but must gain booleans in the admin follow-up.

## 2.5 Comment debloat (#229) — from the comment audit

**Wrong (fix):** `useCollectionEdit.tsx:124-125` isParent doc still says "PARENT-type collections" (contradicts this PR's own re-key — replacement text in audit); `contentTypeGuards.ts:243-248` attributes gallery-access propagation to a predicate that doesn't gate it + "retired type enum" overstates; **slugify parity spec is in the wrong place** — full backend-parity contract sits on the alias (`tagUtils.ts:16-27`) while the implementation (`locationUtils.ts:11-15`) still says "use only as a fallback" — move the spec onto `locationUtils.slugify`, shrink the alias doc (verbatim texts in the audit report).
**Condense:** `Badge.tsx:19-27`; widened-seeding NOTE (`CollectionPageWrapper.tsx:86-88`) → one intent sentence + "pinned by test"; gate comment (`:96-101`) → drop the sentence duplicated in `CollectionModel.isPasswordProtected`'s doc; `contentRatingUtils.ts:23-29` → drop "(Formerly keyed...)" archaeology, ADD the synthetic-tiles side effect sentence; fold `CollectionPage.tsx:63-65` inline into the function docblock.
**Remove:** `CollectionPageWrapper.tsx:87-88` (pre-change history); `contentRatingUtils.ts:27-29` (archaeology); `collections.ts:398` (restates the visible call, inconsistent with siblings).
**Keep (verified):** cover-strip rationale (`CollectionPage.tsx:44-46`); must-never constraint (`contentRendererUtils.ts:239-241`); download-vs-Selects distinction; all type-deprecation docblocks (every factual claim verified against backend).

---

# 3. Frontend PR #230 — /collections showcase, end dates, rating control

## 3.1 MERGE-BLOCKING

**230-G1. `/collections` caches a session-scoped endpoint in the shared Next data cache — up to 60 minutes stale on a brand-new public page.**
`app/collections/page.tsx:53` uses `getCollectionBySlug('all-collections', 0, 500)`, which opts into the data cache (`revalidate: 3600`, tag `collection-all-collections`). Verified against Next 16 internals: `force-dynamic` does NOT cancel an explicit revalidate (`patch-fetch.js:353`); anonymous responses (no Cookie header) are cached for an hour; nothing ever invalidates that tag on mutation. Consequence: LISTED→HIDDEN stays listed and linked up to 60 min; new publishes invisible up to 60 min; the file's own "Render on every request" comment is false for the data. **Not a cross-viewer leak** — `generateCacheKey` folds request headers into the key (verified in `incremental-cache/index.js:165-247`), so an admin's widened response can't serve to anonymous; the defect is staleness.
**Fix (one line):** use the purpose-built `getScopedAllCollections(500)` (`collections.ts:129`, `no-store`, `notFound()`-aware) — `CollectionPageWrapper.tsx:49` already branches to it for this exact slug.

**230-G2. The rating control lies twice: it "succeeds" before metadata loads, and failures are invisible unhandled rejections.** (Both #230 reviewers converged here.)
- `StructureTab.tsx:172-185` renders unconditionally (`CollectionEditSheet.tsx:55`, no `currentState` gate). While the admin fetch is in flight: `isHomeCollection` is false (briefly violating home-exclusion), `onChange` returns `undefined`, `await undefined` resolves, `RatingStars` paints the rating as applied — nothing was sent. And `initialRating` (`RatingStars.tsx:14`) is seeded once at mount, never re-synced when `currentState` arrives — a rated collection shows empty stars all session, inviting a silent overwrite of server state.
- Failure path: `useCollectionEdit.tsx:1570` passes the **raw** `updateCollectionRating` through (unlike every other mutation, which wraps with `setError(handleApiError(...))`); `RatingStars.handleClick` is try/finally with **no catch**; the promise is discarded at `onClick`. A 401/network/400 → stars snap back, no message, no log, "Uncaught (in promise) ApiError" in console. The pre-existing children-rating site shares the flaw; this PR promotes the pattern to a primary control.
**Fix:** (1) gate the section: `{collection?.id != null && !isHomeCollection && (...)}`; (2) wrap in the hook — catch → `logger.error` + `setError(handleApiError(...))` + **rethrow** (so RatingStars skips the optimistic commit); give `RatingStars` a catch that swallows after the caller surfaced (no unhandled rejection); (3) re-sync `initialRating` (sync effect) or key the control on loaded state.

## 3.2 SHOULD-FIX

**230-G3. Showcase error handling:** the bare `catch { collection = null }` (`page.tsx:52-55`) produces zero log lines on an outage AND swallows `notFound()`'s control-flow error (a genuinely missing `all-collections` serves a transient-sounding message with HTTP 200 forever). Fix: `unstable_rethrow(error)` (Next 16.2.6) then `logger.error`, then null. Also `logger.warn` in `extractCollectionBlocks` when `content` isn't an array (contract drift currently renders the cheerful empty state), and log when `blocks.length === 500` (silent truncation bound).
**230-G4. Performance:** every tile lazy-loads (`CollectionShowcaseTile.tsx:35-42`, no `priority`) — LCP regression; set `priority` on the first ~4 tiles via index. And up to 500 tiles each register their own window scroll listener (`useParallax.ts:141`, effect has no `isVisible` early-return) — paginate, add the early-return in `useParallax`, or drop parallax for this grid.
**230-G5. `.saveHeart:hover` (0,2,0) is dead — outranked by the wrapper rule (0,3,0)** (`SaveHeart.module.scss:68-76`); the intended brighten-on-direct-hover never fires. Add the compound selectors (`[data-parallax-container]:hover .saveHeart:hover, ...`).
**230-G6. A11y:** two adjacent unlabeled date inputs (`InfoTab.tsx:145-157` — label without `htmlFor`, input without `id`); use the `Field` pattern like every other field in the file and retrofit Collection Date. `aria-label={title}` on the tile link (`CollectionShowcaseTile.tsx:32`) suppresses the date from the accessible name — drop it (inner text already names the link), `alt=""` on the decorative cover.

## 3.3 Duplication

- **`CollectionShowcaseTile` is a ~90% copy of `LocationCollections`' CollectionCard** (component and ~70 of 154 SCSS lines — details in review notes). Extract `app/components/ui/CoverCard` taking `{href,title,imageUrl,width,height,sizes,priority?,subtitle?,children?}`; LocationCollections passes `children=<FollowButton/>`, the showcase passes `subtitle={dateLabel}`. Bonus: resolves an existing divergence (new file token-clean; twin uses raw `rgb()/#fff/0.75rem`).
- **Two ISO-date parsers added in one PR** (`formatDateRange.ts:43`, `groupCollectionsByYear.ts:33`): export `parseIsoDateParts`; `parseYear` = `parseIsoDateParts(iso)?.year ?? null`.
- **Two public browse surfaces** (`/collections` and `/all-collections`) render the same synthetic parent, both exclude `home`, no cross-link — pick a canonical (redirect the other) or at minimum share the exclusion list. Related: `'home'` is now hardcoded in 7 places — extract `HOME_SLUG`; add `collections` to the reserved-slug list (a collection slugged `collections` is now silently shadowed).

## 3.4 Smaller items

`let collection;` → type it `CollectionModel | null` (`page.tsx:51`); replace the hand-written `any`-based predicate with the existing `isContentCollection` guard (`page.tsx:37-40`); `parseYear` runs twice per collection (single map to pairs); redundant `string → string|null` casts (`InfoTab.tsx:126,153`); `sizes` under-declares desktop width (`(min-width: 768px) 33vw, 45vw`); `--color-warning` is documented as the star-fill color, not a semantic token (`InfoTab.module.scss:91`); `parseIsoDateParts` accepts impossible dates and its regex is unanchored at the end (harmless vs the backend contract; it's exported as a general util); three unused default exports (`formatDateRange.ts:112`, `groupCollectionsByYear.ts:93`, `CollectionShowcaseTile.tsx:56`); tile date labels mix raw ISO (`2026-06-01`) with formatted ranges (`Mar 3–7, 2026`) — the new page has no byte-parity constraint, format singles there; unparseable end date silently drops from the label (one `logger.warn`).

## 3.5 Tests — add / remove (#230)

**Add (top of list):** 1) `createHeaderRow` date metadata item — the call site this PR actually changed has zero coverage (every existing test sets `collectionDate: undefined`); three cases incl. the real byte-parity pin (`'2026-03-03'` verbatim) and the range (`'Mar 3–7, 2026'`); 2) `RatingStars` failure path + pending-disables-all (forces the 230-G2 design fix); 3) MenuDropdown Collections item (Explore has render/route/public coverage at `:166,:181,:196`; Collections has none); 4) InfoTab end-date input wiring (clear button → `('collectionEndDate', null)`; change → value; clear button absent when unset); 5) `buildUpdatePayload` set→different-set (only the from-unset branch is covered — an accidental-date-wipe mutant survives); 6) equal-date sort stability; 7) tile with missing cover + undated; 8) hook seeds `collectionEndDate` (`useCollectionEdit.tsx:373` — a copy-paste seed of the wrong field would silently corrupt saves); 9) reversed range — pin as-is (`'Mar 7–1, 2026'`) or decide a fallback; 10) low-value batch (advisory 4th combo; `{}`-shape page resilience; slug argument pin; parallax endDate threading — note **nothing reads `collectionEndDate` off the parallax model** — add one `toMatchObject` line or drop the speculative threading).
**Remove (5):** `formatDateRange.test.ts:15` (byte-identical duplicate of `:7`); the entire `'timezone-drift safety'` describe (`:72-84`, 3 tests) — the implementation never constructs a `Date` and the tests set no TZ, so they cannot fail timezone-dependently in any environment (placebo; if genuine protection is wanted, set `TZ=America/Los_Angeles` at jest-config level); `groupCollectionsByYear.test.ts:73` (both assertions implied by `:31`).
**Quality:** extract one shared edit-result fixture (InfoTab's hand-rolled 30-field `makeEdit` with `as unknown as` cast diverges from CollectionEditSheet's); comment the `makeParent` cast; if the timezone describe is kept despite the recommendation, rename it (it promises a guarantee it can't deliver).

## 3.6 Comment fixes (#230)

`Content.ts:113-116` — parallax `collectionEndDate` doc claims a consumer that doesn't exist (showcase reads raw blocks; replacement text in audit); `page.tsx:46` — "(prod returns LISTED collections only)" is wrong, scoping is per-session (admin/signed-in/anon — matches 230-G1's fix); `CollectionShowcaseTile.tsx:22` — "approximate ranges" → exact ranges. Condense: `groupCollectionsByYear` header vs function docblock (state the contract once); drop `@param/@returns` that restate signatures (`contentLayout.ts:190-191`); SaveHeart SCSS header vs section comments (keep the cascade-ordering sentence — genuinely invisible constraint). ADD two one-liners: above `isEndBeforeStart` ("Soft advisory only — never blocks save; ISO YYYY-MM-DD compares correctly as strings") and above the Rating section ("Rating orders multi-collection list views; home is excluded from lists, so no rating control"). The `convertCollectionContentToParallax` comment-fold itself is verified complete and a true no-op.

---

# 4. Frontend PR #231 — rowCombination rename/docblock trim

**Zero logic change is PROVEN, not assumed:** identifier-masked token skeleton (4,980 → 4,976 tokens, exactly one structural difference — the safe `{left, right}` shorthand collapse at `:674`, property names fixed by the `AbstractNode` union); rename-normalized text diff (only renames + one Prettier reflow); 61 string literals identical; no transposition at all 23 call sites; no shadowing (`key` at `:713` vs `:665` are different functions); `padRowToWidth` integration byte-identical except the one renamed call (`:308`); the deliberate vStack AR crossing at `:841-842` preserved exactly. All 10 rename families VERIFIED-CLEAN (including the unlisted-but-consistent `candidateCV → candidateWidthCost`). Inline-comment claim verified: 44 → 0 (the 49 remaining `//` lines are pre-existing section banners).

**Before merge (three one-line comment restorations — the trims deleted load-bearing rationale):**
1. `:841` — the rename made the deliberate AR crossing *look* like a copy-paste bug just as the PR deleted the comment defending it. Add to the `:824` docblock: `vStack inverts: leftFactor uses rightAR (and vice versa) — under 1/AR the smaller-AR child claims the larger share.`
2. `:596-598` — the trim welded a false causal claim ("Order is preserved ... **so** the split reflects prominence"). Replace: `Balances on effectiveRating, not width-cost, so the split reflects prominence rather than packing width. Order is preserved — only adjacent splits, never swaps.`
3. `:805-808` — restore the why-not-the-obvious-alternative: `A ceiling rather than a larger LAMBDA, which would over-square vertical-hero rows.`

**Nice-to-have restorations (one line each):** LAMBDA "replaces the retired AR_EQUITY_BAND gate" (`:798`); HERO_SOLO_WIDTH_FRACTION consequence clause (`:219`); `estimateRowAR` invariant "membership decisions never run the expensive search" (`:334`); harmonic-mean form note on `enumerateAssignments` (`:871`); `effectiveMinFill` constant-bar rationale (`:388` — or inline the alias); `newFill <= 1.35` overfill justification (`:451`); mid-row solo-hero deferral intent. Fix `:785` — "Stage-1 AR cost" mislabels `rowAR_Cost` (also used by the Stage-2 fallback ranking; replacement in audit). Reword `:58` `LOW_RATED_THRESHOLD` (parses two ways).

**Missed renames (optional, same-PR or follow-up):** `ac` → `component` (`:172,:827,:857` — the file's only remaining 2-letter param); `bfComponents`/`bfImgs` → `bestFit*` (`:515,:558`); `seqCount` → `sequentialCount` (`:435`); `rowAR_Cost` → `rowARCost` (`:788`, only snake/camel hybrid).

**Follow-up simplifications (NOT this PR):** three near-identical `leftOptions × rightOptions` double loops (`:882,:929,:1002`) → `forEachAssignmentPair(node, fn)` — genuinely invisible under `l`/`r`, a real dividend of the rename; `effectiveMinFill` alias → inline or re-document.

---

# 5. Cross-cutting

1. **Canonicalize the slugify parity spec** (touches #229 + existing code): full backend-parity contract moves to `locationUtils.slugify`; `tagUtils`/`Badge` keep short pointers. Same-fact-three-places today.
2. **Terminology:** standardize on "legacy/deprecated (admin-transitional)" for the type enum — #229 says "retired" in two places while #230 adds a fresh admin-side type read (`InfoTab.tsx:74`).
3. **Tri-state doc symmetry:** after both FE PRs merge, add one field-level line on `clearCollectionEndDate` ("Send clearCollectionEndDate: true (not null) to clear — see buildUpdatePayload").
4. **Phase-2 tracking issue** — still doesn't exist; V50's comment is the only record. Open it with the concrete deletion list (see 132-C12).
5. **FE CI** — add a PR workflow (tsc/jest/eslint). All three FE PRs shipped with zero checks.
6. **Backend/FE nullability pairing** — do 132-B11 (primitive booleans) and 229-F8 (required FE fields) together; keep the FE fail-safe predicates regardless (stale-cache window outlives both).

# 6. Suggested fix order (for the fixer agents)

**Backend #132 branch:** 132-B1 → B2 → B3 → B4(+test) → B5(+test) → B6 → B7 → B8 → B9 (run the SQL count first) → B10 → B11 → comment fixes (§1.7) → test removals (§1.6) → optimizations/duplication as time allows (§1.4-1.5). Re-run `mvn clean install` + `mvn spotless:apply`.

**Frontend #229 branch:** 229-F1 (decide: transitional derive vs form wiring) → F2 (+update the test that pins broken behavior) → F3 (decide predicate; add warn + tests) → F4 (decision: backend tags vs documented loss) → F5 → F6 → F7 → F8 → tests §2.4 → comments §2.5. Re-run `tsc --noEmit` + jest.

**Frontend #230 branch:** 230-G1 → G2 → G3 → G4 → G5 → G6 → duplication §3.3 (CoverCard can be follow-up) → tests §3.5 → comments §3.6.

**Frontend #231 branch:** the three comment restorations → optional nice-to-haves → merge. Missed renames + loop dedup as separate follow-ups.

**Merge sequence:** #132 → deploy → #229 → deploy → #230 (resolve the single five-field conflict against whichever of #229/#230 landed first) → #231 anytime.

---
*Review inputs: 14 agent reports (5 on #132; 3 on #229; 3 on #230; 1 on #231; 1 cross-repo contract/deploy-window; 1 cross-PR FE comment audit). Convergent findings were merged with all lenses credited; the two inter-reviewer contradictions (V9 index parity — resolved against the claim via V20's drop; /collections cache leak — resolved to staleness-only via Next cache-key internals) are reflected above.*
