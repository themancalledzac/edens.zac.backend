package edens.zac.portfolio.backend.entity;

import jakarta.validation.constraints.NotNull;
import edens.zac.portfolio.backend.types.ContentType;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "content_block", 
    indexes = {
        @Index(name = "idx_content_block_collection_id", columnList = "collection_id"),
        @Index(name = "idx_content_block_order_index", columnList = "order_index"),
        @Index(name = "idx_content_block_collection_order", columnList = "collection_id, order_index")
    }
)
@Inheritance(strategy = InheritanceType.JOINED)
@Data
@NoArgsConstructor
@SuperBuilder
public abstract class ContentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Keep FK column for write-side operations and repository queries
    @NotNull
    @Column(name = "collection_id")
    private Long collectionId;

    // Bidirectional association for read/navigation; use same FK column
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", insertable = false, updatable = false)
    private CollectionEntity collection;

    @NotNull
    @Column(name = "order_index")
    private Integer orderIndex;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "block_type")
    private ContentType blockType;

    @Column(name = "caption", length = 500)
    private String caption;

    @NotNull
    @Column(name = "visible", nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Builder.Default
    private Boolean visible = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Method to get the specific content block type
    public abstract ContentType getContentType();
}