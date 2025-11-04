# Collection Hierarchical Refactor

## Random todos - COMPLETED VERIFICATION (2025-11-01)

### Verification Results:

#### [VERIFIED] Removal of all 'code' content
- ContentType.CODE is commented out in enum (ContentType.java:14)
- ContentCodeEntity.java is fully commented out
- All service methods (createCodeContent, etc.) are commented out in ContentServiceImpl.java
- Switch case for CODE is commented out in ContentProcessingUtil.java:90
- STATUS: COMPLETE - Code content type has been fully removed

#### [VERIFIED] Consolidation of 'content' to 'contentText'
- ContentTextEntity now uses 'textContent' field (not just 'content')
- Added 'formatType' field to distinguish text types
- Supported formats: markdown, html, plain, js, py, sql, java, ts, tf, yml
- This elegantly consolidates code content into text content
- STATUS: COMPLETE - Consolidation is done correctly

#### [VERIFIED] content_image table 'id' column
- ContentImageEntity uses JOINED inheritance strategy
- @PrimaryKeyJoinColumn(name = "content_id") means:
  - content_id is the PRIMARY KEY for content_image table
  - content_id is ALSO a FOREIGN KEY to content.id
- NO separate 'id' column is needed
- Current database has 'content_block_id' which should be renamed to 'content_id'
- STATUS: COMPLETE - No separate id column needed, just rename content_block_id to content_id

### MySQL Migration Scripts - UPDATED (2025-11-01)

See: migration-scripts.sql (25 individual scripts)

**UPDATED:** Fixed FK constraint issues from JPA auto-generated tables

Scripts cover:
1. **Script 0 (NEW)**: Drop JPA-created tables with incorrect FK constraints
2. Backing up existing tables (rename to *_old)
3. Creating new tables with correct schema and explicit FK constraint names
4. Migrating data from old to new tables (with ON DUPLICATE KEY UPDATE for idempotency)
5. Comprehensive verification queries
6. Cleanup (dropping old tables after verification)

Key Changes:
- **DROP and recreate**: content, content_image, content_text, content_gif, content_collection, collection_content
- Rename: image_content_block -> content_image
- Rename: text_content_block -> content_text
- Rename: gif_content_block -> content_gif
- Rename: collection_block -> collection_content
- Delete: code_content_block (CODE type removed, migrated to content_text)
- Delete: code_collection_block (legacy)
- Delete: *_collection_block tables (legacy - not used in new schema)
- Column rename: content_block_id -> content_id (in all child tables)
- Column rename: content.content -> content_text.text_content

Migration approach: Clean slate with data preservation
1. **Drop JPA tables** with wrong FK constraints (SET FOREIGN_KEY_CHECKS = 0)
2. Rename old tables to *_old
3. Create new tables with explicit FK constraint names
4. Migrate data with ON DUPLICATE KEY UPDATE (idempotent)
5. Verify data integrity
6. Drop old tables only after confirmation

**FK Constraint Naming Convention:**
- `fk_content_image_content` - content_image.content_id -> content.id
- `fk_content_image_camera` - content_image.camera_id -> content_cameras.id
- `fk_content_image_lens` - content_image.lens_id -> content_lenses.id
- `fk_content_image_film_type` - content_image.film_type_id -> content_film_types.id
- `fk_collection_content_collection` - collection_content.collection_id -> collection.id
- `fk_collection_content_content` - collection_content.content_id -> content.id
- All FK constraints explicitly named for easier debugging

---

### MySQL Migration Details - Specific Data Migrations

#### 1. Image Content Block Migration
**Source:** `image_content_block` table
**Destination:** `content` + `content_image` tables

**Old Table Columns:**
```
content_block_id, author, black_and_white, create_date, f_stop, focal_length,
image_height, image_url_raw, image_url_web, image_width, is_film, iso, lens,
location, rating, raw_file_name, shutter_speed, title, image_url_full_size,
file_identifier, film_format, film_type, camera_id, film_type_id, lens_id
```

**Migration Strategy:**
```sql
-- Step 1: Insert base content entry
INSERT INTO content (id, content_type, created_at, updated_at)
SELECT content_block_id, 'IMAGE', NOW(), NOW()
FROM image_content_block;

-- Step 2: Migrate all image-specific data
INSERT INTO content_image (
    content_id, title, image_width, image_height, iso, author, rating,
    f_stop, lens_id, black_and_white, is_film, film_type_id, film_format,
    shutter_speed, camera_id, focal_length, location, image_url_web,
    create_date, file_identifier
)
SELECT
    content_block_id,
    title,
    image_width,
    image_height,
    iso,
    author,
    rating,
    f_stop,
    lens_id,
    black_and_white,
    is_film,
    film_type_id,
    film_format,
    shutter_speed,
    camera_id,
    focal_length,
    location,
    image_url_web,
    create_date,
    file_identifier
FROM image_content_block;
```

