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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Controller for ContentCollection write operations (dev environment only).
 * Provides endpoints for creating, updating, and managing content collections.
 * TODO: Probably update 'write' to `/api/admin/collections` as we will add an admin 'read' or two
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/write/collections")
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
     * Add media content(images/gifs) to a collection.
     * This endpoint only handles file uploads; no metadata updates are performed.
     *
     * @param id Collection ID
     * @param files Files to upload and append as content
     * @return ResponseEntity with updated collection
     */
    @PostMapping(value = "/{id}/content", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<?> addContent(
            @PathVariable Long id,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        try {
            if (files == null || files.isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body("No files provided. Use 'files' part with one or more images.");
            }

            CollectionModel updatedCollection = collectionService.addContent(id, files);

            log.info("Successfully added {} file(s) to collection: {}", files.size(), id);

            return ResponseEntity.ok(updatedCollection);
        } catch (EntityNotFoundException e) {
            log.warn("Collection not found: {}", id);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Collection with ID: " + id + " not found");
        } catch (Exception e) {
            log.error("Error adding content to collection {}: {}", id, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to add content: " + e.getMessage());
        }
    }

    // TODO: Add a 'addTextContent endpoint
    //  - takes only a string value ( can be rather large )
    //  - Updates the Collection specifically with that ContentText
    //  - Returns... maybe the whole collection? maybe JUST a 'success'?
    //  - - MAYBE, it returns just the contentText object:
    //  - - - {id, collectionId, orderIndex(last), contentType, content, formType, title}
    //  - Can we reuse the same `@PostMapping(value = "/{id}/content"`, but only with a 'text' body instead of a MediaType.MULTIPART_FORM_DATA_VALUE?

    /**
     * Remove a content from a collection
     *
     * @param id Collection ID
     * @param contentId Content ID
     * @return ResponseEntity with updated collection
     */
    @DeleteMapping("/{id}/content/{contentId}")
    public ResponseEntity<?> removeContent(
            @PathVariable Long id,
            @PathVariable Long contentId) {
        try {
            // Create update DTO with content removal information
            CollectionUpdateDTO updateDTO = new CollectionUpdateDTO();
            updateDTO.setContentIdsToRemove(List.of(contentId));

            CollectionModel updatedCollection = collectionService.updateContent(id, updateDTO);
            log.info("Successfully removed content {} from collection: {}", contentId, id);

            return ResponseEntity.ok(updatedCollection);
        } catch (EntityNotFoundException e) {
            log.warn("Collection or content not found: collection={}, content={}", id, contentId);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Collection or content not found");
        } catch (Exception e) {
            log.error("Error removing content {} from collection {}: {}", contentId, id, e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to remove content: " + e.getMessage());
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
