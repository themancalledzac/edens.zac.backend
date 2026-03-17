package edens.zac.portfolio.backend.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import edens.zac.portfolio.backend.config.DefaultValues;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.DisplayMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Collection Model - full DTO returned to the frontend. Includes collection metadata, pagination,
 * and content.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionModel {

  // === Identity ===

  private Long id;

  private CollectionType type;

  @Size(min = 3, max = 100, message = "Title must be between 3 and 100 characters") private String title;

  @Size(min = 3, max = 150, message = "Slug must be between 3 and 150 characters") private String slug;

  @Size(max = 500, message = "Description cannot exceed 500 characters") private String description;

  @Valid private Records.Location location;

  @JsonFormat(pattern = "yyyy-MM-dd")
  private LocalDate collectionDate;

  private Boolean visible;

  private DisplayMode displayMode;

  @Min(1) private Integer rowsWide;

  // === Pagination ===

  @Min(
      value = DefaultValues.default_content_per_page,
      message = "Content per page must be 30 or greater")
  private Integer contentPerPage;

  @Min(value = 0, message = "Total content must be 0 or greater") private Integer contentCount;

  @Min(value = 1, message = "Current page must be 1 or greater") private Integer currentPage;

  @Min(value = 0, message = "Total pages must be 0 or greater") private Integer totalPages;

  // === Content ===

  @Valid private ContentModels.Image coverImage;

  private List<String> tags;

  @Valid private List<ContentModel> content;

  // === Access Control ===

  private Boolean isPasswordProtected;

  // === Timestamps ===

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
