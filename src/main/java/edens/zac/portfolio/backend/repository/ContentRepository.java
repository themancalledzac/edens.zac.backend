package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.ContentEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.types.ContentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
    
    /**
     * Find all content for a specific collection, ordered by their order_index.
     *
     * @param collectionId The ID of the collection
     * @return List of ordered content for the collection
     */
    List<ContentEntity> findByCollectionIdOrderByOrderIndex(Long collectionId);
    
    /**
     * Find content for a specific collection with pagination.
     *
     * @param collectionId The ID of the collection
     * @param pageable Pagination information
     * @return Page of content for the collection
     */
    Page<ContentEntity> findByCollectionId(Long collectionId, Pageable pageable);
    
    /**
     * Count the number of content in a collection.
     *
     * @param collectionId The ID of the collection
     * @return The count of content in the collection
     */
    long countByCollectionId(Long collectionId);
    
    /**
     * Find content of a specific type for a collection.
     *
     * @param collectionId The ID of the collection
     * @param contentType The type of content
     * @return List of content of the specified type for the collection
     */
    List<ContentEntity> findByCollectionIdAndContentType(Long collectionId, ContentType contentType);
    
    /**
     * Find content of a specific type for a collection with pagination.
     *
     * @param collectionId The ID of the collection
     * @param contentType The type of content
     * @param pageable Pagination information
     * @return Page of content of the specified type for the collection
     */
    Page<ContentEntity> findByCollectionIdAndContentType(Long collectionId, ContentType contentType, Pageable pageable);
    
    /**
     * Get the maximum order_index value for a collection's content.
     * This is useful when adding new to ensure correct ordering.
     *
     * @param collectionId The ID of the collection
     * @return The maximum order_index value, or null if no content exist
     */
    @Query("SELECT MAX(cb.orderIndex) FROM ContentEntity cb WHERE cb.collectionId = :collectionId")
    Integer getMaxOrderIndexForCollection(@Param("collectionId") Long collectionId);
    
    /**
     * Update the order_index of a specific content.
     *
     * @param id The ID of the content
     * @param orderIndex The new order_index value
     * @return The number of affected rows (should be 1)
     */
    @Modifying
    @Query("UPDATE ContentEntity cb SET cb.orderIndex = :orderIndex WHERE cb.id = :id")
    int updateOrderIndex(@Param("id") Long id, @Param("orderIndex") Integer orderIndex);
    
    /**
     * Shift the order_index values for a range of content in a collection.
     * This is useful when reordering content.
     *
     * @param collectionId The ID of the collection
     * @param startIndex The starting order_index (inclusive)
     * @param endIndex The ending order_index (inclusive)
     * @param shiftAmount The amount to shift the order_index values (positive or negative)
     * @return The number of affected rows
     */
    @Modifying
    @Query("UPDATE ContentEntity cb SET cb.orderIndex = cb.orderIndex + :shiftAmount " +
           "WHERE cb.collectionId = :collectionId AND cb.orderIndex >= :startIndex AND cb.orderIndex <= :endIndex")
    int shiftOrderIndices(@Param("collectionId") Long collectionId,
                          @Param("startIndex") Integer startIndex,
                          @Param("endIndex") Integer endIndex,
                          @Param("shiftAmount") Integer shiftAmount);
    
    /**
     * Delete all content for a specific collection.
     * This is useful when deleting a collection.
     *
     * @param collectionId The ID of the collection
     */
    void deleteByCollectionId(Long collectionId);

    /**
     * Dissociate the given content IDs from a specific collection by setting their collectionId to NULL.
     * This does not delete the and allows them to be reassigned to another collection later.
     *
     * @param collectionId The collection to dissociate from
     * @param ids          The IDs to dissociate
     */
    @Modifying
    @Query("UPDATE ContentEntity cb SET cb.collectionId = NULL WHERE cb.collectionId = :collectionId AND cb.id IN :ids")
    void dissociateFromCollection(@Param("collectionId") Long collectionId, @Param("ids") List<Long> ids);

    /**
     * Find a content by collection and its exact order index.
     */
    ContentEntity findByCollectionIdAndOrderIndex(Long collectionId, Integer orderIndex);

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