package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.types.CollectionType;
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
public interface CollectionRepository extends JpaRepository<CollectionEntity, Long> {

    // Blog - ordered by date descending, visible only

    /**
     * Find a collection of Content Collections for BLOG
     *
     * @param type Collection type such as: BLOG/CLIENT_GALLERY/PORTFOLIO/MISC
     * @return List of BLOG that are ordered by Date Desc and Visible
     */
    List<CollectionEntity> findTop50ByTypeAndVisibleTrueOrderByCollectionDateDesc(CollectionType type);

    // Blog - ordered by date descending, visibility irrelevant (admin)
    /**
     * Find a collection of Content Collections for BLOG
     *
     * @param type Collection type BLOG
     * @return List of BLOG that are ordered by Date Desc
     */
    List<CollectionEntity> findTop50ByTypeOrderByCollectionDateDesc(CollectionType type);

//    /**
//     * Find a collection by slug and verify it with a password hash for client galleries.
//     * TODO: Not used until we implement 'password' logic
//     *
//     * @param slug The unique slug of the collection
//     * @param passwordHash The hashed password for verification
//     * @return Optional containing the collection if found and password matches
//     */
//    Optional<CollectionEntity> findBySlugAndPasswordHash(String slug, String passwordHash);

    /**
     * Find a Collection's metadata. Used in conjunction with paginated endpoints in ContentRepository
     * @param slug Slug of Collection
     * @return Metadata of Collection
     */
    Optional<CollectionEntity> findBySlug(String slug);

    /**
     * Find a collection by slug with first 50 content, ordered by their order_index.
     * This is the main "basic" endpoint that auto-limits to 50 content for performance.
     * If more than 50 exist, frontend should use pagination endpoints.
     *
     * @param slug The unique slug of the collection
     * @return Optional containing the collection with up to 50 content if found
     */
    @Query("SELECT c FROM CollectionEntity c LEFT JOIN FETCH c.collectionContent cc " +
            "WHERE c.slug = :slug " +
            "ORDER BY cc.orderIndex ASC")
    Optional<CollectionEntity> findBySlugWithContent(@Param("slug") String slug);
    
    /**
     * Count the number of collections of a specific type.
     *
     * @param type The collection type
     * @return The count of collections of the specified type
     */
    long countByType(CollectionType type);

    /**
     * Count collections by type and visibility.
     *
     * @param type The collection type
     * @return The count of visible collections of the specified type
     */
    long countByTypeAndVisibleTrue(CollectionType type);

    /**
     * Find all collections ordered by collection date descending.
     * Returns all collections regardless of visibility or other filters.
     * Intended for admin/dev use only.
     *
     * @return List of all collections ordered by collection date DESC
     */
    List<CollectionEntity> findAllByOrderByCollectionDateDesc();
}