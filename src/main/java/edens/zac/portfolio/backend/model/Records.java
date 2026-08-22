package edens.zac.portfolio.backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * Simple immutable data transfer objects implemented as Java records. These replace the verbose
 * Lombok-annotated classes for simple data carriers.
 *
 * <p>All records in this file are pure DTOs with no business logic, used for API responses and
 * simple data transfer between layers.
 */
public final class Records {
  private Records() {} // Prevent instantiation

  // Equipment records

  /** Model representing a camera for API responses. Contains the camera's ID and name. */
  public record Camera(
      Long id,
      String name,
      Boolean isFilm,
      edens.zac.portfolio.backend.types.FilmFormat defaultFilmFormat) {}

  /** Model representing a lens for API responses. Contains the lens's ID and name. */
  public record Lens(Long id, String name) {}

  /**
   * DTO representing film format information for API responses. Contains the enum name and display
   * name.
   */
  public record FilmFormat(
      /** The enum constant name (e.g., "MM_35") */
      String name,
      /** Human-readable display name (e.g., "35mm") */
      String displayName) {}

  // People and Tags

  /** Model representing a content tag for API responses. Contains the tag's ID and name. */
  public record Tag(Long id, String name, String slug) {}

  /** Model representing a person for API responses. Contains the person's ID and name. */
  public record Person(Long id, String name) {}

  // Location

  /** Model representing a location for API responses. Contains the location's ID and name. */
  public record Location(Long id, String name, String slug) {}

  /**
   * Location with counts of visible collections and orphan images. Used by the frontend to
   * determine if a location link should be clickable.
   */
  public record LocationWithCounts(
      Long id, String name, String slug, int collectionCount, int imageCount) {}

  // Collection references

  /**
   * Model representing a collection for list views. {@code collectionDate} and {@code
   * coverImageUrl} are null when the collection has none or the query did not project them; the
   * cover image lets the frontend render related/sibling collections as image cards rather than
   * text links. The flags are primitive because the wire contract is a required boolean -- the
   * back-compat overloads that defaulted them to null are gone.
   */
  public record CollectionList(
      Long id,
      String name,
      String slug,
      LocalDate collectionDate,
      String coverImageUrl,
      @JsonProperty("isClient") boolean isClient,
      @JsonProperty("isBlog") boolean isBlog) {

    /**
     * Project a {@link SiblingRow} into a list entry, pairing it with the cover image URL the
     * caller resolved in batch. Siblings carry no collection date, so that component is null.
     */
    public static CollectionList fromSibling(SiblingRow row, String coverImageUrl) {
      return new CollectionList(
          row.id(), row.name(), row.slug(), null, coverImageUrl, row.isClient(), row.isBlog());
    }
  }

  /**
   * Internal projection of a sibling collection row. Carries the raw {@code coverImageId} (FK to
   * the content image table) instead of a resolved URL so the cover image URLs can be batch-loaded
   * in a single query (avoiding N+1), then mapped into {@link CollectionList} records. Not
   * serialized to API responses directly.
   */
  public record SiblingRow(
      Long id,
      String name,
      String slug,
      Long coverImageId,
      boolean isClient,
      boolean isBlog,
      /**
       * True when the reverse row exists, i.e. the sibling links back. False means this is a
       * one-way link: the owning collection points at the sibling, the sibling does not point back.
       * Computed by {@code findSiblings}, never stored as a column.
       */
      boolean mutual) {}

  /**
   * DTO for admin hub tile configuration. coverImageUrl and dimensions are null when no image is
   * assigned.
   */
  public record AdminHomeTileResponse(
      String tileKey,
      String coverImageUrl,
      Integer coverImageWidth,
      Integer coverImageHeight,
      int displayOrder) {}

  /**
   * DTO representing the relationship between a child entity (content or collection) and a parent
   * collection. Used in update requests to manage collection associations using the prev/new/remove
   * pattern. Represents the relationship metadata: collectionId, visibility, and order index.
   *
   * <p>Can be used for: - Content (images, text, etc.) belonging to collections - Collections
   * belonging to parent collections
   */
  public record ChildCollection(
      /** The ID of the collection */
      Long collectionId,
      /** The name of the collection (for reference/validation) */
      String name,
      /** The slug of the collection (unique identifier for URL routing) */
      String slug,
      /**
       * The cover image URL of the collection. Useful for displaying collection thumbnails when
       * showing "This image appears in these collections".
       */
      String coverImageUrl,
      /**
       * Whether the child entity is visible in this collection Defaults to true if not specified
       */
      Boolean visible,
      /**
       * The order index of this child entity within this specific collection. INPUT ONLY: Used when
       * adding content to a collection at a specific position. NOT populated in API responses - use
       * content[].orderIndex instead for the current collection's order. If null when adding,
       * content will be appended to the end of the collection.
       */
      Integer orderIndex,
      /**
       * SIBLING PATH ONLY. Whether the sibling link should be mutual. Null means mutual, which
       * keeps every pre-existing client and every stored reciprocal pair behaving as before. False
       * writes a one-way link: this collection points at the sibling, the sibling does not point
       * back. Ignored on the content and child-collection paths.
       */
      Boolean mutual) {

    /**
     * Back-compat constructor for callers predating the {@code mutual} field. Delegates to the
     * canonical constructor with {@code mutual} null, which the sibling path reads as mutual.
     */
    public ChildCollection(
        Long collectionId,
        String name,
        String slug,
        String coverImageUrl,
        Boolean visible,
        Integer orderIndex) {
      this(collectionId, name, slug, coverImageUrl, visible, orderIndex, null);
    }
  }
}
