package edens.zac.portfolio.backend.controller.dev;

import edens.zac.portfolio.backend.model.ContentImageModel;
import edens.zac.portfolio.backend.model.ContentImageUpdateRequest;
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.CreatePersonRequest;
import edens.zac.portfolio.backend.model.CreateTagRequest;
import edens.zac.portfolio.backend.model.CreateTextContentRequest;
import edens.zac.portfolio.backend.services.ContentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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
 * Controller for Content write operations (dev environment only). Provides
 * endpoints for creating,
 * updating, and managing content, tags, and people. Exception handling is
 * delegated to
 * GlobalExceptionHandler.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/content")
public class ContentControllerDev {

  private final ContentService contentService;

  /**
   * Create and upload images to a collection POST
   * /api/admin/content/images/{collectionId}
   *
   * <p>
   * OPTIMIZED: Uses parallel processing for faster batch uploads. Images are
   * processed
   * concurrently (S3 upload, resize, convert) then saved to database in a single
   * transaction.
   *
   * @param collectionId ID of the collection to add images to
   * @param files        List of image files to upload
   * @return ResponseEntity with created images
   */
  @PostMapping(value = "/images/{collectionId}", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
  public ResponseEntity<List<ContentImageModel>> createImages(
      @PathVariable Long collectionId,
      @RequestPart(value = "files", required = true) List<MultipartFile> files) {
    if (files == null || files.isEmpty()) {
      throw new IllegalArgumentException(
          "No files provided. Use 'files' part with one or more images.");
    }

    List<ContentImageModel> createdImages = contentService.createImagesParallel(collectionId, files);
    log.info("Created {} image(s) in collection: {}", createdImages.size(), collectionId);
    return ResponseEntity.status(HttpStatus.CREATED).body(createdImages);
  }

  /**
   * Create text or code content POST /api/admin/content/content
   *
   * @param request CreateTextContentRequest or CreateCodeContentRequest
   * @return ResponseEntity with created content
   */
  @PostMapping("/content")
  public ResponseEntity<ContentModel> createTextContent(
      @RequestBody @Valid CreateTextContentRequest request) {
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
    List<ContentImageModel> updatedImages = (List<ContentImageModel>) response.get("updatedImages");

    if (updatedImages == null || updatedImages.isEmpty()) {
      throw new IllegalArgumentException("No images were updated");
    }

    return ResponseEntity.ok(response);
  }

  /**
   * Get all images ordered by date descending (newest first) GET
   * /api/admin/content/images
   *
   * <p>
   * OPTIMIZED: Uses database-level pagination to prevent loading all images at
   * once.
   *
   * @param page Page number (0-indexed, default: 0)
   * @param size Page size (default: 50)
   * @return ResponseEntity with paginated list of images sorted by createDate
   *         descending
   */
  @GetMapping("/images")
  public ResponseEntity<Page<ContentImageModel>> getAllImages(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    Pageable pageable = PageRequest.of(page, size);
    Page<ContentImageModel> images = contentService.getAllImages(pageable);
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
   * Create a new tag POST /api/admin/content/tags
   *
   * @param request CreateTagRequest containing tag name
   * @return ResponseEntity with created tag
   */
  @PostMapping("/tags")
  public ResponseEntity<Map<String, Object>> createTag(
      @RequestBody @Valid CreateTagRequest request) {
    String tagName = request.getTagName();
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
      @RequestBody @Valid CreatePersonRequest request) {
    String personName = request.getPersonName();
    Map<String, Object> response = contentService.createPerson(personName);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
