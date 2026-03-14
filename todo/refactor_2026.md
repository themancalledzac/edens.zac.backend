# Backend Refactoring Guide - 2026

Modernization roadmap for `edens.zac.portfolio.backend` (Spring Boot 3.4.1 / Java 23).

---

## Current State (as of 2026-03-13, updated after 8a/8c/9a)

| Metric | Current | Target | Notes |
|--------|---------|--------|-------|
| Model/DTO files | 11 | 10-12 | Was 19; Phase 2b: -1; Phase 2c: -4+1 (net -3) |
| Service line counts | 1009 / 1098 | <300 each | CollectionService / ContentService (concrete, no Impl suffix) |
| DAOs | 14 | 5-6 | Unchanged |
| Dependencies per service | 8-14 | 2-4 | CollectionService=8, ContentService=9 (was 14, Phase 3b: -5) |
| Total Java files | ~70 | ~50 | Phase 2c: -4 old models, +1 ContentModels.java (net -3) |
| Tests | 318 | — | Phase 9a-9d: +67 tests (was 251) |

---

## Completed Work

### Phase 1: Quick Wins (DONE)
- [x] `GlobalExceptionHandler.java` created at `config/GlobalExceptionHandler.java`
- [x] Controllers simplified, no try-catch blocks (one harmless re-throw remains in `CollectionControllerProd.getCollectionsByType()`)
- [x] `@Valid` annotations on all `@RequestBody` parameters
- [x] Deleted `oldCreateTextContentRequest.java`

### Phase 2 Partial: Record Consolidation (DONE)
- [x] `Records.java` created with 9 nested records (Camera, Lens, FilmFormat, Tag, Person, Location, CollectionSummary, CollectionList, ChildCollection)
- [x] `CollectionRequests.java` created with Create, Update, Reorder, UpdateResponse + nested update helpers

### Phase 2a: Remaining Records (DONE 2026-03-13)
- [x] `FilmTypeDTO` → record (dropped Lombok)
- [x] `GeneralMetadataDTO` → record (dropped Lombok)
- [x] `ContentFilmTypeModel` → record (dropped Lombok + `@Builder.Default`)
- [x] `ContentRequests.java` created with `CreateTag`, `CreatePerson`, `NewFilmType`, `CreateTextContent` records
- [x] Deleted `CreateTagRequest`, `CreatePersonRequest`, `NewFilmTypeRequest`, `CreateTextContentRequest`, `CreateCodeContentRequest`
- [x] Updated all call sites: builder → constructors, `.getX()` → `.x()` accessors
- [x] Side effect: `CreateTextContentRequest` was abstract with no subclasses — converting to concrete record fixed a latent Jackson deserialization bug

### Phase 3b: Extract MetadataService (DONE 2026-03-13)
- [x] `MetadataService.java` created — owns all metadata CRUD: tags, people, cameras, lenses, film types, locations
- [x] `ContentService` reduced from 14 → 9 dependencies (removed 5 DAO/validator fields)
- [x] `CollectionService.getGeneralMetadata()` delegates to MetadataService instead of ContentService
- [x] `ContentControllerProd` updated to inject MetadataService directly
- [x] `ContentControllerDev` unchanged — thin delegate on ContentService preserves controller API
- [x] Checkstyle suppressions updated for new service file
- [x] BUILD SUCCESS — 241 tests, 0 failures
- Deferred: CollectionContentService, CollectionMapper, ContentMapper (lower ROI, more analysis needed)

### Phase 3a: Remove Service Interface/Impl Pattern (DONE 2026-03-13)
- [x] Deleted `CollectionService.java` (interface) and `ContentService.java` (interface)
- [x] Renamed `CollectionServiceImpl` → `CollectionService`, `ContentServiceImpl` → `ContentService` (both `public`)
- [x] Renamed `CollectionServiceImplTest` → `CollectionServiceTest`
- [x] Updated `checkstyle-suppressions.xml` to match new file names

