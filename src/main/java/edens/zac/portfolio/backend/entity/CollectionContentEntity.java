package edens.zac.portfolio.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Join table entity linking Collections to Content with collection-specific metadata.
 * This allows the same content to appear in multiple collections with different:
 * - ordering (orderIndex)
 * - captions (caption)
 * - visibility (visible)
 *
 * Example: The same image can appear in:
 * - Blog post with caption "My vacation photo" at position 1
 * - Portfolio with caption "Professional landscape work" at position 5
 * - Client gallery with no caption at position 10
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
     * Caption for this content in this specific collection.
     * The same content can have different captions in different collections.
     */
    @Column(name = "caption", length = 500)
    private String caption;

    /**
     * Whether this content is visible in this specific collection.
     * The same content can be visible in one collection but hidden in another.
     */
    @NotNull
    @Column(name = "visible", nullable = false)
    @Builder.Default
    private Boolean visible = true;
}
