package edens.zac.portfolio.backend.entity;

import edens.zac.portfolio.backend.types.ContentType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Base entity for all content types (IMAGE, TEXT, GIF, COLLECTION). Uses JOINED inheritance
 * strategy - base table: content, child tables: content_image, content_text, etc.
 *
 * <p>Database table: content
 */
@Data
@NoArgsConstructor
@SuperBuilder
public abstract class ContentEntity {

  /** Column: id (BIGINT, PRIMARY KEY, auto-generated) */
  private Long id;

  /** Column: content_type (VARCHAR, NOT NULL) - enum: IMAGE, TEXT, GIF, COLLECTION */
  @NotNull private ContentType contentType;

  /** Column: created_at (TIMESTAMP, NOT NULL) */
  private LocalDateTime createdAt;

  /** Column: updated_at (TIMESTAMP, NOT NULL) */
  private LocalDateTime updatedAt;

  // Method to get the specific content type
  public abstract ContentType getContentType();

  /**
   * Automatically set contentType field before persisting to database. This ensures the field is
   * always set based on the concrete class's getContentType() method.
   */
  protected void setContentTypeFromSubclass() {
    if (this.contentType == null) {
      this.contentType = getContentType();
    }
  }
}
