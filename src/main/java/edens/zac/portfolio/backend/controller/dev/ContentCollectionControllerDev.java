package edens.zac.portfolio.backend.controller.dev;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;

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
    private final ObjectMapper objectMapper;

    /**
     * Create a new collection with multipart support for content blocks
     *
     * @param collectionDtoJson JSON string of collection data
     * @return ResponseEntity with the created collection
     */
    @PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<?> createCollection(
            @RequestPart("collection") String collectionDtoJson) {
        try {
            // Convert JSON string to ContentCollectionCreateDTO
            ContentCollectionCreateDTO createDTO = objectMapper.readValue(collectionDtoJson, ContentCollectionCreateDTO.class);

            // Create collection with content
            ContentCollectionModel createdCollection = contentCollectionService.createWithContent(createDTO);
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
     * Update collection metadata
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
     * Reorder content blocks within a collection
     *
     * @param id Collection ID
     * @param orderMap Map of content block IDs to new order indices
     * @return ResponseEntity with updated collection
     */
    @PutMapping("/{id}/content")
    public ResponseEntity<?> reorderContentBlocks(
            @PathVariable Long id,
            @RequestBody Map<Long, Integer> orderMap) {
        try {
            // Create update DTO with reordering information
            ContentCollectionUpdateDTO updateDTO = new ContentCollectionUpdateDTO();

            // Convert map to list of reorder operations
            List<ContentCollectionUpdateDTO.ContentBlockReorderOperation> reorderOperations = 
                orderMap.entrySet().stream()
                    .map(entry -> new ContentCollectionUpdateDTO.ContentBlockReorderOperation(
                        entry.getKey(), entry.getValue()))
                    .toList();

            updateDTO.setReorderOperations(reorderOperations);

            ContentCollectionModel updatedCollection = contentCollectionService.updateContent(id, updateDTO);
            log.info("Successfully reordered content blocks for collection: {}", id);

            return ResponseEntity.ok(updatedCollection);
        } catch (EntityNotFoundException e) {
            log.warn("Collection not found: {}", id);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Collection with ID: " + id + " not found");
        } catch (Exception e) {
            log.error("Error reordering content blocks for collection {}: {}", id, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to reorder content blocks: " + e.getMessage());
        }
    }

    /**
     * Add content blocks to a collection
     *
     * @param id Collection ID
     * @param contentDtoJson JSON string of content block data
     * @param files Optional files for content blocks
     * @return ResponseEntity with updated collection
     */
    @PostMapping(value = "/{id}/content", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<?> addContentBlocks(
            @PathVariable Long id,
            @RequestPart("content") String contentDtoJson,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        try {
            // Create update DTO with new content blocks
            ContentCollectionUpdateDTO updateDTO = objectMapper.readValue(contentDtoJson, ContentCollectionUpdateDTO.class);

            // Update collection with content and process files if provided
            ContentCollectionModel updatedCollection = contentCollectionService.updateContentWithFiles(id, updateDTO, files);

            log.info("Successfully added content blocks to collection: {}", id);

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
