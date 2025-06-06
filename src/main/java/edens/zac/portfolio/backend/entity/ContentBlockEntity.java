package edens.zac.portfolio.backend.entity;

import jakarta.validation.constraints.NotNull;
import edens.zac.portfolio.backend.types.ContentBlockType;
import jakarta.persistence.*;
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
public abstract class ContentBlockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TODO: Replace with proper JPA relationship once ContentCollectionEntity is created
    @NotNull
    @Column(name = "collection_id")
    private Long collectionId;

    @NotNull
    @Column(name = "order_index")
    private Integer orderIndex;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "block_type")
    private ContentBlockType blockType;

    @Column(name = "caption", length = 500)
    private String caption;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Method to get the specific content block type
    public abstract ContentBlockType getBlockType();
}