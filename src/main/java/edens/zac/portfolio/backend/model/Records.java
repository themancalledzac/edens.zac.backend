package edens.zac.portfolio.backend.model;

import jakarta.validation.constraints.Size;

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
  public record Camera(Long id, String name) {}

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
  public record Tag(Long id, String name) {}

  /** Model representing a person for API responses. Contains the person's ID and name. */
  public record Person(Long id, String name) {}

  // Location

  /** Model representing a location for API responses. Contains the location's ID and name. */
  public record Location(
      Long id,
      @Size(max = 255, message = "Location cannot exceed 255 characters") String name) {}

  // Collection references

  /**
   * Model representing a collection summary for API responses. Contains the collection's ID and
   * title.
   */
  public record CollectionSummary(Long id, String title) {}

  /** Model representing a collection for list views. Contains the collection's ID and name. */
  public record CollectionList(Long id, String name) {}

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
      Integer orderIndex) {}
}
