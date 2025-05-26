package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import edens.zac.portfolio.backend.types.CollectionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for ContentCollectionEntity that extends JpaRepository
 * to provide standard CRUD operations, pagination, and custom query methods.
 */
@Repository
public interface ContentCollectionRepository extends JpaRepository<ContentCollectionEntity, Long> {

    /**
     * Find a collection by its unique slug.
     *
     * @param slug The unique slug of the collection
     * @return Optional containing the collection if found
     */
    Optional<ContentCollectionEntity> findBySlug(String slug);

    /**
     * Find all collections of a specific type.
     *
     * @param type The collection type
     * @return List of collections of the specified type
     */
    List<ContentCollectionEntity> findByType(CollectionType type);

    /**
     * Find all collections of a specific type, ordered by priority (descending).
     *
     * @param type The collection type
     * @return List of collections of the specified type ordered by priority
     */
    List<ContentCollectionEntity> findByTypeOrderByPriorityDesc(CollectionType type);

    /**
     * Find all collections of a specific type with pagination.
     *
     * @param type The collection type
     * @param pageable Pagination information
     * @return Page of collections of the specified type
     */
    Page<ContentCollectionEntity> findByType(CollectionType type, Pageable pageable);

    /**
     * Find a collection by slug and verify it with a password hash for client galleries.
     *
     * @param slug The unique slug of the collection
     * @param passwordHash The hashed password for verification
     * @return Optional containing the collection if found and password matches
     */
    Optional<ContentCollectionEntity> findBySlugAndPasswordHash(String slug, String passwordHash);

    /**
     * Find a collection by slug with related content blocks, ordered by their order_index.
     * This query eagerly fetches the content blocks to avoid N+1 query problems.
     *
     * @param slug The unique slug of the collection
     * @return Optional containing the collection with its content blocks if found
     */
    @Query("SELECT c FROM ContentCollectionEntity c LEFT JOIN FETCH c.contentBlocks b WHERE c.slug = :slug ORDER BY b.orderIndex")
    Optional<ContentCollectionEntity> findBySlugWithContentBlocks(@Param("slug") String slug);

    /**
     * Find a collection by ID with paginated content blocks.
     * This custom query returns the collection with a paginated subset of its content blocks.
     *
     * @param id The ID of the collection
     * @param pageable Pagination information for the content blocks
     * @return Optional containing the collection if found
     */
    @Query(value = "SELECT c FROM ContentCollectionEntity c WHERE c.id = :id")
    Optional<ContentCollectionEntity> findByIdWithContentBlocksPaginated(@Param("id") Long id, Pageable pageable);
    
    /**
     * Check if a collection exists with the given slug.
     *
     * @param slug The slug to check
     * @return True if a collection with the given slug exists
     */
    boolean existsBySlug(String slug);
    
    /**
     * Find recent collections of any type, ordered by creation date (descending).
     *
     * @param pageable Pagination information
     * @return Page of recent collections
     */
    Page<ContentCollectionEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
    /**
     * Find visible collections (published status) of a specific type.
     *
     * @param type The collection type
     * @param visible The visibility status
     * @return List of visible collections of the specified type
     */
    List<ContentCollectionEntity> findByTypeAndVisibleIsTrue(CollectionType type);
    
    /**
     * Find visible collections (published status) of a specific type with pagination.
     *
     * @param type The collection type
     * @param visible The visibility status
     * @param pageable Pagination information
     * @return Page of visible collections of the specified type
     */
    Page<ContentCollectionEntity> findByTypeAndVisibleIsTrue(CollectionType type, Pageable pageable);
    
    /**
     * Count the number of collections of a specific type.
     *
     * @param type The collection type
     * @return The count of collections of the specified type
     */
    long countByType(CollectionType type);
}