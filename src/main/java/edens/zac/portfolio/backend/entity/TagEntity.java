package edens.zac.portfolio.backend.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a reusable tag. Tags can be associated with Collections, ContentImages, and
 * ContentGifs via join tables.
 *
 * <p>Database table: tag Columns: - id (BIGINT, PRIMARY KEY, auto-generated) - tag_name (VARCHAR
 * 50, NOT NULL) - created_at (TIMESTAMP, NOT NULL)
 *
 * <p>Relationships via join tables: - collection_tags: collection_id, tag_id (for Collections) -
 * content_tags: content_id, tag_id (for ContentImages, ContentGifs, and other content types)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TagEntity {

  private Long id;

  @NotBlank
  @Size(min = 1, max = 50)
  private String tagName;

  private LocalDateTime createdAt;

  /**
   * Constructor for creating a tag with just a name.
   *
   * @param tagName The name of the tag
   */
  public TagEntity(String tagName) {
    this.tagName = tagName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TagEntity that)) return false;
    return tagName != null && tagName.equals(that.tagName);
  }

  @Override
  public int hashCode() {
    return tagName != null ? tagName.hashCode() : 0;
  }
}
