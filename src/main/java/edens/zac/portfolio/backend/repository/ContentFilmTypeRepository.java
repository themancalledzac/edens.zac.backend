package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.ContentFilmTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for ContentFilmTypeEntity that extends JpaRepository
 * to provide standard CRUD operations and custom query methods for film types.
 */
@Repository
public interface ContentFilmTypeRepository extends JpaRepository<ContentFilmTypeEntity, Long> {

    /**
     * Find a film type by its exact technical name.
     *
     * @param filmTypeName The technical name of the film type (e.g., "KODAK_PORTRA_400")
     * @return Optional containing the film type if found
     */
    Optional<ContentFilmTypeEntity> findByFilmTypeName(String filmTypeName);

    /**
     * Find a film type by its technical name (case-insensitive).
     *
     * @param filmTypeName The technical name of the film type
     * @return Optional containing the film type if found
     */
    Optional<ContentFilmTypeEntity> findByFilmTypeNameIgnoreCase(String filmTypeName);

    /**
     * Find a film type by its display name.
     *
     * @param displayName The display name of the film type (e.g., "Kodak Portra 400")
     * @return Optional containing the film type if found
     */
    Optional<ContentFilmTypeEntity> findByDisplayName(String displayName);

    /**
     * Find all film types containing the search term in either name field (case-insensitive).
     * Useful for film type autocomplete functionality.
     *
     * @param searchTerm The search term to match
     * @return List of film types containing the search term
     */
    @Query("SELECT f FROM ContentFilmTypeEntity f " +
            "WHERE LOWER(f.filmTypeName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(f.displayName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<ContentFilmTypeEntity> findBySearchTerm(String searchTerm);

    /**
     * Get all film types ordered by display name.
     *
     * @return List of all film types ordered alphabetically by display name
     */
    List<ContentFilmTypeEntity> findAllByOrderByDisplayNameAsc();

    /**
     * Get all film types ordered by ISO value.
     *
     * @return List of all film types ordered by ISO
     */
    List<ContentFilmTypeEntity> findAllByOrderByDefaultIsoAsc();

    /**
     * Check if a film type with the given technical name already exists.
     *
     * @param filmTypeName The technical name to check
     * @return True if film type exists
     */
    boolean existsByFilmTypeName(String filmTypeName);

    /**
     * Check if a film type with the given technical name already exists (case-insensitive).
     *
     * @param filmTypeName The technical name to check
     * @return True if film type exists
     */
    boolean existsByFilmTypeNameIgnoreCase(String filmTypeName);

    /**
     * Check if a film type with the given display name already exists.
     *
     * @param displayName The display name to check
     * @return True if film type exists
     */
    boolean existsByDisplayName(String displayName);

    /**
     * Find the most commonly used film types.
     * Ordered by the number of images using each film type.
     *
     * @return List of film types ordered by usage count descending
     */
    @Query("SELECT f FROM ContentFilmTypeEntity f " +
            "LEFT JOIN f.contentImages icb " +
            "GROUP BY f.id " +
            "ORDER BY COUNT(DISTINCT icb.id) DESC")
    List<ContentFilmTypeEntity> findAllOrderByUsageCountDesc();
}
