# ML Image Tagging System - Design Document

## Status: DESIGN PHASE - Under Discussion

## Context

Spring Boot 3.4.1 / Java 23 backend for a photography portfolio. Lightroom plugin exports images to the backend, which stores them in S3, extracts EXIF/XMP metadata (camera, lens, ISO, fStop, shutterSpeed, focalLength, location, blackAndWhite, isFilm, filmType, rating, etc.), and saves everything to PostgreSQL. Tags and people are currently assigned manually via admin endpoints.

- **Existing entities**: ContentImageEntity (25+ fields), TagEntity (many-to-many), ContentPersonEntity (many-to-many), LocationEntity
- **Upload flow**: ContentControllerDev `POST /api/admin/content/images/{collectionId}` -> ContentService.createImagesParallel() -> S3 upload + EXIF extraction -> DB save
- **Image storage**: S3 with CloudFront CDN (full-size originals + web-optimized WebP)
- **Hardware**: MacBook Pro M4 Pro, 48GB unified memory
- **Image count**: Thousands already uploaded, growing via Lightroom exports

## Architecture Decision: Separate Python Sidecar Service

A separate Python FastAPI service running locally alongside the Spring Boot backend.

**Why separate**:
- ML ecosystem is Python-native (PyTorch, Hugging Face, CLIP, InsightFace)
- Apple Silicon MPS acceleration only available via PyTorch Python bindings
- Keeps Spring Boot codebase clean and focused
- Fine-tuning requires Python tooling

**Communication**: REST over localhost
- Backend POSTs image URL + metadata to ML service after upload
- ML service POSTs results back to backend callback endpoint

## Core ML Stack

| Capability | Model | Size | Inference Time (M4 Pro) |
|---|---|---|---|
| Tag Generation | CLIP ViT-L/14 | ~900MB | ~50-100ms |
| Image Captioning | BLIP-2 OPT-2.7B | ~3GB | ~200-500ms |
| Face Detection/Recognition | InsightFace buffalo_l | ~300MB | ~100ms |
| Aesthetic Scoring | CLIP + LAION linear probe | ~5MB addon | ~5ms |
| Similarity Search | CLIP embeddings + ChromaDB | reuses CLIP | ~10ms |
| **Total memory** | | **~5-6GB** | |

## How CLIP Tag Generation Works

1. Define tag vocabulary (~100-200 tags): "portrait", "landscape", "beach", "mountain", etc.
2. CLIP encodes image into 768-dim vector
3. CLIP encodes each tag (as "a photo of {tag}") into same 768-dim space
4. Cosine similarity between image and each tag produces confidence score (0-1)
5. Tags above threshold (e.g., 0.25) get applied
6. **No training needed on day one** -- CLIP was trained on 400M image-text pairs

## EXIF Metadata Enrichment (Hybrid Approach)

Existing EXIF metadata boosts ML predictions:
- `blackAndWhite: true` -> auto-apply "black-and-white" (no ML needed)
- `isFilm: true` -> auto-apply "film" + film type
- Shutter speed > 1s -> boost "long-exposure"
- Focal length > 200mm -> boost "telephoto", "wildlife", "sports"
- Focal length < 24mm -> boost "wide-angle", "architecture", "landscape"
- ISO > 3200 -> boost "low-light", "night"
- Location data -> reverse geocode for place tags

## Fine-Tuning Strategy

1. **Phase 1 (Day 1)**: Zero-shot CLIP with tag vocabulary (~75% accuracy)
2. **Phase 2 (After ~500 corrections)**: Track accepted/rejected tags as training data
3. **Phase 3 (After ~1000+ labels)**: Fine-tune CLIP vision encoder on your photography style (~90%+ accuracy)
4. **Phase 4 (Ongoing)**: Continuous learning from corrections, periodic retraining

## Priority Ordering

1. **Tags first** -- CLIP-based auto-tagging (core feature)
2. Face recognition (InsightFace clustering + user labeling)
3. Image captioning (BLIP-2)
4. Similarity search (ChromaDB)
5. Aesthetic scoring

## Upload Flow Integration

```
Lightroom -> Spring Boot Backend -> S3 + PostgreSQL
                   |
                   | POST /api/ml/analyze (fire-and-forget)
                   v
             Python ML Service (FastAPI)
                   |
                   | POST /api/admin/ml/callback (results)
                   v
             Spring Boot Backend -> Update tags in DB
```

## Backend Changes Needed

1. New callback endpoint: `POST /api/admin/ml/callback`
2. Async HTTP call to ML service after image save in upload flow
3. New DB columns: `ml_caption`, `aesthetic_score`, `ml_processed_at`
4. Tag source tracking: `source` column on `content_image_tags` (MANUAL vs ML)
5. Configuration properties for ML service URL, thresholds, enabled features

## Existing Database Schema (Relevant Tables)

```sql
-- content_image (extends content via JOINED inheritance)
id, title, image_url_web, image_url_full, image_width, image_height,
iso, f_stop, shutter_speed, focal_length, rating, author, location,
create_date, black_and_white, is_film

-- tag + content_image_tags (many-to-many)
tag(id, name), content_image_tags(image_id, tag_id)

-- person + content_image_people (many-to-many)
-- camera, lens, film_type (equipment metadata)
-- location (capture locations)
```

## Existing Code Entry Points

- Upload controller: `controller/dev/ContentControllerDev.java` - `POST /api/admin/content/images/{collectionId}`
- Upload service: `services/ContentService.java` - `createImagesParallel()`, `saveProcessedImages()`
- Metadata extraction: `services/ContentProcessingUtil.java` - `extractImageMetadata()`
- Tag/person management: `services/MetadataService.java`
- Image search: `controller/prod/ContentControllerProd.java` - search/filter endpoints

## Open Discussion Areas

### 1. Face Recognition Pipeline
- InsightFace detection + 512-dim face embeddings
- DBSCAN clustering for identity groups
- User labeling workflow via admin UI
- Storage: face_embedding table linked to ContentPersonEntity
- Recognition threshold tuning

### 2. Python ML Service Setup
- FastAPI project structure and dependencies
- PyTorch + MPS backend configuration for Apple Silicon
- Model loading/caching strategy
- Running as daemon alongside Spring Boot
- Development workflow

### 3. Backend Integration Details
- Callback endpoint design (request/response DTOs)
- Async trigger mechanism (WebClient? RestTemplate? CompletableFuture?)
- Schema migration approach
- Tag source tracking (MANUAL vs ML vs ML_CONFIRMED)
- Error handling when ML service is unavailable
