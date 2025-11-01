# Collection Hierarchical Refactor

## Random todos:

- Verify our removal of all 'code' content, and consolidation of 'content' to 'contentText'

## Recent Updates (2025-11-01)

### ✅ Completed Today
1. **Fixed CollectionServiceImpl.deleteCollection()**
   - Now properly deletes only join table entries (CollectionContentEntity)
   - Content blocks are preserved as they're reusable across collections
   - Prevents data loss when deleting collections

2. **Moved Image Upload Logic to ContentService**
   - Removed `CollectionServiceImpl.addContent()` method
   - Implemented `ContentServiceImpl.createImages()` method
   - Proper separation of concerns: CollectionService manages collections, ContentService manages content
   - ContentControllerDev can now properly call contentService.createImages()

3. **Reviewed All CollectionControllerProd Endpoints**
   - ✅ GET / (getAllCollections) - Pagination working correctly
   - ✅ GET /{slug} (getCollectionBySlug) - Using join table for paginated content
   - ✅ GET /type/{type} (getCollectionsByType) - Returns HomeCardModel list correctly
   - ✅ POST /{slug}/access (validateClientGalleryAccess) - Working (password protection temporarily disabled)
   - All endpoints properly use the new join table architecture

## Concept
Collections are **folders** containing **content**. Content can be images, text, code, gifs, OR other collections.

## Architecture

### Before (Old Many-to-Many)
```
Collection <-> Join Table <-> ContentBlocks (images only)
```

### After (Hierarchical)
```
Collection (folder)
  |-> List<ContentModel> content
       |- ContentImageModel
       |- ContentTextModel
       |- ContentCodeModel
       |- ContentGifModel
       '- ContentCollectionModel (link to another collection)
```

## Database Structure

### Three-Layer Architecture

**LAYER 1: Collections** (the "folder")
```sql
collection
  ├─ id (PK)
  ├─ title, slug, description, location
  ├─ collection_date, visible
  ├─ type (BLOG, PORTFOLIO, ART_GALLERY, CLIENT_GALLERY, HOME, MISC)
  ├─ cover_image_id (FK -> content.id, nullable)
  └─ created_at, updated_at
```
Note: Remove `priority` field (use `orderIndex` in join table instead)

**LAYER 2: Join Table** (collection-content association)
```sql
collection_content
  ├─ id (PK)
  ├─ collection_id (FK -> collection.id)
  ├─ content_id (FK -> content.id)  <- NOT polymorphic! Points to base table
  ├─ order_index (ordering within collection)
  ├─ caption (collection-specific caption)
  └─ visible (collection-specific visibility)
```

**Purpose**: Same content can appear in multiple collections with different order/caption/visibility

**Join Table Fields Explained**:

These three fields exist ONLY in the context of "this content in this collection":

1. **order_index** (Integer, required)
   - Where does this content appear in this collection?
   - Lower values appear first
   - Same image could be position 1 in blog, position 5 in portfolio
   - Allows manual ordering/reordering within each collection

2. **caption** (String, 500 chars, optional)
   - What caption does this content have in this specific collection?
   - Same image could have different captions:
     - Blog: "My vacation photo from Iceland"
     - Portfolio: "Professional landscape photography"
     - Client gallery: No caption
   - Collection-specific storytelling

3. **visible** (Boolean, required, default: true)
   - Is this content visible in this specific collection?
   - Same image could be:
     - Visible in published portfolio
     - Hidden in draft blog post
     - Visible in client gallery
   - Allows per-collection publish/draft workflow

**Example Scenario**:
```
Content: image_001.jpg (a sunset photo)

In Blog "Iceland Trip" (collection_id: 1):
  - order_index: 3
  - caption: "Sunset on our last day in Reykjavik"
  - visible: true

In Portfolio "Landscapes" (collection_id: 5):
  - order_index: 1
  - caption: "Golden Hour - Iceland 2024"
  - visible: true

In Client Gallery "Draft Work" (collection_id: 12):
  - order_index: 8
  - caption: null
  - visible: false
```

**LAYER 3a: Content Base Table** (abstract, JOINED inheritance)
```sql
content (base table)
  ├─ id (PK)
  ├─ content_type (IMAGE, TEXT, CODE, GIF, COLLECTION)
  ├─ created_at
  └─ updated_at
```
Note: NO collection_id! Content is independent and reusable

