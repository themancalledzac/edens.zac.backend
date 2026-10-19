package edens.zac.portfolio.backend.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import edens.zac.portfolio.backend.config.DefaultValues;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import edens.zac.portfolio.backend.types.DisplayMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

  private Long id;

  /** Client gallery. JsonProperty pins the wire name against the Lombok is-getter rename. */
  @JsonProperty("isClient")
  private boolean isClient;

  /** Blog. JsonProperty pins the wire name against the Lombok is-getter rename. */
  @JsonProperty("isBlog")
  private boolean isBlog;

  @Size(min = 3, max = 100, message = "Title must be between 3 and 100 characters") private String title;

  @Size(min = 3, max = 150, message = "Slug must be between 3 and 150 characters") private String slug;

  @Size(max = 500, message = "Description cannot exceed 500 characters") private String description;

  @Valid private List<Records.Location> locations;

  /** People associated with this collection (independent from per-image people). */
  @Builder.Default @Valid private List<Records.Person> people = new ArrayList<>();

  @JsonFormat(pattern = "yyyy-MM-dd")
  private LocalDate collectionDate;

  /** Inclusive end of the collection's date range; null when the collection is single-day. */
  @JsonFormat(pattern = "yyyy-MM-dd")
  private LocalDate collectionEndDate;

  private CollectionVisibility visibility;

  /** Rating 0-5, nullable. Used for ordering multi-collection list views. */
  @Min(0) @Max(5) private Integer rating;

  private DisplayMode displayMode;

  /** True for a synthetic tag-view model; unset/false for real collections. */
  private boolean derived;

  @Min(1) private Integer rowsWide;

  @Min(
      value = DefaultValues.default_content_per_page,
      message = "Content per page must be 30 or greater")
  private Integer contentPerPage;

  @Min(value = 0, message = "Total content must be 0 or greater") private Integer contentCount;

  @Min(value = 1, message = "Current page must be 1 or greater") private Integer currentPage;

  @Min(value = 0, message = "Total pages must be 0 or greater") private Integer totalPages;

  @Valid private ContentModels.Image coverImage;

  /** Tag names on this collection. Empty when the collection has no tags. */
  @Builder.Default private List<String> tags = new ArrayList<>();

  /** Tag id, name and slug on this collection, in the same order as {@link #tags}. */
  @Builder.Default private List<Records.Tag> tagRefs = new ArrayList<>();

  /**
   * Sibling collections (directional association via collection_sibling; a link may be mutual or
   * one-way, see {@link #oneWaySiblingIds}). LISTED-only on the public read path; all siblings on
   * the admin manage payload. Null/empty when none.
   */
  private List<Records.CollectionList> siblings;

  /**
   * IDs among {@link #siblings} that this collection links to one-way -- they do not link back.
   * Populated only on the admin manage payload; null on the public read path, which has no use for
   * link direction. The public payload's {@code siblings} shape is unchanged.
   */
  private List<Long> oneWaySiblingIds;

  /**
   * Parent collections. Admin sees every relationship, including HIDDEN ones; public reads get
   * LISTED parents only. Null/empty when none. Each parent's cover image URL is null when it has no
   * cover image assigned.
   */
  private List<Records.CollectionList> parents;

  @Valid private List<ContentModel> content;

  private Boolean isPasswordProtected;

  /** Admin-only: plaintext gallery password. Populated only in admin/manage responses. */
  private String galleryPassword;

  /** Admin-only: recipient email addresses. Populated only in admin/manage responses. */
  private List<String> recipientEmails;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