### Branch Cleanup QF: Pre-Merge Fixes (DONE 2026-03-13)
- [x] Replaced inline `java.util.Arrays.stream` with `Arrays.stream` in `CollectionService.java`
- [x] Fixed `CollectionServiceTest`: removed stale `@Mock ContentTextDao`, added `@Mock TagDao`
- [x] Replaced `assert` with `IllegalArgumentException` in `ContentProcessingUtil.java` (2 sites)
- [x] `@Disabled("Diagnostic tool -- not an automated test")` on `ImageMetadataExtractionTest`
- [x] Deleted ~100 lines of commented-out tests from `ContentProcessingUtilTest`
- [x] `Arrays.asList()` → `List.of()` in `ImageMetadata.ExifTags.of()` and `XmpProperty.ofFallbacks()`

### Phase 3c: Fix DB Connection Pool Starvation (DONE)
- [x] `PreparedImageData` record defined in `ContentProcessingUtil.java` (carries raw metadata, no entity refs)
- [x] `prepareImageForUpload()` extracts metadata + uploads S3 + resizes + converts WebP -- NO DB calls
- [x] `savePreparedImage()` handles all DB work: location/camera/lens upserts, duplicate check, `saveImage()` INSERT
- [x] `createImagesParallel()` in `ContentService` orchestrates Phase 1 (parallel virtual threads) → Phase 2 (single `@Transactional`)
- [ ] Pool size revert (20 → 10) pending manual test with batch upload

### Phase 2b: Flatten CollectionModel / CollectionBaseModel (DONE 2026-03-13)
- [x] `CollectionBaseModel.java` deleted — fields absorbed into `CollectionModel`
- [x] `DisplayMode` enum moved to `types/DisplayMode.java`
- [x] `CollectionModel` converted from `@SuperBuilder` + `extends` to flat `@Builder` class
- [x] All references updated: `CollectionRequests`, `CollectionEntity`, `CollectionDao`, `CollectionProcessingUtil`, `CollectionUpdateRequestTest`
- [x] `GeneralMetadataDTO` compact constructor added — all 8 list fields null-safe (serialize as `[]` not `null`)
- [x] `Records.CollectionList.type` changed from `String` to `CollectionType` enum
- [x] BUILD SUCCESS — 302 tests, 0 failures

### Phase 2c: Content Models — Sealed Interface (DONE 2026-03-13)
- [x] `ContentModel.java` rewritten as sealed interface with `@JsonTypeInfo(visible=true)` + `@JsonSubTypes`
- [x] `ContentModels.java` created with `Image`, `Text`, `Gif`, `Collection` records (4 types, 1 file)
- [x] `ContentFilmTypeModel.name` renamed to `displayName`; compact constructor for null-safe `contentImageIds`
- [x] `ContentImageModel`, `ContentTextModel`, `ContentGifModel`, `ContentCollectionModel` deleted (4 files removed)
- [x] `ContentProcessingUtil` conversion methods updated to record constructors; `copyBaseProperties` removed
- [x] `CollectionService.populateCollectionsOnContent`: `peek`+setter → `map`+`withCollections()` (immutability)
- [x] `ContentService`, `CollectionModel`, `CollectionProcessingUtil`, `ContentImageUpdateResponse`, `ContentControllerDev` updated
- [x] Test files updated; obsolete Lombok-setter tests deleted
- [x] Checkstyle suppression added for `fStop` record component name
- [x] BUILD SUCCESS — 241 tests, 0 failures

### Phase 8a: ResourceNotFoundException (DONE 2026-03-13)
- [x] `ResourceNotFoundException` created in `config/` package
- [x] `GlobalExceptionHandler` — dedicated `@ExceptionHandler(ResourceNotFoundException.class)` → 404; `handleIllegalArgument` simplified to always return 400 (string-match removed)
- [x] All "not found" `IllegalArgumentException` throws in `CollectionService` (7 sites) and `ContentService` (5 sites) replaced with `ResourceNotFoundException`
- [x] Test mocks/assertions updated in 5 test files
- [x] BUILD SUCCESS — 251 tests, 0 failures

### Phase 8c: Silent Failure Quick Fixes (DONE 2026-03-13)
- [x] `extractImageMetadata` catch: `log.warn` → `log.error` with stack trace + filename
- [x] `parseExifDateToLocalDateTime`: fixed misleading log ("will use upload time" → "date will be null")
- [x] `convertRegularContentEntityToModel` `COLLECTION -> null`: added `log.warn` with entity ID
- [x] `deleteImages` error log: added `e` as third argument (stack trace now included)

