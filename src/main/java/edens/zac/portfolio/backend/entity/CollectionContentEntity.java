package edens.zac.portfolio.backend.entity;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Universal join table entity linking Collections to ANY type of Content with collection-specific metadata.
 *
 * This is the ONLY join table used for ALL content types (TEXT, IMAGE, GIF, CODE, COLLECTION, etc.).
 * It allows the same content to appear in multiple collections with different:
 * - ordering (orderIndex)
 * - visibility (visible)
 *
 * Examples:
 * - Same TEXT content in "Blog Post A" at position 1 and "Blog Post B" at position 3
 * - Same IMAGE in "Portfolio" and "Gallery" at different positions
 * - Same COLLECTION reference (ContentCollectionEntity) in "Home Page" and "Archive Page" at different positions
 * 
 * Database table: collection_content
 * Indexes:
 *   - idx_collection_content_collection (collection_id)
 *   - idx_collection_content_content (content_id)
 *   - idx_collection_content_order (collection_id, order_index)
 * 
 * Foreign keys:
 *   - collection_id -> collection.id (NOT NULL)
 *   - content_id -> content.id (NOT NULL)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionContentEntity {

    /** Column: id (BIGINT, PRIMARY KEY, auto-generated) */
    private Long id;

    /** Column: collection_id (BIGINT, NOT NULL, FK to collection.id) */
    private Long collectionId;

    /** Column: content_id (BIGINT, NOT NULL, FK to content.id) */
    private Long contentId;

    /**
     * Column: order_index (INTEGER, NOT NULL)
     * Position of this content within the collection.
     * Lower values appear first. Collection-specific.
     */
    @NotNull
    private Integer orderIndex;

    /**
     * Column: visible (BOOLEAN, NOT NULL, default: true)
     * Whether this content is visible in this specific collection.
     * The same content can be visible in one collection but hidden in another.
     */
    @NotNull
    @Builder.Default
    private Boolean visible = true;

    /** Column: created_at (TIMESTAMP, NOT NULL) */
    private LocalDateTime createdAt;

    /** Column: updated_at (TIMESTAMP, NOT NULL) */
    private LocalDateTime updatedAt;

}
