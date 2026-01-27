# Image Upload Flow - Complete Breakdown

## Overview

This document provides a comprehensive step-by-step breakdown of the image upload flow from client request to database/S3 storage and back to client response.

---

## Complete Flow: Client → Upload → Database/S3 → Client

### Phase 1: Client Request

**Location:** `ContentControllerDev.createImages()`

1. **Client sends POST request**
   - Endpoint: `POST /api/admin/content/images/{collectionId}`
   - Content-Type: `multipart/form-data`
   - Body: List of `MultipartFile` objects in `files` part
   - Path parameter: `collectionId` (Long)

2. **Controller receives and validates**
   - Validates files list is not null/empty
   - Returns 400 if validation fails

---

### Phase 2: Service Layer Processing

**Location:** `ContentServiceImpl.createImages()`

3. **Service method entry**
   - Validates files using `ContentValidator`
   - Verifies collection exists (1 database query: `collectionDao.findById()`)
   - Gets max order index for collection (1 database query: `collectionContentDao.getMaxOrderIndexForCollection()`)

4. **Loop through each file** (for each image file):

#### 4a. Duplicate Detection Check

- **Location:** `ContentServiceImpl.createImages()` (lines 691-708)
- **Action:** Check if image already exists
- **Database Query:** `contentDao.existsByFileIdentifier(fileIdentifier)`
- **Problem:** Uses `LocalDate.now().toString()` format (YYYY-MM-DD) but actual format is "YYYY-MM/filename.jpg"
- **Result:** This check is likely ineffective due to format mismatch
- If duplicate found: Skip file and continue to next

#### 4b. Image Processing

- **Location:** `ContentProcessingUtil.processImageContent()`
- **Called with:** `file` (MultipartFile), `title` (null)

---

### Phase 3: Image Processing (Core Logic)

**Location:** `ContentProcessingUtil.processImageContent()`

#### Step 1: Check for Existing Image

- **Action:** Extract base filename (e.g., "DSC_6275" from "DSC_6275.webp")
- **Database Query:** `contentDao.findImagesByBaseFilename(baseFilename)`
- **Purpose:** Find existing JPG when processing WebP to reuse metadata
- **Result:**
  - If found: Reuse ALL metadata from existing image (no extraction needed)
  - If not found: Proceed to metadata extraction

#### Step 1b: Extract Metadata (if no existing image)

- **Location:** `ContentProcessingUtil.extractImageMetadata()`
- **Action:** Read file input stream and extract EXIF/XMP metadata
- **File Read:** `file.getInputStream()` - **FIRST FILE READ**
- **Library:** Drew Noakes metadata-extractor
- **Extracts:** createDate, imageWidth, imageHeight, ISO, author, rating, fStop, shutterSpeed, focalLength, location, camera, lens, etc.
- **Additional:** Reads XMP metadata for rating, blackAndWhite
- **Fallback:** If dimensions missing, reads image again to get from BufferedImage
- **Result:** Map<String, String> with all metadata

#### Step 1c: Parse Date Information

- **Action:** Parse EXIF createDate to get year/month for S3 path organization
- **Method:** `parseImageDate()` - extracts year/month from "YYYY:MM:DD HH:MM:SS" format
- **Result:** `imageYear`, `imageMonth` for S3 path organization
- **Note:** The `createDate` string is already extracted via `ImageMetadata.CREATE_DATE` enum
  - Stored in metadata map as "createDate" (string format: "2026:01:26 17:48:38")
  - Used for `createDate` field (string) in entity
  - **BUT:** NOT converted to `LocalDateTime` for `createdAt` field

#### Step 2: Upload Original JPG to S3

- **Location:** `ContentProcessingUtil.uploadImageToS3()`
- **Action:** Upload original full-size image
- **File Read:** `file.getBytes()` - **SECOND FILE READ** (entire file loaded into memory)
- **S3 Path:** `Image/Full/{year}/{month}/{originalFilename}`
- **S3 Operation:** `amazonS3.putObject()` - **FIRST S3 UPLOAD**
- **Result:** CloudFront URL stored in `imageUrlOriginal`

