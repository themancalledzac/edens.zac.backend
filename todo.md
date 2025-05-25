# Content Collection Refactor TODO

## Overview
Refactoring from simple Catalog/Image system to flexible Content Collection system with types: `blog`, `art_gallery`, `client_gallery`, `portfolio`.

**Strategy**: Build new system in parallel to existing Catalog/Image system, then gradually migrate.

---

## Phase 1: Database Schema & Core Entities (Backend)

### 1.1 Create CollectionType Enum
- [ ] Create `CollectionType.java` enum with values: `BLOG`, `ART_GALLERY`, `CLIENT_GALLERY`, `PORTFOLIO`
- [ ] Add to `edens.zac.portfolio.backend.enums` package
- **Files to create**: `src/main/java/edens/zac/portfolio/backend/enums/CollectionType.java`
- **Files to modify**: None (new enum)

### 1.2 Create ContentBlock Base System
- [ ] Create `ContentBlockType.java` enum: `IMAGE`, `TEXT`, `CODE`, `VIDEO`
- [ ] Create `ContentBlockEntity.java` base entity
- [ ] Create `ImageContentBlockEntity.java` extending ContentBlockEntity
- [ ] Create `TextContentBlockEntity.java` extending ContentBlockEntity
- **Files to create**: 
  - `src/main/java/edens/zac/portfolio/backend/enums/ContentBlockType.java`
  - `src/main/java/edens/zac/portfolio/backend/entity/ContentBlockEntity.java`
  - `src/main/java/edens/zac/portfolio/backend/entity/ImageContentBlockEntity.java`
  - `src/main/java/edens/zac/portfolio/backend/entity/TextContentBlockEntity.java`
- **Files to modify**: None (completely new entities)

### 1.3 Create ContentCollection Entity
- [ ] Create `ContentCollectionEntity.java` with CollectionType field
- [ ] Add OneToMany relationship to ContentBlockEntity (ordered)
- [ ] Include all common fields: title, slug, description, date, visibility, etc.
- [ ] Add type-specific JSON configuration field
- **Files to create**: `src/main/java/edens/zac/portfolio/backend/entity/ContentCollectionEntity.java`
- **Files to modify**: None (new entity)

### 1.4 Create Repository Layer
- [ ] Create `ContentCollectionRepository.java`
- [ ] Create `ContentBlockRepository.java`
- [ ] Add basic CRUD operations and findBySlug, findByType methods
- **Files to create**: 
  - `src/main/java/edens/zac/portfolio/backend/repository/ContentCollectionRepository.java`
  - `src/main/java/edens/zac/portfolio/backend/repository/ContentBlockRepository.java`
- **Files to modify**: None (new repositories)

---

## Phase 2: Models & DTOs (Backend)

### 2.1 Create Content Block Models
- [ ] Create `ContentBlockModel.java` base model
- [ ] Create `ImageContentBlockModel.java`
- [ ] Create `TextContentBlockModel.java`
- **Files to create**: 
  - `src/main/java/edens/zac/portfolio/backend/model/ContentBlockModel.java`
  - `src/main/java/edens/zac/portfolio/backend/model/ImageContentBlockModel.java`
  - `src/main/java/edens/zac/portfolio/backend/model/TextContentBlockModel.java`
- **Files to modify**: None (new models)

### 2.2 Create ContentCollection Models
- [ ] Create `ContentCollectionModel.java`
- [ ] Create `ContentCollectionCreateDTO.java`
- [ ] Create `ContentCollectionUpdateDTO.java`
- [ ] Include List<ContentBlockModel> for content items
- **Files to create**: 
  - `src/main/java/edens/zac/portfolio/backend/model/ContentCollectionModel.java`
  - `src/main/java/edens/zac/portfolio/backend/model/ContentCollectionCreateDTO.java`
  - `src/main/java/edens/zac/portfolio/backend/model/ContentCollectionUpdateDTO.java`
- **Files to modify**: None (new models)

---

## Phase 3: Service Layer (Backend)

