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

---

## Deep Dive: Face Recognition Pipeline

### How InsightFace Works

InsightFace's `buffalo_l` model bundle includes two stages:
1. **RetinaFace** -- detects face bounding boxes + 5 landmark points (eyes, nose, mouth corners)
2. **ArcFace** -- extracts 512-dimensional embedding vectors from aligned/cropped faces

Each face becomes a 512-float vector where similar-looking faces have high cosine similarity (>0.4 = likely same person, >0.6 = high confidence match).

```python
from insightface.app import FaceAnalysis

app = FaceAnalysis(name="buffalo_l", providers=["CoreMLExecutionProvider", "CPUExecutionProvider"])
app.prepare(ctx_id=0, det_size=(640, 640))

faces = app.get(img)  # returns list of Face objects
for face in faces:
    bbox = face.bbox           # [x1, y1, x2, y2]
    embedding = face.embedding  # numpy array, shape (512,)
    det_score = face.det_score  # detection confidence 0-1
    age = face.age              # estimated age
    gender = face.gender        # estimated gender
```

### Clustering Strategy: HDBSCAN

Use HDBSCAN (not DBSCAN) for face clustering -- it handles varying density better and automatically determines cluster count:

```python
import hdbscan

clusterer = hdbscan.HDBSCAN(
    min_cluster_size=3,      # need at least 3 photos to form a person cluster
    min_samples=2,
    metric="euclidean",
    cluster_selection_epsilon=0.5
)
labels = clusterer.fit_predict(all_face_embeddings)
# labels: -1 = noise/unknown, 0+ = cluster IDs
```

Why HDBSCAN over DBSCAN:
- No need to tune epsilon globally -- adapts to local density
- `min_cluster_size=3` means a person needs to appear in at least 3 photos to form a cluster
- Outliers (one-off faces in backgrounds) naturally fall to noise cluster (-1)

### User Labeling Workflow

1. **ML service clusters faces** after backfill or as new images arrive
2. **Backend exposes cluster API**:
   - `GET /api/admin/ml/face-clusters` -- returns clusters with sample face thumbnails
   - `POST /api/admin/ml/face-clusters/{clusterId}/label` -- user assigns a person name
   - `POST /api/admin/ml/face-clusters/merge` -- user merges two clusters (same person, different looks)
3. **After labeling**: cluster's embeddings become the reference set for that person
4. **On new uploads**: compare new face embeddings against all labeled reference embeddings
   - Cosine similarity > 0.6 = auto-assign person (high confidence)
   - 0.4-0.6 = suggest person (needs review)
   - < 0.4 = unknown face, add to clustering pool

### Database Schema

```sql
CREATE TABLE face_detection (
    id              BIGSERIAL PRIMARY KEY,
    image_id        BIGINT NOT NULL REFERENCES content_image(id) ON DELETE CASCADE,
    bbox_x          FLOAT NOT NULL,
    bbox_y          FLOAT NOT NULL,
    bbox_w          FLOAT NOT NULL,
    bbox_h          FLOAT NOT NULL,
    embedding       FLOAT[512] NOT NULL,
    detection_score FLOAT NOT NULL,
    person_id       BIGINT REFERENCES person(id),    -- NULL until labeled
    cluster_id      INTEGER,                          -- from HDBSCAN, NULL if noise
    confidence      FLOAT,                            -- match confidence when auto-assigned
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_face_detection_image ON face_detection(image_id);
CREATE INDEX idx_face_detection_person ON face_detection(person_id);
CREATE INDEX idx_face_detection_cluster ON face_detection(cluster_id);
```

Embeddings in PostgreSQL (not a vector DB) because:
- Face embeddings are small (512 floats = 2KB per face)
- Queries are simple nearest-neighbor against labeled faces (small set)
- ChromaDB is used for CLIP image embeddings (much higher volume, text search needed)

### Edge Cases

| Scenario | Handling |
|---|---|
| Same person, different ages/styles | HDBSCAN may split into multiple clusters; user merges via admin UI |
| Group photos (10+ faces) | Process all faces; limit to faces with det_score > 0.5 to skip blurry background faces |
| Side profiles, sunglasses | ArcFace handles moderate occlusion; low det_score faces are skipped |
| No faces in image | InsightFace returns empty list; skip face pipeline, cost is ~20ms |
| Backfill 5000 images | ~100ms/image = ~8 minutes; run as background batch job |

---

## Deep Dive: Python ML Service Setup

### Project Structure