#### Step 3: Resize Image

- **Action:** Resize image if needed (max 2500px on longest side)
- **File Read:** `ImageIO.read(file.getInputStream())` - **THIRD FILE READ**
- **Note:** File is read AGAIN from input stream
- **Processing:** Resize BufferedImage using Graphics2D
- **Metadata Update:** Updates metadata map with new dimensions if resized
- **Result:** Resized `BufferedImage` object

#### Step 4: Convert to WebP

- **Action:** Convert resized image to WebP format with compression
- **Method:** `convertJpgToWebP(resizedImage)`
- **Processing:** Uses ImageIO with WebP writer, quality 0.85
- **Filename:** Changes extension to `.webp` (e.g., "DSC_6275.jpg" → "DSC_6275.webp")
- **Result:** `byte[]` array of WebP image data

#### Step 5: Upload WebP to S3

- **Location:** `ContentProcessingUtil.uploadImageToS3()`
- **Action:** Upload web-optimized WebP image
- **S3 Path:** `Image/Web/{year}/{month}/{finalFilename.webp}`
- **S3 Operation:** `amazonS3.putObject()` - **SECOND S3 UPLOAD**
- **Result:** CloudFront URL stored in `imageUrlWeb`

#### Step 6: Build Entity

- **Action:** Create `ContentImageEntity` builder with all metadata
- **Data Sources:**
  - Metadata map (from extraction or existing image)
  - S3 URLs (from uploads)
  - File identifier: `"{year}-{month}/{originalFilename}"`
- **CRITICAL ISSUE:** `createdAt` field is NOT set on entity builder
  - `createDate` (string) is set from metadata: `.createDate(metadata.getOrDefault("createDate", ...))`
  - `createdAt` (LocalDateTime) is NOT set - remains null
  - Result: `ContentDao.saveImage()` defaults `createdAt` to `LocalDateTime.now()` (upload time)

#### Step 6a: Handle Camera

- **Action:** Find or create camera entity
- **Database Query:** `contentCameraDao.findByCameraNameIgnoreCase(cameraName)`
- **If not found:** Create new camera (1 database insert: `contentCameraDao.save()`)
- **Result:** Camera entity set on image entity

#### Step 6b: Handle Lens

- **Action:** Find or create lens entity
- **Database Query:** `contentLensDao.findByLensNameIgnoreCase(lensName)`
- **If not found:** Create new lens (1 database insert: `contentLensDao.save()`)
- **Result:** Lens entity set on image entity

#### Step 7: Save Image to Database

- **Location:** `ContentDao.saveImage()`
- **Action:** Insert image entity into database
- **Database Operations:**
  1.  Insert into `content` table (base content record)
      - Returns generated ID
  2.  Insert into `content_image` table (image-specific data)
      - Uses same ID from content table
- **Transaction:** Both inserts happen in single transaction
- **Result:** Saved `ContentImageEntity` with ID populated

---

### Phase 4: Collection Association

**Location:** `ContentServiceImpl.createImages()` (after image processing)

#### Step 8: Create Collection-Content Join Entry

- **Action:** Link image to collection via join table
- **Entity:** `CollectionContentEntity` with:
  - `collectionId`
  - `contentId` (from saved image)
  - `orderIndex` (incremented for each image)
  - `visible` (true by default)
- **Database Insert:** `collectionContentDao.save(joinEntry)` - **THIRD DATABASE INSERT**

#### Step 9: Convert to Model for Response

- **Location:** `ContentProcessingUtil.convertEntityToModel(CollectionContentEntity)`
- **Action:** Convert entity to model for JSON response
- **Database Queries:**
  1.  `contentDao.findAllByIds([contentId])` - Load base content to determine type
  2.  `contentDao.findImageById(contentId)` - Load full image entity with JOINs
      - This query includes LEFT JOINs for camera, lens, filmType
- **Problem:** We just saved the image, but now we're querying it back from database
- **Result:** `ContentImageModel` with all data populated

---