### Phase 9a: GlobalExceptionHandlerTest (DONE 2026-03-13)
- [x] `config/GlobalExceptionHandlerTest.java` created — 10 tests
- [x] Covers all 7 handlers: `ResourceNotFoundException` (404), `IllegalArgumentException` (400), null message, `IllegalStateException` (400), `DataIntegrityViolationException` (409), `MethodArgumentTypeMismatchException` (400, null requiredType), `ConstraintViolationException` (400), `MethodArgumentNotValidException` (field error aggregation), `Exception` catch-all (500)
- [x] Verifies response body shape (`status`, `error`, `message`, `timestamp`)

### Phase 9b: ImageMetadata & XMP Extraction Tests (DONE 2026-03-13)
- [x] `ImageMetadataTest.java` created — 29 tests
- [x] XMP fallback ordering (CREATE_DATE 4-entry chain, first match wins)
- [x] ExifTags case-insensitive matching
- [x] SimpleStringExtractor (trims whitespace, null handling)
- [x] NumericExtractor (strips prefixes, preserves decimals, null for non-numeric)
- [x] BooleanExtractor (null/empty -> "false", predicate matching)

### Phase 9c: Request Record Validation Tests (DONE 2026-03-13)
- [x] `ContentRequestsTest.java` created — 26 tests
- [x] Jakarta Bean Validation for CreateTag, CreatePerson, NewFilmType, CreateTextContent
- [x] CollectionRequests.Create validation (null type, title size boundaries)
- [x] Boundary value tests (@Size min/max, @Positive)

### Phase 9d: Date Parsing Tests (DONE 2026-03-13)
- [x] `DateParsingTest.java` created — 12 tests
- [x] `parseImageDate` fallback chain (createDate -> modifyDate -> current date)
- [x] `parseExifDateToLocalDateTime` EXIF format parsing
- [x] Malformed/null input graceful handling
- [x] Made `parseImageDate` and `parseExifDateToLocalDateTime` package-private for testability
- [x] Finding: `parseExifDateToLocalDateTime` cannot handle ISO-8601 format (replaceFirst mangles time colons)
- [x] BUILD SUCCESS — 318 tests, 0 failures

### Phase 7a: Fix Image Capture Date Extraction (DONE 2026-03-13)
- [x] `XmpProperty` extended to `List<NamespaceProp>` supporting multiple namespace fallbacks
- [x] `CREATE_DATE` now tries: `NS_EXIF/DateTimeOriginal` → `NS_EXIF/DateTimeDigitized` → `NS_XMP/CreateDate` (Lightroom) → `NS_PHOTOSHOP/DateCreated`
- [x] Added `MODIFY_DATE` field (`NS_XMP/ModifyDate`, needed for Phase 7b dedupe)
- [x] Removed manual `NS_XMP/CreateDate` fallback block — now handled by the enum
- [x] `parseImageDate()` uses `modifyDate` as fallback for S3 path instead of `LocalDate.now()`
- [x] Warning log when no capture date found (film scan case)

---

## Remaining Work - Phase Index

Each phase is a self-contained task. Execute in order within a priority tier; tiers can be parallelized.

### Tier 1: Quick Cleanup
| Phase | File | Summary | Effort |
|-------|------|---------|--------|
| ~~2a~~ | ~~Convert remaining simple models to records~~ | DONE 2026-03-13 | — |
| ~~3a~~ | ~~Delete service interface files~~ | DONE 2026-03-13 | — |

### ~~Tier 1b: Branch Cleanup (before merge)~~ (DONE 2026-03-13)
| Phase | File | Summary | Effort |
|-------|------|---------|--------|
| ~~QF~~ | ~~[review-quick-fixes.md](review-quick-fixes.md)~~ | ~~Fix inline FQN, wrong test mock, assert in prod code, test cleanup~~ | DONE |

### Tier 2: Bug Fixes
| Phase | File | Summary | Effort |
|-------|------|---------|--------|
| ~~7a~~ | ~~Fix capture date extraction from EXIF/XMP~~ | DONE 2026-03-13 (+ post-review fixes pending) | — |
| ~~7a+~~ | ~~[phase-7a-image-date-fix.md](phase-7a-image-date-fix.md)~~ | ~~Fix ISO-8601 date regex, XMP empty catch block~~ | DONE 2026-03-13 |
| ~~3c~~ | ~~[phase-3c-connection-pool-fix.md](phase-3c-connection-pool-fix.md)~~ | ~~Split processImageContent to fix DB connection starvation~~ | DONE (pending: pool revert + build) |

