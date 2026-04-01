# ContentService Refactor: Extract Upload Pipeline & Eliminate Duplication

**Date:** 2026-04-01
**Branch:** From `0073-tag-people-update-endpoints` (or new branch)
**Scope:** ContentService.java (1254 lines) decomposition + 7 duplication pattern fixes

## Problem

ContentService.java is a 1254-line god class that owns:
- Image CRUD (query, update, delete, search)
- Two distinct upload pipelines (web multipart + disk/Lightroom)
- Collection orchestration (create-with-images, post-upload processing)
- Thread infrastructure (2 ExecutorServices, 1 Semaphore, shutdown lifecycle)
- Text and GIF content creation

It contains 7 identified duplication patterns and 5 methods violating single-responsibility.

## Approach: Extract Upload Pipeline + Shared Helpers

### 1. New File: `ImageUploadPipelineService.java`

A new `@Service` class that owns all upload orchestration. Approximately 600 lines move out of ContentService.

**Methods that move (with current line ranges in ContentService):**

| Method | Lines | Description |
|---|---|---|
| `createCollectionWithImages` | 616-633 | Orchestrate new-collection + upload |
| `processFilesFromDisk` | 643-661 | Kick off background disk upload job |
| `processFilesFromDiskBackground` | 663-672 | Background error wrapper |
| `processFilesFromDiskLoop` | 674-801 | Core disk-upload loop |
| `createImagesParallel` | 927-1007 | Web-upload phase 1 (parallel S3 prep) |
| `prepareImageAsync` | 1016-1039 | Virtual-thread image preparation |
| `saveProcessedImages` | 1050-1150 | Web-upload phase 2 (DB save + wiring) |
| `postUploadProcessing` | 831-868 | Post-upload collection metadata |
| `deriveCollectionDate` | 870-884 | Set collection date from EXIF |
| `selectCoverImage` | 886-898 | Pick highest-rated cover image |
| `linkToStagingCollection` | 900-909 | Link to staging collection |
| `PreparedImage` record | 1152-1153 | Internal data carrier |

**Infrastructure that moves:**
- `imageProcessingExecutor` (virtual thread executor for parallel image processing)
- `rawUploadExecutor` (background executor for RAW file uploads)
- `uploadSemaphore` (prevents concurrent upload OOM)
- `shutdown()` (`@PreDestroy` lifecycle)
- `PARALLEL_BATCH_SIZE` constant
- `STAGING_COLLECTION_SLUG` constant

**Dependencies injected into `ImageUploadPipelineService`:**
- `CollectionRepository collectionRepository`
- `PersonRepository personRepository`
- `ImageProcessingService imageProcessingService`
- `ContentMutationUtil contentMutationUtil`
- `ContentModelConverter contentModelConverter`
- `ContentValidator contentValidator`
- `CollectionService collectionService`
- `JobTrackingService jobTrackingService`
- `CacheManager cacheManager`
- `ContentService contentService` (only for `setCollectionLocationIfMissing` and `nextOrderIndex` — see shared helpers below)

### 2. Shared Helpers to Extract (Eliminate All 7 Duplication Patterns)

#### Pattern 1: RAW Upload Scheduling (3 locations -> 1)

Extract a private method in `ImageUploadPipelineService`:

```java
private void scheduleRawUploadIfNeeded(
    ImageProcessingService.DedupeResult dedupeResult,
    String rawFilePath, int year, int month) {
  if (rawFilePath == null || rawFilePath.isBlank()) return;
  boolean isCreate = dedupeResult.action() == ImageProcessingService.DedupeAction.CREATE;
  if (!isCreate && dedupeResult.entity().getImageUrlRaw() != null) return;

  Long imageId = dedupeResult.entity().getId();
  rawUploadExecutor.submit(
      () -> imageProcessingService.uploadRawAndUpdateDb(imageId, rawFilePath, year, month));
}
```

**Replaces:**
- `saveProcessedImages` lines 1086-1100
- `processFilesFromDiskLoop` CREATE branch lines 743-752
- `processFilesFromDiskLoop` UPDATE branch lines 765-778

**Behavioral note:** The current disk-path CREATE branch unconditionally schedules RAW upload (no `imageUrlRaw` check). The unified helper adds the CREATE guard (`isCreate` short-circuits the null check). This is correct — a newly created image always has null `imageUrlRaw`.

#### Pattern 2: Collection Link Entry Creation (5 locations -> 1)