### Phase 5: Response to Client

**Location:** `ContentControllerDev.createImages()`

10. **Return response**
    - Status: 201 CREATED
    - Body: List of `ContentImageModel` objects (JSON)
    - Each model contains:
      - Image metadata (ISO, f-stop, camera, lens, etc.)
      - S3 URLs (original and web)
      - Collection-specific metadata (orderIndex, visible)
      - All relationships (tags, people - though empty on creation)

---

## Database Operations Summary

### Per Image File:

1. **Collection validation:** `collectionDao.findById()` - 1 query
2. **Max order index:** `collectionContentDao.getMaxOrderIndexForCollection()` - 1 query (once per batch)
3. **Duplicate check:** `contentDao.existsByFileIdentifier()` - 1 query (ineffective due to format bug)
4. **Existing image lookup:** `contentDao.findImagesByBaseFilename()` - 1 query
5. **Camera lookup/create:** `contentCameraDao.findByCameraNameIgnoreCase()` - 1 query, possibly 1 insert
6. **Lens lookup/create:** `contentLensDao.findByLensNameIgnoreCase()` - 1 query, possibly 1 insert
7. **Save image:** `contentDao.saveImage()` - 2 inserts (content + content_image tables)
8. **Save join entry:** `collectionContentDao.save()` - 1 insert
9. **Convert to model:** `contentDao.findAllByIds()` + `contentDao.findImageById()` - 2 queries

**Total per image:** ~10-12 database operations (queries + inserts)

---

## File I/O Operations Summary

### Per Image File:

1. **Metadata extraction:** `file.getInputStream()` - Read file for EXIF/XMP
2. **Upload original:** `file.getBytes()` - Read entire file into memory
3. **Resize processing:** `ImageIO.read(file.getInputStream())` - Read file again
4. **WebP conversion:** Process in-memory BufferedImage
5. **S3 uploads:** 2 uploads (original JPG + WebP)

**Total:** File is read **3 times** from input stream

---

## S3 Operations Summary

### Per Image File:

1. **Upload original JPG:** `Image/Full/{year}/{month}/{filename}`
2. **Upload WebP:** `Image/Web/{year}/{month}/{filename.webp}`

**Total:** 2 S3 PUT operations per image

---

## Issues and Optimization Opportunities

### Critical Issues

#### 1. **File Read Redundancy**

- **Problem:** File is read 3 times from input stream
  - Once for metadata extraction
  - Once for S3 upload (`getBytes()`)
  - Once for resize (`ImageIO.read()`)
- **Impact:** Unnecessary I/O, memory usage, potential stream exhaustion
- **Solution:** Read file once into byte array, reuse for all operations

#### 2. **Ineffective Duplicate Detection**

- **Problem:** Duplicate check uses wrong date format
  - Uses: `LocalDate.now().toString()` → "2026-01-26/filename.jpg"
  - Actual format: "2026-01/filename.jpg" (YYYY-MM, not YYYY-MM-DD)
- **Impact:** Duplicate detection never works, allows duplicate uploads
- **Solution:** Fix date format to match actual fileIdentifier format

#### 3. **Redundant Database Queries After Save**

- **Problem:** After saving image, we immediately query it back for model conversion
  - Save: `contentDao.saveImage()` - inserts entity
  - Then: `contentDao.findImageById()` - queries it back
- **Impact:** Unnecessary database round-trip
- **Solution:** Use the saved entity directly, or build model from entity before saving

#### 4. **Missing createdAt from EXIF Metadata**

- **Problem:** `createdAt` field is not being set from extracted EXIF `createDate`
  - `ImageMetadata.CREATE_DATE` enum already extracts `createDate` as string (e.g., "2026:01:26 17:48:38")
  - This string is stored in metadata map and used for `createDate` field
  - **BUT:** The string is NOT parsed to `LocalDateTime` for `createdAt` field
  - Entity builder does NOT set `.createdAt()` - it remains null
  - `ContentDao.saveImage()` sees null and defaults to `LocalDateTime.now()` (upload time)
