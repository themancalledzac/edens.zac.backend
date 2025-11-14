package edens.zac.portfolio.backend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import edens.zac.portfolio.backend.model.CollectionBaseModel;
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
        name = "collection",
        indexes = {
                @Index(name = "idx_collection_slug", columnList = "slug", unique = true),
                @Index(name = "idx_collection_type", columnList = "type"),
                @Index(name = "idx_collection_created_at", columnList = "created_at")
        }
)
@Data
public class CollectionEntity {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "display_mode")
    private CollectionBaseModel.DisplayMode displayMode;

    // Foreign key reference to ContentEntity (cover image)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cover_image_id", foreignKey = @ForeignKey(name = "fk_collection_cover_image"))
    private ContentImageEntity coverImage;

//    // Client gallery security
//    @Column(name = "password_hash")
//    private String passwordHash;
//
//    @Column(name = "password_protected")
//    private Boolean passwordProtected;

    // Pagination metadata
    @Min(1)
    @Column(name = "content_per_page")
    private Integer contentPerPage;

    @Column(name = "total_content")
    private Integer totalContent;

    // Timestamps
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Relationship with ContentEntity - bidirectional
    @OneToMany(
            mappedBy = "collection",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OrderBy("orderIndex ASC")
    private List<CollectionContentEntity> collectionContent = new ArrayList<>();

    // Many-to-many relationship with ContentTagEntity
    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "collection_tags",
            joinColumns = @JoinColumn(name = "collection_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"),
            indexes = {
                    @Index(name = "idx_collection_tags_collection", columnList = "collection_id"),
                    @Index(name = "idx_collection_tags_tag", columnList = "tag_id")
            }
    )
    private Set<ContentTagEntity> tags = new HashSet<>();

    // Many-to-many relationship with ContentPersonEntity
    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "collection_people",
            joinColumns = @JoinColumn(name = "collection_id"),
            inverseJoinColumns = @JoinColumn(name = "person_id"),
            indexes = {
                    @Index(name = "idx_collection_people_collection", columnList = "collection_id"),
                    @Index(name = "idx_collection_people_person", columnList = "person_id")
            }
    )
    private Set<ContentPersonEntity> people = new HashSet<>();

    /**
     * Constructor that ensures all collections are properly initialized
     */
    public CollectionEntity() {
        // Set all defaults in constructor
        this.visible = true;
        this.contentPerPage = 50;
        this.totalContent = 0;
//        this.passwordProtected = false;
        this.collectionContent = new ArrayList<>();
        this.tags = new HashSet<>();
        this.people = new HashSet<>();
    }

//    /**
//     * Check if this collection is password-protected.
//     *
//     * @return True if the collection is password-protected
//     */
//    public boolean isPasswordProtected() {
//        return passwordProtected != null && passwordProtected && passwordHash != null && !passwordHash.isEmpty();
//    }

    /**
     * Get the total number of pages based on blocks per page.
     *
     * @return The total number of pages
     */
    public int getTotalPages() {
        if (totalContent == null || totalContent == 0 || contentPerPage == null || contentPerPage == 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalContent / contentPerPage);
    }

    /**
     * Helper method to get the actual content entities from the join table.
     * This provides a convenient way to access content without dealing with the join table directly.
     *
     * @return List of ContentEntity objects ordered by orderIndex
     */
    public List<ContentEntity> getContent() {
        return collectionContent.stream()
                .map(CollectionContentEntity::getContent)
                .toList();
    }
}