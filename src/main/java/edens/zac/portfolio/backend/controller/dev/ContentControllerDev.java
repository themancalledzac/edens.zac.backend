package edens.zac.portfolio.backend.controller.dev;

import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.ContentImageUpdateRequest;
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.ContentRequests;
import edens.zac.portfolio.backend.model.DiskUploadRequest;
import edens.zac.portfolio.backend.model.ImageUploadResult;
import edens.zac.portfolio.backend.services.ContentService;
import edens.zac.portfolio.backend.services.ImageUploadPipelineService;
import edens.zac.portfolio.backend.services.JobTrackingService;
import edens.zac.portfolio.backend.types.CollectionType;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller for Content write operations (dev environment only). Provides endpoints for creating,
 * updating, and managing content, tags, and people. Exception handling is delegated to
 * GlobalExceptionHandler.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/content")
public class ContentControllerDev {

  private final ContentService contentService;
  private final ImageUploadPipelineService imageUploadPipelineService;
  private final JobTrackingService jobTrackingService;

  /**
   * Create and upload images to a collection POST /api/admin/content/images/{collectionId}
   *
   * <p>OPTIMIZED: Uses parallel processing for faster batch uploads. Images are processed
   * concurrently (S3 upload, resize, convert) then saved to database in a single transaction.
   *
   * @param collectionId ID of the collection to add images to
   * @param files List of image files to upload
   * @return ResponseEntity with created images
   */
  @PostMapping(
      value = "/images/{collectionId}",
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

    // Optionally set collection locations if provided and not already set
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

  /**
   * Create text or code content POST /api/admin/content/content
   *
   * @param request CreateTextContentRequest or CreateCodeContentRequest
   * @return ResponseEntity with created content
   */
  @PostMapping("/content")
  public ResponseEntity<ContentModel> createTextContent(
      @RequestBody @Valid ContentRequests.CreateTextContent request) {
    ContentModel textContent = contentService.createTextContent(request);
    if (textContent == null) {
      throw new IllegalArgumentException("Failed to create text content");
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(textContent);
  }

  /**
   * Update one or more images PATCH /api/admin/content/images
   *
   * @param updates List of image updates
   * @return ResponseEntity with updated images and newly created metadata
   */
  @PatchMapping("/images")
  public ResponseEntity<Map<String, Object>> updateImages(
      @RequestBody @Valid List<ContentImageUpdateRequest> updates) {
    Map<String, Object> response = contentService.updateImages(updates);

    @SuppressWarnings("unchecked")
    List<ContentModels.Image> updatedImages =
        (List<ContentModels.Image>) response.get("updatedImages");

    if (updatedImages == null || updatedImages.isEmpty()) {
      throw new IllegalArgumentException("No images were updated");
    }

    return ResponseEntity.ok(response);
  }

  /**
   * Get all images ordered by date descending (newest first) GET /api/admin/content/images
   *
   * <p>OPTIMIZED: Uses database-level pagination to prevent loading all images at once.
   *
   * @param page Page number (0-indexed, default: 0)
   * @param size Page size (default: 50)
   * @return ResponseEntity with paginated list of images sorted by createDate descending
   */
  @GetMapping("/images")
  public ResponseEntity<Page<ContentModels.Image>> getAllImages(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    Pageable pageable = PageRequest.of(page, size);
    Page<ContentModels.Image> images = contentService.getAllImages(pageable);
    return ResponseEntity.ok(images);
  }

  /**
   * Delete one or more images DELETE /api/admin/content/images
   *
   * @param request Map containing "imageIds" list
   * @return ResponseEntity with deleted image IDs
   */
  @DeleteMapping("/images")
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

  /**
   * Upload an MP4 or GIF as animated content in a collection. POST
   * /api/admin/content/{collectionId}/gifs
   */
  @PostMapping(
      value = "/{collectionId}/gifs",
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
   * Create a new collection and upload images to it in one request. POST
   * /api/admin/content/images/create-collection
   *
   * <p>The collection is automatically linked as a child of the "staging" collection. If
   * collectionDate is not provided, it is auto-derived from image EXIF data. The highest-rated
   * uploaded image is auto-set as the cover image.
   */
  @PostMapping(
      value = "/images/create-collection",
      consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
  public ResponseEntity<ImageUploadResult> createCollectionWithImages(
      @RequestParam("title") String title,
      @RequestParam("type") String type,
      @RequestParam(value = "description", required = false) String description,
      @RequestParam(value = "locationIds", required = false) List<Long> locationIds,
      @RequestParam(value = "locationNames", required = false) List<String> locationNames,
      @RequestParam(value = "collectionDate", required = false)
          @org.springframework.format.annotation.DateTimeFormat(
              iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
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

  /**
   * Create a new tag POST /api/admin/content/tags
   *
   * @param request CreateTagRequest containing tag name
   * @return ResponseEntity with created tag
   */
  @PostMapping("/tags")
  public ResponseEntity<Map<String, Object>> createTag(
      @RequestBody @Valid ContentRequests.CreateTag request) {
    String tagName = request.tagName();
    Map<String, Object> response = contentService.createTag(tagName);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * Create a new person POST /api/admin/content/people
   *
   * @param request CreatePersonRequest containing person name
   * @return ResponseEntity with created person
   */
  @PostMapping("/people")
  public ResponseEntity<Map<String, Object>> createPerson(
      @RequestBody @Valid ContentRequests.CreatePerson request) {
    String personName = request.personName();
    Map<String, Object> response = contentService.createPerson(personName);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * Accept file paths from Lightroom and process images from disk in background. Returns 202
   * Accepted immediately with a job ID for status polling.
   *
   * @param collectionId ID of the collection to add images to
   * @param request File paths and optional locationId
   * @return 202 Accepted with jobId and totalFiles
   */
  @PostMapping("/images/{collectionId}/from-disk")
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

  /**
   * Poll for background job status.
   *
   * @param jobId The job ID returned from the /from-disk endpoint
   * @return Job status with progress counters, or 404 if not found
   */
  @GetMapping("/images/jobs/{jobId}")
  public ResponseEntity<JobTrackingService.JobStatusResponse> getJobStatus(
      @PathVariable UUID jobId) {
    return jobTrackingService
        .getJob(jobId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  /**
   * Parse rawFilePaths parameter entries into a map of rendered filename to RAW file path. Each
   * entry is formatted as "renderedFilename|/absolute/path/to/raw.NEF".
   *
   * @param rawFilePaths List of "filename|path" strings, or null
   * @return Map of rendered filename to RAW path, empty map if input is null
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
