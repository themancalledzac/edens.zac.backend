# RAW File Upload to S3 -- Design Spec

## Summary

Add RAW file upload support to the Lightroom-to-backend export pipeline. When images are exported from Lightroom through the portfolio export plugin, the original RAW source files (NEF, JPG from film scans, or any other format) are uploaded to S3 alongside the existing JPEG and WebP versions. The RAW S3 URL is stored in PostgreSQL on the `content_image` table.

## Context

### Current Flow
1. Lightroom renders JPEGs via the export plugin (`lightroom-portfolio-export`)
2. Plugin uploads rendered JPEGs in batches of 20 via multipart POST to `POST /api/admin/content/images/{collectionId}`
3. Backend uploads original JPEG to `Image/Full/{year}/{month}/`
4. Backend resizes + converts to WebP, uploads to `Image/Web/{year}/{month}/`
5. Backend extracts EXIF/XMP metadata from the JPEG and saves entity to PostgreSQL

### New Flow (additions only)
1. Plugin also retrieves the RAW source file path from the Lightroom catalog per photo
2. Plugin sends RAW file paths as lightweight string fields in the same multipart request
3. Backend reads each RAW file from local disk (backend always runs locally during Lightroom exports)
4. Backend uploads RAW file to `Image/Raw/{year}/{month}/`
5. Backend saves the RAW CloudFront URL to a new `image_url_raw` column

## Architecture Decisions

### RAW file path passed as string, not bytes
The backend always runs on the same machine as Lightroom during exports. The RAW file path is a simple string (~100 chars). The backend reads the file directly from local disk and streams it to S3. No RAW file bytes cross the HTTP boundary.

### Optional parameter -- frontend compatibility
The `rawFilePaths` request parameter is optional (`required = false`). When the frontend uploads images without RAW paths, the RAW step is skipped entirely. No behavioral change for non-Lightroom upload flows.

### No special error handling for missing RAW files
Lightroom will not allow export of a photo whose source file is missing. By the time the backend receives the request, the RAW file is guaranteed to exist on disk. Standard exception handling covers any truly unexpected failures.

### Format-agnostic
The backend does not validate or restrict RAW file types. Primary format is Nikon NEF; film scans are JPG. Any file format is accepted -- the backend detects MIME type from the file extension and uploads as-is. No processing or conversion is performed on RAW files.

## S3 Structure (final state)

```
edens.zac.portfolio/
  Image/
    Full/2025/03/DSC_1234.jpg       (original JPEG from Lightroom export)
    Web/2025/03/DSC_1234.webp        (resized + WebP converted)
    Raw/2025/03/DSC_1234.NEF         (original RAW source file)
```

## Changes by Component

### 1. Lightroom Plugin (`lightroom-portfolio-export`)

**File:** `PortfolioExportServiceProvider.lua`

**In `processRenderedPhotos()`:**

After rendering each photo, retrieve the original source file path:
```lua
local rawPath = rendition.photo:getRawMetadata("path")
```

When building `mimeChunks` for each batch, add a text field mapping the rendered filename to its RAW path:
```lua
table.insert(mimeChunks, {
    name = "rawFilePaths",
    value = renderedFilename .. "|" .. rawPath,
    contentType = "text/plain",
})
```

This produces repeated `rawFilePaths` multipart fields like:
```
rawFilePaths = "DSC_1234.jpg|/Users/me/Photos/2025/DSC_1234.NEF"
rawFilePaths = "DSC_1235.jpg|/Users/me/Photos/2025/DSC_1235.NEF"
```

**No other plugin changes.** Export dialog, batching (20 per batch), error handling, and create-collection flow remain unchanged.

### 2. Database Migration (backend)

New Flyway migration (next version number in sequence):

```sql
ALTER TABLE content_image ADD COLUMN image_url_raw VARCHAR(512);
```

Nullable column. Existing rows will have `NULL` for `image_url_raw`. No backfill needed.

### 3. Entity (backend)

**File:** `ContentImageEntity.java`

Add field:
```java
@Column(name = "image_url_raw")
private String imageUrlRaw;
```

### 4. Controller (backend)

**File:** `ContentControllerDev.java`

Add optional parameter to `createImages()`:
```java
@RequestParam(required = false) List<String> rawFilePaths
```

Parse into a `Map<String, String>` (rendered filename -> RAW file path) using the `|` delimiter. Pass the map to the service layer.

The `create-collection` endpoint (`createCollectionWithImages()`) receives the same optional parameter.

### 5. ImageProcessingService (backend)

**File:** `ImageProcessingService.java`

Add constant:
```java
private static final String PATH_IMAGE_RAW = "Image/Raw";
```

Add method or extend `prepareImageForUpload()`:
- Accept an optional RAW file path parameter
- When present: read file from disk, detect MIME type from extension, upload to S3 at `Image/Raw/{year}/{month}/{rawFilename}`
- Return the CloudFront URL in `PreparedImageData`

The `PreparedImageData` record gains an optional `imageUrlRaw` field (nullable String).

MIME type detection: map common extensions to MIME types:
- `.nef` -> `image/x-nikon-nef`
- `.cr2` -> `image/x-canon-cr2`
- `.arw` -> `image/x-sony-arw`
- `.dng` -> `image/x-adobe-dng`
- `.raf` -> `image/x-fuji-raf`
- `.jpg`/`.jpeg` -> `image/jpeg`
- `.tiff`/`.tif` -> `image/tiff`
- Fallback: `application/octet-stream`

### 6. ContentService (backend)

**File:** `ContentService.java`

In `createImagesParallel()`:
- Pass the RAW path map through to `ImageProcessingService` during Phase 1 (parallel processing)
- When saving entities in Phase 2, set `imageUrlRaw` from `PreparedImageData` if present

Deduplication behavior (in `savePreparedImageWithDedupe()`):
- On UPDATE (re-export with newer date): delete old RAW S3 file alongside old JPEG/WebP, save new RAW URL
- On SKIP: no change
- On CREATE: save RAW URL if present

### 7. DTOs/Models (backend)

Add `imageUrlRaw` (nullable String) to the image response DTO (`ContentModels.Image` or equivalent) so the RAW URL is available in API responses.

## What Does NOT Change

- Frontend image uploads: no `rawFilePaths` param sent, RAW step is skipped
- Batch sizing (20 images per batch)
- EXIF/XMP metadata extraction (still from the JPEG)
- Deduplication key (`original_filename` + `capture_date`)
- HTTP file size limits (RAW is read from local disk, not sent over HTTP)
- WebP conversion logic
- Collection management endpoints
- Production read endpoints

## Testing Strategy

### Backend Unit Tests
- `ImageProcessingService`: test RAW upload to S3 with mocked S3 client
- `ContentService`: test that `imageUrlRaw` is set on entity when RAW path provided, `null` when not
- Controller: test endpoint accepts optional `rawFilePaths` param, parses correctly

### Backend Integration Tests
- Upload with `rawFilePaths`: verify S3 keys created for all three formats, entity has all three URLs
- Upload without `rawFilePaths`: verify existing behavior unchanged, `imageUrlRaw` is null
- Dedup UPDATE case: verify old RAW S3 file deleted, new one saved

### Plugin Manual Testing
- Export 1 image: verify RAW path sent in multipart request
- Export batch of 20+: verify RAW paths included in all batches
- Verify film scan (JPG source) works alongside NEF source in same batch
