package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.ContentTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for ContentTagEntity that extends JpaRepository
 * to provide standard CRUD operations and custom query methods for tags.
 */
@Repository
public interface ContentTagRepository extends JpaRepository<ContentTagEntity, Long> {

    /**
     * Find a tag by its exact name.
     *
     * @param tagName The name of the tag
     * @return Optional containing the tag if found
     */
    Optional<ContentTagEntity> findByTagName(String tagName);

    /**
     * Find a tag by its name (case-insensitive).
     *
     * @param tagName The name of the tag
     * @return Optional containing the tag if found
     */
    Optional<ContentTagEntity> findByTagNameIgnoreCase(String tagName);

    /**
     * Find all tags containing the search term (case-insensitive).
     * Useful for tag autocomplete functionality.
     *
     * @param searchTerm The search term to match
     * @return List of tags containing the search term
     */
    List<ContentTagEntity> findByTagNameContainingIgnoreCase(String searchTerm);

    /**
     * Get all tags ordered by name.
     *
     * @return List of all tags ordered alphabetically
     */
    List<ContentTagEntity> findAllByOrderByTagNameAsc();

    /**
     * Check if a tag with the given name already exists.
     *
     * @param tagName The name to check
     * @return True if tag exists
     */
    boolean existsByTagName(String tagName);

    /**
     * Check if a tag with the given name already exists (case-insensitive).
     *
     * @param tagName The name to check
     * @return True if tag exists
     */
    boolean existsByTagNameIgnoreCase(String tagName);

//    /**
//     * Find the most commonly used tags across all entities.
//     * Ordered by usage count (collections + image content + gif content).
//     *
//     * @return List of tags ordered by total usage count descending
//     */
//    @Query("SELECT t FROM ContentTagEntity t " +
//            "LEFT JOIN t.collections cc " +
//            "LEFT JOIN t.ContentImage icb " +
//            "LEFT JOIN t.contentGifs gcb " +
//            "GROUP BY t.id " +
//            "ORDER BY (COUNT(DISTINCT cc.id) + COUNT(DISTINCT icb.id) + COUNT(DISTINCT gcb.id)) DESC")
//    List<ContentTagEntity> findAllOrderByUsageCountDesc();
}
