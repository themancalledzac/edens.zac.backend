# RAW File Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upload original RAW source files to S3 alongside JPEG/WebP during Lightroom exports, storing the RAW URL in PostgreSQL.

**Architecture:** The Lightroom plugin sends RAW file paths as lightweight strings in the existing multipart upload request. The backend reads RAW files from local disk (backend always runs locally during Lightroom exports), uploads them to `Image/Raw/{year}/{month}/` in S3, and stores the CloudFront URL in a new `image_url_raw` column on `content_image`.

**Tech Stack:** Spring Boot 3.4.1, Java 23, AWS SDK v2 (S3), PostgreSQL + Flyway, Lua (Lightroom SDK)

---

## File Map

**Backend (edens.zac.backend):**
- Create: `src/main/resources/db/migration/V10__add_image_url_raw.sql`
- Modify: `src/main/java/edens/zac/portfolio/backend/entity/ContentImageEntity.java:87-88`
- Modify: `src/main/java/edens/zac/portfolio/backend/model/ContentModels.java:21-50`
- Modify: `src/main/java/edens/zac/portfolio/backend/services/ContentModelConverter.java:358-394`
- Modify: `src/main/java/edens/zac/portfolio/backend/services/ImageProcessingService.java:80-104,133-208,218-268,445-448`
- Modify: `src/main/java/edens/zac/portfolio/backend/controller/dev/ContentControllerDev.java:56-80,191-224`
- Modify: `src/main/java/edens/zac/portfolio/backend/services/ContentService.java:723-785,794-817`
- Modify: `src/test/java/edens/zac/portfolio/backend/controller/dev/ContentControllerDevTest.java` (existing test file)

**Lightroom Plugin (lightroom-portfolio-export):**
- Modify: `PortfolioExport.lrdevplugin/PortfolioExportServiceProvider.lua:499-551`

---

### Task 1: Database Migration -- Add `image_url_raw` Column

**Files:**
- Create: `src/main/resources/db/migration/V10__add_image_url_raw.sql`

- [ ] **Step 1: Create migration file**

```sql
-- V10__add_image_url_raw.sql
-- Add column for RAW file S3 URL (nullable - only populated for Lightroom exports)
ALTER TABLE content_image ADD COLUMN image_url_raw VARCHAR(512);
```

- [ ] **Step 2: Verify migration applies cleanly**

Run: `mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/portfolio -Dflyway.user=$POSTGRES_USER -Dflyway.password=$POSTGRES_PASS`

