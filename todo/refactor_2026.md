# Backend Refactoring Guide - 2026

Modernization roadmap for `edens.zac.portfolio.backend` (Spring Boot 3.4.1 / Java 23).

---

## Current State (as of 2026-03-13)

| Metric | Current | Target | Notes |
|--------|---------|--------|-------|
| Model/DTO files | 19 | 10-12 | Was 35; Records.java + CollectionRequests.java consolidation done |
| Service impl line counts | 1029 / 1200+ | <300 each | CollectionServiceImpl / ContentServiceImpl |
| DAOs | 14 | 5-6 | Unchanged |
| Dependencies per service | 8-14 | 2-4 | CollectionServiceImpl=8, ContentServiceImpl=14 |
| Total Java files | ~79 | ~50 | Was ~91 before Phase 1+2 partial |

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

---

## Remaining Work - Phase Index

Each phase is a self-contained task. Execute in order within a priority tier; tiers can be parallelized.

### Tier 1: Quick Cleanup
| Phase | File | Summary | Effort |
|-------|------|---------|--------|
| 2a | [phase-2a-remaining-records.md](phase-2a-remaining-records.md) | Convert remaining simple models to records | 1-2 hours |
| 3a | [phase-3a-remove-interfaces.md](phase-3a-remove-interfaces.md) | Delete service interface files | 30 min |

### Tier 2: Bug Fixes
| Phase | File | Summary | Effort |
|-------|------|---------|--------|
| 7a | [phase-7a-image-date-fix.md](phase-7a-image-date-fix.md) | Fix capture date extraction from EXIF/XMP | 1 day |
| 3c | [phase-3c-connection-pool-fix.md](phase-3c-connection-pool-fix.md) | Split processImageContent to fix DB connection starvation | 1 day |

### Tier 3: Model Architecture
| Phase | File | Summary | Effort |
|-------|------|---------|--------|
| 2b | [phase-2b-collection-model.md](phase-2b-collection-model.md) | Flatten CollectionBaseModel into CollectionModel | 2-3 hours |
| 2c | [phase-2c-content-sealed-interface.md](phase-2c-content-sealed-interface.md) | Sealed interface for Content model types | 1 day |

### Tier 4: Service Refactoring
| Phase | File | Summary | Effort |
|-------|------|---------|--------|
| 3b | [phase-3b-extract-services.md](phase-3b-extract-services.md) | Extract MetadataService + CollectionContentService | 2-3 days |

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
- Unnecessary interface/impl split (single implementations)
- DB connection pool starvation during parallel image uploads
- Missing/wrong capture dates on film scan uploads
- No GIF/MP4 upload path (stub throws UnsupportedOperationException)

**Recommendation: stay with Java.** Modernize with records, sealed interfaces, and focused services rather than rewriting in another language.
