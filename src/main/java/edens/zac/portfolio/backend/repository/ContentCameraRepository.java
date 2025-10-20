package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.ContentCameraEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for ContentCameraEntity that extends JpaRepository
 * to provide standard CRUD operations and custom query methods for cameras.
 */
@Repository
public interface ContentCameraRepository extends JpaRepository<ContentCameraEntity, Long> {

    /**
     * Find a camera by its exact name.
     *
     * @param cameraName The name of the camera
     * @return Optional containing the camera if found
     */
    Optional<ContentCameraEntity> findByCameraName(String cameraName);

    /**
     * Find a camera by its name (case-insensitive).
     *
     * @param cameraName The name of the camera
     * @return Optional containing the camera if found
     */
    Optional<ContentCameraEntity> findByCameraNameIgnoreCase(String cameraName);

    /**
     * Find all cameras containing the search term (case-insensitive).
     * Useful for camera autocomplete functionality.
     *
     * @param searchTerm The search term to match
     * @return List of cameras containing the search term
     */
    List<ContentCameraEntity> findByCameraNameContainingIgnoreCase(String searchTerm);

    /**
     * Get all cameras ordered by name.
     *
     * @return List of all cameras ordered alphabetically
     */
    List<ContentCameraEntity> findAllByOrderByCameraNameAsc();

    /**
     * Check if a camera with the given name already exists.
     *
     * @param cameraName The name to check
     * @return True if camera exists
     */
    boolean existsByCameraName(String cameraName);

    /**
     * Check if a camera with the given name already exists (case-insensitive).
     *
     * @param cameraName The name to check
     * @return True if camera exists
     */
    boolean existsByCameraNameIgnoreCase(String cameraName);

    /**
     * Find the most commonly used cameras.
     * Ordered by the number of images using each camera.
     *
     * @return List of cameras ordered by usage count descending
     */
    @Query("SELECT c FROM ContentCameraEntity c " +
            "LEFT JOIN c.imageContentBlocks icb " +
            "GROUP BY c.id " +
            "ORDER BY COUNT(DISTINCT icb.id) DESC")
    List<ContentCameraEntity> findAllOrderByUsageCountDesc();
}
