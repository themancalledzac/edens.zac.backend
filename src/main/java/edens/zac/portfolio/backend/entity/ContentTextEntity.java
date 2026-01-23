package edens.zac.portfolio.backend.entity;

import edens.zac.portfolio.backend.types.ContentType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Entity representing text content. Extends ContentEntity (JOINED inheritance - base table:
 * content, child table: content_text).
 *
 * <p>Database table: content_text Primary key: id (inherited from content table, FK to content.id)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
public class ContentTextEntity extends ContentEntity {

  /** Column: text_content (TEXT, NOT NULL) */
  @NotNull private String textContent;

  /**
   * Column: format_type (VARCHAR) - Options: "markdown", "html", "plain", "js", "py", "sql",
   * "java", "ts", "tf", "yml"
   */
  private String formatType;

  @Override
  public ContentType getContentType() {
    return ContentType.TEXT;
  }
}