- **Root Cause:** Missing connection between extracted `createDate` string and `createdAt` LocalDateTime field
- **Impact:** Images get upload time instead of original EXIF creation date
- **Solution:** Parse the `createDate` string from metadata map to `LocalDateTime` and set on entity builder
  - This should be a simple addition: parse `metadata.get("createDate")` and set `.createdAt(parsedDateTime)`
  - The metadata extraction is already working - we just need to use it for `createdAt`

#### 5. **Metadata Extraction Timing**

- **Current:** Extract metadata, then upload original, then read again for resize
- **Better:** Read file once, extract metadata, then use same data for upload/resize
- **Impact:** Reduces file reads from 3 to 1

### Optimization Opportunities

#### 1. **Batch Database Operations**

- **Current:** Each image processed individually with separate queries
- **Opportunity:** Batch camera/lens lookups for all images in upload
- **Benefit:** Reduce database round-trips

#### 2. **Eliminate Redundant Query**

- **Current:** `convertEntityToModel()` queries database to reload entity
- **Opportunity:** Pass saved entity directly to conversion, or build model before save
- **Benefit:** Eliminate 2 database queries per image

#### 3. **Stream Processing**

- **Current:** Load entire file into memory multiple times
- **Opportunity:** Process file in single pass: read → extract → resize → convert → upload
- **Benefit:** Lower memory footprint, faster processing

#### 4. **Parallel S3 Uploads**

- **Current:** Upload original, then upload WebP sequentially
- **Opportunity:** Upload both in parallel (if not already optimized by S3 SDK)
- **Benefit:** Faster upload completion

#### 5. **Cache Metadata Extraction**

- **Current:** Extract metadata every time, even for WebP (which has no EXIF)
- **Current State:** Already optimized - checks for existing image first
- **Note:** This is already implemented correctly

#### 6. **Transaction Optimization**

- **Current:** Each image save is in separate transaction
- **Opportunity:** Batch all images in single transaction (if acceptable)
- **Benefit:** Faster batch uploads, atomic operation

### Recommended Refactoring Priority

1. **CRITICAL PRIORITY:**
   - **Fix createdAt field from EXIF metadata** - Parse `createDate` string to `LocalDateTime` and set on entity
     - This is a simple fix: the metadata is already extracted, just need to parse and use it
     - Add parsing logic when building entity: `.createdAt(parseExifDateToLocalDateTime(metadata.get("createDate")))`
     - Should be a single line addition to entity builder

2. **HIGH PRIORITY:**
   - Fix duplicate detection date format bug
   - Eliminate redundant file reads (read once, reuse)
   - Remove redundant database query after save (use saved entity directly)

3. **MEDIUM PRIORITY:**
   - Batch camera/lens lookups for multiple images
   - Optimize model conversion to not require database reload

4. **LOW PRIORITY:**
   - Parallel S3 uploads (if not already optimized)
   - Transaction batching (consider rollback implications)

---

## Step-by-Step Flow Diagram

```
Client Request
    ↓
Controller (ContentControllerDev)
    ↓
Service (ContentServiceImpl.createImages)
    ├─ Validate files
    ├─ Verify collection exists [DB Query 1]
    ├─ Get max order index [DB Query 2]
    └─ For each file:
        ├─ Duplicate check [DB Query 3 - BUG: wrong format]
        └─ Process image (ContentProcessingUtil.processImageContent)
            ├─ Check existing image [DB Query 4]
            ├─ Extract metadata OR reuse [File Read 1]
            │   └─ createDate extracted as string via ImageMetadata.CREATE_DATE
            ├─ Upload original to S3 [File Read 2, S3 Upload 1]
            ├─ Resize image [File Read 3]
            ├─ Convert to WebP
            ├─ Upload WebP to S3 [S3 Upload 2]
            ├─ Build entity [BUG: createdAt NOT set from createDate string]
            ├─ Find/create camera [DB Query 5, possibly Insert 1]
            ├─ Find/create lens [DB Query 6, possibly Insert 2]
            ├─ Save image [DB Insert 3, Insert 4]
            │   └─ createdAt defaults to LocalDateTime.now() (upload time) - BUG
            └─ Return entity
        ├─ Create join entry [DB Insert 5]
        ├─ Convert to model [DB Query 7, DB Query 8 - REDUNDANT]
        └─ Add to results
    ↓
Return JSON response to client
```

