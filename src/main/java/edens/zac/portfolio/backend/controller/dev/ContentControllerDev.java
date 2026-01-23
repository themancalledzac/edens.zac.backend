package edens.zac.portfolio.backend.controller.dev;

import edens.zac.portfolio.backend.model.*;
import edens.zac.portfolio.backend.services.ContentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller for Content write operations (dev environment only). Provides endpoints for creating,
 * updating, and managing content, tags, and people.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/content")
public class ContentControllerDev {

  private final ContentService contentService;

  /**
   * Create and upload images to a collection POST /api/admin/content/images/{collectionId}
   *
   * @param collectionId ID of the collection to add images to
   * @param files List of image files to upload
   * @return ResponseEntity with created images
   */
  @PostMapping(
      value = "/images/{collectionId}",
      consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
  public ResponseEntity<?> createImages(
      @PathVariable Long collectionId,
      @RequestPart(value = "files", required = true) List<MultipartFile> files) {
    try {
      if (files == null || files.isEmpty()) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body("No files provided. Use 'files' part with one or more images.");
      }

      List<ContentImageModel> createdImages = contentService.createImages(collectionId, files);

      log.info("Successfully created {} image(s) in collection: {}", files.size(), collectionId);

      return ResponseEntity.status(HttpStatus.CREATED).body(createdImages);
    } catch (IllegalArgumentException e) {
      log.warn("Invalid image creation request: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    } catch (Exception e) {
      log.error("Error creating images for collection {}: {}", collectionId, e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Failed to create images: " + e.getMessage());
    }
  }

  /**
   * Create text or code content POST /api/admin/content/content
   *
   * @param request CreateTextContentRequest or CreateCodeContentRequest
   * @return ResponseEntity with created content
   */
  @PostMapping("/content")
  public ResponseEntity<?> createTextContent(@RequestBody @Valid CreateTextContentRequest request) {

    try {
      ContentModel textContent = contentService.createTextContent(request);

      if (textContent == null) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
      }
      return ResponseEntity.status(HttpStatus.CREATED).body(textContent);

    } catch (IllegalArgumentException e) {
      log.warn("Invalid content creation request: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    } catch (Exception e) {
      log.error("Error creating content: {}", e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Failed to create content: " + e.getMessage());
    }
  }

  /**
   * Update one or more images PATCH /api/admin/content/images
   *
   * @param updates List of image updates
   * @return ResponseEntity with updated images and newly created metadata
   */
  @PatchMapping("/images")
  public ResponseEntity<?> updateImages(
      @RequestBody @Valid List<ContentImageUpdateRequest> updates) {
    try {
      Map<String, Object> response = contentService.updateImages(updates);

      @SuppressWarnings("unchecked")
      List<ContentImageModel> updatedImages =
          (List<ContentImageModel>) response.get("updatedImages");

      if (updatedImages == null || updatedImages.isEmpty()) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
      }

      return ResponseEntity.ok(response);
    } catch (IllegalArgumentException e) {
      log.warn("Invalid image update request: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    } catch (Exception e) {
      log.error("Error updating images: {}", e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Failed to update images: " + e.getMessage());
    }
  }

  /**
   * Get all images ordered by date descending (newest first) GET /api/admin/content/images
   *
   * @return ResponseEntity with list of all images sorted by createDate descending
   */
  @GetMapping("/images")
  public ResponseEntity<List<ContentImageModel>> getAllImages() {
    try {
      List<ContentImageModel> images = contentService.getAllImages();
      return ResponseEntity.ok(images);
    } catch (Exception e) {
      log.error("Error fetching all images: {}", e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Delete one or more images DELETE /api/admin/content/images
   *
   * @param request Map containing "imageIds" list
   * @return ResponseEntity with deleted image IDs
   */
  @DeleteMapping("/images")
  public ResponseEntity<?> deleteImages(@RequestBody Map<String, List<Long>> request) {
    try {
      List<Long> imageIds = request.get("imageIds");
      Map<String, Object> response = contentService.deleteImages(imageIds);

      @SuppressWarnings("unchecked")
      List<Long> deletedIds = (List<Long>) response.get("deletedIds");

      if (deletedIds.isEmpty()) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
      }

      return ResponseEntity.ok(response);
    } catch (IllegalArgumentException e) {
      log.warn("Invalid image deletion request: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    } catch (Exception e) {
      log.error("Error deleting images: {}", e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Failed to delete images: " + e.getMessage());
    }
  }

  /**
   * Create a new tag POST /api/admin/content/tags
   *
   * @param request CreateTagRequest containing tag name
   * @return ResponseEntity with created tag
   */
  @PostMapping("/tags")
  public ResponseEntity<?> createTag(@RequestBody @Valid CreateTagRequest request) {
    try {
      String tagName = request.getTagName();
      Map<String, Object> response = contentService.createTag(tagName);
      return ResponseEntity.status(HttpStatus.CREATED).body(response);
    } catch (IllegalArgumentException e) {
      log.warn("Invalid tag creation request: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    } catch (DataIntegrityViolationException e) {
      log.error("Data integrity error creating tag: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    } catch (Exception e) {
      log.error("Error creating tag: {}", e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Failed to create tag: " + e.getMessage());
    }
  }

  /**
   * Create a new person POST /api/admin/content/people
   *
   * @param request CreatePersonRequest containing person name
   * @return ResponseEntity with created person
   */
  @PostMapping("/people")
  public ResponseEntity<?> createPerson(@RequestBody @Valid CreatePersonRequest request) {
    try {
      String personName = request.getPersonName();
      Map<String, Object> response = contentService.createPerson(personName);
      return ResponseEntity.status(HttpStatus.CREATED).body(response);
    } catch (IllegalArgumentException e) {
      log.warn("Invalid person creation request: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    } catch (DataIntegrityViolationException e) {
      log.error("Data integrity error creating person: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    } catch (Exception e) {
      log.error("Error creating person: {}", e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Failed to create person: " + e.getMessage());
    }
  }
}