```
portfolio-ml/
  pyproject.toml
  src/portfolio_ml/
    __init__.py
    main.py                    # FastAPI app entry point
    api/
      routes.py                # All endpoint definitions
      models.py                # Pydantic request/response schemas
    ml/
      clip_tagger.py           # CLIP inference + tag scoring
      face_analyzer.py         # InsightFace detection + clustering
      captioner.py             # BLIP-2 captioning (Phase 3)
      aesthetic_scorer.py      # LAION aesthetic probe (Phase 5)
      embeddings.py            # ChromaDB storage + similarity queries
    pipeline/
      analyzer.py              # Orchestrates all ML steps per image
      backfill.py              # Batch processing with progress tracking
      metadata_enricher.py     # EXIF-based rule boosts
    config/
      settings.py              # Pydantic Settings (env vars, defaults)
      tag_taxonomy.py          # Tag vocabulary with categories
    db/
      chroma_client.py         # ChromaDB connection
  tests/
    conftest.py
    test_clip_tagger.py
    test_analyzer.py
```

### Key Dependencies (pyproject.toml)

```toml
[project]
name = "portfolio-ml"
version = "0.1.0"
requires-python = ">=3.11"
dependencies = [
    "fastapi>=0.115.0",
    "uvicorn[standard]>=0.34.0",
    "torch>=2.5.0",
    "torchvision>=0.20.0",
    "open-clip-torch>=2.29.0",
    "transformers>=4.47.0",
    "insightface>=0.7.3",
    "onnxruntime>=1.20.0",
    "chromadb>=0.5.23",
    "hdbscan>=0.8.40",
    "pillow>=11.0.0",
    "httpx>=0.28.0",
    "pydantic-settings>=2.7.0",
    "numpy>=1.26.0",
]

[project.optional-dependencies]
dev = ["pytest>=8.0", "pytest-asyncio>=0.24", "ruff>=0.8.0"]
```

### Apple Silicon MPS Configuration

```python
import torch

def get_device() -> torch.device:
    if torch.backends.mps.is_available():
        return torch.device("mps")     # Apple Silicon GPU
    if torch.cuda.is_available():
        return torch.device("cuda")
    return torch.device("cpu")

DEVICE = get_device()
# On M4 Pro: returns "mps", uses Metal Performance Shaders
# 48GB unified memory shared between CPU and GPU -- no VRAM limit issues
```

MPS notes for M4 Pro:
- Most PyTorch ops run on GPU via Metal. A few rare ops fall back to CPU silently.
- CLIP and BLIP-2 both work fully on MPS.
- InsightFace uses ONNX Runtime, not PyTorch -- uses CoreML provider on Apple Silicon (equally fast).
- No special configuration needed. PyTorch 2.5+ has mature MPS support.

### Model Loading Strategy

```python
class ModelManager:
    """Lazy-loads models on first use, keeps them resident."""

    def __init__(self, device: torch.device):
        self.device = device
        self._clip_model = None
        self._clip_preprocess = None
        self._face_app = None

    @property
    def clip(self):
        if self._clip_model is None:
            import open_clip
            model, _, preprocess = open_clip.create_model_and_transforms(
                "ViT-L-14", pretrained="openai", device=self.device
            )
            model.eval()
            self._clip_model = model
            self._clip_preprocess = preprocess
        return self._clip_model, self._clip_preprocess

    @property
    def face_app(self):
        if self._face_app is None:
            from insightface.app import FaceAnalysis
            self._face_app = FaceAnalysis(
                name="buffalo_l",
                providers=["CoreMLExecutionProvider", "CPUExecutionProvider"]
            )
            self._face_app.prepare(ctx_id=0, det_size=(640, 640))
        return self._face_app
```

With 48GB, keep all models resident. Cold start is ~10-15s; after that, inference is instant.

### FastAPI Endpoints

