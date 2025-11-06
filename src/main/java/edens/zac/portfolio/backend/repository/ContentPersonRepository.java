package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for ContentPersonEntity that extends JpaRepository
 * to provide standard CRUD operations and custom query methods for people.
 */
@Repository
public interface ContentPersonRepository extends JpaRepository<ContentPersonEntity, Long> {

    /**
     * Find a person by their exact name.
     *
     * @param personName The name of the person
     * @return Optional containing the person if found
     */
    Optional<ContentPersonEntity> findByPersonName(String personName);

    /**
     * Find a person by their name (case-insensitive).
     *
     * @param personName The name of the person
     * @return Optional containing the person if found
     */
    Optional<ContentPersonEntity> findByPersonNameIgnoreCase(String personName);

    /**
     * Find all people whose names contain the search term (case-insensitive).
     * Useful for person autocomplete functionality.
     *
     * @param searchTerm The search term to match
     * @return List of people containing the search term
     */
    List<ContentPersonEntity> findByPersonNameContainingIgnoreCase(String searchTerm);

    /**
     * Get all people ordered by name.
     *
     * @return List of all people ordered alphabetically
     */
    List<ContentPersonEntity> findAllByOrderByPersonNameAsc();

    /**
     * Check if a person with the given name already exists.
     *
     * @param personName The name to check
     * @return True if person exists
     */
    boolean existsByPersonName(String personName);

    /**
     * Check if a person with the given name already exists (case-insensitive).
     *
     * @param personName The name to check
     * @return True if person exists
     */
    boolean existsByPersonNameIgnoreCase(String personName);

    /**
     * Find the most photographed people ordered by image count.
     *
     * @return List of people ordered by image count descending
     */
    @Query("SELECT p FROM ContentPersonEntity p " +
            "LEFT JOIN p.contentImages icb " +
            "GROUP BY p.id " +
            "ORDER BY COUNT(icb.id) DESC")
    List<ContentPersonEntity> findAllOrderByImageCountDesc();
}