**Dropped Columns:**
- `image_url_raw` - Not used in new schema
- `raw_file_name` - Not used in new schema
- `image_url_full_size` - Handled differently in new schema
- `lens` (text) - Replaced by lens_id FK relationship
- `film_type` (text) - Replaced by film_type_id FK relationship

---

#### 2. Collection Table Column Migrations
**Source:** Existing `collection` table
**Action:** Modify columns, drop old ones, add new ones

**Old Columns to Migrate:**
```
id, blocks_per_page, collection_date, config_json, cover_image_url,
created_at, description, location, password_hash, password_protected,
priority, slug, title, total_blocks, type, updated_at, visible,
cover_image_block_id, parent_type, content_per_page, cover_image_id,
total_content
```

**Migration Strategy:**
```sql
-- Step 1: Drop obsolete columns
ALTER TABLE collection
    DROP COLUMN IF EXISTS password_hash,
    DROP COLUMN IF EXISTS password_protected,
    DROP COLUMN IF EXISTS priority,
    DROP COLUMN IF EXISTS blocks_per_page,
    DROP COLUMN IF EXISTS config_json,
    DROP COLUMN IF EXISTS cover_image_url,
    DROP COLUMN IF EXISTS cover_image_block_id,
    DROP COLUMN IF EXISTS parent_type,
    DROP COLUMN IF EXISTS total_blocks;

-- Step 2: Ensure required columns exist with proper names
ALTER TABLE collection
    MODIFY COLUMN content_per_page INT DEFAULT 50,
    MODIFY COLUMN total_content INT DEFAULT 0,
    MODIFY COLUMN cover_image_id BIGINT,
    ADD CONSTRAINT fk_collection_cover_image
        FOREIGN KEY (cover_image_id) REFERENCES content(id) ON DELETE SET NULL;

-- Step 3: Update total_content counts (should match collection_content join table)
UPDATE collection c
SET c.total_content = (
    SELECT COUNT(*)
    FROM collection_content cc
    WHERE cc.collection_id = c.id
);
```

**Dropped Columns:**
- `password_hash` - Security feature removed (for now)
- `password_protected` - Security feature removed (for now)
- `priority` - Replaced by `order_index` in join table
- `blocks_per_page` - Replaced by `content_per_page`
- `config_json` - Not used
- `cover_image_url` - Replaced by `cover_image_id` FK
- `cover_image_block_id` - Replaced by `cover_image_id`
- `parent_type` - Not used in new schema
- `total_blocks` - Replaced by `total_content`

**Kept/Modified Columns:**
- `content_per_page` - Pagination size (kept)
- `total_content` - Total content count (kept, updated via migration)
- `cover_image_id` - FK to content.id (kept, ensures proper FK constraint)

---

#### 3. Home Collection & Collection Block Migration
**Issue:** Home page collections already exist in database

**Current State:**
- Collection `id=17` exists with `type='HOME'` and `slug='home'`
- `collection_block` table contains associations between HOME and child collections

**collection_block Example Data:**
```
id=1:  collection_id=17, content_id=1, block_type=COLLECTION, order_index=0
id=2:  collection_id=17, content_id=2, block_type=COLLECTION, order_index=0
id=4:  collection_id=17, content_id=2, block_type=COLLECTION, order_index=1
id=5:  collection_id=17, content_id=4, block_type=COLLECTION, order_index=2
id=6:  collection_id=17, content_id=5, block_type=COLLECTION, order_index=3
id=7:  collection_id=17, content_id=6, block_type=COLLECTION, order_index=4
id=8:  collection_id=17, content_id=8, block_type=COLLECTION, order_index=5
```

**Migration Strategy:**

**Step 1: Verify HOME collection exists**
```sql
-- Check if HOME collection exists
SELECT * FROM collection WHERE type = 'HOME' AND slug = 'home';
-- Result: id=17, slug='home', type='HOME', title='Home', description='Home page - main collection parent'
```

