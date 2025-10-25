package edens.zac.portfolio.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing the relationship between an image and a collection.
 * Used in both ImageContentBlockModel (for reading) and ImageUpdateRequest (for updating).
 * Represents one entry in the content_block join table for a specific image/collection relationship.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageCollection {

    /**
     * The ID of the collection
     */
    private Long collectionId;

    /**
     * The name of the collection (for reference/validation)
     */
    private String name;

    /**
     * Whether the image is visible in this collection
     * Defaults to true if not specified
     */
    private Boolean visible;

    /**
     * The order index of this image within this specific collection
     * Each image/collection relationship has its own order_index
     */
    private Integer orderIndex;
}