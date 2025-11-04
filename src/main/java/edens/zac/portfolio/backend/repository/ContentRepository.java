package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.ContentEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
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
     * Uses JOIN FETCH to eagerly load relationships and avoid N+1 queries.
     *
     * @return List of all image content ordered by createDate descending
     */
    @Query("SELECT DISTINCT i FROM ContentImageEntity i " +
           "LEFT JOIN FETCH i.tags " +
           "LEFT JOIN FETCH i.people " +
           "LEFT JOIN FETCH i.camera " +
           "LEFT JOIN FETCH i.lens " +
           "LEFT JOIN FETCH i.filmType " +
           "ORDER BY i.createDate DESC")
    List<ContentImageEntity> findAllImagesOrderByCreateDateDesc();

    /**
     * Find all ContentEntity instances with the given contentType.
     * Uses native query to avoid issues with JOINED inheritance strategy.
     *
     * @param contentType The content type to filter by
     * @return List of all content IDs with this content type
     */
    @Query(value = "SELECT id FROM content WHERE content_type = :contentType", nativeQuery = true)
    List<Long> findIdsByContentType(@Param("contentType") String contentType);

    /**
     * Find all ContentEntity instances by their IDs.
     * This properly loads all subclasses (ContentImageEntity, ContentTextEntity, ContentGifEntity, ContentCollectionEntity)
     * due to JOINED inheritance strategy. This is more efficient than loading them one by one.
     *
     * @param ids List of content IDs to fetch
     * @return List of ContentEntity instances (properly typed subclasses)
     */
    @Query("SELECT c FROM ContentEntity c WHERE c.id IN :ids")
    List<ContentEntity> findAllByIds(@Param("ids") List<Long> ids);

    /**
     * Find a ContentCollectionEntity by its referenced collection ID.
     * This efficiently finds the ContentCollectionEntity that references a specific collection
     * without loading all COLLECTION content types.
     *
     * @param referencedCollectionId The ID of the collection being referenced
     * @return Optional containing the ContentCollectionEntity if found
     */
    @Query("SELECT c FROM ContentCollectionEntity c WHERE c.referencedCollection.id = :referencedCollectionId")
    java.util.Optional<ContentCollectionEntity> findContentCollectionByReferencedCollectionId(@Param("referencedCollectionId") Long referencedCollectionId);
}