package edens.zac.portfolio.backend.controller.dev;

import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.GeneralMetadataDTO;
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.services.PaginationUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for Collection write operations (dev environment only). Provides endpoints for
 * creating, updating, and managing collections. Exception handling is delegated to
 * GlobalExceptionHandler.
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
   * Create a new collection.
   *
   * @param createRequest Collection creation data
   * @return ResponseEntity with the created collection and all metadata for the manage page
   */
  @PostMapping(value = "/createCollection", consumes = "application/json")
  public ResponseEntity<CollectionRequests.UpdateResponse> createCollection(
      @RequestBody @Valid CollectionRequests.Create createRequest) {
    CollectionRequests.UpdateResponse response = collectionService.createCollection(createRequest);
    log.info("Created collection: {}", response.collection().getId());
    return ResponseEntity.ok(response);
  }

  /**
   * Update collection metadata. Accepts partial updates.
   *
   * @param id Collection ID
   * @param updateDTO DTO with update data
   * @return ResponseEntity with updated collection
   */
  @PutMapping("/{id}")
  public ResponseEntity<CollectionModel> updateCollection(
      @PathVariable Long id, @RequestBody @Valid CollectionRequests.Update updateDTO) {
    log.debug("Updating collection {} with request: {}", id, updateDTO);
    CollectionModel updatedCollection = collectionService.updateContent(id, updateDTO);
    log.info("Updated collection: {}", updatedCollection.getId());
    return ResponseEntity.ok(updatedCollection);
  }

  /**
   * Delete a collection.
   *
   * @param id Collection ID
   * @return ResponseEntity with no content
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteCollection(@PathVariable Long id) {
    collectionService.deleteCollection(id);
    log.info("Deleted collection: {}", id);
    return ResponseEntity.noContent().build();
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
  public ResponseEntity<Page<CollectionModel>> getAllCollectionsOrderedByDate(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    Pageable pageable = PaginationUtil.normalizeCollectionPageable(page, size);
    Page<CollectionModel> collections = collectionService.getAllCollections(pageable);
    log.debug("Retrieved {} collections ordered by date", collections.getNumberOfElements());
    return ResponseEntity.ok(collections);
  }

  /**
   * Get collection with all metadata for the update/manage page. Returns the collection along with
   * all available tags, people, cameras, and film metadata. This single endpoint provides
   * everything needed for the image management UI.
   *
   * @param slug Collection slug
   * @return ResponseEntity with collection and metadata
   */
  @GetMapping("/{slug}/update")
  public ResponseEntity<CollectionRequests.UpdateResponse> getUpdateCollection(
      @PathVariable String slug) {
    CollectionRequests.UpdateResponse response = collectionService.getUpdateCollectionData(slug);
    log.debug("Retrieved update data for collection: {}", slug);
    return ResponseEntity.ok(response);
  }

  /**
   * Get general metadata without a specific collection. Returns all available tags, people,
   * cameras, lenses, film types, film formats, and collections. This is useful when you already
   * have collection data and only need the metadata.
   *
   * @return ResponseEntity with general metadata
   */
  @GetMapping("/metadata")
  public ResponseEntity<GeneralMetadataDTO> getMetadata() {
    GeneralMetadataDTO response = collectionService.getGeneralMetadata();
    log.debug("Retrieved general metadata");
    return ResponseEntity.ok(response);
  }

  /**
   * Reorder images within a collection. Updates the orderIndex for specified images and recomputes
   * sequential indices for all content. This is an atomic operation that ensures all order indices
   * are sequential (0, 1, 2, ...).
   *
   * @param collectionId Collection ID
   * @param request Reorder request containing image IDs and their new order indices
   * @return ResponseEntity with updated collection
   */
  @PostMapping("/{collectionId}/reorder")
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
}
