package edens.zac.portfolio.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Entity representing a reusable content tag.
 * Tags can be associated with ContentCollections, ImageContentBlocks, and GifContentBlocks.
 */
@Entity
@Table(
        name = "content_tags",
        indexes = {
                @Index(name = "idx_content_tag_name", columnList = "tag_name", unique = true)
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentTagEntity {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContentTagEntity that)) return false;
        return tagName != null && tagName.equals(that.tagName);
    }

    @Override
    public int hashCode() {
        return tagName != null ? tagName.hashCode() : 0;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(min = 1, max = 50)
    @Column(name = "tag_name", unique = true, nullable = false, length = 50)
    private String tagName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Many-to-many relationships (mappedBy side - non-owning)
    @ManyToMany(mappedBy = "tags", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<ContentCollectionEntity> contentCollections = new HashSet<>();

    @ManyToMany(mappedBy = "tags", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<ImageContentBlockEntity> imageContentBlocks = new HashSet<>();

    @ManyToMany(mappedBy = "tags", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<GifContentBlockEntity> gifContentBlocks = new HashSet<>();

    /**
     * Constructor for creating a tag with just a name.
     * Useful for quick tag creation.
     *
     * @param tagName The name of the tag
     */
    public ContentTagEntity(String tagName) {
        this.tagName = tagName;
        this.contentCollections = new HashSet<>();
        this.imageContentBlocks = new HashSet<>();
        this.gifContentBlocks = new HashSet<>();
    }

    /**
     * Get the total usage count of this tag across all entities.
     *
     * @return The total number of times this tag is used
     */
    public int getTotalUsageCount() {
        return contentCollections.size() + imageContentBlocks.size() + gifContentBlocks.size();
    }
}