### 3.1 Create ContentBlock Processing Utils
- [ ] Create `ContentBlockProcessingUtil.java`
- [ ] Add entity-to-model conversion methods
- [ ] Add content block ordering logic
- **Files to create**: `src/main/java/edens/zac/portfolio/backend/services/ContentBlockProcessingUtil.java`
- **Files to modify**: None (new utility)

### 3.2 Create ContentCollection Service
- [ ] Create `ContentCollectionService.java` interface
- [ ] Create `ContentCollectionServiceImpl.java`
- [ ] Implement CRUD operations
- [ ] Add methods: findByType, findBySlug, createWithContent, updateContent
- **Files to create**: 
  - `src/main/java/edens/zac/portfolio/backend/services/ContentCollectionService.java`
  - `src/main/java/edens/zac/portfolio/backend/services/ContentCollectionServiceImpl.java`
- **Files to modify**: None (new services)

### 3.3 Create ContentCollection Processing Util
- [ ] Create `ContentCollectionProcessingUtil.java`
- [ ] Add entity-to-model conversion methods
- [ ] Add type-specific processing logic
- [ ] Add slug generation and validation
- **Files to create**: `src/main/java/edens/zac/portfolio/backend/services/ContentCollectionProcessingUtil.java`
- **Files to modify**: None (new utility)

---

## Phase 4: API Controllers (Backend)

### 4.1 Create ContentCollection Read Controller
- [ ] Create `ContentCollectionControllerProd.java` in prod package
- [ ] Add endpoints: GET /collections, GET /collections/{slug}, GET /collections/type/{type}
- [ ] Keep completely separate from existing CatalogController
- **Files to create**: `src/main/java/edens/zac/portfolio/backend/controller/prod/ContentCollectionControllerProd.java`
- **Files to modify**: None (new controller)

### 4.2 Create ContentCollection Write Controller (Dev Only)
- [ ] Create `ContentCollectionControllerDev.java` in dev package
- [ ] Add endpoints: POST /collections, PUT /collections/{id}, DELETE /collections/{id}
- [ ] Include multipart support for mixed content creation
- **Files to create**: `src/main/java/edens/zac/portfolio/backend/controller/dev/ContentCollectionControllerDev.java`
- **Files to modify**: None (new controller)

---

## Phase 5: Frontend Types & Models

### 5.1 Create TypeScript Types
- [ ] Create `types/ContentCollection.ts`
- [ ] Create `types/ContentBlock.ts`
- [ ] Define CollectionType enum in TypeScript
- [ ] Define ContentBlockType enum in TypeScript
- **Files to create**: 
  - `types/ContentCollection.ts`
  - `types/ContentBlock.ts`
- **Files to modify**: None (new types)

### 5.2 Create API Functions
- [ ] Create `lib/api/contentCollections.ts`
- [ ] Implement fetch functions: fetchCollectionBySlug, fetchCollectionsByType, createCollection, updateCollection
- [ ] Keep separate from existing catalog API functions
- **Files to create**: `lib/api/contentCollections.ts`
- **Files to modify**: None (new API layer)

---

## Phase 6: Frontend Components

### 6.1 Create Content Block Components
- [ ] Create `Components/ContentBlocks/ImageContentBlock.tsx`
- [ ] Create `Components/ContentBlocks/TextContentBlock.tsx`
- [ ] Create `Components/ContentBlocks/ContentBlockRenderer.tsx` (switch component)
- **Files to create**: 
  - `Components/ContentBlocks/ImageContentBlock.tsx`
  - `Components/ContentBlocks/TextContentBlock.tsx`
  - `Components/ContentBlocks/ContentBlockRenderer.tsx`
- **Files to modify**: None (new components)

### 6.2 Create ContentCollection Display Components
- [ ] Create `Components/ContentCollection/ContentCollectionView.tsx`
- [ ] Create `Components/ContentCollection/BlogView.tsx`
- [ ] Create `Components/ContentCollection/ArtGalleryView.tsx`
- [ ] Create `Components/ContentCollection/PortfolioView.tsx`
- [ ] Create `Components/ContentCollection/ClientGalleryView.tsx`
- **Files to create**: Multiple new view components
- **Files to modify**: None (new components)

