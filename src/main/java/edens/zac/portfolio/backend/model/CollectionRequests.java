package edens.zac.portfolio.backend.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import edens.zac.portfolio.backend.types.CollectionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

/**
 * Consolidated request and response DTOs for Collection operations. Uses Java records for
 * immutability and conciseness.
 */
public final class CollectionRequests {
  private CollectionRequests() {} // Prevent instantiation

  /**
   * Minimal request for creating a new ContentCollection. Only accepts the essentials: type and
   * title.
   */
  public record Create(
      @NotNull(message = "Type is required") CollectionType type,
      @NotNull(message = "Title is required") @Size(min = 3, max = 100, message = "Title must be between 3 and 100 characters") String title) {}

  /**
   * Request DTO for updating collections. All fields except 'id' are optional to support partial
   * updates. Only fields included in the request will be updated. Uses a prev/new/remove pattern
   * for entity relationships.
   */
  public record Update(
      /** The ID of the collection to update (required) */
      @NotNull(message = "Collection ID is required for updates") Long id,
      /** Collection type */
      CollectionType type,
      /** Collection title */
      @Size(min = 3, max = 100, message = "Title must be between 3 and 100 characters") String title,
      /** Collection slug */
      @Size(min = 3, max = 150, message = "Slug must be between 3 and 150 characters") String slug,
      /** Collection description */
      @Size(max = 500, message = "Description cannot exceed 500 characters") String description,
      /** Location update using prev/new/remove pattern */
      @Valid LocationUpdate location,
      /** Date associated with the collection */
      @JsonFormat(pattern = "yyyy-MM-dd") LocalDate collectionDate,
      /** Whether the collection is visible */
      Boolean visible,
      /** Display mode for ordering content in the collection */
      CollectionBaseModel.DisplayMode displayMode,
      /** Password for client galleries (raw password, will be hashed) null = no change */
      @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters") String password,
      /** Number of content items per page */
      @Min(value = 1, message = "Content per page must be 1 or greater") Integer contentPerPage,
      /** Number of items per row (chunk size for layout). Null uses default (4). */
      @Min(value = 1, message = "Rows wide must be 1 or greater") Integer rowsWide,
      /**
       * Cover image ID (FK to content.id) for the collection Must reference a valid
       * ContentImageEntity Set to 0 or null to clear the cover image
       */
      Long coverImageId,
      /** Tag updates using prev/new/remove pattern */
      TagUpdate tags,
      /** Person updates using prev/new/remove pattern */
      PersonUpdate people,
      /**
       * Collection updates using prev/new/remove pattern. Used to manage child collections within
       * this collection (add, remove, update visibility/order of nested collections).
       */
      CollectionUpdate collections) {}

  /**
   * Request for reordering content within a collection. Allows multiple content items to be
   * reordered in a single atomic operation. Works with any content type: IMAGE, TEXT, GIF,
   * COLLECTION.
   */
  public record Reorder(
      @NotEmpty(message = "At least one reorder item is required") @Valid List<ReorderItem> reorders) {

    /** Represents a single content reorder operation. */
    public record ReorderItem(
        /**
         * The content ID (from content[].id in the collection response). This is the content table
         * ID, consistent across all content types.
         */
        @NotNull(message = "Content ID is required") Long contentId,
        @NotNull(message = "New order index is required") Integer newOrderIndex) {}
  }

  /**
   * Response DTO for the collection update/manage endpoint. Contains the collection along with all
   * metadata needed for the update UI. Uses composition with GeneralMetadataDTO to avoid field
   * duplication.
   */
  public record UpdateResponse(
      /** The content collection with all its data */
      CollectionModel collection,
      /**
       * General metadata including tags, people, cameras, lenses, film types, film formats, and
       * collections. This is unwrapped during JSON serialization to maintain backwards
       * compatibility with the API.
       */
      @JsonUnwrapped GeneralMetadataDTO metadata) {}

  // ===========================================================================================
  // NESTED UPDATE HELPERS - prev/new/remove pattern
  // ===========================================================================================

  /**
   * Location update wrapper using prev/new/remove pattern. All fields are optional to support
   * partial updates.
   *
   * <p>- prev: ID of existing location to use - newValue: Name of new location to create - remove:
   * true to remove location association
   *
   * <p>Examples: - {prev: 5} = Use existing location ID 5 - {newValue: "New York"} = Create new
   * location "New York" - {remove: true} = Remove location association
   */
  public record LocationUpdate(
      Long prev,
      @Size(max = 255, message = "Location cannot exceed 255 characters") String newValue,
      Boolean remove) {}

  /**
   * Tag update wrapper using prev/new/remove pattern. All fields are optional to support partial
   * updates.
   *
   * <p>- prev: List of existing tag IDs to keep/add - newValue: List of new tag names to create and
   * add - remove: List of tag IDs to remove
   *
   * <p>Examples: - {prev: [2, 3]} = Add existing tags 2 and 3 - {newValue: ["landscape", "nature"]}
   * = Create and add new tags - {remove: [1]} = Remove tag ID 1 - {prev: [2], newValue:
   * ["landscape"], remove: [1]} = All operations at once
   */
  public record TagUpdate(List<Long> prev, List<String> newValue, List<Long> remove) {}

  /**
   * Person update wrapper using prev/new/remove pattern. All fields are optional to support partial
   * updates.
   *
   * <p>- prev: List of existing person IDs to keep/add - newValue: List of new person names to
   * create and add - remove: List of person IDs to remove
   *
   * <p>Examples: - {prev: [5, 6]} = Add existing people 5 and 6 - {newValue: ["John Doe"]} = Create
   * and add new person - {remove: [3]} = Remove person ID 3 - {prev: [5], newValue: ["Jane"],
   * remove: [3]} = All operations at once
   */
  public record PersonUpdate(List<Long> prev, List<String> newValue, List<Long> remove) {}

  /**
   * Collection update wrapper using prev/new/remove pattern for managing child collections within a
   * parent collection. All fields are optional to support partial updates.
   *
   * <p>- prev: Update existing child collections (visibility/order) - newValue: Add new child
   * collections to the parent - remove: IDs of child collections to remove from the parent
   *
   * <p>The remove field accepts either: - The content ID (ContentCollectionEntity.id) - matches the
   * "id" field in API responses - The referenced collection ID (referencedCollectionId) - matches
   * the "referencedCollectionId" field
   *
   * <p>Examples: - {prev: [{collectionId: 1, visible: true}]} = Update visibility of child
   * collection - {newValue: [{collectionId: 2, visible: true, orderIndex: 5}]} = Add child
   * collection at position 5 - {remove: [3]} = Remove child collection with ID 3 from parent
   *
   * <p>Note: For reordering content within a collection, use the dedicated reorder endpoint
   * instead.
   */
  public record CollectionUpdate(
      List<Records.ChildCollection> prev,
      List<Records.ChildCollection> newValue,
      List<Long> remove) {}
}
