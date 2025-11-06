package edens.zac.portfolio.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing the relationship between a child entity (content or collection) and a parent collection.
 * Used in update requests to manage collection associations using the prev/new/remove pattern.
 * Represents the relationship metadata: collectionId, visibility, and order index.
 *
 * Can be used for:
 * - Content (images, text, etc.) belonging to collections
 * - Collections belonging to parent collections
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChildCollection {

    /**
     * The ID of the collection
     */
    private Long collectionId;

    /**
     * The name of the collection (for reference/validation)
     */
    private String name;

    /**
     * The cover image URL of the collection.
     * Useful for displaying collection thumbnails when showing
     * "This image appears in these collections".
     */
    private String coverImageUrl;

    /**
     * Whether the child entity is visible in this collection
     * Defaults to true if not specified
     */
    private Boolean visible;

    /**
     * The order index of this child entity within this specific collection
     * Each child/collection relationship has its own order_index
     */
    private Integer orderIndex;
}