```python
# Pydantic models
class AnalyzeRequest(BaseModel):
    image_id: int
    image_url: str
    callback_url: str
    metadata: dict[str, Any] = {}            # EXIF data from backend
    features: list[str] = ["tags"]           # which ML features to run

class TagResult(BaseModel):
    name: str
    confidence: float
    source: str = "ML"                        # ML, METADATA, ML_CONFIRMED

class FaceResult(BaseModel):
    bbox: tuple[float, float, float, float]
    detection_score: float
    embedding: list[float]
    cluster_id: int | None = None
    matched_person_id: int | None = None
    match_confidence: float | None = None

class AnalysisResult(BaseModel):
    image_id: int
    tags: list[TagResult] = []
    faces: list[FaceResult] = []
    caption: str | None = None
    aesthetic_score: float | None = None
    embedding: list[float] | None = None      # CLIP embedding for similarity search

# Endpoints
@app.post("/api/ml/analyze")
async def analyze(request: AnalyzeRequest, background_tasks: BackgroundTasks):
    """Accept analysis request, process in background, POST results to callback."""
    background_tasks.add_task(run_analysis_pipeline, request)
    return {"status": "accepted", "image_id": request.image_id}

@app.post("/api/ml/batch")
async def batch_analyze(request: BatchRequest, background_tasks: BackgroundTasks):
    """Backfill: accept list of images, process sequentially, callback per image."""
    background_tasks.add_task(run_batch_pipeline, request)
    return {"status": "accepted", "count": len(request.images)}

@app.get("/api/ml/health")
async def health():
    return {"status": "ok", "models_loaded": model_manager.loaded_models()}
```

### Running Locally

**Development:**
```bash
cd portfolio-ml
uvicorn src.portfolio_ml.main:app --reload --port 8081
```

**macOS LaunchAgent (auto-start on login):**

File: `~/Library/LaunchAgents/com.portfolio.ml.plist`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "...">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.portfolio.ml</string>
    <key>ProgramArguments</key>
    <array>
        <string>/path/to/venv/bin/uvicorn</string>
        <string>src.portfolio_ml.main:app</string>
        <string>--port</string>
        <string>8081</string>
    </array>
    <key>WorkingDirectory</key>
    <string>/path/to/portfolio-ml</string>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>StandardOutPath</key>
    <string>/tmp/portfolio-ml.log</string>
    <key>StandardErrorPath</key>
    <string>/tmp/portfolio-ml.err</string>
</dict>
</plist>
```

Spring Boot runs on 8080, ML service on 8081. No conflicts.

---

## Deep Dive: Backend Integration

### 1. ML Callback Endpoint

New controller following existing project patterns:

```java
@RestController
@RequestMapping("/api/admin/ml")
@RequiredArgsConstructor
@Profile("dev")
class MlControllerDev {

    private final MlIntegrationService mlIntegrationService;