---

## Summary Statistics

**Per Image File:**

- **Database Operations:** 10-12 (queries + inserts)
- **File Reads:** 3 separate reads from input stream
- **S3 Uploads:** 2 (original + WebP)
- **Memory Allocations:** File loaded into memory multiple times
- **Processing Time:** Sequential operations (could be parallelized)

**For Batch of 10 Images:**

- **Database Operations:** ~100-120
- **File Reads:** ~30
- **S3 Uploads:** 20
- **Total Round-trips:** Significant overhead from redundant operations

---

## Critical Bug: createdAt Field Not Using EXIF Metadata

### The Problem

Images are getting `created_at` set to the upload time (e.g., `2026-01-26 06:57:36`) instead of the original EXIF creation date (e.g., August 2025).

### Root Cause Analysis

1. **Metadata Extraction is Working:**
   - `ImageMetadata.CREATE_DATE` enum is defined (line 84-88 in `ImageMetadata.java`)
   - Uses `SimpleStringExtractor` to extract date as string
   - Extracts from EXIF tags: "Date/Time Original", "Date/Time"
   - Extracts from XMP: `NS_EXIF.DateTimeOriginal`
   - Successfully stores in metadata map as "createDate" (string format: "2026:01:26 17:48:38")

2. **String Field is Set:**
   - Entity builder sets: `.createDate(metadata.getOrDefault("createDate", ...))`
   - This stores the EXIF date as a STRING in the `createDate` field
   - This works correctly

3. **LocalDateTime Field is NOT Set:**
   - Entity builder does NOT set `.createdAt()` field
   - The `createDate` string is NOT parsed to `LocalDateTime`
   - `createdAt` remains `null` on the entity

4. **Database Defaults to Upload Time:**
   - `ContentDao.saveImage()` checks: `entity.getCreatedAt() != null ? entity.getCreatedAt() : now`
   - Since `createdAt` is null, it defaults to `LocalDateTime.now()` (current upload time)

### The Fix

This should be a **simple metadata row addition** or fixing a broken connector:

**Option 1: Parse existing metadata string**

- When building entity, parse the `createDate` string from metadata map
- Convert "2026:01:26 17:48:38" format to `LocalDateTime`
- Set on entity: `.createdAt(parseExifDateToLocalDateTime(metadata.get("createDate")))`

**Option 2: Add DateTimeExtractor to ImageMetadata**

- Create a new `DateTimeExtractor` that converts EXIF date string to `LocalDateTime`
- Add a new metadata field `CREATED_AT` that uses this extractor
- This would be more consistent with the existing metadata extraction pattern

**Recommended:** Option 1 is simpler and faster to implement. The metadata is already extracted correctly - we just need to parse it and use it.

### Code Location

- **Entity Builder:** `ContentProcessingUtil.processImageContent()` around line 760
- **Current:** `.createDate(metadata.getOrDefault("createDate", ...))` - sets string field
- **Missing:** `.createdAt(parseExifDateToLocalDateTime(metadata.get("createDate")))` - should set LocalDateTime field

---

## Conclusion

The current implementation works but has several inefficiencies:

1. **CRITICAL:** `createdAt` field not using EXIF metadata (defaults to upload time)
2. File is read multiple times unnecessarily
3. Database queries happen redundantly (save then reload)
4. Duplicate detection is broken
5. Opportunities for batching and parallelization exist

The highest impact fixes would be:

1. **Fix createdAt from EXIF metadata** (simple parse and set)
2. Fix duplicate detection
3. Read file once and reuse
4. Eliminate redundant database queries after save

The `createdAt` bug is the most critical as it causes incorrect timestamps in the database, affecting sorting, filtering, and display of images by their actual creation date.