**LAYER 3b: Content Type Tables** (JOINED inheritance children)
```sql
content_image (PK = content_id, FK -> content.id)
  ├─ content_id (PK, FK)
  ├─ image_url_web, image_url_full_size
  ├─ image_width, image_height
  ├─ iso, f_stop, shutter_speed, focal_length
  ├─ camera_id (FK), lens_id (FK), film_type_id (FK)
  ├─ file_identifier (unique)
  └─ ... (all image metadata)

content_text (PK = content_id, FK -> content.id)
  ├─ content_id (PK, FK)
  └─ text_content (TEXT/LONGTEXT)

content_code (PK = content_id, FK -> content.id)
  ├─ content_id (PK, FK)
  ├─ code_content (TEXT)
  └─ language

content_gif (PK = content_id, FK -> content.id)
  ├─ content_id (PK, FK)
  ├─ gif_url, thumbnail_url
  └─ width, height

content_collection (PK = content_id, FK -> content.id)  <- NEW!
  ├─ content_id (PK, FK)
  └─ referenced_collection_id (FK -> collection.id)
```

### Key Design Benefits

1. **Content Reusability**: Same image can be in blog post, portfolio, and client gallery
2. **Content Library**: Create/upload content before assigning to collections
3. **Not Polymorphic FK**: `collection_content.content_id -> content.id` is a normal FK
4. **JPA Handles Polymorphism**: Hibernate joins to correct child table based on `content_type`
5. **Collection-Specific Metadata**: `caption`, `orderIndex`, `visible` in join table (same image can have different captions in different collections)

## Key Changes Required

### 1. Add COLLECTION Type
```java
// ContentType.java
enum ContentType {
    IMAGE, TEXT, GIF, CODE, COLLECTION  // <- Add COLLECTION
}
```

### 2. Create ContentCollectionEntity
```java
@Entity
@Table(name = "content_collection")
public class ContentCollectionEntity extends ContentEntity {
    private Long referencedCollectionId;  // FK to collection table
}
```

### 3. Implement ContentCollectionModel
```java
public class ContentCollectionModel extends ContentModel {
    // Minimal embedded collection data for frontend
    private Long referencedCollectionId;
    private String referencedSlug;
    private String referencedTitle;
    private CollectionType referencedType;
    private ContentImageModel referencedCoverImage;
}
```

### 4. Update Services
- `CollectionProcessingUtil.convertToModel()` - handle ContentCollectionEntity
- `CollectionServiceImpl` - support creating/updating collection references

## Routing Examples

```
/home
  -> Collection (type: HOME)
     -> content: [ContentCollectionModel, ContentCollectionModel, ...]

/blog/{slug}
  -> Collection (type: BLOG)
     -> content: [ContentImageModel, ContentTextModel, ...]

/portfolio
  -> Special endpoint returning all PORTFOLIO collections

/portfolio/{slug}
  -> Collection (type: PORTFOLIO)
     -> content: [ContentImageModel, ContentCollectionModel, ...]
```

## Migration Tasks

### Phase 1: Entity Refactoring (Critical - Changes Architecture)
- [ ] **Refactor ContentEntity** - Remove fields that belong in join table:
  - Remove `collectionId` field (content is now independent)
  - Remove `orderIndex` field (moves to collection_content)
  - Remove `caption` field (moves to collection_content)
  - Remove `visible` field (moves to collection_content)
  - Remove `collection` ManyToOne relationship
- [ ] **Create CollectionContentEntity** - New join table entity:
  - Fields: id, collectionId, contentId, orderIndex, caption, visible
  - ManyToOne to CollectionEntity
  - ManyToOne to ContentEntity (polymorphic via JOINED inheritance)
- [ ] **Update CollectionEntity** - Change relationship:
  - Remove: `List<ContentEntity> content`
  - Add: `List<CollectionContentEntity> collectionContent`
  - Add helper method: `getContent()` to extract ContentEntity list
- [ ] **Add COLLECTION to ContentType enum**
- [ ] **Create ContentCollectionEntity** (extends ContentEntity):
  - Field: `referencedCollectionId` (FK to collection table)
  - ManyToOne relationship to CollectionEntity

### Phase 2: Models & DTOs
- [ ] **Create CollectionContentModel** - DTO for join table data
  - Fields: orderIndex, caption, visible
  - Used for collection-specific metadata
- [ ] **Implement ContentCollectionModel** (extends ContentModel):
  - Minimal embedded collection data (id, slug, title, type, coverImage)
  - Prevent deep nesting - don't include full nested content