    @PostMapping("/callback")
    ResponseEntity<MlCallbackResponse> handleCallback(
            @Valid @RequestBody MlAnalysisResultRequest request) {
        var result = mlIntegrationService.processAnalysisResult(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/backfill")
    ResponseEntity<MlBackfillResponse> triggerBackfill(
            @Valid @RequestBody MlBackfillRequest request) {
        var result = mlIntegrationService.triggerBackfill(request);
        return ResponseEntity.accepted().body(result);
    }
}
```

### 2. Request/Response DTOs

```java
// What the ML service sends back
public record MlAnalysisResultRequest(
    @NotNull Long imageId,
    List<MlTagResult> tags,
    List<MlFaceResult> faces,
    String caption,
    Double aestheticScore
) {
    public record MlTagResult(
        @NotBlank String name,
        @NotNull Double confidence,
        String source   // "ML" or "METADATA"
    ) {}

    public record MlFaceResult(
        double bboxX, double bboxY, double bboxW, double bboxH,
        double detectionScore,
        List<Double> embedding,
        Integer clusterId,
        Long matchedPersonId,
        Double matchConfidence
    ) {}
}

// What the backend sends to the ML service
public record MlAnalyzeRequest(
    Long imageId,
    String imageUrl,
    String callbackUrl,
    Map<String, Object> metadata,  // EXIF data
    List<String> features          // ["tags", "faces", "caption"]
) {}
```

### 3. Async Trigger in Upload Flow

Add to `ContentService.saveProcessedImages()`, after the DB save succeeds:

```java
// At end of saveProcessedImages(), after all images are saved
if (mlIntegrationService.isEnabled()) {
    var imageIds = savedImages.stream().map(ContentModels.Image::id).toList();
    mlIntegrationService.triggerAnalysisAsync(imageIds);
}
```

The service uses WebClient (non-blocking, fire-and-forget):

```java
@Service
@RequiredArgsConstructor
class MlIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(MlIntegrationService.class);

    private final MlServiceConfig config;
    private final WebClient webClient;
    private final MetadataService metadataService;
    private final ContentRepository contentRepository;

    boolean isEnabled() {
        return config.isEnabled();
    }

    void triggerAnalysisAsync(List<Long> imageIds) {
        for (var imageId : imageIds) {
            var image = contentRepository.findImageById(imageId);
            if (image == null) continue;

            var request = new MlAnalyzeRequest(
                imageId,
                image.getImageUrlWeb(),
                config.getCallbackUrl(),
                buildMetadataMap(image),
                config.getEnabledFeatures()
            );

            webClient.post()
                .uri(config.getServiceUrl() + "/api/ml/analyze")
                .bodyValue(request)
                .retrieve()
                .toBodilessEntity()
                .doOnError(e -> log.warn("ML service unavailable for image {}: {}", imageId, e.getMessage()))
                .subscribe();  // fire-and-forget
        }
    }

    @Transactional
    MlCallbackResponse processAnalysisResult(MlAnalysisResultRequest result) {
        // Apply ML-generated tags
        for (var tag : result.tags()) {
            if (tag.confidence() >= config.getConfidenceThreshold()) {
                metadataService.addTagToImage(result.imageId(), tag.name(), tag.source());
            }
        }
        // Store caption and aesthetic score
        contentRepository.updateMlFields(result.imageId(), result.caption(), result.aestheticScore());
        return new MlCallbackResponse(result.imageId(), "processed");
    }
}
```

### 4. Configuration

```java
@Configuration
@ConfigurationProperties(prefix = "ml")
@Data
class MlServiceConfig {
    private boolean enabled = false;
    private String serviceUrl = "http://localhost:8081";
    private String callbackUrl = "http://localhost:8080/api/admin/ml/callback";
    private double confidenceThreshold = 0.25;
    private List<String> enabledFeatures = List.of("tags");
}
```

```yaml
# application.yml
ml:
  enabled: false                    # off by default, enable when ML service is running
  service-url: http://localhost:8081
  callback-url: http://localhost:8080/api/admin/ml/callback
  confidence-threshold: 0.25
  enabled-features:
    - tags
```

### 5. Schema Migration

```sql
-- V20__add_ml_fields.sql

ALTER TABLE content_image
    ADD COLUMN ml_caption TEXT,
    ADD COLUMN aesthetic_score DECIMAL(4,2),
    ADD COLUMN ml_processed_at TIMESTAMP;

ALTER TABLE content_image_tags
    ADD COLUMN source VARCHAR(20) DEFAULT 'MANUAL';

CREATE TABLE face_detection (
    id              BIGSERIAL PRIMARY KEY,
    image_id        BIGINT NOT NULL REFERENCES content_image(id) ON DELETE CASCADE,
    bbox_x          FLOAT NOT NULL,
    bbox_y          FLOAT NOT NULL,
    bbox_w          FLOAT NOT NULL,
    bbox_h          FLOAT NOT NULL,
    embedding       FLOAT[] NOT NULL,
    detection_score FLOAT NOT NULL,
    person_id       BIGINT REFERENCES person(id),
    cluster_id      INTEGER,
    confidence      FLOAT,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_face_detection_image ON face_detection(image_id);
CREATE INDEX idx_face_detection_person ON face_detection(person_id);

CREATE TABLE image_embedding (
    id             BIGSERIAL PRIMARY KEY,
    image_id       BIGINT NOT NULL REFERENCES content_image(id) ON DELETE CASCADE,
    embedding      FLOAT[] NOT NULL,
    model_version  VARCHAR(50) NOT NULL,
    created_at     TIMESTAMP DEFAULT NOW(),
    UNIQUE(image_id, model_version)
);
```

### 6. Graceful Degradation

When the ML service is unavailable:
- `triggerAnalysisAsync` logs a warning and continues -- upload is never blocked
- `ml.enabled=false` skips the ML call entirely
- Images get `ml_processed_at = NULL`, making it easy to find unprocessed images for later backfill
- No retry logic needed: backfill endpoint handles catch-up

---

## Implementation Phases

### Phase 1: Backend Integration Points (this repo)
- MlControllerDev callback endpoint
- MlIntegrationService with async trigger
- MlServiceConfig configuration
- Schema migration (new columns + tables)
- Tag source tracking

### Phase 2: Python ML Service MVP (new repo: portfolio-ml)
- FastAPI project with CLIP tag generation
- Tag taxonomy definition
- EXIF metadata enrichment rules
- Callback to Spring Boot
- Single-image analysis working end-to-end

### Phase 3: Face Recognition
- InsightFace integration
- HDBSCAN clustering
- Cluster management endpoints (backend)
- User labeling workflow

### Phase 4: Backfill + Refinement
- Batch processing pipeline
- ChromaDB for embeddings/similarity search
- Fine-tuning pipeline
- Feedback tracking (accepted/rejected tags)

### Phase 5: Advanced Features
- BLIP-2 captioning
- Aesthetic scoring
- Similarity search endpoints
- Natural language image search
