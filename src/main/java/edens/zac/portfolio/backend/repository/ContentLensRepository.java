package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.ContentLensEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for ContentLensEntity that extends JpaRepository
 * to provide standard CRUD operations and custom query methods for lenses.
 */
@Repository
public interface ContentLensRepository extends JpaRepository<ContentLensEntity, Long> {

    /**
     * Find a lens by its exact name.
     *
     * @param lensName The name of the lens
     * @return Optional containing the lens if found
     */
    Optional<ContentLensEntity> findByLensName(String lensName);

    /**
     * Find a lens by its name (case-insensitive).
     *
     * @param lensName The name of the lens
     * @return Optional containing the lens if found
     */
    Optional<ContentLensEntity> findByLensNameIgnoreCase(String lensName);

    /**
     * Find all lenses containing the search term (case-insensitive).
     * Useful for lens autocomplete functionality.
     *
     * @param searchTerm The search term to match
     * @return List of lenses containing the search term
     */
    List<ContentLensEntity> findByLensNameContainingIgnoreCase(String searchTerm);

    /**
     * Get all lenses ordered by name.
     *
     * @return List of all lenses ordered alphabetically
     */
    List<ContentLensEntity> findAllByOrderByLensNameAsc();

    /**
     * Check if a lens with the given name already exists.
     *
     * @param lensName The name to check
     * @return True if lens exists
     */
    boolean existsByLensName(String lensName);

    /**
     * Check if a lens with the given name already exists (case-insensitive).
     *
     * @param lensName The name to check
     * @return True if lens exists
     */
    boolean existsByLensNameIgnoreCase(String lensName);

    /**
     * Find the most commonly used lenses.
     * Ordered by the number of images using each lens.
     *
     * @return List of lenses ordered by usage count descending
     */
    @Query("SELECT l FROM ContentLensEntity l " +
            "LEFT JOIN l.contentImages icb " +
            "GROUP BY l.id " +
            "ORDER BY COUNT(DISTINCT icb.id) DESC")
    List<ContentLensEntity> findAllOrderByUsageCountDesc();
}