- [ ] **Update ContentModel base class** - Remove join table fields if present

### Phase 3: Repository Layer
- [x] **Create CollectionContentRepository** - ✅ For join table operations (COMPLETE)
  - All join table query methods implemented
  - Pagination support added
  - Content removal/reordering methods working
- [ ] **Create ContentCollectionRepository** - For collection content type (future - for nested collections)
- [x] **Update ContentRepository** - ✅ Collection-specific queries updated
- [x] **Update CollectionRepository** - ✅ Queries updated for new join table structure

### Phase 4: Service Layer Updates
- [x] **Update ContentProcessingUtil**:
  - ✅ Add conversion for ContentCollectionEntity -> ContentCollectionModel
  - ✅ Populate referenced collection data (basic info only)
  - ✅ Handle polymorphic content conversion via join table
- [x] **Update CollectionProcessingUtil**:
  - ✅ Convert CollectionContentEntity to models
  - ✅ Handle join table in convertToModel() methods
  - ✅ Populate referenced collection data for ContentCollectionModel
- [x] **Update CollectionServiceImpl**:
  - ✅ Support creating/updating collection references
  - ✅ Handle CollectionContentEntity CRUD operations
  - ✅ Add validation (prevent deep nesting, circular references)
  - ✅ Update content ordering via join table
  - ✅ **Fixed deleteCollection** - Now properly deletes join table entries only (preserves reusable content)
  - ✅ **Moved addContent** - Image upload logic moved to ContentServiceImpl.createImages()

### Phase 5: Database Migration (CRITICAL - Data Migration Required)
- [ ] **Migration Part 1: Create new tables**
  - Create `collection_content` join table
  - Create `content_collection` table (for COLLECTION content type)
  - Add COLLECTION to content_type enum
- [ ] **Migration Part 2: Data migration**
  - Copy data from existing content table to collection_content:
    - content.id -> collection_content.content_id
    - content.collection_id -> collection_content.collection_id
    - content.order_index -> collection_content.order_index
    - content.caption -> collection_content.caption
    - content.visible -> collection_content.visible
  - Verify all content has corresponding join table entries
- [ ] **Migration Part 3: Schema cleanup**
  - Remove `collection_id` column from content table
  - Remove `order_index` column from content table
  - Remove `caption` column from content table
  - Remove `visible` column from content table
  - Remove `priority` column from collection table (optional)
- [ ] **Create rollback migration** - In case we need to revert

### Phase 6: Testing & Validation
- [ ] **Test content reusability** - Same image in multiple collections
- [ ] **Test collection-specific metadata** - Different captions/order per collection
- [ ] **Test hierarchical operations** - Collections containing collections
- [ ] **Test home page as regular collection** - ContentCollectionModel items
- [ ] **Test data integrity** - No orphaned content or join table entries
- [ ] **Performance testing** - Query performance with join table
- [ ] **Test circular reference prevention** - Validation logic

### Phase 7: Controller & API Updates
- [x] **Update CollectionControllerProd**:
  - ✅ Handle new join table structure in responses
  - ✅ All 4 endpoints reviewed and working correctly:
    - GET / (getAllCollections) - Uses pagination correctly
    - GET /{slug} (getCollectionBySlug) - Uses join table for paginated content
    - GET /type/{type} (getCollectionsByType) - Returns HomeCardModel list
    - POST /{slug}/access (validateClientGalleryAccess) - Works (password protection temporarily disabled)
- [x] **Update CollectionControllerDev**:
  - ✅ Handle new join table structure in responses
  - ✅ All endpoints updated for join table architecture
- [x] **Create ContentControllerDev**:
  - ✅ Endpoints for content library implemented
  - ✅ ContentServiceImpl.createImages() implemented (moved from CollectionService)
  - ✅ Tag/People/Camera creation endpoints working
  - ✅ Image update/delete endpoints working
- [ ] **Create ContentControllerProd** (if needed):
  - Read-only endpoints for content library
- [ ] **Update routing patterns** for new hierarchical structure

### Phase 8: Cleanup & Documentation
- [ ] Remove legacy code references
- [ ] Update API documentation
- [ ] Update database schema diagrams
- [ ] Document content library workflow
- [ ] Document collection hierarchy best practices

## Design Principles

