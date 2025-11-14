package edens.zac.portfolio.backend.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import edens.zac.portfolio.backend.types.ContentType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "contentType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ContentImageModel.class, name = "IMAGE"),
        @JsonSubTypes.Type(value = ContentTextModel.class, name = "TEXT"),
//        @JsonSubTypes.Type(value = ContentCodeModel.class, name = "CODE"),
        @JsonSubTypes.Type(value = ContentGifModel.class, name = "GIF"),
        @JsonSubTypes.Type(value = ContentCollectionModel.class, name = "COLLECTION")
})
public class ContentModel {
    /**
     * ID of the actual entity (not the content table ID).
     * - For IMAGE: ContentImageEntity.id
     * - For COLLECTION: The referenced CollectionEntity.id
     * - For TEXT/CODE/GIF: Their respective entity IDs
     */
    private Long id;

    /**
     * Type of content (IMAGE, TEXT, CODE, GIF, COLLECTION).
     * Used for frontend rendering logic.
     */
    @NotNull
    private ContentType contentType;

    /**
     * Title of this content.
     * - For IMAGE: Image title
     * - For COLLECTION: Collection title
     * - For TEXT/CODE/GIF: Content title
     */
    @Size(max = 250)
    private String title;

    /**
     * Description text for this content.
     * Populated from:
     * - For COLLECTION content: The referenced collection's description field
     * - For all other content: The content entity's own description/title field
     */
    @Size(max = 500)
    private String description;

    /**
     * Preview/cover image URL.
     * - For IMAGE: The image URL itself
     * - For COLLECTION: The collection's cover image URL
     * - For GIF: The GIF or thumbnail URL
     * - For TEXT/CODE: null (no image)
     */
    private String imageUrl;

    // =============================================================================
    // JOIN TABLE METADATA (from collection_content)
    // These fields are populated from CollectionContentEntity when content is
    // fetched in the context of a specific collection. Same content can have
    // different values in different collections.
    // =============================================================================

    /**
     * Position of this content within the parent collection.
     * Lower values appear first. Collection-specific.
     * Populated from collection_content.order_index.
     */
    @NotNull
    @Min(0)
    private Integer orderIndex;

    /**
     * Whether this content is visible in the parent collection.
     * Same content can be visible in one collection but hidden in another.
     * Populated from collection_content.visible.
     * Defaults to true if not specified.
     */
    private Boolean visible;

    // =============================================================================
    // CONTENT METADATA (from content table)
    // =============================================================================

    /**
     * When this content was created.
     */
    private LocalDateTime createdAt;

    /**
     * When this content was last updated.
     */
    private LocalDateTime updatedAt;
}
