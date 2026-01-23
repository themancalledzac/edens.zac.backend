package edens.zac.portfolio.backend.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a reusable content tag. Tags can be associated with Collections,
 * ContentImages, and ContentGifs.
 */
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

  private Long id;

  @NotBlank
  @Size(min = 1, max = 50)
  private String tagName;

  private LocalDateTime createdAt;

  // Many-to-many relationships (mappedBy side - non-owning)
  @Builder.Default private Set<CollectionEntity> collections = new HashSet<>();

  @Builder.Default private Set<ContentImageEntity> ContentImages = new HashSet<>();

  @Builder.Default private Set<ContentGifEntity> contentGifs = new HashSet<>();

  /**
   * Constructor for creating a tag with just a name. Useful for quick tag creation.
   *
   * @param tagName The name of the tag
   */
  public ContentTagEntity(String tagName) {
    this.tagName = tagName;
    this.collections = new HashSet<>();
    this.ContentImages = new HashSet<>();
    this.contentGifs = new HashSet<>();
  }

  /**
   * Get the total usage count of this tag across all entities.
   *
   * @return The total number of times this tag is used
   */
  public int getTotalUsageCount() {
    return collections.size() + ContentImages.size() + contentGifs.size();
  }
}
