package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.model.ContentCollectionModel;
import edens.zac.portfolio.backend.model.ContentCollectionCreateDTO;
import edens.zac.portfolio.backend.model.ContentCollectionUpdateDTO;
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
     * Find a collection by slug.
     *
     * @param slug The unique slug of the collection
     * @return Optional containing the collection if found
     */
    Optional<ContentCollectionModel> findBySlug(String slug);

    /**
     * Create a new collection with content.
     *
     * @param createDTO The DTO containing collection data
     * @return The created collection
     */
    ContentCollectionModel createWithContent(ContentCollectionCreateDTO createDTO);


    /**
     * Update a collection's content.
     *
     * @param id The ID of the collection
     * @param updateDTO The DTO containing update data
     * @return The updated collection
     */
    ContentCollectionModel updateContent(Long id, ContentCollectionUpdateDTO updateDTO);

    /**
     * Update a collection's content and add files.
     *
     * @param id The ID of the collection
     * @param updateDTO The DTO containing update data
     * @param files The files to be processed and added as content blocks
     * @return The updated collection
     */
    ContentCollectionModel updateContentWithFiles(Long id, ContentCollectionUpdateDTO updateDTO, List<MultipartFile> files);

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
}