If Flyway is not configured as a Maven plugin, verify by running the app:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
Check logs for: `Successfully applied 1 migration to schema "public", now at version v10`

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V10__add_image_url_raw.sql
git commit -m "feat: add image_url_raw column to content_image table"
```

---

### Task 2: Entity -- Add `imageUrlRaw` Field

**Files:**
- Modify: `src/main/java/edens/zac/portfolio/backend/entity/ContentImageEntity.java`

- [ ] **Step 1: Add `imageUrlRaw` field to `ContentImageEntity`**

Add after the existing `imageUrlOriginal` field (after line 88):

```java
  /** Column: image_url_raw (VARCHAR) - S3 URL for original RAW source file */
  private String imageUrlRaw;
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/edens/zac/portfolio/backend/entity/ContentImageEntity.java
git commit -m "feat: add imageUrlRaw field to ContentImageEntity"
```

---

### Task 3: DTO -- Add `imageUrlRaw` to `ContentModels.Image`

**Files:**
- Modify: `src/main/java/edens/zac/portfolio/backend/model/ContentModels.java:21-50`
- Modify: `src/main/java/edens/zac/portfolio/backend/services/ContentModelConverter.java:358-394`

- [ ] **Step 1: Add `imageUrlRaw` field to `ContentModels.Image` record**

The current record at `ContentModels.java:21` is:
```java
  public record Image(
      Long id,
      ContentType contentType,
      String title,
      String description,
      String imageUrl,
      Integer orderIndex,
      Boolean visible,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      Integer imageWidth,
      Integer imageHeight,
      Integer iso,
      String author,
      Integer rating,
      String fStop,
      Records.Lens lens,
      Boolean blackAndWhite,
      Boolean isFilm,
      String filmType,
      FilmFormat filmFormat,
      String shutterSpeed,
      Records.Camera camera,
      String focalLength,
      Records.Location location,
      LocalDateTime captureDate,
      List<Records.Tag> tags,
      List<Records.Person> people,
      List<Records.ChildCollection> collections)
      implements ContentModel {
```

Add `String imageUrlRaw` after `String imageUrl` (after `description`). The `imageUrl` field maps to the web URL. Add the new field right after it:

```java
  public record Image(
      Long id,
      ContentType contentType,
      String title,
      String description,
      String imageUrl,
      String imageUrlRaw,
      Integer orderIndex,
      ...
```

- [ ] **Step 2: Update `ContentModelConverter.buildImageRecord()` to pass `imageUrlRaw`**

In `ContentModelConverter.java`, the `buildImageRecord` method at line 365 constructs the Image record positionally. Add `entity.getImageUrlRaw()` after `entity.getImageUrlWeb()`:

Current (line 365-393):
```java
    return new ContentModels.Image(
        entity.getId(),
        entity.getContentType(),
        entity.getTitle(),
        null,
        entity.getImageUrlWeb(),
        orderIndex,
        ...
```

Updated:
```java
    return new ContentModels.Image(
        entity.getId(),
        entity.getContentType(),
        entity.getTitle(),
        null,
        entity.getImageUrlWeb(),
        entity.getImageUrlRaw(),
        orderIndex,
        ...
```

- [ ] **Step 3: Fix any other call sites that construct `ContentModels.Image`**

Search for `new ContentModels.Image(` across the codebase. There should only be the one in `buildImageRecord`. If there are others in test files, add a `null` for the new `imageUrlRaw` parameter in the correct position.

Run: `mvn compile -q`
Expected: BUILD SUCCESS (fix any compilation errors from positional record constructor changes)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/edens/zac/portfolio/backend/model/ContentModels.java
git add src/main/java/edens/zac/portfolio/backend/services/ContentModelConverter.java
git commit -m "feat: add imageUrlRaw to Image DTO and model converter"
```

---

### Task 4: ImageProcessingService -- RAW File Upload to S3

**Files:**
- Modify: `src/main/java/edens/zac/portfolio/backend/services/ImageProcessingService.java`

- [ ] **Step 1: Write failing test for RAW upload**

Create or add to the existing test file. The test verifies that when a RAW file path is provided, the service reads it from disk and uploads to S3 under `Image/Raw/`.

```java
@Test
void prepareImageForUpload_withRawFilePath_uploadsRawToS3() throws IOException {
    // Create a temp file to simulate a RAW file on disk
    Path tempRaw = Files.createTempFile("test-raw-", ".NEF");
    Files.write(tempRaw, "fake-raw-data".getBytes());

    try {
        MultipartFile mockFile = createMockJpegFile("DSC_1234.jpg");

        PreparedImageData result = imageProcessingService.prepareImageForUpload(
            mockFile, tempRaw.toString());

        assertNotNull(result.imageUrlRaw());
        assertTrue(result.imageUrlRaw().contains("Image/Raw/"));
        assertTrue(result.imageUrlRaw().endsWith(".NEF"));
    } finally {
        Files.deleteIfExists(tempRaw);
    }
}

@Test
void prepareImageForUpload_withoutRawFilePath_returnsNullRawUrl() throws IOException {
    MultipartFile mockFile = createMockJpegFile("DSC_1234.jpg");

    PreparedImageData result = imageProcessingService.prepareImageForUpload(mockFile, null);

    assertNull(result.imageUrlRaw());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl . -Dtest="ImageProcessingServiceTest" -q`
Expected: FAIL -- `prepareImageForUpload` doesn't accept a RAW path parameter yet

- [ ] **Step 3: Add `PATH_IMAGE_RAW` constant and MIME type helper**

At line 84, after `PATH_GIF_THUMBNAIL`, add:

```java
  private static final String PATH_IMAGE_RAW = "Image/Raw";
```

Add a MIME type detection method in the file type helpers section (after line 623):

```java
  /**
   * Detect MIME type from file extension for RAW and common image formats.
   *
   * @param filename The filename with extension
   * @return The MIME type string
   */
  private String detectMimeType(String filename) {
    if (filename == null) {
      return "application/octet-stream";
    }
    String lower = filename.toLowerCase();
    if (lower.endsWith(".nef")) return "image/x-nikon-nef";
    if (lower.endsWith(".cr2")) return "image/x-canon-cr2";
    if (lower.endsWith(".cr3")) return "image/x-canon-cr3";
    if (lower.endsWith(".arw")) return "image/x-sony-arw";
    if (lower.endsWith(".dng")) return "image/x-adobe-dng";
    if (lower.endsWith(".raf")) return "image/x-fuji-raf";
    if (lower.endsWith(".orf")) return "image/x-olympus-orf";
    if (lower.endsWith(".rw2")) return "image/x-panasonic-rw2";
    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
    if (lower.endsWith(".tiff") || lower.endsWith(".tif")) return "image/tiff";
    if (lower.endsWith(".png")) return "image/png";
    if (lower.endsWith(".webp")) return "image/webp";
    return "application/octet-stream";
  }
```

- [ ] **Step 4: Add `imageUrlRaw` to `PreparedImageData` record**

Update the record at line 94:

```java
  public record PreparedImageData(
      String originalFilename,
      String imageUrlOriginal,
      String imageUrlWeb,
      String imageUrlRaw,
      Map<String, String> metadata,
      List<String> extractedTags,
      List<String> extractedPeople,
      int imageYear,
      int imageMonth,
      LocalDateTime captureDate,
      LocalDateTime lastExportDate) {}
```

- [ ] **Step 5: Add overloaded `prepareImageForUpload` method with RAW path**

Add a new method that accepts the optional RAW file path. Keep the existing no-arg version as a delegate:

```java
  /**
   * Prepare an image for upload without RAW file processing.
   * Delegates to the full method with null rawFilePath.
   */
  public PreparedImageData prepareImageForUpload(MultipartFile file) throws IOException {
    return prepareImageForUpload(file, null);
  }

  /**
   * Prepare an image for upload: extract metadata, upload to S3, resize, convert to WebP.
   * Optionally uploads the original RAW source file to S3.
   * This method does NO database calls and is safe to run in parallel virtual threads.
   *
   * @param file The image file to process
   * @param rawFilePath Optional absolute path to the RAW source file on local disk
   * @return PreparedImageData with S3 URLs and metadata, ready for DB save
   * @throws IOException If there's an error processing the file
   */
  public PreparedImageData prepareImageForUpload(MultipartFile file, String rawFilePath)
      throws IOException {
```

In the body, after the WebP upload (after line 187), add the RAW upload logic:

```java
    // Upload RAW source file to S3 if path provided
    String imageUrlRaw = null;
    if (rawFilePath != null && !rawFilePath.isBlank()) {
      Path rawPath = Path.of(rawFilePath);
      byte[] rawBytes = Files.readAllBytes(rawPath);
      String rawFilename = rawPath.getFileName().toString();
      String rawMimeType = detectMimeType(rawFilename);
      imageUrlRaw =
          uploadToS3(rawBytes, rawFilename, rawMimeType, PATH_IMAGE_RAW, imageYear, imageMonth);
      log.info("Uploaded RAW file to S3: {}", rawFilename);
    }
```

Update the return statement to include `imageUrlRaw`:

```java
    return new PreparedImageData(
        originalFilename,
        imageUrlOriginal,
        imageUrlWeb,
        imageUrlRaw,
        metadata,
        extraction.extractedTags(),
        extraction.extractedPeople(),
        imageYear,
        imageMonth,
        captureDate,
        lastExportDate);
```

- [ ] **Step 6: Update `savePreparedImageWithDedupe` to handle RAW URL**

In the UPDATE branch (around line 252), add after the existing URL updates:

```java
        existing.setImageUrlRaw(prepared.imageUrlRaw());
```

And capture the old RAW URL for deletion (around line 249):

```java
        final String oldImageUrlRaw = existing.getImageUrlRaw();
```

After the existing S3 deletions (after line 265), add:

```java
        deleteS3ObjectByUrl(oldImageUrlRaw);
```

In the CREATE branch (around line 272), add to the entity builder after `.imageUrlWeb(prepared.imageUrlWeb())` (line 293):

```java
            .imageUrlRaw(prepared.imageUrlRaw())
```

- [ ] **Step 7: Update `deleteImageFromS3` to include RAW file**

At line 445-448, update:

```java
  public void deleteImageFromS3(ContentImageEntity image) {
    deleteS3ObjectByUrl(image.getImageUrlWeb());
    deleteS3ObjectByUrl(image.getImageUrlOriginal());
    deleteS3ObjectByUrl(image.getImageUrlRaw());
  }
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `mvn test -q`
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add src/main/java/edens/zac/portfolio/backend/services/ImageProcessingService.java
git add src/test/java/edens/zac/portfolio/backend/services/ImageProcessingServiceTest.java
git commit -m "feat: add RAW file upload to S3 in ImageProcessingService"
```

---

### Task 5: Controller -- Accept `rawFilePaths` Parameter

**Files:**
- Modify: `src/main/java/edens/zac/portfolio/backend/controller/dev/ContentControllerDev.java:56-80,191-224`

- [ ] **Step 1: Add `rawFilePaths` parameter to `createImages` endpoint**

Update the method signature at line 59:

```java
  public ResponseEntity<ImageUploadResult> createImages(
      @PathVariable Long collectionId,
      @RequestParam(value = "locationId", required = false) Long locationId,
      @RequestParam(value = "rawFilePaths", required = false) List<String> rawFilePaths,
      @RequestPart(value = "files", required = true) List<MultipartFile> files) {
```

Parse `rawFilePaths` into a map and pass to service. Update the body:

```java
    // Parse rawFilePaths: each entry is "renderedFilename|/path/to/raw.NEF"
    Map<String, String> rawFilePathMap = parseRawFilePaths(rawFilePaths);

    ImageUploadResult result =
        contentService.createImagesParallel(collectionId, files, rawFilePathMap);
```

- [ ] **Step 2: Add `rawFilePaths` parameter to `createCollectionWithImages` endpoint**

Update the method signature at line 194 to add the parameter after `collectionDate`:

```java
      @RequestParam(value = "rawFilePaths", required = false) List<String> rawFilePaths,
      @RequestPart(value = "files", required = true) List<MultipartFile> files) {
```

Update the service call:

```java
    Map<String, String> rawFilePathMap = parseRawFilePaths(rawFilePaths);

    ImageUploadResult result =
        contentService.createCollectionWithImages(createRequest, files, rawFilePathMap);
```

- [ ] **Step 3: Add `parseRawFilePaths` helper method**

Add at the bottom of the controller class:

```java
  /**
   * Parse rawFilePaths parameter entries into a map of rendered filename to RAW file path.
   * Each entry is formatted as "renderedFilename|/absolute/path/to/raw.NEF".
   *
   * @param rawFilePaths List of "filename|path" strings, or null
   * @return Map of rendered filename to RAW path, empty map if input is null
   */
  private Map<String, String> parseRawFilePaths(List<String> rawFilePaths) {
    if (rawFilePaths == null || rawFilePaths.isEmpty()) {
      return Map.of();
    }
    return rawFilePaths.stream()
        .filter(entry -> entry != null && entry.contains("|"))
        .collect(
            java.util.stream.Collectors.toMap(
                entry -> entry.substring(0, entry.indexOf('|')),
                entry -> entry.substring(entry.indexOf('|') + 1),
                (existing, replacement) -> replacement));
  }
```

- [ ] **Step 4: Verify compilation**

Run: `mvn compile -q`
Expected: FAIL -- `ContentService.createImagesParallel` doesn't accept the map yet (that's Task 6)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/edens/zac/portfolio/backend/controller/dev/ContentControllerDev.java
git commit -m "feat: accept rawFilePaths parameter in image upload endpoints"
```

---

### Task 6: ContentService -- Pass RAW Paths Through Pipeline

**Files:**
- Modify: `src/main/java/edens/zac/portfolio/backend/services/ContentService.java`

- [ ] **Step 1: Update `createImagesParallel` to accept RAW path map**

Update method signature at line 723:

```java
  public ImageUploadResult createImagesParallel(
      Long collectionId, List<MultipartFile> files, Map<String, String> rawFilePathMap) {
```

- [ ] **Step 2: Update `prepareImageAsync` to accept and use RAW path**

Update the method signature and body at line 794:

```java
  private PreparedImage prepareImageAsync(MultipartFile file, String rawFilePath) {
    String filename = file.getOriginalFilename();
    try {
      log.debug("Preparing image: {}", filename);

      if (file.getContentType() == null
          || !file.getContentType().startsWith("image/")
          || file.getContentType().equals("image/gif")) {
        log.debug("Skipping non-image or GIF: {}", filename);
        return null;
      }

      ImageProcessingService.PreparedImageData prepared =
          imageProcessingService.prepareImageForUpload(file, rawFilePath);

      return new PreparedImage(prepared, filename);

    } catch (Exception e) {
      log.error("Failed to prepare image {}: {}", filename, e.getMessage(), e);
      return null;
    }
  }
```

- [ ] **Step 3: Update the parallel processing loop to look up RAW paths**

In `createImagesParallel`, update the `CompletableFuture` mapping (around line 748-753):

```java
      List<CompletableFuture<PreparedImage>> futures =
          batch.stream()
              .map(
                  file -> {
                    String rawPath = rawFilePathMap.getOrDefault(
                        file.getOriginalFilename(), null);
                    return CompletableFuture.supplyAsync(
                        () -> prepareImageAsync(file, rawPath), imageProcessingExecutor);
                  })
              .toList();
```

- [ ] **Step 4: Update `createCollectionWithImages` to accept and pass RAW path map**

Update method signature at line 606:

```java
  public ImageUploadResult createCollectionWithImages(
      CollectionRequests.Create createRequest,
      List<MultipartFile> files,
      Map<String, String> rawFilePathMap) {
```

Update the call to `createImagesParallel` at line 612:

```java
    ImageUploadResult result = createImagesParallel(newCollectionId, files, rawFilePathMap);
```

- [ ] **Step 5: Update `savePreparedImageWithDedupe` entity save to include `imageUrlRaw`**

This was already handled in Task 4, Step 6. Verify the `imageUrlRaw` field is being set in both CREATE and UPDATE paths by checking the entity builder and update code.

- [ ] **Step 6: Verify full compilation and tests pass**

Run: `mvn clean compile -q && mvn test -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/edens/zac/portfolio/backend/services/ContentService.java
git commit -m "feat: pass RAW file paths through image upload pipeline"
```

---

### Task 7: DAO -- Add `imageUrlRaw` to Database Queries

**Files:**
- Check: `src/main/java/edens/zac/portfolio/backend/dao/ContentRepository.java` and its JDBC implementation

- [ ] **Step 1: Check if `saveImage` and row mappers handle new column**

Read the `ContentRepository` implementation (likely `ContentRepositoryImpl` or similar JDBC class). The project uses `NamedParameterJdbcTemplate`, not JPA repositories, so INSERT/UPDATE SQL and row mappers need the new column.

Search for the `saveImage` method and the row mapper that builds `ContentImageEntity`. Add `image_url_raw` to:
- The INSERT SQL statement
- The UPDATE SQL statement
- The row mapper that reads `ContentImageEntity` from `ResultSet`

For the INSERT, add `image_url_raw` to both the column list and values:
```sql
... image_url_original, image_url_raw, capture_date ...
... :imageUrlOriginal, :imageUrlRaw, :captureDate ...
```

For the row mapper, add:
```java
entity.setImageUrlRaw(rs.getString("image_url_raw"));
```

For the parameter map in saveImage, add:
```java
params.addValue("imageUrlRaw", entity.getImageUrlRaw());
```

- [ ] **Step 2: Verify compilation and tests**

Run: `mvn clean compile -q && mvn test -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/edens/zac/portfolio/backend/dao/
git commit -m "feat: add image_url_raw to content_image SQL queries and row mapper"
```

---

### Task 8: Lightroom Plugin -- Send RAW File Paths

**Files:**
- Modify: `PortfolioExport.lrdevplugin/PortfolioExportServiceProvider.lua`

- [ ] **Step 1: Capture RAW file path during rendition collection**

In `processRenderedPhotos()`, update the rendition loop (around line 501-519). After `rendition:waitForRender()`, get the source photo's file path:

```lua
  for i, rendition in exportSession:renditions() do
    local success, pathOrMessage = rendition:waitForRender()
    progressScope:setPortionComplete(i - 1, nPhotos)

    if progressScope:isCanceled() then
      break
    end

    if success then
      -- Get original source file path (RAW/NEF/JPG on disk)
      local rawPath = rendition.photo:getRawMetadata("path")

      table.insert(renderedFiles, {
        path = pathOrMessage,
        rendition = rendition,
        rawPath = rawPath,
      })
    else
      failCount = failCount + 1
      logger:warn("Render failed for photo: " .. tostring(pathOrMessage))
    end
  end
```

- [ ] **Step 2: Add RAW file path entries to mimeChunks**

In the batch upload loop (around line 544-551), after adding the file chunk, add a text field for the RAW path:

```lua
      local mimeChunks = {}
      for i = batchStart, batchEnd do
        local renderedFilename = LrPathUtils.leafName(renderedFiles[i].path)
        table.insert(mimeChunks, {
          name = "files",
          fileName = renderedFilename,
          filePath = renderedFiles[i].path,
          contentType = "image/jpeg",
        })

        -- Send RAW source file path for backend to upload to S3
        if renderedFiles[i].rawPath then
          table.insert(mimeChunks, {
            name = "rawFilePaths",
            value = renderedFilename .. "|" .. renderedFiles[i].rawPath,
            contentType = "text/plain",
          })
        end
      end
```

- [ ] **Step 3: Manual test**

1. Open Lightroom, select 1-2 photos
2. Export via Portfolio Export plugin to local backend
3. Check backend logs for `Uploaded RAW file to S3:` messages
4. Verify S3 bucket has files under `Image/Raw/{year}/{month}/`
5. Verify database has `image_url_raw` populated

- [ ] **Step 4: Commit (in the lightroom-portfolio-export repo)**

```bash
cd /Users/themancalledzac/Code/lightroom-portfolio-export
git add PortfolioExport.lrdevplugin/PortfolioExportServiceProvider.lua
git commit -m "feat: send RAW source file paths to backend during export"
```

---

### Task 9: End-to-End Verification

- [ ] **Step 1: Run full backend test suite**

```bash
cd /Users/themancalledzac/Code/edens.zac.backend
mvn clean test -q
```
Expected: BUILD SUCCESS

- [ ] **Step 2: Run code formatting**

```bash
mvn spotless:apply
```

- [ ] **Step 3: Run checkstyle**

```bash
mvn checkstyle:check
```

- [ ] **Step 4: Commit any formatting fixes**

```bash
git add -A
git commit -m "style: apply spotless formatting"
```

- [ ] **Step 5: Verify API response includes `imageUrlRaw`**

Start the backend locally, upload an image via the API (without RAW path), and verify:
- `imageUrlRaw` is `null` in the JSON response
- No errors, no behavioral changes for non-Lightroom uploads

- [ ] **Step 6: Final commit and summary**

Review all changes with `git log --oneline` to ensure clean commit history.
