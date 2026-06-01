package edens.zac.portfolio.backend.entity;

import edens.zac.portfolio.backend.types.ContentType;
import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Entity representing GIF content. Extends ContentEntity (JOINED inheritance - base table: content,
 * child table: content_gif).
 *
 * <p>Database table: content_gif Primary key: id (inherited from content table, FK to content.id)
 *
 * <p>Join tables: - content_gif_tags (gif_id, tag_id) - many-to-many with tags -
 * content_image_people (content_id, person_id) - content-keyed people - content_image_locations
 * (content_id, location_id) - content-keyed locations
 *
 * <p>Indexes on join table: - idx_gif_tags_gif (gif_id) - idx_gif_tags_tag (tag_id)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
public class ContentGifEntity extends ContentEntity {

  /** Column: title (VARCHAR) */
  private String title;

  /** Column: gif_url (VARCHAR, NOT NULL) - S3 URL for the 2000px "full" master (fullscreen). */
  @NotNull private String gifUrl;

  /**
   * Column: gif_url_web (VARCHAR) - S3 URL for the 1080px "web" display variant used in the row
   * layout. NULL for pre-existing gifs and actual image/gif uploads; consumers fall back to gifUrl.
   */
  private String gifUrlWeb;

  /** Column: thumbnail_url (VARCHAR) - S3 URL for thumbnail */
  private String thumbnailUrl;

  /** Column: width (INTEGER) */
  private Integer width;

  /** Column: height (INTEGER) */
  private Integer height;

  /** Column: author (VARCHAR) */
  private String author;

  /** Column: create_date (VARCHAR) */
  private String createDate;

  /**
   * Column: rating (INT, 0-5 or NULL).
   *
   * <p>Drives the layout slot-width algorithm: a horizontal GIF with rating &gt;= 4 takes the full
   * row, rating 3 takes half a row, lower ratings take a single slot. New uploads default to 4 so
   * animated content reads as feature media; admins can downgrade later.
   */
  private Integer rating;

  /** Relationship: Many-to-many with TagEntity (via content_tags table) */
  @Builder.Default private Set<TagEntity> tags = new HashSet<>();

  /** Relationship: people associated with this content (via content_image_people, content-keyed) */
  @Builder.Default private Set<ContentPersonEntity> people = new HashSet<>();

  /**
   * Relationship: locations associated with this content (via content_image_locations,
   * content-keyed)
   */
  @Builder.Default private Set<LocationEntity> locations = new HashSet<>();

  @Override
  public ContentType getContentType() {
    return ContentType.GIF;
  }
}
