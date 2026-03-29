# Architecture

## System Overview
```
Frontend (Next.js) --> REST API --> Services --> DAOs --> PostgreSQL
                                     |
                                     v
                              AWS S3 (images)
                                     |
                                     v
                              CloudFront CDN
```

## Content Inheritance (JOINED Strategy)
```
ContentEntity (abstract base)
    |-- ContentImageEntity  (photos with EXIF metadata)
    |-- ContentTextEntity   (blog text, descriptions)
    |-- ContentGifEntity    (animated content)
    |-- ContentCollectionEntity (nested collections)
```

All content types share: `id`, `contentType`, `createdAt`, `updatedAt`
Child tables add type-specific fields (e.g., image dimensions, ISO, lens).

## Content Model Layer (DTOs)

Entities are converted to a sealed interface hierarchy for API responses:

```
ContentModel (sealed interface)
    |-- ContentModels.Image    (record, 28 fields)
    |-- ContentModels.Text     (record, 11 fields)
    |-- ContentModels.Gif      (record, 16 fields)
    |-- ContentModels.Collection (record, 13 fields)
```

Jackson polymorphism uses `@JsonTypeInfo(use=NAME, include=EXISTING_PROPERTY, property="contentType", visible=true)`.
`visible=true` is required so the `contentType` field is passed to the record constructor (not just consumed for type resolution).
Records are immutable; `ContentModels.Image.withCollections()` returns a new instance with collections replaced.

## Collection-Content Relationship
```
CollectionEntity (1) <---> (*) CollectionContentEntity <---> (1) ContentEntity
                               ^
                               |
                     ordering, caption, visibility per-collection
```

A single image can belong to multiple collections with different ordering/captions.

## Key Entity Relationships

### Image Metadata (Many-to-Many via join entities)
- `ContentImageEntity` --> `ContentTagEntity` --> `TagEntity`
- `ContentImageEntity` --> `ContentPersonEntity` --> `PersonEntity`
- `ContentImageEntity` --> `ContentCameraEntity` (embedded)
- `ContentImageEntity` --> `ContentLensEntity` (embedded)
- `ContentImageEntity` --> `ContentFilmTypeEntity` (embedded)

### Collection Hierarchy
- Collections can contain any ContentEntity type
- CollectionType enum: `BLOG`, `PORTFOLIO`, `ART_GALLERY`, `CLIENT_GALLERY`, `HOME`, `MISC`
- Collections have: slug, title, coverImage, date, visibility flags

## Data Flow: Image Upload
1. Controller receives multipart file
2. ImageProcessingService extracts EXIF/XMP metadata
3. Image optimized (WebP conversion, thumbnails)
4. Uploaded to S3, CloudFront URL generated
5. ContentImageEntity created with metadata
6. Added to collection via CollectionContentEntity

## Data Flow: API Request
1. Controller receives request (params validated by `@Valid`)
2. Calls service method (concrete class, no interface wrapper)
3. Service uses DAO for database queries (JDBC)
4. DAO returns entities
5. Service converts entities to models (DTOs)
6. Controller wraps in `ResponseEntity<T>`
7. On exception: `GlobalExceptionHandler` maps to appropriate HTTP status/body

<!-- PLANNED CHANGES (refactor_2026.md):
- Phase 4: DAOs will be consolidated and potentially renamed to *Repository
-->