**Step 2: Migrate collection_block to collection_content**
```sql
-- For COLLECTION type blocks (collections containing other collections)
-- We need to create content_collection entries first, then link them

-- Create content_collection entries for each referenced collection
INSERT INTO content (content_type, created_at, updated_at)
SELECT 'COLLECTION', NOW(), NOW()
FROM collection_block cb
WHERE cb.block_type = 'COLLECTION'
  AND cb.collection_id = 17
GROUP BY cb.content_id;

-- Get the newly created content IDs and create content_collection links
INSERT INTO content_collection (content_id, referenced_collection_id)
SELECT
    c.id as content_id,
    cb.content_id as referenced_collection_id
FROM collection_block cb
JOIN content c ON c.content_type = 'COLLECTION'
WHERE cb.block_type = 'COLLECTION'
  AND cb.collection_id = 17;

-- Finally, create the join table entries linking HOME to these content_collection items
INSERT INTO collection_content (collection_id, content_id, order_index, caption, visible)
SELECT
    cb.collection_id,
    cc.content_id,  -- The newly created content_collection content_id
    cb.order_index,
    cb.caption,
    COALESCE(cb.visible, 1)
FROM collection_block cb
JOIN content_collection cc ON cc.referenced_collection_id = cb.content_id
WHERE cb.block_type = 'COLLECTION'
  AND cb.collection_id = 17;
```

**Step 3: Handle duplicates in collection_block**
```sql
-- Note: collection_block has duplicate entries (e.g., content_id=2 appears 3 times)
-- Before migrating, deduplicate based on latest order_index:

CREATE TEMPORARY TABLE collection_block_deduped AS
SELECT cb.*
FROM collection_block cb
INNER JOIN (
    SELECT collection_id, content_id, block_type, MAX(order_index) as max_order
    FROM collection_block
    GROUP BY collection_id, content_id, block_type
) dedup
ON cb.collection_id = dedup.collection_id
   AND cb.content_id = dedup.content_id
   AND cb.block_type = dedup.block_type
   AND cb.order_index = dedup.max_order;

-- Then use collection_block_deduped for migration instead of collection_block
```

**Alternative Simplified Approach:**
If `collection_block.content_id` already points to valid collection IDs (1, 2, 4, 5, 6, 8), we can:

```sql
-- Option A: If content_id points directly to collections
-- Create content_collection wrapper for each referenced collection
INSERT INTO content (content_type, created_at, updated_at)
SELECT DISTINCT 'COLLECTION', NOW(), NOW()
FROM collection_block
WHERE block_type = 'COLLECTION' AND collection_id = 17;

INSERT INTO content_collection (content_id, referenced_collection_id)
SELECT
    (SELECT id FROM content WHERE content_type = 'COLLECTION' ORDER BY id LIMIT 1 OFFSET rn),
    content_id
FROM (
    SELECT
        content_id,
        ROW_NUMBER() OVER (ORDER BY content_id) - 1 as rn
    FROM collection_block
    WHERE block_type = 'COLLECTION' AND collection_id = 17
    GROUP BY content_id
) numbered;

-- Then migrate to collection_content
INSERT INTO collection_content (collection_id, content_id, order_index, caption, visible)
SELECT
    cb.collection_id,
    cc.content_id,
    cb.order_index,
    cb.caption,
    COALESCE(cb.visible, 1)
FROM collection_block cb
JOIN content_collection cc ON cc.referenced_collection_id = cb.content_id
WHERE cb.block_type = 'COLLECTION';
```

---

#### 4. Verification Queries After Migration

```sql
-- Verify HOME collection
SELECT * FROM collection WHERE type = 'HOME';

-- Verify content_collection entries for HOME's children
SELECT
    c.id as content_id,
    c.content_type,
    cc.referenced_collection_id,
    col.slug as referenced_collection_slug,
    col.title as referenced_collection_title
FROM collection_content collc
JOIN content c ON c.id = collc.content_id
JOIN content_collection cc ON cc.content_id = c.id
JOIN collection col ON col.id = cc.referenced_collection_id
WHERE collc.collection_id = 17
ORDER BY collc.order_index;

-- Verify all collections have proper content counts
SELECT
    c.id,
    c.slug,
    c.total_content as stored_count,
    COUNT(cc.id) as actual_count
FROM collection c
LEFT JOIN collection_content cc ON cc.collection_id = c.id
GROUP BY c.id, c.slug, c.total_content
HAVING stored_count != actual_count;

-- Verify no orphaned content
SELECT c.*
FROM content c
LEFT JOIN collection_content cc ON cc.content_id = c.id
WHERE cc.id IS NULL
  AND c.content_type != 'COLLECTION';  -- content_collection items can be orphaned initially
```