### Tier 3: Model Architecture
| Phase | File | Summary | Effort |
|-------|------|---------|--------|
| ~~2b~~ | ~~[phase-2b-collection-model.md](phase-2b-collection-model.md)~~ | ~~Flatten CollectionBaseModel + DisplayMode move + null-safe lists~~ | DONE 2026-03-13 |
| ~~2c~~ | ~~[phase-2c-content-sealed-interface.md](phase-2c-content-sealed-interface.md)~~ | ~~Sealed interface + ChildCollection split + film type naming fix~~ | DONE 2026-03-13 |

### Tier 4: Service Refactoring
| Phase | File | Summary | Effort |
|-------|------|---------|--------|
| ~~3b~~ | ~~[phase-3b-extract-services.md](phase-3b-extract-services.md)~~ | ~~Extract MetadataService + CollectionContentService~~ | DONE 2026-03-13 (partial) |

### Tier 5: Features
| Phase | File | Summary | Effort |
|-------|------|---------|--------|
| 5 | [phase-5-gif-mp4-upload.md](phase-5-gif-mp4-upload.md) | MP4-as-GIF upload support | 1 day |
| 7b | [phase-7b-smart-dedupe.md](phase-7b-smart-dedupe.md) | Smart deduplicate on image re-upload | 2-3 days |

### Tier 6: Larger Refactors
| Phase | File | Summary | Effort |
|-------|------|---------|--------|
| 4 | [phase-4-dao-consolidation.md](phase-4-dao-consolidation.md) | Consolidate 14 DAOs to 5-6 | 1-2 weeks |
| 6 | [phase-6-future.md](phase-6-future.md) | Home page, image search, individual content management | TBD |

### Tier 7: Reliability & Quality
| Phase | File | Summary | Effort |
|-------|------|---------|--------|
| ~~8a~~ | ~~ResourceNotFoundException + deterministic 404 routing~~ | DONE 2026-03-13 | — |
| ~~8c~~ | ~~Silent failure quick fixes (logging, misleading log msg, COLLECTION null)~~ | DONE 2026-03-13 | — |
| 8b | [phase-8-error-handling.md](phase-8-error-handling.md#8b-image-upload-silent-failures) | Image upload silent failures — structured partial-success response | 1-2 days |
| ~~9a~~ | ~~GlobalExceptionHandlerTest (P0)~~ | DONE 2026-03-13 | — |
| ~~9b~~ | ~~ImageMetadata & XMP extraction tests~~ | DONE 2026-03-13 | — |
| ~~9c~~ | ~~Request record validation tests~~ | DONE 2026-03-13 | — |
| ~~9d~~ | ~~Date parsing tests~~ | DONE 2026-03-13 | — |
| 9e | [phase-9-test-coverage.md](phase-9-test-coverage.md#9e-collectionservicetest-coverage-priority-p3) | CollectionService test coverage | 1 day |

---

## Architecture Notes

**What's working well:**
- JDBC over JPA (performance control, no N+1)
- Dev/Prod controller separation
- Constructor injection with `@RequiredArgsConstructor`
- Batch loading patterns
- Google Java Format via Spotless

**Key problems being addressed:**
- Service bloat (1000+ line services with 8-14 dependencies)
- ~~Unnecessary interface/impl split (single implementations)~~ — fixed Phase 3a
- ~~DB connection pool starvation during parallel image uploads~~ — fixed Phase 3c
- ~~Missing/wrong capture dates on film scan uploads~~ — fixed Phase 7a/7a+
- No GIF/MP4 upload path (stub throws UnsupportedOperationException)
- Silent failures in image upload pipeline (partial results returned as success) — 8c quick fixes done; 8b structured response pending
- ~~Fragile string-matching for HTTP status routing in GlobalExceptionHandler~~ — fixed Phase 8a
- ~~Test coverage gaps: GlobalExceptionHandler, ImageMetadata, request validation, date parsing~~ — fixed Phases 9a-9d; 9e (CollectionService tests) pending

**Recommendation: stay with Java.** Modernize with records, sealed interfaces, and focused services rather than rewriting in another language.