Promote `linkImageToCollection` to accept an explicit `orderIndex` parameter and make it a package-private method on `ContentService` (so `ImageUploadPipelineService` can call it), or move it to a shared location:

```java
void linkContentToCollection(Long collectionId, Long contentId, int orderIndex) {
  CollectionContentEntity joinEntry = CollectionContentEntity.builder()
      .collectionId(collectionId)
      .contentId(contentId)
      .orderIndex(orderIndex)
      .visible(true)
      .build();
  collectionRepository.saveContent(joinEntry);
}
```

**Replaces:**
- `linkImageToCollection` lines 815-825 (currently computes orderIndex internally)
- `saveProcessedImages` lines 1118-1127
- `createTextContent` lines 1180-1188
- `createGif` lines 1229-1237
- `handleAddToCollections` lines 474-483 (this one also sets `visible` from input — the helper should accept an optional `visible` parameter or the caller handles it)

**Decision:** This method stays in ContentService (since `createTextContent` and `createGif` remain there) and becomes package-private so `ImageUploadPipelineService` can access it. Both classes are in the same package.

The overload for `handleAddToCollections` where `visible` comes from the request will call `linkContentToCollection(collectionId, contentId, orderIndex, visible)` with a 4-arg version.

#### Pattern 3: Post-Dedupe Wiring (2 locations -> 1)

Both `saveProcessedImages` and `processFilesFromDiskLoop` perform the same three steps after a successful dedupe:
1. `contentMutationUtil.associateExtractedKeywords(entityId, tags, people)`
2. `scheduleRawUploadIfNeeded(dedupeResult, rawFilePath, year, month)`
3. Link to collection (with UPDATE duplicate-check)

Extract into `ImageUploadPipelineService`:

```java
private void wireImageAfterDedupe(
    ImageProcessingService.DedupeResult dedupeResult,
    List<String> tags, List<String> people,
    String rawFilePath, int year, int month,
    Long collectionId, int orderIndex) {

  contentMutationUtil.associateExtractedKeywords(
      dedupeResult.entity().getId(), tags, people);

  scheduleRawUploadIfNeeded(dedupeResult, rawFilePath, year, month);

  // For UPDATE, skip collection link if already present
  if (dedupeResult.action() == ImageProcessingService.DedupeAction.UPDATE) {
    Optional<CollectionContentEntity> existing =
        collectionRepository.findContentByCollectionIdAndContentId(
            collectionId, dedupeResult.entity().getId());
    if (existing.isPresent()) return;
  }

  contentService.linkContentToCollection(collectionId, dedupeResult.entity().getId(), orderIndex);
}
```

#### Pattern 4: Ensure Plugin People Exist (pre-flight in disk upload)

Extract to a private method in `ImageUploadPipelineService`:

```java
private Set<String> ensurePluginPeopleExist(DiskUploadRequest request) {
  List<ContentPersonEntity> existingPeople = personRepository.findAllByOrderByPersonNameAsc();
  Set<String> existingSlugs = existingPeople.stream()
      .map(ContentPersonEntity::getSlug)
      .collect(Collectors.toSet());

  request.files().stream()
      .filter(f -> f.people() != null)
      .flatMap(f -> f.people().stream())
      .filter(name -> existingSlugs.add(SlugUtil.generateSlug(name)))
      .forEach(name -> {
        personRepository.save(new ContentPersonEntity(name));
        log.info("Created new person from plugin: {}", name);
      });

  // Return all known people names for tag filtering
  Set<String> allKnownPeople = existingPeople.stream()
      .map(p -> p.getPersonName().toLowerCase())
      .collect(Collectors.toCollection(HashSet::new));
  request.files().stream()
      .filter(f -> f.people() != null)
      .flatMap(f -> f.people().stream())
      .forEach(name -> allKnownPeople.add(name.toLowerCase()));

  return allKnownPeople;
}
```

#### Pattern 5: ContentModel Type Cast (3 locations -> 1)

Extract to a private static helper in ContentService (used by `createTextContent`, `createGif`) and another instance in `ImageUploadPipelineService` (or make it package-private):

```java
@SuppressWarnings("unchecked")
static <T extends ContentModel> T castContentModel(
    ContentModel model, Class<T> expectedType) {
  if (expectedType.isInstance(model)) {
    return expectedType.cast(model);
  }
  throw new IllegalStateException(
      "Expected " + expectedType.getSimpleName() + " but got "
          + (model != null ? model.getClass().getSimpleName() : "null"));
}
```