---

#### Migration Execution Order

1. **Backup Database** - Create full backup before starting
2. **Run Scripts 1-13** - Table creation and schema updates (migration-scripts.sql)
3. **Run Script 14** - Migrate image_content_block data (updated with all columns)
4. **Run Scripts 15-16** - Migrate text and GIF data
5. **Run Scripts 17-19** - Migrate tag/people relationships
6. **Handle collection_block Migration** - Create content_collection entries and migrate to collection_content
7. **Update collection Table** - Drop old columns, update counts
8. **Run Script 21** - Verification queries
9. **Run Script 22** - Drop old tables (only after full verification)

---

### Remaining Random Todos:

- A look at our 'updateCollection' endpoint/models/logic. verify we are doing 'less' here
- A look at our 'updateImage' endpoint, and verify we are removing the 'old' stuff, and still properly updating.
- Updated TODO for frontend.
- - This includes:
- - - all relevant ( to the frontend ) models, such as:
- - - - CollectionModel
- - - - CollectionContentModel
- - - - ContentModel
- - - - ContentTextModel
- - - - ContentGifModel
- - - - ContentCollectionModel



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

---

## Top API Endpoints - Quick Reference

### 1. Create Collection
**Endpoint:** `POST http://localhost:8080/api/write/collections/createCollection`

**Example Body:**
```json
{
  "type": "PORTFOLIO",
  "title": "My Portfolio Collection"
}
```

**Notes:**
- **SIMPLIFIED:** Only `type` and `title` are required for initial creation
- Type enum: `BLOG`, `PORTFOLIO`, `ART_GALLERY`, `CLIENT_GALLERY`, `HOME`, `MISC`
- Title must be 3-100 characters
- **Slug is auto-generated** from title (can be updated later)
- All other fields (description, location, etc.) are set via Update Collection endpoint
- Returns `CollectionUpdateResponseDTO` with:
  - Newly created `CollectionModel` (with auto-generated slug, default values)
  - All available metadata (tags, people, cameras, etc.) for the edit form

---

### 2. Update Collection
**Endpoint:** `PUT http://localhost:8080/api/write/collections/{id}`

**Full Example Body (ALL available fields):**
```json
{
  "type": "PORTFOLIO",
  "title": "Updated Portfolio Title",
  "slug": "updated-portfolio-slug",
  "description": "A comprehensive description of my updated portfolio",
  "location": "Seattle, WA",
  "collectionDate": "2024-12-01",
  "visible": true,
  "isPasswordProtected": false,
  "password": "newSecurePassword123",
  "displayMode": "ORDERED",
  "tags": ["landscape", "portrait", "street"],
  "contentPerPage": 50,
  "coverImageId": 456,
  "reorderOperations": [
    {
      "contentId": 123,
      "oldOrderIndex": 0,
      "newOrderIndex": 2
    },
    {
      "contentId": 456,
      "oldOrderIndex": 1,
      "newOrderIndex": 0
    }
  ],
  "contentIdsToRemove": [789, 101],
  "newTags": ["aerial", "drone"],
  "newPeople": ["Jane Doe", "John Smith"]
}
```

**Minimal Example Body (common update):**
```json
{
  "title": "Updated Title",
  "description": "Updated description",
  "visible": false,
  "coverImageId": 123
}
```

**Notes:**
- **PARTIAL UPDATE:** Only include fields you want to change
- All fields are optional (no required fields)
- Returns `CollectionModel` with updated data

**Field Details:**

**Basic Collection Fields (from CollectionBaseModel):**
- `type`: CollectionType enum - `BLOG`, `PORTFOLIO`, `ART_GALLERY`, `CLIENT_GALLERY`, `HOME`, `MISC`
- `title`: String (3-100 chars) - Collection title
- `slug`: String (3-150 chars) - URL-friendly identifier
- `description`: String (max 500 chars) - Collection description
- `location`: String (max 255 chars) - Geographic location
- `collectionDate`: Date string (YYYY-MM-DD) - When collection was created/shot
- `visible`: Boolean - Public visibility
- `isPasswordProtected`: Boolean - Whether collection requires password
- `displayMode`: Enum - `CHRONOLOGICAL` or `ORDERED` (content ordering mode)
- `tags`: List<String> - Collection-level tags

**Update-Specific Fields (from CollectionUpdateDTO):**
- `password`: String (8-100 chars) - Raw password for client galleries (will be hashed)
  - `null` or omit = no change to existing password
  - Empty string = remove password protection
