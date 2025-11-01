package edens.zac.portfolio.backend.controller.dev;

import edens.zac.portfolio.backend.model.CollectionCreateRequest;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.CollectionUpdateDTO;
import edens.zac.portfolio.backend.model.CollectionUpdateResponseDTO;
import edens.zac.portfolio.backend.model.GeneralMetadataDTO;
import edens.zac.portfolio.backend.services.CollectionService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for Collection write operations (dev environment only).
 * Provides endpoints for creating, updating, and managing collections.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/collections")
@Configuration
@Profile("dev")
public class CollectionControllerDev {

    private final CollectionService collectionService;

    /**
     * Create a new collection
     *
     * @param createRequest Collection creation data
     * @return ResponseEntity with the created collection and all metadata for the manage page
     */
    @PostMapping(
            value = "/createCollection",
            consumes = {MediaType.APPLICATION_JSON_VALUE}
    )
    public ResponseEntity<?> createCollection(
            @RequestBody CollectionCreateRequest createRequest) {
        try {
            // Create collection and get full update response with all metadata (tags, people, cameras, etc.)
            CollectionUpdateResponseDTO response = collectionService.createCollection(createRequest);
            log.info("Successfully created collection: {}", response.getCollection().getId());

            return ResponseEntity.ok(response);
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
            @RequestBody CollectionUpdateDTO updateDTO) {
        try {
            CollectionModel updatedCollection = collectionService.updateContent(id, updateDTO);
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
     * Delete a collection
     *
     * @param id Collection ID
     * @return ResponseEntity with success message
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCollection(@PathVariable Long id) {
        try {
            collectionService.deleteCollection(id);
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

    /**
     * Get all collections ordered by collection date.
     * Returns all collections regardless of visibility, hidden status, or lacking images.
     * Dev/admin only endpoint for viewing complete collection list.
     *
     * @return ResponseEntity with list of all collections ordered by collection date DESC
     */
    @GetMapping("/all")
    public ResponseEntity<?> getAllCollectionsOrderedByDate() {
        try {
            List<CollectionModel> collections = collectionService.getAllCollectionsOrderedByDate();
            log.info("Successfully retrieved {} collections ordered by date", collections.size());

            return ResponseEntity.ok(collections);
        } catch (Exception e) {
            log.error("Error retrieving all collections: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve collections: " + e.getMessage());
        }
    }

    /**
     * Get collection with all metadata for the update/manage page.
     * Returns the collection along with all available tags, people, cameras, and film metadata.
     * This single endpoint provides everything needed for the image management UI.
     *
     * @param slug Collection slug
     * @return ResponseEntity with collection and metadata
     */
    @GetMapping("/{slug}/update")
    public ResponseEntity<?> getUpdateCollection(@PathVariable String slug) {
        try {
            CollectionUpdateResponseDTO response = collectionService.getUpdateCollectionData(slug);
            log.info("Successfully retrieved update data for collection: {}", slug);
            return ResponseEntity.ok(response);
        } catch (EntityNotFoundException e) {
            log.warn("Collection not found: {}", slug);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Collection with slug: " + slug + " not found");
        } catch (Exception e) {
            log.error("Error retrieving update data for collection {}: {}", slug, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve update data: " + e.getMessage());
        }
    }

    /**
     * Get general metadata without a specific collection.
     * Returns all available tags, people, cameras, lenses, film types, film formats, and collections.
     * This is useful when you already have collection data and only need the metadata.
     *
     * @return ResponseEntity with general metadata
     */
    @GetMapping("/metadata")
    public ResponseEntity<?> getMetadata() {
        try {
            GeneralMetadataDTO response = collectionService.getGeneralMetadata();
            log.info("Successfully retrieved general metadata");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving general metadata: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve general metadata: " + e.getMessage());
        }
    }
}