**Replaces:**
- `createTextContent` lines 1197-1204
- `createGif` lines 1245-1252
- `saveProcessedImages` lines 1109-1113 and 1130-1133

#### Pattern 6: `nextOrderIndex` Shared Access

Currently private in ContentService. Make it package-private so `ImageUploadPipelineService` can call it:

```java
int nextOrderIndex(Long collectionId) {
  Integer maxOrder = collectionRepository.getMaxOrderIndexForCollection(collectionId);
  return maxOrder != null ? maxOrder + 1 : 0;
}
```

#### Pattern 7: `updateImages` Response Assembly (inline -> extracted)

Extract the 30-line `ContentImageUpdateResponse` builder (lines 253-302) into:

```java
private Map<String, Object> buildUpdateResponse(
    List<ContentModels.Image> updatedImages,
    Set<TagEntity> newTags, Set<ContentPersonEntity> newPeople,
    Set<ContentCameraEntity> newCameras, Set<ContentLensEntity> newLenses,
    Set<ContentFilmTypeEntity> newFilmTypes, List<String> errors) { ... }
```

This stays in ContentService. No behavioral change.

### 3. ContentService After Refactor

**Estimated size:** ~550 lines (down from 1254)

**Remaining public methods:**
- `createTag`, `createPerson` (thin delegations)
- `updateImages` (batch image mutation)
- `deleteImages`
- `searchImages`, `getAllImages`
- `setCollectionLocationIfMissing`
- `createTextContent`, `createGif`

**Remaining private methods:**
- `applyImageUpdatesWithTracking`
- `updateImageTagsOptimized`, `updateImagePeopleOptimized`
- `handleAddToCollections`
- `buildUpdateResponse` (new, extracted from `updateImages`)
- `linkContentToCollection` (promoted from `linkImageToCollection`, package-private)
- `nextOrderIndex` (promoted to package-private)
- `castContentModel` (new static helper)

Note: `evictGeneralMetadataCache` moves to `ImageUploadPipelineService` since it is only called from `processFilesFromDiskLoop`. The `CacheManager` dependency is already listed for the new service.

**Removed dependencies from ContentService:**
- `JobTrackingService` (only used by upload pipeline)
- `TransactionTemplate` (only used by `postUploadProcessing`, which moves to the pipeline service)

### 4. Controller Changes

**`ContentControllerDev`** currently injects only `ContentService`. After refactor:
- Inject `ImageUploadPipelineService` for: `createCollectionWithImages`, `createImagesParallel`, `processFilesFromDisk`
- Keep `ContentService` for: `updateImages`, `deleteImages`, `createTextContent`, `createGif`, and all read endpoints

Changes are mechanical — swap `contentService.X()` to `imageUploadPipelineService.X()` for the three upload endpoints.

### 5. What Does NOT Change

- No database schema changes
- No API contract changes (same endpoints, same request/response shapes)
- No changes to `ImageProcessingService`, `ContentMutationUtil`, `ContentModelConverter`, `MetadataService`
- No changes to models, entities, or DTOs
- No changes to test contracts (though tests may need to inject the new service)

## File Change Summary

| File | Action | Estimated Delta |
|---|---|---|
| `services/ImageUploadPipelineService.java` | CREATE | ~650 lines |
| `services/ContentService.java` | EDIT (major) | 1254 -> ~550 lines |
| `controller/dev/ContentControllerDev.java` | EDIT (minor) | Inject new service, swap 3 call sites |

## Risk Assessment

- **Low risk:** All logic is being moved, not rewritten. Method signatures and behavior remain identical.
- **Medium risk:** The shared helpers (`wireImageAfterDedupe`, `scheduleRawUploadIfNeeded`) consolidate logic that currently has subtle differences between the disk and web paths. The spec calls out each difference explicitly (e.g., CREATE RAW upload guard). These must be preserved in the unified helper.
- **Verification:** Run `mvn clean install` after each phase. The existing test suite covers the upload paths. Manual verification with a Lightroom export is recommended for the disk path.

## Success Criteria

1. `ContentService.java` is under 600 lines
2. Zero duplicated patterns (all 7 eliminated)
3. `mvn clean install` passes (compile + tests + spotless + checkstyle)
4. All existing API endpoints return identical responses
5. Upload pipeline (web + disk) works identically to pre-refactor