- `contentPerPage`: Integer (min 1) - Pagination size for this collection
- `coverImageId`: Long - ID of content.id to use as cover image
  - Must reference a valid ContentImageEntity

**Content Operations:**
- `reorderOperations`: List<ContentReorderOperation> - Batch reorder content items
  - `contentId`: Long - ID of content to move
  - `oldOrderIndex`: Integer - Current position (optional, for validation)
  - `newOrderIndex`: Integer - New position
- `contentIdsToRemove`: List<Long> - Content IDs to unlink from this collection
  - Only removes join table entries, content itself is preserved

**Metadata Creation:**
- `newTags`: List<String> - Create new tags if they don't exist, then associate with collection
- `newPeople`: List<String> - Create new people if they don't exist

**Response:**
- Returns updated `CollectionModel` with all changes applied
- Does NOT return metadata (use GET /{slug}/update for that)

---

### 3. Create Images
**Endpoint:** `POST http://localhost:8080/api/write/content/images/{collectionId}`

**Example:** `POST http://localhost:8080/api/write/content/images/5`

**Request Body (multipart/form-data):**
```form-data
files: [file1.jpg, file2.jpg, file3.jpg]
```

**Path Parameters:**
- `collectionId` (Long, required) - ID of the collection to add images to

**Notes:**
- **UPDATED:** Moved from `CollectionControllerDev` to `ContentControllerDev`
- Multipart form-data request (file upload)
- **ONLY accepts files** - no metadata in the request
- `collectionId` is in the URL path (required)
- Creates:
  - `ContentEntity` (base content record)
  - `ContentImageEntity` (image-specific data with auto-extracted EXIF metadata)
  - `CollectionContentEntity` (join table entry linking image to collection)
