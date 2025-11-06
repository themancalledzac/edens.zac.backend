package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.CollectionContentEntity;
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
 * Repository for CollectionContentEntity (join table between Collection and Content).
 * This replaces the old ContentRepository methods that referenced collectionId/orderIndex.
 */
@Repository
public interface CollectionContentRepository extends JpaRepository<CollectionContentEntity, Long> {

    /**
     * Find all join table entries for a collection, ordered by orderIndex.
     * Note: Content entities are loaded separately via bulk fetch for better performance and proper typing.
     *
     * @param collectionId The ID of the collection
     * @return List of CollectionContentEntity ordered by orderIndex
     */
    @Query("SELECT cc FROM CollectionContentEntity cc WHERE cc.collection.id = :collectionId ORDER BY cc.orderIndex ASC")
    List<CollectionContentEntity> findByCollectionIdOrderByOrderIndex(@Param("collectionId") Long collectionId);

    /**
     * Find join table entries for a collection with pagination.
     * Note: Content entities are loaded separately via bulk fetch for better performance and proper typing.
     *
     * @param collectionId The ID of the collection
     * @param pageable     Pagination information
     * @return Page of CollectionContentEntity
     */
    @Query("SELECT cc FROM CollectionContentEntity cc WHERE cc.collection.id = :collectionId ORDER BY cc.orderIndex ASC")
    Page<CollectionContentEntity> findByCollectionId(@Param("collectionId") Long collectionId, Pageable pageable);

    /**
     * Count the number of content items in a collection (via join table).
     *
     * @param collectionId The ID of the collection
     * @return The count of content in the collection
     */
    @Query("SELECT COUNT(cc) FROM CollectionContentEntity cc WHERE cc.collection.id = :collectionId")
    long countByCollectionId(@Param("collectionId") Long collectionId);

    /**
     * Find content of a specific type for a collection.
     *
     * @param collectionId The ID of the collection
     * @param contentType  The type of content
     * @return List of CollectionContentEntity with specified content type
     */
    @Query("SELECT cc FROM CollectionContentEntity cc WHERE cc.collection.id = :collectionId AND cc.content.contentType = :contentType ORDER BY cc.orderIndex ASC")
    List<CollectionContentEntity> findByCollectionIdAndContentType(@Param("collectionId") Long collectionId,
                                                                     @Param("contentType") ContentType contentType);

    /**
     * Get the maximum orderIndex value for a collection's content.
     *
     * @param collectionId The ID of the collection
     * @return The maximum orderIndex value, or null if no content exists
     */
    @Query("SELECT MAX(cc.orderIndex) FROM CollectionContentEntity cc WHERE cc.collection.id = :collectionId")
    Integer getMaxOrderIndexForCollection(@Param("collectionId") Long collectionId);

    /**
     * Get the next orderIndex value for a collection's content.
     * This is a convenience method that returns the next available orderIndex (max + 1, or 0 if no content exists).
     *
     * @param collectionId The ID of the collection
     * @return The next orderIndex value (0 if collection is empty, max + 1 otherwise)
     */
    default Integer getNextOrderIndexForCollection(Long collectionId) {
        Integer maxOrderIndex = getMaxOrderIndexForCollection(collectionId);
        return maxOrderIndex != null ? maxOrderIndex + 1 : 0;
    }

    /**
     * Update the orderIndex of a specific join table entry.
     *
     * @param id         The ID of the CollectionContentEntity
     * @param orderIndex The new orderIndex value
     */
    @Modifying
    @Query("UPDATE CollectionContentEntity cc SET cc.orderIndex = :orderIndex WHERE cc.id = :id")
    void updateOrderIndex(@Param("id") Long id, @Param("orderIndex") Integer orderIndex);

    @Modifying
    @Query("UPDATE CollectionContentEntity cc SET cc.visible = :visible WHERE cc.id = :id")
    void updateVisible(@Param("id") Long id, @Param("visible") Boolean visible);

    /**
     * Shift orderIndex values for a range of content in a collection.
     *
     * @param collectionId The ID of the collection
     * @param startIndex   The starting orderIndex (inclusive)
     * @param endIndex     The ending orderIndex (inclusive)
     * @param shiftAmount  The amount to shift (positive or negative)
     * @return The number of affected rows
     */
    @Modifying
    @Query("UPDATE CollectionContentEntity cc SET cc.orderIndex = cc.orderIndex + :shiftAmount " +
            "WHERE cc.collection.id = :collectionId AND cc.orderIndex >= :startIndex AND cc.orderIndex <= :endIndex")
    int shiftOrderIndices(@Param("collectionId") Long collectionId,
                          @Param("startIndex") Integer startIndex,
                          @Param("endIndex") Integer endIndex,
                          @Param("shiftAmount") Integer shiftAmount);

    /**
     * Delete all join table entries for a collection.
     * This dissociates all content from the collection but does NOT delete the content itself.
     *
     * @param collectionId The ID of the collection
     */
    @Modifying
    @Query("DELETE FROM CollectionContentEntity cc WHERE cc.collection.id = :collectionId")
    void deleteByCollectionId(@Param("collectionId") Long collectionId);

    /**
     * Find a join table entry by collection and orderIndex.
     *
     * @param collectionId The ID of the collection
     * @param orderIndex   The orderIndex value
     * @return The CollectionContentEntity, or null if not found
     */
    @Query("SELECT cc FROM CollectionContentEntity cc WHERE cc.collection.id = :collectionId AND cc.orderIndex = :orderIndex")
    CollectionContentEntity findByCollectionIdAndOrderIndex(@Param("collectionId") Long collectionId,
                                                             @Param("orderIndex") Integer orderIndex);

    /**
     * Remove specific content from a collection (delete join table entries).
     *
     * @param collectionId The ID of the collection
     * @param contentIds   The IDs of the content to remove
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CollectionContentEntity cc WHERE cc.collection.id = :collectionId AND cc.content.id IN :contentIds")
    void removeContentFromCollection(@Param("collectionId") Long collectionId,
                                      @Param("contentIds") List<Long> contentIds);

    /**
     * Find a join table entry by collection ID and content ID.
     * This is useful for updating collection-specific metadata for a particular content item.
     *
     * @param collectionId The ID of the collection
     * @param contentId    The ID of the content
     * @return The CollectionContentEntity, or null if not found
     */
    @Query("SELECT cc FROM CollectionContentEntity cc WHERE cc.collection.id = :collectionId AND cc.content.id = :contentId")
    CollectionContentEntity findByCollectionIdAndContentId(@Param("collectionId") Long collectionId,
                                                             @Param("contentId") Long contentId);

    /**
     * Find all join table entries for multiple content IDs.
     * Used for batch-loading collections for multiple content items efficiently.
     * Returns all collections that contain any of the specified content items.
     * Eagerly fetches collection and cover image to avoid lazy loading issues.
     *
     * @param contentIds The IDs of the content items
     * @return List of CollectionContentEntity entries for the specified content items
     */
    @Query("SELECT DISTINCT cc FROM CollectionContentEntity cc " +
           "LEFT JOIN FETCH cc.collection c " +
           "LEFT JOIN FETCH c.coverImage " +
           "WHERE cc.content.id IN :contentIds")
    List<CollectionContentEntity> findByContentIdsIn(@Param("contentIds") List<Long> contentIds);
}
