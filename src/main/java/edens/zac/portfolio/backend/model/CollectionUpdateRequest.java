package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.types.CollectionType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Request DTO for updating collections.
 * All fields except 'id' are optional to support partial updates.
 * Only fields included in the request will be updated.
 * Uses a prev/new/remove pattern for entity relationships:
 * - prev: Reference to existing entity by ID or keep existing relationships
 * - newValue: Create new entity (by name) or add new relationships
 * - remove: Remove the association or remove specific IDs
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionUpdateRequest {

    /**
     * The ID of the collection to update (required)
     */
    @NotNull(message = "Collection ID is required for updates")
    private Long id;

    /**
     * Collection type
     */
    private CollectionType type;

    /**
     * Collection title
     */
    @Size(min = 3, max = 100, message = "Title must be between 3 and 100 characters")
    private String title;

    /**
     * Collection slug
     */
    @Size(min = 3, max = 150, message = "Slug must be between 3 and 150 characters")
    private String slug;

    /**
     * Collection description
     */
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    /**
     * Location associated with the collection
     */
    @Size(max = 255, message = "Location cannot exceed 255 characters")
    private String location;

    /**
     * Date associated with the collection
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate collectionDate;

    /**
     * Whether the collection is visible
     */
    private Boolean visible;

    /**
     * Display mode for ordering content in the collection
     */
    private CollectionBaseModel.DisplayMode displayMode;

    /**
     * Password for client galleries (raw password, will be hashed)
     * null = no change
     */
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;

    /**
     * Number of content items per page
     */
    @Min(value = 1, message = "Content per page must be 1 or greater")
    private Integer contentPerPage;

    /**
     * Cover image ID (FK to content.id) for the collection
     * Must reference a valid ContentImageEntity
     * Set to 0 or null to clear the cover image
     */
    private Long coverImageId;

    /**
     * Tag updates using prev/new/remove pattern
     */
    private TagUpdate tags;

    /**
     * Person updates using prev/new/remove pattern
     */
    private PersonUpdate people;

    /**
     * Collection updates using prev/new/remove pattern
     * Used to manage which parent collections this collection belongs to
     */
    private CollectionUpdate collections;
}