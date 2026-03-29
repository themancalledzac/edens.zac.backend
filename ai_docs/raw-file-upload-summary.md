# RAW File Upload Feature - Implementation Summary

## What Was Built

When images are exported from the Lightroom plugin through the backend, the original RAW source files (NEF, JPG film scans, etc.) are now uploaded to S3 alongside the existing JPEG and WebP versions.

## How It Works

1. **Lightroom plugin** (`lightroom-portfolio-export`) retrieves each photo's source file path via `photo:getRawMetadata("path")` and sends it as a `rawFilePaths` multipart text field (format: `renderedFilename|/absolute/path/to/raw.NEF`)
2. **Backend controller** parses the optional `rawFilePaths` parameter into a `Map<String, String>` and passes it through the service layer
3. **ImageProcessingService** reads the RAW file from local disk, detects MIME type from extension, and uploads to `Image/Raw/{year}/{month}/` in S3
4. **Database** stores the CloudFront URL in `content_image.image_url_raw` (nullable VARCHAR(512), added by V10 migration)

## Key Design Decisions

- **RAW path as string, not bytes**: Backend always runs locally during Lightroom exports, so it reads RAW files directly from disk. No large file transfer over HTTP.
- **Optional parameter**: `rawFilePaths` is `required = false`. Frontend uploads work unchanged (imageUrlRaw is null).
- **Format-agnostic**: Supports NEF, CR2, CR3, ARW, DNG, RAF, ORF, RW2, JPG, TIFF via `detectMimeType()`. Fallback: `application/octet-stream`.
- **V10 migration already applied** to the EC2 PostgreSQL database (ran on 2026-03-29 via local dev startup). Does not break the production backend -- the column is nullable and the old code ignores it.

## S3 Structure

```
edens.zac.portfolio/Image/
  Full/2025/03/DSC_1234.jpg    (original JPEG)
  Web/2025/03/DSC_1234.webp     (optimized WebP)
  Raw/2025/03/DSC_1234.NEF      (original RAW - NEW)
```

## Files Changed

### Backend (`edens.zac.backend`, branch `0071-raw-file-feature`)
- `V10__add_image_url_raw.sql` - Migration
- `ContentImageEntity.java` - Added `imageUrlRaw` field
- `ContentModels.java` - Added `imageUrlRaw` to Image record
- `ContentModelConverter.java` - Passes `imageUrlRaw` to DTO
- `ImageProcessingService.java` - `PATH_IMAGE_RAW`, `detectMimeType()`, overloaded `prepareImageForUpload(file, rawFilePath)`, dedup/delete handles RAW
- `ContentControllerDev.java` - Optional `rawFilePaths` param on both upload endpoints, `parseRawFilePaths()` helper
- `ContentService.java` - Threads `rawFilePathMap` through `createImagesParallel()` and `createCollectionWithImages()`
- `ContentRepository.java` - INSERT, UPDATE, SELECT, GROUP BY, row mapper all include `image_url_raw`

### Lightroom Plugin (`lightroom-portfolio-export`, branch `main`)
- `PortfolioExportServiceProvider.lua` - Captures `rawPath` per photo, sends as `rawFilePaths` multipart field

## Status

- All 384 backend tests pass
- Spotless + checkstyle clean
- Not yet tested end-to-end with actual Lightroom export
- Production EC2 backend does NOT have the new code yet (only the migration)

## Specs & Plans

- Design spec: `docs/superpowers/specs/2026-03-29-raw-file-upload-design.md`
- Implementation plan: `docs/superpowers/plans/2026-03-29-raw-file-upload.md`
