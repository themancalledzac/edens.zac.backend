package edens.zac.portfolio.backend.controller.dev;

import edens.zac.portfolio.backend.model.ImageContentBlockModel;
import edens.zac.portfolio.backend.model.ImageUpdateRequest;
import edens.zac.portfolio.backend.services.ContentBlockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for ContentBlock write operations (dev environment only).
 * Provides endpoints for creating, updating, and managing content blocks, tags, and people.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/write/blocks")
public class ContentBlockControllerDev {

    private final ContentBlockService contentBlockService;

    /**
     * Create a new tag
     * POST /api/dev/blocks/tags
     * TODO: Update our 'createTagModel' to be a static map of 'tagName' and somethign with image
     *
     * @param request Map containing "tagName"
     * @return ResponseEntity with created tag
     */
    @PostMapping("/tags")
    public ResponseEntity<?> createTag(@RequestBody Map<String, String> request) {
        try {
            String tagName = request.get("tagName");
            Map<String, Object> response = contentBlockService.createTag(tagName);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid tag creation request: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        } catch (DataIntegrityViolationException e) {
            log.error("Data integrity error creating tag: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(e.getMessage());
        } catch (Exception e) {
            log.error("Error creating tag: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to create tag: " + e.getMessage());
        }
    }

    /**
     * Create a new person
     * POST /api/dev/blocks/people
     *
     * @param request Map containing "personName"
     * @return ResponseEntity with created person
     */
    @PostMapping("/people")
    public ResponseEntity<?> createPerson(@RequestBody Map<String, String> request) {
        try {
            String personName = request.get("personName");
            Map<String, Object> response = contentBlockService.createPerson(personName);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid person creation request: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        } catch (DataIntegrityViolationException e) {
            log.error("Data integrity error creating person: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(e.getMessage());
        } catch (Exception e) {
            log.error("Error creating person: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to create person: " + e.getMessage());
        }
    }

    /**
     * Update one or more images
     * PATCH /api/dev/blocks/images
     *
     * @param updates List of image updates
     * @return ResponseEntity with updated images and newly created metadata
     */
    @PatchMapping("/images")
    public ResponseEntity<?> updateImages(@RequestBody @Valid List<ImageUpdateRequest> updates) {
        try {
            Map<String, Object> response = contentBlockService.updateImages(updates);

            @SuppressWarnings("unchecked")
            List<ImageContentBlockModel> updatedImages = (List<ImageContentBlockModel>) response.get("updatedImages");

            if (updatedImages == null || updatedImages.isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(response);
            }

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid image update request: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        } catch (Exception e) {
            log.error("Error updating images: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to update images: " + e.getMessage());
        }
    }

    /**
     * Delete one or more images
     * DELETE /api/dev/blocks/images
     *
     * @param request Map containing "imageIds" list
     * @return ResponseEntity with deleted image IDs
     */
    @DeleteMapping("/images")
    public ResponseEntity<?> deleteImages(@RequestBody Map<String, List<Long>> request) {
        try {
            List<Long> imageIds = request.get("imageIds");
            Map<String, Object> response = contentBlockService.deleteImages(imageIds);

            @SuppressWarnings("unchecked")
            List<Long> deletedIds = (List<Long>) response.get("deletedIds");

            if (deletedIds.isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(response);
            }

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid image deletion request: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        } catch (Exception e) {
            log.error("Error deleting images: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to delete images: " + e.getMessage());
        }
    }

    /**
     * Get all images ordered by date descending
     * GET /api/write/blocks/images
     *
     * @return ResponseEntity with list of all images
     */
    @GetMapping("/images")
    public ResponseEntity<List<ImageContentBlockModel>> getAllImages() {
        try {
            List<ImageContentBlockModel> images = contentBlockService.getAllImages();
            return ResponseEntity.ok(images);
        } catch (Exception e) {
            log.error("Error fetching all images: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }
}
