package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.CollectionCreateRequest;
import edens.zac.portfolio.backend.model.CollectionUpdateRequest;
import edens.zac.portfolio.backend.model.CollectionUpdateResponseDTO;
import edens.zac.portfolio.backend.types.CollectionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing Collection entities.
 * Provides methods for CRUD operations, pagination, and client gallery access.
 */
public interface CollectionService {

    /**
     * Get a collection by slug with pagination for content.
     *
     * @param slug The unique slug of the collection
     * @param page The page number (0-based)
     * @param size The page size (default: 30)
     * @return CollectionModel with paginated content
     */
    CollectionModel getCollectionWithPagination(String slug, int page, int size);

    /**
     * Validate access to a client gallery using a password.
     *
     * @param slug The unique slug of the client gallery
     * @param password The password for access
     * @return True if access is granted, false otherwise
     */
    boolean validateClientGalleryAccess(String slug, String password);

    /**
     * Find collections by type with pagination.
     *
     * @param type The collection type
     * @param pageable Pagination information
     * @return Page of collections of the specified type
     */
    Page<CollectionModel> findByType(CollectionType type, Pageable pageable);

    /**
     * Find visible collections by type ordered by collection date (newest first).
     * Returns all visible collections of the specified type as CollectionModel objects.
     *
     * @param type The collection type
     * @return List of visible collections ordered by collection date DESC
     */
    List<CollectionModel> findVisibleByTypeOrderByDate(CollectionType type);

    /**
     * Find a collection by slug.
     *
     * @param slug The unique slug of the collection
     * @return Optional containing the collection if found
     */
    Optional<CollectionModel> findBySlug(String slug);

    /**
     * Find a collection by ID.
     *
     * @param id The ID of the collection
     * @return The collection if found
     * @throws jakarta.persistence.EntityNotFoundException if collection not found
     */
    CollectionModel findById(Long id);

    /**
     * Create a new collection with content.
     *
     * @param createRequest The request containing collection data
     * @return The created collection with all metadata for the manage page
     */
    CollectionUpdateResponseDTO createCollection(CollectionCreateRequest createRequest);


    /**
     * Update a collection's content.
     *
     * @param id The ID of the collection
     * @param updateDTO The DTO containing update data
     * @return The updated collection
     */
    CollectionModel updateContent(Long id, CollectionUpdateRequest updateDTO);

    /**
     * Delete a collection by ID.
     * This deletes the collection and all join table entries (content associations),
     * but does NOT delete the content itself as content is reusable.
     *
     * @param id The ID of the collection to delete
     */
    void deleteCollection(Long id);

    /**
     * Get all collections with basic information (no content).
     *
     * @param pageable Pagination information
     * @return Page of collections with basic information
     */
    Page<CollectionModel> getAllCollections(Pageable pageable);

    /**
     * Get all collections ordered by collection date descending.
     * Returns all collections regardless of visibility or other filters.
     * Intended for admin/dev use only.
     *
     * @return List of all collections ordered by collection date DESC
     */
    List<CollectionModel> getAllCollectionsOrderedByDate();

    /**
     * Get collection with all metadata for the update/manage page.
     * Returns the collection along with all available tags, people, cameras, and film metadata.
     * This provides everything needed for the image management UI in a single call.
     *
     * @param slug The collection slug
     * @return Complete update data including collection and all metadata
     * @throws jakarta.persistence.EntityNotFoundException if collection not found
     */
    CollectionUpdateResponseDTO getUpdateCollectionData(String slug);

    /**
     * Get all general metadata without a specific collection.
     * Returns all available tags, people, cameras, lenses, film types, film formats, and collections.
     * This is useful when you already have collection data and only need the metadata.
     *
     * @return General metadata including tags, people, cameras, lenses, film types, film formats, and collections
     */
    edens.zac.portfolio.backend.model.GeneralMetadataDTO getGeneralMetadata();

    /**
     * Reorder images within a collection.
     * Updates the orderIndex for specified images and recomputes sequential indices for all content.
     * This is an atomic operation that ensures all order indices are sequential (0, 1, 2, ...).
     *
     * @param collectionId The ID of the collection
     * @param request The reorder request containing image IDs and their new order indices
     * @return The updated collection model
     * @throws jakarta.persistence.EntityNotFoundException if collection not found
     * @throws IllegalArgumentException if any image ID doesn't belong to the collection
     */
    CollectionModel reorderContent(Long collectionId, edens.zac.portfolio.backend.model.CollectionReorderRequest request);
}
