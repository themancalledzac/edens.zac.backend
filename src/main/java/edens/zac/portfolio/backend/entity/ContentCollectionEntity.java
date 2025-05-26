package edens.zac.portfolio.backend.entity;

import edens.zac.portfolio.backend.types.CollectionType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Entity representing a content collection in the portfolio.
 * This is the main container for different types of content (blog, gallery, etc.)
 * with relationships to content blocks that make up the collection.
 */
@Entity
@Table(
        name = "content_collection",
        indexes = {
                @Index(name = "idx_content_collection_slug", columnList = "slug", unique = true),
                @Index(name = "idx_content_collection_type", columnList = "type"),
                @Index(name = "idx_content_collection_created_at", columnList = "created_at"),
                @Index(name = "idx_content_collection_priority", columnList = "priority"),
                @Index(name = "idx_content_collection_type_priority", columnList = "type, priority")
        }
)
@Data
public class ContentCollectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CollectionType type;

    @NotBlank
    @Size(min = 3, max = 100)
    @Column(name = "title", nullable = false)
    private String title;

    @NotBlank
    @Size(min = 3, max = 150)
    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Size(max = 500)
    @Column(name = "description", length = 500)
    private String description;

    @Size(max = 255)
    @Column(name = "location")
    private String location;

    @Column(name = "collection_date")
    private LocalDateTime collectionDate;

    @NotNull
    @Column(name = "visible", nullable = false)
    private Boolean visible;

    @Min(0)
    @Column(name = "priority")
    private Integer priority;

    // Pagination metadata
    @Min(1)
    @Column(name = "blocks_per_page")
    private Integer blocksPerPage;

    @Column(name = "total_blocks")
    private Integer totalBlocks;

    // Client gallery security
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "password_protected")
    private Boolean passwordProtected;

    // Type-specific configuration stored as JSON
    @Column(name = "config_json", columnDefinition = "json")
    private String configJson;

    // Cover image URL for the collection
    @Column(name = "cover_image_url")
    private String coverImageUrl;

    // Timestamps
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Relationship with ContentBlockEntity
    @OneToMany(
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @JoinColumn(name = "collection_id")
    @OrderBy("orderIndex ASC")
    private List<ContentBlockEntity> contentBlocks = new ArrayList<>();

    /**
     * Constructor that ensures all collections are properly initialized
     */
public ContentCollectionEntity() {
    // Set all defaults in constructor
    this.visible = true;
    this.priority = 0;
    this.blocksPerPage = 30;
    this.totalBlocks = 0;
    this.passwordProtected = false;
    this.contentBlocks = new ArrayList<>();
}

    /**
     * Add a content block to this collection.
     * Automatically sets the orderIndex to the next available value.
     *
     * @param contentBlock The content block to add
     * @return The updated collection entity
     */
    public ContentCollectionEntity addContentBlock(ContentBlockEntity contentBlock) {
        if (contentBlock.getOrderIndex() == null) {
            contentBlock.setOrderIndex(contentBlocks.size());
        }

        contentBlocks.add(contentBlock);
        updateTotalBlocks();
        return this;
    }

    /**
     * Remove a content block from this collection.
     *
     * @param contentBlock The content block to remove
     * @return The updated collection entity
     */
    public ContentCollectionEntity removeContentBlock(ContentBlockEntity contentBlock) {
        if (contentBlocks.remove(contentBlock)) {
            reorderContentBlocks();
            updateTotalBlocks();
        }
        return this;
    }

    /**
     * Reorder content blocks to ensure sequential orderIndex values.
     * This is useful after removing content blocks or when reorganizing.
     */
    public void reorderContentBlocks() {
        contentBlocks.sort(Comparator.comparing(ContentBlockEntity::getOrderIndex));

        for (int i = 0; i < contentBlocks.size(); i++) {
            contentBlocks.get(i).setOrderIndex(i);
        }
    }

    /**
     * Move a content block to a new position in the collection.
     *
     * @param contentBlockId The ID of the content block to move
     * @param newIndex The new position index
     * @return True if the content block was moved successfully
     */
    public boolean moveContentBlock(Long contentBlockId, int newIndex) {
        // Find the content block by ID
        ContentBlockEntity blockToMove = contentBlocks.stream()
                .filter(block -> block.getId().equals(contentBlockId))
                .findFirst()
                .orElse(null);

        if (blockToMove == null || newIndex < 0 || newIndex >= contentBlocks.size()) {
            return false;
        }

        // Get the current index
        int currentIndex = contentBlocks.indexOf(blockToMove);
        if (currentIndex == newIndex) {
            return true; // Already in the correct position
        }

        // Remove from the current position
        contentBlocks.remove(currentIndex);

        // Insert at new position
        contentBlocks.add(newIndex, blockToMove);

        // Update all orderIndex values
        for (int i = 0; i < contentBlocks.size(); i++) {
            contentBlocks.get(i).setOrderIndex(i);
        }

        return true;
    }

    /**
     * Check if this collection is password-protected.
     *
     * @return True if the collection is password-protected
     */
    public boolean isPasswordProtected() {
        return passwordProtected != null && passwordProtected && passwordHash != null && !passwordHash.isEmpty();
    }

    /**
     * Get the total number of pages based on blocks per page.
     *
     * @return The total number of pages
     */
    public int getTotalPages() {
        if (totalBlocks == null || totalBlocks == 0 || blocksPerPage == null || blocksPerPage == 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalBlocks / blocksPerPage);
    }

    /**
     * Update the total blocks count.
     * This should be called whenever content blocks are added or removed.
     */
    public void updateTotalBlocks() {
        this.totalBlocks = contentBlocks.size();
    }
}