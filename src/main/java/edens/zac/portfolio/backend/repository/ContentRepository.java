package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.ContentEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for ContentEntity that extends JpaRepository
 * to provide standard CRUD operations, pagination, and custom query methods.
 */
@Repository
public interface ContentRepository extends JpaRepository<ContentEntity, Long> {

    // NOTE: All collection-specific queries have been moved to CollectionContentRepository
    // Content is now independent and reusable across collections via the collection_content join table

    /**
     * Check if an image with the given fileIdentifier already exists in the database.
     * This is used for duplicate detection during image uploads.
     *
     * @param fileIdentifier The file identifier (format: "YYYY-MM-DD/filename.jpg")
     * @return true if an image with this identifier exists, false otherwise
     */
    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM ContentImageEntity i WHERE i.fileIdentifier = :fileIdentifier")
    boolean existsByFileIdentifier(@Param("fileIdentifier") String fileIdentifier);

    /**
     * Find all ContentImageEntity instances with the given fileIdentifier.
     * This returns all collection relationships for the same image.
     *
     * @param fileIdentifier The file identifier (format: "YYYY-MM-DD/filename.jpg")
     * @return List of all image content with this file identifier across all collections
     */
    @Query("SELECT i FROM ContentImageEntity i WHERE i.fileIdentifier = :fileIdentifier")
    List<ContentImageEntity> findAllByFileIdentifier(@Param("fileIdentifier") String fileIdentifier);

    /**
     * Find all ContentImageEntity instances ordered by createDate descending.
     * This returns all images across all collections, sorted by most recent first.
     *
     * @return List of all image content ordered by createDate descending
     */
    @Query("SELECT i FROM ContentImageEntity i ORDER BY i.createDate DESC")
    List<ContentImageEntity> findAllImagesOrderByCreateDateDesc();
}