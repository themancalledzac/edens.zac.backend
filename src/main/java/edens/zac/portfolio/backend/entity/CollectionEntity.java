package edens.zac.portfolio.backend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import edens.zac.portfolio.backend.model.CollectionBaseModel;
import edens.zac.portfolio.backend.types.CollectionType;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionEntity {

  /** Column: id (BIGINT, PRIMARY KEY, auto-generated) */
  private Long id;

  /** Column: type (VARCHAR, NOT NULL) - enum: BLOG, CLIENT_GALLERY, PORTFOLIO, MISC */
  @NotNull private CollectionType type;

  /** Column: title (VARCHAR(100), NOT NULL) */
  @NotBlank
  @Size(min = 3, max = 100)
  private String title;

  /** Column: slug (VARCHAR(150), NOT NULL, UNIQUE) */
  @NotBlank
  @Size(min = 3, max = 150)
  private String slug;

  /** Column: description (VARCHAR(500)) */
  @Size(max = 500)
  private String description;

  /** Column: location_id (BIGINT, FK to location.id) */
  private Long locationId;

  /** Column: collection_date (DATE) */
  @JsonFormat(pattern = "yyyy-MM-dd")
  private LocalDate collectionDate;

  /** Column: visible (BOOLEAN, NOT NULL, default: true) */
  @NotNull private Boolean visible;

  /** Column: display_mode (VARCHAR) - enum: GRID, LIST, etc. */
  private CollectionBaseModel.DisplayMode displayMode;

  /** Column: cover_image_id (BIGINT, FK to content_image.id) */
  private Long coverImageId;

  /** Column: content_per_page (INTEGER) */
  @Min(1)
  private Integer contentPerPage;

  /** Column: total_content (INTEGER) */
  private Integer totalContent;

  /** Column: rows_wide (INTEGER) - Number of items per row (chunk size for layout) */
  @Min(1)
  private Integer rowsWide;

  /** Column: created_at (TIMESTAMP, NOT NULL) */
  private LocalDateTime createdAt;

  /** Column: updated_at (TIMESTAMP, NOT NULL) */
  private LocalDateTime updatedAt;

  /**
   * Get the total number of pages based on blocks per page.
   *
   * @return The total number of pages
   */
  public int getTotalPages() {
    if (totalContent == null
        || totalContent == 0
        || contentPerPage == null
        || contentPerPage == 0) {
      return 0;
    }
    return (int) Math.ceil((double) totalContent / contentPerPage);
  }
}