### 6.3 Create ContentCollection Page Route
- [ ] Create `pages/collection/[slug].tsx`
- [ ] Implement dynamic routing based on CollectionType
- [ ] Keep completely separate from existing `pages/catalog/[slug].tsx`
- **Files to create**: `pages/collection/[slug].tsx`
- **Files to modify**: None (new page route)

---

## Phase 7: Content Creation & Management

### 7.1 Create ContentCollection Create/Edit Components
- [ ] Create `Components/ContentCollection/ContentCollectionEditor.tsx`
- [ ] Create `Components/ContentCollection/ContentBlockEditor.tsx`
- [ ] Add drag-and-drop reordering for content blocks
- [ ] Create `pages/collection/create.tsx`
- **Files to create**: Multiple editor components and create page
- **Files to modify**: None (new functionality)

### 7.2 Update Header/Navigation
- [ ] Add ContentCollection routes to header navigation
- [ ] Update admin menu to include "Create Collection" option
- [ ] Keep existing Catalog navigation intact
- **Files to modify**: 
  - `Components/Header/Header.tsx`
  - `Components/MenuDropdown/MenuDropdown.tsx`

---

## Phase 8: Data Migration Strategy

### 8.1 Create Migration Utilities
- [ ] Create `MigrationService.java` for converting Catalogs to ContentCollections
- [ ] Create migration endpoint (dev-only): POST /api/write/migration/catalog-to-collection/{catalogId}
- [ ] Add rollback functionality
- **Files to create**: `src/main/java/edens/zac/portfolio/backend/services/MigrationService.java`
- **Files to modify**: None (new service)

### 8.2 Create Migration Scripts
- [ ] Create database migration script for bulk conversion
- [ ] Add validation script to ensure data integrity
- [ ] Create frontend tool for selective migration
- **Files to create**: Migration scripts and tools
- **Files to modify**: None (new tooling)

---

## Phase 9: Testing & Validation

### 9.1 Test New System
- [ ] Create test collections of each type
- [ ] Verify all CRUD operations work
- [ ] Test content block ordering and editing
- [ ] Validate responsive design on all collection types

### 9.2 Performance Testing
- [ ] Test with large collections (100+ content blocks)
- [ ] Verify image loading performance
- [ ] Test database query performance

---

## Phase 10: Gradual Migration & Deprecation

### 10.1 Migrate Existing Data
- [ ] Identify which catalogs belong to which CollectionType
- [ ] Run migration for art galleries first (lowest risk)
- [ ] Migrate portfolio pieces
- [ ] Migrate any existing blogs
- [ ] Keep client galleries as catalogs initially (if any exist)

### 10.2 Update Home Page
- [ ] Modify home page to pull from ContentCollections instead of Catalogs
- [ ] Update `fetchHomePage()` to use new API
- [ ] Ensure backwards compatibility during transition
- **Files to modify**: 
  - `lib/api/home.ts`
  - `pages/index.tsx`

### 10.3 Deprecate Old System (Future)
- [ ] Add deprecation warnings to old endpoints
- [ ] Remove old Catalog entities and services
- [ ] Remove old frontend components
- [ ] Update all references to use new system

---

## Notes for Future Claude Sessions

**What we need:**
- Flexible content system supporting multiple collection types
- Ordered content blocks (images, text, code, etc.)
- Separate creation/editing interfaces per type
- Migration path from existing Catalog system

**What we don't need:**
- To break existing functionality during development
- Complex permissions system initially (can add later)
- Video/advanced content types in Phase 1 (images + text sufficient)
- Real-time collaboration features

**Key Principles:**
- Build in parallel, don't modify existing system until ready
- Each phase should be fully testable independently
- Maintain clear separation between collection types
- Keep migration path simple and reversible