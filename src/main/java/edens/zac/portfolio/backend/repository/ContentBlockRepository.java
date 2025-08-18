package edens.zac.portfolio.backend.repository;

import edens.zac.portfolio.backend.entity.ContentBlockEntity;
import edens.zac.portfolio.backend.types.ContentBlockType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for ContentBlockEntity that extends JpaRepository
 * to provide standard CRUD operations, pagination, and custom query methods.
 */
@Repository
public interface ContentBlockRepository extends JpaRepository<ContentBlockEntity, Long> {
    
    /**
     * Find all content blocks for a specific collection, ordered by their order_index.
     *
     * @param collectionId The ID of the collection
     * @return List of ordered content blocks for the collection
     */
    List<ContentBlockEntity> findByCollectionIdOrderByOrderIndex(Long collectionId);
    
    /**
     * Find content blocks for a specific collection with pagination.
     *
     * @param collectionId The ID of the collection
     * @param pageable Pagination information
     * @return Page of content blocks for the collection
     */
    Page<ContentBlockEntity> findByCollectionId(Long collectionId, Pageable pageable);
    
    /**
     * Count the number of content blocks in a collection.
     *
     * @param collectionId The ID of the collection
     * @return The count of content blocks in the collection
     */
    long countByCollectionId(Long collectionId);
    
    /**
     * Find content blocks of a specific type for a collection.
     *
     * @param collectionId The ID of the collection
     * @param blockType The type of content block
     * @return List of content blocks of the specified type for the collection
     */
    List<ContentBlockEntity> findByCollectionIdAndBlockType(Long collectionId, ContentBlockType blockType);
    
    /**
     * Find content blocks of a specific type for a collection with pagination.
     *
     * @param collectionId The ID of the collection
     * @param blockType The type of content block
     * @param pageable Pagination information
     * @return Page of content blocks of the specified type for the collection
     */
    Page<ContentBlockEntity> findByCollectionIdAndBlockType(Long collectionId, ContentBlockType blockType, Pageable pageable);
    
    /**
     * Get the maximum order_index value for a collection's content blocks.
     * This is useful when adding new blocks to ensure correct ordering.
     *
     * @param collectionId The ID of the collection
     * @return The maximum order_index value, or null if no content blocks exist
     */
    @Query("SELECT MAX(cb.orderIndex) FROM ContentBlockEntity cb WHERE cb.collectionId = :collectionId")
    Integer getMaxOrderIndexForCollection(@Param("collectionId") Long collectionId);
    
    /**
     * Update the order_index of a specific content block.
     *
     * @param id The ID of the content block
     * @param orderIndex The new order_index value
     * @return The number of affected rows (should be 1)
     */
    @Modifying
    @Query("UPDATE ContentBlockEntity cb SET cb.orderIndex = :orderIndex WHERE cb.id = :id")
    int updateOrderIndex(@Param("id") Long id, @Param("orderIndex") Integer orderIndex);
    
    /**
     * Shift the order_index values for a range of content blocks in a collection.
     * This is useful when reordering content blocks.
     *
     * @param collectionId The ID of the collection
     * @param startIndex The starting order_index (inclusive)
     * @param endIndex The ending order_index (inclusive)
     * @param shiftAmount The amount to shift the order_index values (positive or negative)
     * @return The number of affected rows
     */
    @Modifying
    @Query("UPDATE ContentBlockEntity cb SET cb.orderIndex = cb.orderIndex + :shiftAmount " +
           "WHERE cb.collectionId = :collectionId AND cb.orderIndex >= :startIndex AND cb.orderIndex <= :endIndex")
    int shiftOrderIndices(@Param("collectionId") Long collectionId,
                          @Param("startIndex") Integer startIndex,
                          @Param("endIndex") Integer endIndex,
                          @Param("shiftAmount") Integer shiftAmount);
    
    /**
     * Delete all content blocks for a specific collection.
     * This is useful when deleting a collection.
     *
     * @param collectionId The ID of the collection
     */
    void deleteByCollectionId(Long collectionId);

    /**
     * Dissociate the given content block IDs from a specific collection by setting their collectionId to NULL.
     * This does not delete the blocks and allows them to be reassigned to another collection later.
     *
     * @param collectionId The collection to dissociate from
     * @param ids          The block IDs to dissociate
     */
    @Modifying
    @Query("UPDATE ContentBlockEntity cb SET cb.collectionId = NULL WHERE cb.collectionId = :collectionId AND cb.id IN :ids")
    void dissociateFromCollection(@Param("collectionId") Long collectionId, @Param("ids") List<Long> ids);

    /**
     * Find a content block by collection and its exact order index.
     */
    ContentBlockEntity findByCollectionIdAndOrderIndex(Long collectionId, Integer orderIndex);
}