- Image metadata (ISO, f-stop, etc.) is **auto-extracted from EXIF data** during upload
- Returns list of created `ContentImageModel` objects
- Use "Update Image(s)" endpoint (#7) to modify metadata after upload

---

### 4. Get All Collections
**Endpoint:** `GET http://localhost:8080/api/read/collections?page=0&size=20`

**Example Body:** None (GET request)

**Notes:**
- Public endpoint (no auth required)
- Returns paginated `CollectionPageDTO`
- Default page size: 20
- Includes total pages, total elements, current page info

---

### 5. Get Update Collection (for editing)
**Endpoint:** `GET http://localhost:8080/api/write/collections/{slug}/update`

**Example Body:** None (GET request)

**Notes:**
- Admin only endpoint
- Returns `CollectionUpdateResponseDTO` with:
  - Collection data
  - All available tags
  - All available people
  - All available cameras
  - Film metadata (types & formats)
- Used to populate admin edit forms

---

### 6. Get Collection by Slug
**Endpoint:** `GET http://localhost:8080/api/read/collections/{slug}?page=0&contentPerPage=50`

**Example:** `GET http://localhost:8080/api/read/collections/home?page=0&contentPerPage=50`

**Example Body:** None (GET request)

**Notes:**
- Public endpoint
- Returns `CollectionModel` with paginated content
- Content includes polymorphic types: `ContentImageModel`, `ContentTextModel`, `ContentGifModel`, `ContentCollectionModel`
- Home example: slug="home", returns collection with nested collections as content items

**Example Response 1: Home Collection (Collection of Collections)**
```json
{
  "id": 17,
  "type": "HOME",
  "title": "Home",
  "slug": "home",
  "description": "Home page - main collection parent",
  "location": null,
  "collectionDate": null,
  "visible": true,
  "isPasswordProtected": false,
  "hasAccess": true,
  "displayMode": "ORDERED",
  "tags": [],
  "createdAt": "2025-01-15T10:30:00",
  "updatedAt": "2025-10-20T14:22:00",
  "contentPerPage": 50,
  "contentCount": 6,
  "currentPage": 1,
  "totalPages": 1,
  "coverImage": null,
  "content": [
    {
      "contentType": "COLLECTION",
      "id": 1,
      "title": "Oval Lakes",
      "description": "Backpacking trip to a hidden gem of the cascades, Oval Lakes. Just outside of Winthrop in north central Washington State, an initial hike through a recent burn brought us through the Oval Lakes region, summiting Gray Peak, and an eventual campsite at Tuckaway Lake. Highly recommend.",
      "imageUrl": "https://d2qp8h5pbkohe6.cloudfront.net/2022-10-08/WEB/DSC_0306.jpg",
      "orderIndex": 0,
      "visible": true,
      "createdAt": "2025-09-20T17:57:17.866849",
      "updatedAt": "2025-09-21T21:00:07.325471",
      "slug": "oval-lakes",
      "collectionType": "BLOG"
    },
    {
      "contentType": "COLLECTION",
      "id": 2,
      "title": "Iceland 2024",
      "description": "Two weeks exploring the land of fire and ice. From the black sand beaches of Vik to the northern lights over Jokulsarlon glacier lagoon.",
      "imageUrl": "https://d2qp8h5pbkohe6.cloudfront.net/2024-03-15/WEB/iceland-cover.jpg",
      "orderIndex": 1,
      "visible": true,
      "createdAt": "2025-03-15T08:12:00",
      "updatedAt": "2025-03-20T16:45:00",
      "slug": "iceland-2024",
      "collectionType": "BLOG"
    },
    {
      "contentType": "COLLECTION",
      "id": 4,
      "title": "Portfolio - Landscapes",
      "description": "A curated selection of landscape photography from across the Pacific Northwest and beyond.",
      "imageUrl": "https://d2qp8h5pbkohe6.cloudfront.net/portfolio/landscapes/mount-rainier-sunrise.jpg",
      "orderIndex": 2,
      "visible": true,
      "createdAt": "2025-01-10T12:00:00",
      "updatedAt": "2025-10-15T09:30:00",
      "slug": "landscapes",
      "collectionType": "PORTFOLIO"
    },
    {
      "contentType": "COLLECTION",
      "id": 5,
      "title": "Street Photography - Seattle",
      "description": "Urban scenes and moments captured in the streets of Seattle.",
      "imageUrl": "https://d2qp8h5pbkohe6.cloudfront.net/portfolio/street/pike-place-market.jpg",
      "orderIndex": 3,
      "visible": true,
      "createdAt": "2025-02-05T14:20:00",
      "updatedAt": "2025-08-12T11:15:00",
      "slug": "seattle-street",
      "collectionType": "PORTFOLIO"
    },
    {
      "contentType": "COLLECTION",
      "id": 6,
      "title": "Film Photography Collection",
      "description": "Shot on various film stocks - Portra 400, Ektar 100, and Tri-X 400.",
      "imageUrl": "https://d2qp8h5pbkohe6.cloudfront.net/film/portra-400-sample.jpg",
      "orderIndex": 4,
      "visible": true,
      "createdAt": "2025-04-01T10:00:00",
      "updatedAt": "2025-09-30T16:00:00",
      "slug": "film-collection",
      "collectionType": "ART_GALLERY"
    },
    {
      "contentType": "COLLECTION",
      "id": 8,
      "title": "Client Gallery - Johnson Wedding",
      "description": "Private gallery for the Johnson wedding - September 2025.",
      "imageUrl": "https://d2qp8h5pbkohe6.cloudfront.net/clients/johnson-wedding/preview.jpg",
      "orderIndex": 5,
      "visible": true,
      "createdAt": "2025-09-15T13:00:00",
      "updatedAt": "2025-09-16T18:30:00",
      "slug": "johnson-wedding-2025",
      "collectionType": "CLIENT_GALLERY"
    }
  ]
}
```

**Example Response 2: Oval Lakes Blog (Collection of Images - 30 images total, showing first 3)**
```json
{
  "id": 1,
  "type": "BLOG",
  "title": "Oval Lakes",
  "slug": "oval-lakes",
  "description": "Backpacking trip to a hidden gem of the cascades, Oval Lakes. Just outside of Winthrop in north central Washington State, an initial hike through a recent burn brought us through the Oval Lakes region, summiting Gray Peak, and an eventual campsite at Tuckaway Lake. Highly recommend.",
  "location": "Oval Lakes, Washington State",
  "collectionDate": "2022-10-08",
  "visible": true,
  "isPasswordProtected": false,
  "hasAccess": true,
  "displayMode": "ORDERED",
  "tags": ["backpacking", "washington", "cascades", "hiking"],
  "createdAt": "2025-09-20T17:57:17.866849",
  "updatedAt": "2025-09-21T21:00:07.325471",
  "contentPerPage": 30,
  "contentCount": 30,
  "currentPage": 1,
  "totalPages": 1,
  "coverImage": {
    "contentType": "IMAGE",
    "id": 15,
    "title": "Oval Lakes",
    "description": null,
    "imageUrl": "https://d2qp8h5pbkohe6.cloudfront.net/2022-10-08/WEB/DSC_0306.jpg",
    "orderIndex": 0,
    "visible": true,
    "createdAt": "2022-10-08T09:44:37",
    "updatedAt": "2025-09-21T10:15:00",
    "imageWidth": 1996,
    "imageHeight": 3000,
    "iso": 640,
    "author": "Zechariah Edens",
    "rating": 3,
    "fStop": "f/4.0",
    "lens": {
      "id": 4,
      "name": "Nikkor 24-70mm f/2.8"
    },
    "blackAndWhite": true,
    "isFilm": false,
    "filmType": null,
    "filmFormat": null,
    "shutterSpeed": "1/399 sec",
    "camera": {
      "id": 1,
      "make": "Nikon",
      "model": "D750"
    },
    "focalLength": "33 mm",
    "location": "Oval Lakes, Washington State",
    "createDate": "2022-10-08T09:44:37",
    "tags": [
      {
        "id": 1,
        "name": "landscape"
      },
      {
        "id": 5,
        "name": "black-and-white"
      }
    ],
    "people": [],
    "collections": [
      {
        "collectionId": 1,
        "name": "Oval Lakes",
        "coverImageUrl": "https://d2qp8h5pbkohe6.cloudfront.net/2022-10-08/WEB/DSC_0306.jpg",
        "visible": true,
        "orderIndex": 0
      }
    ]
  },
  "content": [
    {
      "contentType": "IMAGE",
      "id": 15,
      "title": "Oval Lakes",
      "description": "The approach through recent burn scars, with peaks emerging in the distance",
      "imageUrl": "https://d2qp8h5pbkohe6.cloudfront.net/2022-10-08/WEB/DSC_0306.jpg",
      "orderIndex": 0,
      "visible": true,
      "createdAt": "2022-10-08T09:44:37",
      "updatedAt": "2025-09-21T10:15:00",
      "imageWidth": 1996,
      "imageHeight": 3000,
      "iso": 640,
      "author": "Zechariah Edens",
      "rating": 3,
      "fStop": "f/4.0",
      "lens": {
        "id": 4,
        "name": "Nikkor 24-70mm f/2.8"
      },
      "blackAndWhite": true,
      "isFilm": false,
      "filmType": null,
      "filmFormat": null,
      "shutterSpeed": "1/399 sec",
      "camera": {
        "id": 1,
        "make": "Nikon",
        "model": "D750"
      },
      "focalLength": "33 mm",
      "location": "Oval Lakes, Washington State",
      "createDate": "2022-10-08T09:44:37",
      "tags": [
        {
          "id": 1,
          "name": "landscape"
        },
        {
          "id": 5,
          "name": "black-and-white"
        }
      ],
      "people": [],
      "collections": [
        {
          "collectionId": 1,
          "name": "Oval Lakes",
          "coverImageUrl": "https://d2qp8h5pbkohe6.cloudfront.net/2022-10-08/WEB/DSC_0306.jpg",
          "visible": true,
          "orderIndex": 0
        }
      ]
    },
    {
      "contentType": "IMAGE",
      "id": 16,
      "title": "First Glimpse of Oval Lakes",
      "description": "Cresting the ridge, the alpine lakes spread out below",
      "imageUrl": "https://d2qp8h5pbkohe6.cloudfront.net/2022-10-08/WEB/DSC_0312.jpg",
      "orderIndex": 1,
      "visible": true,
      "createdAt": "2022-10-08T11:22:15",
      "updatedAt": "2025-09-21T10:16:00",
      "imageWidth": 3000,
      "imageHeight": 1996,
      "iso": 400,
      "author": "Zechariah Edens",
      "rating": 4,
      "fStop": "f/8.0",
      "lens": {
        "id": 4,
        "name": "Nikkor 24-70mm f/2.8"
      },
      "blackAndWhite": false,
      "isFilm": false,
      "filmType": null,
      "filmFormat": null,
      "shutterSpeed": "1/640 sec",
      "camera": {
        "id": 1,
        "make": "Nikon",
        "model": "D750"
      },
      "focalLength": "24 mm",
      "location": "Oval Lakes, Washington State",
      "createDate": "2022-10-08T11:22:15",
      "tags": [
        {
          "id": 1,
          "name": "landscape"
        },
        {
          "id": 8,
          "name": "alpine"
        }
      ],
      "people": [],
      "collections": [
        {
          "collectionId": 1,
          "name": "Oval Lakes",
          "coverImageUrl": "https://d2qp8h5pbkohe6.cloudfront.net/2022-10-08/WEB/DSC_0306.jpg",
          "visible": true,
          "orderIndex": 1
        }
      ]
    },
    {
      "contentType": "IMAGE",
      "id": 17,
      "title": "Gray Peak Summit",
      "description": "360-degree views from the summit of Gray Peak at 8,321 feet",
      "imageUrl": "https://d2qp8h5pbkohe6.cloudfront.net/2022-10-08/WEB/DSC_0324.jpg",
      "orderIndex": 2,
      "visible": true,
      "createdAt": "2022-10-08T13:45:33",
      "updatedAt": "2025-09-21T10:17:00",
      "imageWidth": 3000,
      "imageHeight": 1996,
      "iso": 200,
      "author": "Zechariah Edens",
      "rating": 5,
      "fStop": "f/11.0",
      "lens": {
        "id": 5,
        "name": "Nikkor 70-200mm f/2.8"
      },
      "blackAndWhite": false,
      "isFilm": false,
      "filmType": null,
      "filmFormat": null,
      "shutterSpeed": "1/800 sec",
      "camera": {
        "id": 1,
        "make": "Nikon",
        "model": "D750"
      },
      "focalLength": "85 mm",
      "location": "Gray Peak, Washington State",
      "createDate": "2022-10-08T13:45:33",
      "tags": [
        {
          "id": 1,
          "name": "landscape"
        },
        {
          "id": 8,
          "name": "alpine"
        },
        {
          "id": 12,
          "name": "summit"
        }
      ],
      "people": [],
      "collections": [
        {
          "collectionId": 1,
          "name": "Oval Lakes",
          "coverImageUrl": "https://d2qp8h5pbkohe6.cloudfront.net/2022-10-08/WEB/DSC_0306.jpg",
          "visible": true,
          "orderIndex": 2
        }
      ]
    }
  ]
}
```

**Note:** The Oval Lakes response shows only the first 3 of 30 images for brevity. In a real response with `contentPerPage: 30`, all 30 images would be included in the `content` array. Each image follows the same structure as shown above, with varying metadata (title, description, ISO, f-stop, shutter speed, focal length, rating, tags, etc.).

---

### 7. Update Image(s)
**Endpoint:** `PATCH http://localhost:8080/api/write/content/images`

**Example Body:**
```json
{
  "contentIds": [123, 456, 789],
  "title": "Updated Title",
  "author": "Jane Smith",
  "iso": 800,
  "rating": 4,
  "tagIds": [1, 2, 3],
  "peopleIds": [10, 11]
}
```

**Notes:**
- Batch update multiple images at once
- Only include fields you want to update
- `tagIds` and `peopleIds` replace existing associations
- Returns list of updated `ContentImageModel` objects

---

### 8. Get Metadata
**Endpoint:** `GET http://localhost:8080/api/write/collections/metadata`

**Example Body:** None (GET request)

**Response Example:**
```json
{
  "tags": [...],
  "people": [...],
  "cameras": [...],
  "filmTypes": [...],
  "filmFormats": [...]
}
```

**Notes:**
- Admin only endpoint
- Returns all available metadata for dropdowns/forms
- Used when creating/editing collections or content

---

### 9. Get All Images
**Endpoint:** `GET http://localhost:8080/api/write/content/images?page=0&size=50`

**Example Body:** None (GET request)

**Notes:**
- Admin only endpoint
- Returns paginated list of all images in content library
- Useful for browsing/selecting images to add to collections

---

### 10. Create Tag
**Endpoint:** `POST http://localhost:8080/api/write/content/tags`

**Example Body:**
```json
{
  "name": "landscape"
}
```

**Notes:**
- Admin only endpoint
- Tag names should be unique
- Returns created tag entity

---

### 11. Create Person
**Endpoint:** `POST http://localhost:8080/api/write/content/people`

**Example Body:**
```json
{
  "name": "John Doe",
  "bio": "Professional photographer",
  "website": "https://johndoe.com"
}
```

**Notes:**
- Admin only endpoint
- Used for tagging people in images
- Returns created person entity

---

### 12. Get Collections by Type
**Endpoint:** `GET http://localhost:8080/api/read/collections/type/{type}`

**Example:** `GET http://localhost:8080/api/read/collections/type/PORTFOLIO`

**Example Body:** None (GET request)

**Notes:**
- Public endpoint
- Type values: `BLOG`, `PORTFOLIO`, `ART_GALLERY`, `CLIENT_GALLERY`, `HOME`, `MISC`
- Returns list of `HomeCardModel` (simplified collection view with cover image)
- Used for portfolio grid, blog listing pages, etc.
