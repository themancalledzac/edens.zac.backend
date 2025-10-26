package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.model.ContentCollectionModel;
import edens.zac.portfolio.backend.model.ContentCollectionCreateRequest;
import edens.zac.portfolio.backend.model.ContentCollectionUpdateDTO;
import edens.zac.portfolio.backend.model.ContentCollectionUpdateResponseDTO;
import edens.zac.portfolio.backend.model.HomeCardModel;
import edens.zac.portfolio.backend.types.CollectionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing ContentCollection entities.
 * Provides methods for CRUD operations, pagination, and client gallery access.
 */
public interface ContentCollectionService {

    /**
     * Get a collection by slug with pagination for content blocks.
     *
     * @param slug The unique slug of the collection
     * @param page The page number (0-based)
     * @param size The page size (default: 30)
     * @return ContentCollectionModel with paginated content blocks
     */
    ContentCollectionModel getCollectionWithPagination(String slug, int page, int size);

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
    Page<ContentCollectionModel> findByType(CollectionType type, Pageable pageable);

    /**
     * Find visible collections by type ordered by collection date (newest first).
     * Returns all visible collections of the specified type as HomeCardModel objects.
     *
     * @param type The collection type
     * @return List of visible collections ordered by collection date DESC
     */
    List<HomeCardModel> findVisibleByTypeOrderByDate(CollectionType type);

    /**
     * Find a collection by slug.
     *
     * @param slug The unique slug of the collection
     * @return Optional containing the collection if found
     */
    Optional<ContentCollectionModel> findBySlug(String slug);

    /**
     * Find a collection by ID.
     *
     * @param id The ID of the collection
     * @return The collection if found
     * @throws jakarta.persistence.EntityNotFoundException if collection not found
     */
    ContentCollectionModel findById(Long id);

    /**
     * Create a new collection with content.
     *
     * @param createRequest The request containing collection data
     * @return The created collection with all metadata for the manage page
     */
    ContentCollectionUpdateResponseDTO createCollection(ContentCollectionCreateRequest createRequest);


    /**
     * Update a collection's content.
     *
     * @param id The ID of the collection
     * @param updateDTO The DTO containing update data
     * @return The updated collection
     */
    ContentCollectionModel updateContent(Long id, ContentCollectionUpdateDTO updateDTO);


    /**
     * Add media files (images/gifs) to a collection as new content blocks without changing metadata.
     *
     * @param id The ID of the collection
     * @param files The files to be processed and added as content blocks
     * @return The updated collection after files are added
     */
    ContentCollectionModel addContentBlocks(Long id, List<MultipartFile> files);

    /**
     * Delete a collection by ID.
     *
     * @param id The ID of the collection to delete
     */
    void deleteCollection(Long id);

    /**
     * Get all collections with basic information (no content blocks).
     *
     * @param pageable Pagination information
     * @return Page of collections with basic information
     */
    Page<ContentCollectionModel> getAllCollections(Pageable pageable);

    /**
     * Get all collections ordered by collection date descending.
     * Returns all collections regardless of visibility or other filters.
     * Intended for admin/dev use only.
     *
     * @return List of all collections ordered by collection date DESC
     */
    List<ContentCollectionModel> getAllCollectionsOrderedByDate();

    /**
     * Get collection with all metadata for the update/manage page.
     * Returns the collection along with all available tags, people, cameras, and film metadata.
     * This provides everything needed for the image management UI in a single call.
     *
     * @param slug The collection slug
     * @return Complete update data including collection and all metadata
     * @throws jakarta.persistence.EntityNotFoundException if collection not found
     */
    ContentCollectionUpdateResponseDTO getUpdateCollectionData(String slug);

    /**
     * Get all general metadata without a specific collection.
     * Returns all available tags, people, cameras, lenses, film types, film formats, and collections.
     * This is useful when you already have collection data and only need the metadata.
     *
     * @return General metadata including tags, people, cameras, lenses, film types, film formats, and collections
     */
    edens.zac.portfolio.backend.model.GeneralMetadataDTO getGeneralMetadata();
}
