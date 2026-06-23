package edens.zac.portfolio.backend.controller.dev;

import edens.zac.portfolio.backend.config.GlobalExceptionHandler;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.ContentImageUpdateRequest;
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.ContentRequests;
import edens.zac.portfolio.backend.model.DiskUploadRequest;
import edens.zac.portfolio.backend.model.GeneralMetadataDTO;
import edens.zac.portfolio.backend.model.ImageSearchRequest;
import edens.zac.portfolio.backend.model.ImageSearchResponse;
import edens.zac.portfolio.backend.model.ImageUploadResult;
import edens.zac.portfolio.backend.model.PagedResponse;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.services.AdminHomeService;
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.services.ContentService;
import edens.zac.portfolio.backend.services.ImageUploadPipelineService;
import edens.zac.portfolio.backend.services.JobTrackingService;
import edens.zac.portfolio.backend.services.MetadataService;
import edens.zac.portfolio.backend.services.PaginationUtil;
import edens.zac.portfolio.backend.types.CollectionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Single dev-only admin controller. Owns every {@code /api/admin/*} endpoint — admin-home tiles,
 * cache eviction, collection write ops, content uploads/edits, and metadata edits. All routes are
 * profile-gated and the class is package-private. Exception handling is delegated to {@link
 * GlobalExceptionHandler}.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin")
@Profile("dev")
class AdminController {

  private final AdminHomeService adminHomeService;
  private final CollectionService collectionService;
  private final ContentService contentService;
  private final ImageUploadPipelineService imageUploadPipelineService;
  private final JobTrackingService jobTrackingService;
  private final MetadataService metadataService;

  // ============================================================================
  // Admin home tiles
  // ============================================================================

  @GetMapping("/admin-home/tiles")
  ResponseEntity<List<Records.AdminHomeTileResponse>> getTiles() {
    List<Records.AdminHomeTileResponse> tiles = adminHomeService.getTiles();
    log.info("Returning {} admin home tiles", tiles.size());
    return ResponseEntity.ok(tiles);
  }

  // ============================================================================
  // Cache management
  // ============================================================================

  /** Clears in-process admin caches (currently the admin home tile cover cache). */
  @PostMapping("/cache/clear")
  ResponseEntity<Void> clearCache() {
    log.info("Clearing in-process admin caches");
    adminHomeService.evictAll();
    return ResponseEntity.noContent().build();
  }

  // ============================================================================
  // Collections
  // ============================================================================

  @PostMapping(value = "/collections/createCollection", consumes = "application/json")
  public ResponseEntity<CollectionRequests.UpdateResponse> createCollection(
      @RequestBody @Valid CollectionRequests.Create createRequest) {
    CollectionRequests.UpdateResponse response = collectionService.createCollection(createRequest);
    log.info("Created collection: {}", response.collection().getId());
    return ResponseEntity.ok(response);
  }

  @PutMapping("/collections/{id}")
  public ResponseEntity<CollectionRequests.UpdateResponse> updateCollection(
      @PathVariable Long id, @RequestBody @Valid CollectionRequests.Update updateDTO) {
    log.debug("Updating collection {} with request: {}", id, updateDTO);
    CollectionRequests.UpdateResponse response =
        collectionService.updateContentWithMetadata(id, updateDTO);
    log.info("Updated collection: {}", response.collection().getId());
    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/collections/{id}")
  public ResponseEntity<Map<String, Boolean>> deleteCollection(@PathVariable Long id) {
    collectionService.deleteCollection(id);
    log.info("Deleted collection: {}", id);
    return ResponseEntity.ok(Map.of("success", true));
  }

  /** All collections paginated, ordered by collection date DESC. Visibility is not filtered. */
  @GetMapping("/collections/all")
  public ResponseEntity<PagedResponse<CollectionModel>> getAllCollectionsOrderedByDate(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    Pageable pageable = PaginationUtil.normalizeCollectionPageable(page, size);
    Page<CollectionModel> collections = collectionService.getAllCollections(pageable);
    log.debug("Retrieved {} collections ordered by date", collections.getNumberOfElements());
    return ResponseEntity.ok(PagedResponse.from(collections));
  }

  /** Manage-page payload: collection plus all metadata (tags, people, cameras, films, etc.). */
  @GetMapping("/collections/{slug}/update")
  public ResponseEntity<CollectionRequests.UpdateResponse> getUpdateCollection(
      @PathVariable String slug) {
    CollectionRequests.UpdateResponse response = collectionService.getUpdateCollectionData(slug);
    log.debug("Retrieved update data for collection: {}", slug);
    return ResponseEntity.ok(response);
  }

  /** General metadata only (tags/people/cameras/lenses/etc.) — no collection. */
  @GetMapping("/collections/metadata")
  public ResponseEntity<GeneralMetadataDTO> getMetadata() {
    GeneralMetadataDTO response = collectionService.getGeneralMetadata();
    log.debug("Retrieved general metadata");
    return ResponseEntity.ok(response);
  }

  /** Atomic image reorder; recomputes sequential indices for all content in the collection. */
  @PostMapping("/collections/{collectionId}/reorder")
  public ResponseEntity<CollectionModel> reorderCollectionContent(
      @PathVariable Long collectionId, @RequestBody @Valid CollectionRequests.Reorder request) {
    log.debug(
        "Reordering content in collection {} with {} reorder operations",
        collectionId,
        request.reorders().size());
    CollectionModel updatedCollection = collectionService.reorderContent(collectionId, request);
    log.info("Reordered content in collection: {}", collectionId);
    return ResponseEntity.ok(updatedCollection);
  }

  /** Click-to-rate endpoint for the home manage page. */
  @PatchMapping("/collections/{id}/rating")
  ResponseEntity<Void> patchRating(@PathVariable Long id, @Valid @RequestBody RatingPatch body) {
    collectionService.updateRating(id, body.rating());
    return ResponseEntity.noContent().build();
  }

  public record RatingPatch(@Min(0) @Max(5) Integer rating) {}

  /** Replace the entire People list for a collection (DELETE-then-INSERT semantics). */
  @PutMapping("/collections/{id}/people")
  ResponseEntity<Void> setPeople(
      @PathVariable Long id,
      @RequestBody List<Long> personIds,
      @org.springframework.security.core.annotation.AuthenticationPrincipal
          edens.zac.portfolio.backend.model.AuthPrincipal principal) {
    collectionService.setCollectionPeople(
        id, personIds, principal == null ? null : principal.userId());
    log.info("Set {} people on collection {}", personIds == null ? 0 : personIds.size(), id);
    return ResponseEntity.noContent().build();
  }

  /** Auto-fill the collection's People list from the people tagged on its visible images. */
  @PostMapping("/collections/{id}/people/regenerate")
  ResponseEntity<Void> regeneratePeople(@PathVariable Long id) {
    collectionService.regeneratePeopleFromContents(id);
    log.info("Regenerated people from contents for collection {}", id);
    return ResponseEntity.noContent().build();
  }

  /** Create a new child collection under an existing parent. */
  @PostMapping("/collections/{parentId}/child")
  public ResponseEntity<CollectionRequests.UpdateResponse> createChildCollection(
      @PathVariable Long parentId, @RequestBody @Valid CollectionRequests.Create createRequest) {
    CollectionRequests.UpdateResponse response =
        collectionService.createChildCollection(parentId, createRequest);
    log.info(
        "Created child collection: {} under parent: {}", response.collection().getId(), parentId);
    return ResponseEntity.ok(response);
  }

  // ============================================================================
  // Content — images, text, gifs, jobs
  // ============================================================================

  /** Upload images to an existing collection (parallel pipeline). */
  @PostMapping(
      value = "/content/images/{collectionId}",
      consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
  public ResponseEntity<ImageUploadResult> createImages(
      @PathVariable Long collectionId,
      @RequestParam(value = "locationIds", required = false) List<Long> locationIds,
      @RequestParam(value = "rawFilePaths", required = false) List<String> rawFilePaths,
      @RequestPart(value = "files", required = true) List<MultipartFile> files) {
    if (files == null || files.isEmpty()) {
      throw new IllegalArgumentException(
          "No files provided. Use 'files' part with one or more images.");
    }

    if (locationIds != null && !locationIds.isEmpty()) {
      contentService.setCollectionLocationsIfMissing(collectionId, locationIds);
    }

    Map<String, String> rawFilePathMap = parseRawFilePaths(rawFilePaths);
    ImageUploadResult result =
        imageUploadPipelineService.createImagesParallel(collectionId, files, rawFilePathMap);
    log.info(
        "Created {} image(s) in collection: {} ({} failed)",
        result.successful().size(),
        collectionId,
        result.failed().size());
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }

  /** Create text or code content. */
  @PostMapping("/content/content")
  public ResponseEntity<ContentModel> createTextContent(
      @RequestBody @Valid ContentRequests.CreateTextContent request) {
    ContentModel textContent = contentService.createTextContent(request);
    if (textContent == null) {
      throw new IllegalArgumentException("Failed to create text content");
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(textContent);
  }

  /** Patch one or more images. */
  @PatchMapping("/content/images")
  // Unchecked: Spring's @RequestBody binding of a generic List<T>, plus the
  // Map<String,Object> -> List<ContentModels.Image> cast below. Both are inherently unchecked.
  @SuppressWarnings("unchecked")
  public ResponseEntity<Map<String, Object>> updateImages(
      @RequestBody @Valid List<ContentImageUpdateRequest> updates) {
    Map<String, Object> response = contentService.updateImages(updates);

    List<ContentModels.Image> updatedImages =
        (List<ContentModels.Image>) response.get("updatedImages");

    if (updatedImages == null || updatedImages.isEmpty()) {
      throw new IllegalArgumentException("No images were updated");
    }

    return ResponseEntity.ok(response);
  }

  /**
   * Paginated images with optional filters. Routes through {@link ContentService#searchImages} so
   * filter+pagination plumbing is shared with the public read endpoint. With all filter params
   * null, behaves like an unfiltered all-images fetch. Admin tier — does not apply collection-level
   * visibility filtering.
   */
  @GetMapping("/content/images")
  public ResponseEntity<PagedResponse<ContentModels.Image>> getAllImages(
      @RequestParam(required = false) List<Long> personIds,
      @RequestParam(required = false) List<Long> tagIds,
      @RequestParam(required = false) Long cameraId,
      @RequestParam(required = false) Long locationId,
      @RequestParam(required = false) Long lensId,
      @RequestParam(required = false) Integer minRating,
      @RequestParam(required = false) Boolean isFilm,
      @RequestParam(required = false) Boolean blackAndWhite,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate captureStartDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate captureEndDate,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    int safeSize = Math.min(Math.max(size, 1), 200);
    ImageSearchRequest request =
        new ImageSearchRequest(
            personIds,
            tagIds,
            cameraId,
            locationId,
            lensId,
            minRating,
            isFilm,
            blackAndWhite,
            captureStartDate,
            captureEndDate,
            page,
            safeSize);
    ImageSearchResponse response = contentService.searchImages(request);
    Pageable pageable = PageRequest.of(page, safeSize);
    Page<ContentModels.Image> wrapped =
        new PageImpl<>(response.content(), pageable, response.totalElements());
    return ResponseEntity.ok(PagedResponse.from(wrapped));
  }

  /** Delete one or more images. */
  @DeleteMapping("/content/images")
  public ResponseEntity<Map<String, Object>> deleteImages(
      @RequestBody Map<String, List<Long>> request) {
    List<Long> imageIds = request.get("imageIds");
    if (imageIds == null || imageIds.isEmpty()) {
      throw new IllegalArgumentException("No image IDs provided");
    }

    Map<String, Object> response = contentService.deleteImages(imageIds);

    @SuppressWarnings("unchecked")
    List<Long> deletedIds = (List<Long>) response.get("deletedIds");

    if (deletedIds == null || deletedIds.isEmpty()) {
      throw new IllegalArgumentException("No images were deleted");
    }

    return ResponseEntity.ok(response);
  }

  /** Upload an MP4 or GIF as animated content in a collection. */
  @PostMapping(
      value = "/content/{collectionId}/gifs",
      consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
  public ResponseEntity<ContentModels.Gif> createGif(
      @PathVariable Long collectionId,
      @RequestPart("file") MultipartFile file,
      @RequestParam(value = "title", required = false) String title,
      @RequestParam(value = "orderIndex", required = false) Integer orderIndex) {
    if (file.isEmpty()) {
      throw new IllegalArgumentException("No file provided. Use 'file' part with an MP4 or GIF.");
    }
    ContentModels.Gif gif = contentService.createGif(collectionId, file, title, orderIndex);
    log.info("Created GIF {} in collection {}", gif.id(), collectionId);
    return ResponseEntity.status(HttpStatus.CREATED).body(gif);
  }

  /**
   * Patch an existing GIF/MP4 content block. Only the fields present on the request are applied.
   */
  @PatchMapping("/content/gifs/{id}")
  public ResponseEntity<ContentModels.Gif> updateGif(
      @PathVariable Long id, @RequestBody @Valid ContentRequests.UpdateGif request) {
    ContentModels.Gif gif = contentService.updateGif(id, request);
    log.info("Updated GIF {}", id);
    return ResponseEntity.ok(gif);
  }

  /**
   * Delete a GIF/MP4 content block. Cleans up the S3 objects (full media + thumbnail) and the
   * content_gif row + base content row + tag join entries.
   */
  @DeleteMapping("/content/gifs/{id}")
  public ResponseEntity<Map<String, Object>> deleteGif(@PathVariable Long id) {
    Long deletedId = contentService.deleteGif(id);
    if (deletedId == null) {
      throw new IllegalArgumentException("GIF not found: " + id);
    }
    log.info("Deleted GIF {}", deletedId);
    return ResponseEntity.ok(Map.of("deletedId", deletedId));
  }

  /** Create a new collection and upload images to it in one request. */
  @PostMapping(
      value = "/content/images/create-collection",
      consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
  public ResponseEntity<ImageUploadResult> createCollectionWithImages(
      @RequestParam("title") String title,
      @RequestParam("type") String type,
      @RequestParam(value = "description", required = false) String description,
      @RequestParam(value = "locationIds", required = false) List<Long> locationIds,
      @RequestParam(value = "locationNames", required = false) List<String> locationNames,
      @RequestParam(value = "collectionDate", required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate collectionDate,
      @RequestParam(value = "rawFilePaths", required = false) List<String> rawFilePaths,
      @RequestPart(value = "files", required = true) List<MultipartFile> files) {
    if (files == null || files.isEmpty()) {
      throw new IllegalArgumentException(
          "No files provided. Use 'files' part with one or more images.");
    }

    CollectionType collectionType = CollectionType.forValue(type);

    CollectionRequests.Create createRequest =
        new CollectionRequests.Create(
            collectionType, title, description, locationIds, locationNames, collectionDate);

    Map<String, String> rawFilePathMap = parseRawFilePaths(rawFilePaths);
    ImageUploadResult result =
        imageUploadPipelineService.createCollectionWithImages(createRequest, files, rawFilePathMap);
    log.info(
        "Created collection '{}' with {} image(s) ({} failed)",
        title,
        result.successful().size(),
        result.failed().size());
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }

  /** Create a new tag. */
  @PostMapping("/content/tags")
  public ResponseEntity<Map<String, Object>> createTag(
      @RequestBody @Valid ContentRequests.CreateTag request) {
    String tagName = request.tagName();
    Map<String, Object> response = contentService.createTag(tagName);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /** Create a new person. */
  @PostMapping("/content/people")
  public ResponseEntity<Map<String, Object>> createPerson(
      @RequestBody @Valid ContentRequests.CreatePerson request) {
    String personName = request.personName();
    Map<String, Object> response = contentService.createPerson(personName);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /** Background disk-import job. Returns 202 with jobId for polling. */
  @PostMapping("/content/images/{collectionId}/from-disk")
  public ResponseEntity<Map<String, Object>> createImagesFromDisk(
      @PathVariable Long collectionId, @RequestBody @Valid DiskUploadRequest request) {
    var jobStatus = imageUploadPipelineService.processFilesFromDisk(collectionId, request);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            Map.of(
                "jobId", jobStatus.jobId().toString(),
                "totalFiles", jobStatus.totalFiles(),
                "message", "Processing started"));
  }

  /** Poll a disk-import job by id. */
  @GetMapping("/content/images/jobs/{jobId}")
  public ResponseEntity<JobTrackingService.JobStatusResponse> getJobStatus(
      @PathVariable UUID jobId) {
    return jobTrackingService
        .getJob(jobId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  // ============================================================================
  // Metadata edits — cameras, tags, people, locations
  // ============================================================================

  /** Create a new camera with optional film metadata. */
  @PostMapping("/metadata/cameras")
  public ResponseEntity<Map<String, Object>> createCamera(
      @RequestBody @Valid ContentRequests.CreateCamera request) {
    Map<String, Object> response =
        metadataService.createCamera(
            request.cameraName(),
            request.bodySerialNumber(),
            request.isFilm(),
            request.defaultFilmFormat());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PutMapping("/metadata/tags/{id}")
  ResponseEntity<Records.Tag> updateTag(
      @PathVariable Long id, @RequestBody Map<String, String> body) {
    String name = body.get("name");
    Records.Tag updated = metadataService.updateTag(id, name);
    log.info("Updated tag: {} -> {}", id, name);
    return ResponseEntity.ok(updated);
  }

  @PutMapping("/metadata/people/{id}")
  ResponseEntity<Records.Person> updatePerson(
      @PathVariable Long id, @RequestBody Map<String, String> body) {
    String name = body.get("name");
    Records.Person updated = metadataService.updatePerson(id, name);
    log.info("Updated person: {} -> {}", id, name);
    return ResponseEntity.ok(updated);
  }

  @PutMapping("/metadata/locations/{id}")
  ResponseEntity<Records.Location> updateLocation(
      @PathVariable Long id, @RequestBody Map<String, String> body) {
    String name = body.get("name");
    Records.Location updated = metadataService.updateLocation(id, name);
    log.info("Updated location: {} -> {}", id, name);
    return ResponseEntity.ok(updated);
  }

  @DeleteMapping("/metadata/tags/{id}")
  ResponseEntity<Map<String, Boolean>> deleteTag(@PathVariable Long id) {
    metadataService.deleteTag(id);
    log.info("Deleted tag: {}", id);
    return ResponseEntity.ok(Map.of("success", true));
  }

  @DeleteMapping("/metadata/people/{id}")
  ResponseEntity<Map<String, Boolean>> deletePerson(@PathVariable Long id) {
    metadataService.deletePerson(id);
    log.info("Deleted person: {}", id);
    return ResponseEntity.ok(Map.of("success", true));
  }

  @DeleteMapping("/metadata/locations/{id}")
  ResponseEntity<Map<String, Boolean>> deleteLocation(@PathVariable Long id) {
    metadataService.deleteLocation(id);
    log.info("Deleted location: {}", id);
    return ResponseEntity.ok(Map.of("success", true));
  }

  // ============================================================================
  // Helpers
  // ============================================================================

  /**
   * Parse rawFilePaths entries into a map of rendered filename to RAW file path. Each entry is
   * formatted {@code "renderedFilename|/absolute/path/to/raw.NEF"}.
   */
  private Map<String, String> parseRawFilePaths(List<String> rawFilePaths) {
    if (rawFilePaths == null || rawFilePaths.isEmpty()) {
      return Map.of();
    }
    return rawFilePaths.stream()
        .filter(entry -> entry != null && entry.contains("|"))
        .collect(
            Collectors.toMap(
                entry -> entry.substring(0, entry.indexOf('|')),
                entry -> entry.substring(entry.indexOf('|') + 1),
                (existing, replacement) -> replacement));
  }
}
