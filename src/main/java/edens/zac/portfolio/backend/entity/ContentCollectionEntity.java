package edens.zac.portfolio.backend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import edens.zac.portfolio.backend.types.CollectionType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
                @Index(name = "idx_content_collection_type_priority", columnList = "type, priority"),
                @Index(name = "idx_content_collection_cover_block", columnList = "cover_image_block_id")
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

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Column(name = "collection_date")
    private LocalDate collectionDate;

    @NotNull
    @Column(name = "visible", nullable = false)
    private Boolean visible;

    @Min(value = 1, message = "Priority must be between 1 and 4")
    @Max(value = 4, message = "Priority must be between 1 and 4")
    @Column(name = "priority")
    private Integer priority; // 1 | 2 | 3 | 4 - 1 being 'best', 4 worst

    // Foreign key reference to the image content block used as cover (nullable)
    @Column(name = "cover_image_block_id")
    private Long coverImageBlockId;

    // Client gallery security
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "password_protected")
    private Boolean passwordProtected;

    // Pagination metadata
    @Min(1)
    @Column(name = "blocks_per_page")
    private Integer blocksPerPage;

    @Column(name = "total_blocks")
    private Integer totalBlocks;

    // Timestamps
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Relationship with ContentBlockEntity - bidirectional
    @OneToMany(
            mappedBy = "collection",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OrderBy("orderIndex ASC")
    private List<ContentEntity> contentBlocks = new ArrayList<>();

    // Many-to-many relationship with ContentTagEntity
    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "content_collection_tags",
            joinColumns = @JoinColumn(name = "collection_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"),
            indexes = {
                    @Index(name = "idx_collection_tags_collection", columnList = "collection_id"),
                    @Index(name = "idx_collection_tags_tag", columnList = "tag_id")
            }
    )
    private Set<ContentTagEntity> tags = new HashSet<>();

    /**
     * Constructor that ensures all collections are properly initialized
     */
    public ContentCollectionEntity() {
        // Set all defaults in constructor
        this.visible = true;
        this.priority = 1; // Changed from 0 to 1 to satisfy @Min(1) constraint
        this.blocksPerPage = 50;
        this.totalBlocks = 0;
        this.passwordProtected = false;
        this.contentBlocks = new ArrayList<>();
        this.tags = new HashSet<>();
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
}