### REUSE
- Existing `CollectionEntity` structure
- JOINED inheritance pattern already in place
- `ContentEntity` -> `ContentModel` conversion patterns
- Repository/Service layer patterns

### RECYCLE
- Copy `ContentImageEntity` structure for `ContentCollectionEntity`
- Reuse conversion logic from `ContentImageModel`
- Follow existing validation patterns

### REMOVE
- `priority` field (use `orderIndex`)
- Old many-to-many join table concepts (already removed)
- Deep nesting (max 1-2 levels)

## Benefits

1. **Simplified Mental Model** - Collections are just folders
2. **Code Reduction** - HomePage is regular collection, not special case
3. **Flexibility** - Collections can contain any content type
4. **Reusability** - Same collection everywhere (home, blogs, galleries)
5. **Clean URLs** - `/{collectionType}/{slug}` pattern

## Critical Notes

- **Prevent Deep Nesting** - Limit to 1-2 levels for performance
- **Circular References** - Validate collection A doesn't reference collection B if B references A
- **ContentCollectionModel** - Only embed minimal data (don't fetch full nested collection)
- **Performance** - Lazy load referenced collections on demand

---

# CollectionControllerDev Analysis & Improvements

## Current State (After Phase 1 Refactor)

### Endpoints:
1. `POST /api/admin/collections/createCollection` - Create collection
2. `PUT /api/admin/collections/{id}` - Update collection
3. `POST /api/admin/collections/{id}/text-content` - Add text content (NEW)
4. `DELETE /api/admin/collections/{id}/content/{contentId}` - Unlink content
5. `DELETE /api/admin/collections/{id}` - Delete collection
6. `GET /api/admin/collections/all` - Get all collections
7. `GET /api/admin/collections/{slug}/update` - Get collection + metadata
8. `GET /api/admin/collections/metadata` - Get general metadata

### Removed:
- ❌ `POST /api/admin/collections/{id}/content` - Moved to ContentControllerDev

---

## Issues & Improvements

### 1. **Duplicated Error Handling Logic** ⚠️ HIGH PRIORITY

**Problem:** Every endpoint has identical try-catch blocks with:
- EntityNotFoundException → 404
- Generic Exception → 500
- Same logging patterns
- Same error message construction

**Current Pattern (repeated 8 times):**
```java
try {
    // Business logic
} catch (EntityNotFoundException e) {
    log.warn("...");
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("...");
} catch (Exception e) {
    log.error("...");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("...");
}
```

**Solution Options:**

**Option A: @ControllerAdvice (RECOMMENDED)**
Create `GlobalExceptionHandler`:
```java
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException e) {
        log.warn("Entity not found: {}", e.getMessage());
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NOT_FOUND", e.getMessage(), 404));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_ERROR", e.getMessage(), 500));
    }
}
```

**Benefits:**
- Remove ~120 lines of duplicate code across all controllers
- Centralized error handling
- Consistent error response format
- Easier to add new exception types

**Option B: Custom Service Exceptions**
Create domain exceptions that carry HTTP status:
```java
public class CollectionNotFoundException extends RuntimeException {
    private final HttpStatus status = HttpStatus.NOT_FOUND;
}
```

**Recommendation:** Use **Option A** (@ControllerAdvice) - it's Spring Boot best practice

---

### 2. **Error Response Consistency** ⚠️ MEDIUM PRIORITY

**Problem:** Error responses return plain strings, not structured JSON

**Current:**
```json
"Collection with ID: 123 not found"
```

**Should Be:**
```json
{
  "error": "NOT_FOUND",
  "message": "Collection with ID: 123 not found",
  "status": 404,
  "timestamp": "2024-10-31T19:30:00Z",
  "path": "/api/admin/collections/123"
}
```

**Solution:** Create `ErrorResponse` DTO:
```java
@Data
@Builder
public class ErrorResponse {
    private String error;
    private String message;
    private int status;
    private Instant timestamp;
    private String path;
}
```

---

### 3. **Parameter Validation in Controller** ⚠️ LOW PRIORITY

**Problem:** Some validation exists in controller that should be in service layer

**Example:** `unlinkContent()` creates DTO manually:
```java
// Line 141-143 - Controller doing service work
CollectionUpdateDTO updateDTO = new CollectionUpdateDTO();
updateDTO.setContentIdsToRemove(List.of(contentId));
CollectionModel updatedCollection = collectionService.updateContent(id, updateDTO);
```

**Better Approach:**
```java
// Controller just delegates
CollectionModel updatedCollection = collectionService.unlinkContent(id, contentId);
```

**Benefits:**
- Cleaner controller (just routing)
- Service method named for what it does
- No DTO construction in controller

---

### 4. **Inconsistent Endpoint Naming** ⚠️ LOW PRIORITY

**Problem:** Mix of REST conventions

**Current:**
- `/createCollection` ❌ - Verb in URL
- `/{id}` ✅ - Proper REST
- `/{slug}/update` ❌ - Verb in URL

**Should Be:**
```
POST   /api/admin/collections              (not /createCollection)
GET    /api/admin/collections/{slug}/edit  (not /{slug}/update)
```

**Alternative:** Keep current naming if frontend depends on it, fix in Phase 2

---

### 5. **Missing Service Method** ⚠️ HIGH PRIORITY

**Problem:** `addTextContent()` calls non-existent `collectionService.addTextContent()`

**Status:** ❌ Not implemented yet

**Needs:**
```java
// CollectionService.java
CollectionModel addTextContent(Long collectionId, CreateTextContentRequest request);

// CollectionServiceImpl.java
@Override
@Transactional
public CollectionModel addTextContent(Long collectionId, CreateTextContentRequest request) {
    // 1. Find collection
    // 2. Create ContentTextEntity
    // 3. Create CollectionContentEntity (join table)
    // 4. Save and return CollectionModel
}
```

---

### 6. **Return Type Inconsistency** ⚠️ LOW PRIORITY

**Problem:** Endpoints return different response shapes

**Current Mix:**
- `createCollection` → `CollectionUpdateResponseDTO` (collection + metadata)
- `updateCollection` → `CollectionModel` (just collection)
- `deleteCollection` → `String` ("Collection deleted successfully")
- `getAllCollections` → `List<CollectionModel>`

**Recommendation:** Standardize based on frontend needs:
```java
// Create/Update: Return full data
ResponseEntity<CollectionModel>

// Delete: Return confirmation
ResponseEntity<DeleteResponse>  // { "deleted": true, "id": 123 }

// List: Return paginated
ResponseEntity<Page<CollectionModel>>
```

---

### 7. **Logging Improvements** ⚠️ LOW PRIORITY

**Current:** Mix of log levels and inconsistent messages

**Pattern to Follow:**
```java
// Success - INFO with meaningful context
log.info("Collection created: id={}, slug={}", id, slug);

// Not Found - WARN (expected error)
log.warn("Collection not found: slug={}", slug);

// Unexpected Error - ERROR with stack trace
log.error("Unexpected error creating collection: {}", e.getMessage(), e);
```

**Add Structured Logging:**
```java
log.info("Collection operation",
    kv("operation", "create"),
    kv("collectionId", id),
    kv("userId", getCurrentUserId())
);
```

---

### 8. **Missing Endpoints (From Refactor Plan)** ⚠️ HIGH PRIORITY

**Still Needed:**

**A. Link Existing Content to Collection**
```java
POST /api/admin/collections/{id}/link-content
Body: {
  "contentId": 456,
  "orderIndex": 3,
  "caption": "Sunset in Iceland",
  "visible": true
}
Response: CollectionModel
```

**B. Update Collection-Specific Metadata (Join Table)**
```java
PATCH /api/admin/collections/{collectionId}/content/{contentId}/metadata
Body: {
  "orderIndex": 5,
  "caption": "Updated caption",
  "visible": false
}
Response: CollectionContentModel
```

**C. Batch Reorder Content**
```java
PUT /api/admin/collections/{collectionId}/content/reorder
Body: [
  { "contentId": 1, "orderIndex": 0 },
  { "contentId": 2, "orderIndex": 1 }
]
Response: CollectionModel
```

---

### 9. **Best Practices Missing**

**A. Input Validation**
- Add `@Valid` to request bodies where missing
- Use Bean Validation annotations consistently

**B. API Versioning**
- Consider: `/api/v1/admin/collections`
- Easier future changes without breaking clients

**C. HATEOAS Links** (Optional)
- Add `_links` to responses for discoverability
- Spring HATEOAS support

**D. Rate Limiting**
- Add `@RateLimit` for create/update endpoints
- Prevent abuse of admin endpoints

**E. Audit Logging**
- Log who created/updated/deleted collections
- Track all admin actions for security

---

## Recommended Refactoring Priority

### Phase 2A: Error Handling (HIGH IMPACT, LOW EFFORT)
1. Create `ErrorResponse` DTO
2. Create `@ControllerAdvice` for global exception handling
3. Remove all try-catch blocks from controllers
4. **Estimated Impact:** -120 lines, +40 lines = **-80 lines**

### Phase 2B: Service Layer Cleanup (MEDIUM IMPACT, MEDIUM EFFORT)
1. Implement `collectionService.addTextContent()`
2. Add `collectionService.unlinkContent()` (replace DTO construction)
3. Add `collectionService.linkContent()` (new endpoint)
4. Add `collectionService.updateContentMetadata()` (new endpoint)
5. Add `collectionService.reorderContent()` (new endpoint)

### Phase 2C: Missing Endpoints (HIGH IMPACT, MEDIUM EFFORT)
1. Add `/link-content` endpoint
2. Add `/content/{id}/metadata` endpoint
3. Add `/content/reorder` endpoint

### Phase 2D: Polish (LOW IMPACT, LOW EFFORT)
1. Fix endpoint naming (if frontend allows)
2. Standardize return types
3. Improve logging
4. Add audit logging

---

## Code Metrics

### Current State:
- **Lines of Code:** ~220
- **Endpoints:** 8
- **Duplicated Exception Handling:** ~90 lines (41%)
- **Service Delegation:** 100%
- **DTO Usage:** 90% (missing unlinkContent)

### After Phase 2A (Global Exception Handling):
- **Lines of Code:** ~140 (-80)
- **Duplicated Code:** 0 lines (0%)
- **Maintainability:** ↑↑

### After Phase 2B+2C (Full Refactor):
- **Lines of Code:** ~200
- **Endpoints:** 11
- **Service Coverage:** 100%
- **Architecture:** ✅ Clean





---- 

API Endpoint Audit & Refactor Analysis

1. Current Endpoints Inventory

CollectionControllerDev (/api/write/collections) - Admin Only

| Method | Path                      | Purpose
| Status                       |
|--------|---------------------------|--------------------------------
-------|------------------------------|
| POST   | /createCollection         | Create new collection
| ✅ Keep                       |
| PUT    | /{id}                     | Update collection metadata
| ✅ Keep                       |
| POST   | /{id}/content             | Add media files to collection
| ⚠️ MOVE to ContentController |
| DELETE | /{id}/content/{contentId} | Remove content from collection
| ✅ Keep (but rename)          |
| DELETE | /{id}                     | Delete collection
| ✅ Keep                       |
| GET    | /all                      | Get all collections (admin
view)      | ✅ Keep                       |
| GET    | /{slug}/update            | Get collection + metadata for
editing | ✅ Keep                       |
| GET    | /metadata                 | Get general metadata
| ✅ Keep                       |

ContentControllerDev (/api/write/content) - Admin Only

| Method | Path    | Purpose               | Status                    |
|--------|---------|-----------------------|---------------------------|
| POST   | /tags   | Create new tag        | ✅ Keep (fix DTO)          |
| POST   | /people | Create new person     | ✅ Keep (fix DTO)          |
| POST   | /images | Create images         | ✅ IMPLEMENTED - ContentServiceImpl.createImages() |
| PATCH  | /images | Update image metadata | ✅ Keep                    |
| DELETE | /images | Delete images         | ✅ Keep                    |
| GET    | /images | Get all images        | ✅ Keep                    |

CollectionControllerProd (/api/read/collections) - Public

| Method | Path           | Purpose                             |
Status   |
|--------|----------------|-------------------------------------|-----
-----|
| GET    | ``             | Get all collections (paginated)     | ✅
Keep   |
| GET    | /{slug}        | Get collection by slug with content | ✅
Keep   |
| GET    | /type/{type}   | Get collections by type             | ✅
Keep   |
| POST   | /{slug}/access | Validate password for galleries     | ✅
Keep   |
| GET    | /homePage      | COMMENTED OUT                       | ❌
DELETE |

ContentControllerProd (/api/read/content) - Public

| Method | Path           | Purpose                    | Status |
  |--------|----------------|----------------------------|--------|
| GET    | /tags          | Get all tags               | ✅ Keep |
| GET    | /people        | Get all people             | ✅ Keep |
| GET    | /cameras       | Get all cameras            | ✅ Keep |
| GET    | /film-metadata | Get film types and formats | ✅ Keep |
