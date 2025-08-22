package edens.zac.portfolio.backend.controller.dev;

import edens.zac.portfolio.backend.model.ContentCollectionCreateDTO;
import edens.zac.portfolio.backend.model.ContentCollectionModel;
import edens.zac.portfolio.backend.model.ContentCollectionUpdateDTO;
import edens.zac.portfolio.backend.services.ContentCollectionService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Controller for ContentCollection write operations (dev environment only).
 * Provides endpoints for creating, updating, and managing content collections.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/write/collections")
@Configuration
@Profile("dev")
public class ContentCollectionControllerDev {

    private final ContentCollectionService contentCollectionService;

    /**
     * Create a new collection
     *
     * @param createDTO Collection creation data
     * @return ResponseEntity with the created collection
     */
    @PostMapping(
            value = "/createCollection",
            consumes = {MediaType.APPLICATION_JSON_VALUE}
    )
    public ResponseEntity<?> createCollection(
            @RequestBody ContentCollectionCreateDTO createDTO) {
        try {
            // Create collection with content
            ContentCollectionModel createdCollection = contentCollectionService.createCollection(createDTO);
            log.info("Successfully created collection: {}", createdCollection.getId());

            return ResponseEntity.ok(createdCollection);
        } catch (Exception e) {
            log.error("Error creating collection: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to create collection: " + e.getMessage());
        }
    }

    /**
     * Update collection metadata. Accepts partial updates
     *
     * @param id Collection ID
     * @param updateDTO DTO with update data
     * @return ResponseEntity with updated collection
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateCollection(
            @PathVariable Long id,
            @RequestBody ContentCollectionUpdateDTO updateDTO) {
        try {
            ContentCollectionModel updatedCollection = contentCollectionService.updateContent(id, updateDTO);
            log.info("Successfully updated collection: {}", updatedCollection.getId());

            return ResponseEntity.ok(updatedCollection);
        } catch (EntityNotFoundException e) {
            log.warn("Collection not found: {}", id);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Collection with ID: " + id + " not found");
        } catch (Exception e) {
            log.error("Error updating collection {}: {}", id, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to update collection: " + e.getMessage());
        }
    }

    /**
     * Add media content blocks (images/gifs) to a collection.
     * This endpoint only handles file uploads; no metadata updates are performed.
     *
     * @param id Collection ID
     * @param files Files to upload and append as content blocks
     * @return ResponseEntity with updated collection
     */
    @PostMapping(value = "/{id}/content", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<?> addContentBlocks(
            @PathVariable Long id,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        try {
            if (files == null || files.isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body("No files provided. Use 'files' part with one or more images.");
            }

            // Reject WebP uploads for now
            boolean hasWebp = files.stream()
                    .filter(f -> f != null && f.getOriginalFilename() != null)
                    .map(f -> f.getOriginalFilename().toLowerCase())
                    .anyMatch(name -> name.endsWith(".webp"));
            if (hasWebp) {
                return ResponseEntity
                        .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                        .body("WebP is not supported at this time. Please upload JPEG or PNG.");
            }

            ContentCollectionModel updatedCollection = contentCollectionService.addContentBlocks(id, files);

            log.info("Successfully added {} file(s) to collection: {}", files.size(), id);

            return ResponseEntity.ok(updatedCollection);
        } catch (EntityNotFoundException e) {
            log.warn("Collection not found: {}", id);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Collection with ID: " + id + " not found");
        } catch (Exception e) {
            log.error("Error adding content blocks to collection {}: {}", id, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to add content blocks: " + e.getMessage());
        }
    }

    /**
     * Remove a content block from a collection
     *
     * @param id Collection ID
     * @param blockId Content block ID
     * @return ResponseEntity with updated collection
     */
    @DeleteMapping("/{id}/content/{blockId}")
    public ResponseEntity<?> removeContentBlock(
            @PathVariable Long id,
            @PathVariable Long blockId) {
        try {
            // Create update DTO with block removal information
            ContentCollectionUpdateDTO updateDTO = new ContentCollectionUpdateDTO();
            updateDTO.setContentBlockIdsToRemove(List.of(blockId));

            ContentCollectionModel updatedCollection = contentCollectionService.updateContent(id, updateDTO);
            log.info("Successfully removed content block {} from collection: {}", blockId, id);

            return ResponseEntity.ok(updatedCollection);
        } catch (EntityNotFoundException e) {
            log.warn("Collection or content block not found: collection={}, block={}", id, blockId);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Collection or content block not found");
        } catch (Exception e) {
            log.error("Error removing content block {} from collection {}: {}", blockId, id, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to remove content block: " + e.getMessage());
        }
    }

    /**
     * Delete a collection
     *
     * @param id Collection ID
     * @return ResponseEntity with success message
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCollection(@PathVariable Long id) {
        try {
            contentCollectionService.deleteCollection(id);
            log.info("Successfully deleted collection: {}", id);

            return ResponseEntity.ok("Collection deleted successfully");
        } catch (EntityNotFoundException e) {
            log.warn("Collection not found: {}", id);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Collection with ID: " + id + " not found");
        } catch (Exception e) {
            log.error("Error deleting collection {}: {}", id, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to delete collection: " + e.getMessage());
        }
    }
}
