package edens.zac.portfolio.backend.controller.dev;

import edens.zac.portfolio.backend.model.CollectionCreateRequest;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.CollectionReorderRequest;
import edens.zac.portfolio.backend.model.CollectionUpdateRequest;
import edens.zac.portfolio.backend.model.CollectionUpdateResponseDTO;
import edens.zac.portfolio.backend.model.GeneralMetadataDTO;
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.services.PaginationUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for Collection write operations (dev environment only). Provides
 * endpoints for
 * creating, updating, and managing collections.
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
   * @return ResponseEntity with the created collection and all metadata for the
   *         manage page
   */
  @PostMapping(value = "/createCollection", consumes = { MediaType.APPLICATION_JSON_VALUE })
  public ResponseEntity<?> createCollection(@RequestBody CollectionCreateRequest createRequest) {
    try {
      // Create collection and get full update response with all metadata (tags,
      // people, cameras,
      // etc.)
      CollectionUpdateResponseDTO response = collectionService.createCollection(createRequest);
      log.info("Successfully created collection: {}", response.getCollection().getId());

      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("Error creating collection: {}", e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Failed to create collection: " + e.getMessage());
    }
  }

  /**
   * Update collection metadata. Accepts partial updates
   *
   * @param id        Collection ID
   * @param updateDTO DTO with update data
   * @return ResponseEntity with updated collection
   */
  @PutMapping("/{id}")
  public ResponseEntity<?> updateCollection(
      @PathVariable Long id, @RequestBody CollectionUpdateRequest updateDTO) {
    try {
      log.debug("Updating collection {} with request: {}", id, updateDTO);
      CollectionModel updatedCollection = collectionService.updateContent(id, updateDTO);
      log.info("Successfully updated collection: {}", updatedCollection.getId());

      return ResponseEntity.ok(updatedCollection);
    } catch (IllegalArgumentException e) {
      log.warn("Collection not found: {}", id);
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body("Collection with ID: " + id + " not found");
    } catch (Exception e) {
      log.error("Error updating collection {}: {}", id, e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
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
    } catch (IllegalArgumentException e) {
      log.warn("Collection not found: {}", id);
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body("Collection with ID: " + id + " not found");
    } catch (Exception e) {
      log.error("Error deleting collection {}: {}", id, e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Failed to delete collection: " + e.getMessage());
    }
  }

  /**
   * Get all collections ordered by collection date (paginated). Returns all collections regardless
   * of visibility, hidden status, or lacking images. Dev/admin only endpoint for viewing complete
   * collection list.
   *
   * @param page Page number (0-based)
   * @param size Page size (default: 50)
   * @return ResponseEntity with paginated collections ordered by collection date DESC
   */
  @GetMapping("/all")
  public ResponseEntity<?> getAllCollectionsOrderedByDate(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    try {
      Pageable pageable = PaginationUtil.normalizeCollectionPageable(page, size);
      Page<CollectionModel> collections = collectionService.getAllCollections(pageable);
      log.info("Successfully retrieved {} collections ordered by date", collections.getSize());

      return ResponseEntity.ok(collections);
    } catch (Exception e) {
      log.error("Error retrieving all collections: {}", e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Failed to retrieve collections: " + e.getMessage());
    }
  }

  /**
   * Get collection with all metadata for the update/manage page. Returns the
   * collection along with
   * all available tags, people, cameras, and film metadata. This single endpoint
   * provides
   * everything needed for the image management UI.
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
    } catch (IllegalArgumentException e) {
      log.warn("Collection not found: {}", slug);
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body("Collection with slug: " + slug + " not found");
    } catch (Exception e) {
      log.error("Error retrieving update data for collection {}: {}", slug, e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Failed to retrieve update data: " + e.getMessage());
    }
  }

  /**
   * Get general metadata without a specific collection. Returns all available
   * tags, people,
   * cameras, lenses, film types, film formats, and collections. This is useful
   * when you already
   * have collection data and only need the metadata.
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
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Failed to retrieve general metadata: " + e.getMessage());
    }
  }

  /**
   * Reorder images within a collection. Updates the orderIndex for specified
   * images and recomputes
   * sequential indices for all content. This is an atomic operation that ensures
   * all order indices
   * are sequential (0, 1, 2, ...).
   *
   * @param collectionId Collection ID
   * @param request      Reorder request containing image IDs and their new order
   *                     indices
   * @return ResponseEntity with updated collection
   */
  @PostMapping("/{collectionId}/reorder")
  public ResponseEntity<?> reorderCollectionContent(
      @PathVariable Long collectionId, @RequestBody CollectionReorderRequest request) {
    try {
      log.debug(
          "Reordering content in collection {} with {} reorder operations",
          collectionId,
          request.getReorders().size());
      CollectionModel updatedCollection = collectionService.reorderContent(collectionId, request);
      log.info("Successfully reordered content in collection: {}", collectionId);
      return ResponseEntity.ok(updatedCollection);
    } catch (IllegalArgumentException e) {
      // Check error message to determine if it's a "not found" or "invalid request"
      // error
      String errorMessage = e.getMessage();
      if (errorMessage != null && errorMessage.contains("not found")) {
        log.warn("Collection not found: {}", collectionId);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body("Collection with ID: " + collectionId + " not found");
      } else {
        log.warn("Invalid reorder request for collection {}: {}", collectionId, errorMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body("Invalid reorder request: " + errorMessage);
      }
    } catch (Exception e) {
      log.error("Error reordering content in collection {}: {}", collectionId, e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Failed to reorder content: " + e.getMessage());
    }
  }
}
