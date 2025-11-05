package edens.zac.portfolio.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
 */
@Entity
@Table(
        name = "collection_content",
        indexes = {
                @Index(name = "idx_collection_content_collection", columnList = "collection_id"),
                @Index(name = "idx_collection_content_content", columnList = "content_id"),
                @Index(name = "idx_collection_content_order", columnList = "collection_id, order_index")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionContentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    private CollectionEntity collection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private ContentEntity content;

    /**
     * Position of this content within the collection.
     * Lower values appear first. Collection-specific.
     */
    @NotNull
    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    /**
     * Whether this content is visible in this specific collection.
     * The same content can be visible in one collection but hidden in another.
     */
    @NotNull
    @Column(name = "visible", nullable = false)
    @Builder.Default
    private Boolean visible = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

}
