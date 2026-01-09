package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.types.CollectionType;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Model for content that represents a collection within another collection.
 * This enables hierarchical collections - collections containing other collections.
 *
 * Use cases:
 * - Home page collection containing links to blog/portfolio/gallery collections
 * - Portfolio collection containing links to individual project collections
 * - Gallery collection containing links to sub-galleries
 *
 * Inherits from ContentModel which provides:
 * - id: The content table ID (ContentCollectionEntity.id) - used for reordering
 * - title: The collection's title
 * - description: The collection's description
 * - imageUrl: The collection's cover image URL
 * - orderIndex, visible: Position and visibility in the parent collection
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ContentCollectionModel extends ContentModel {

    /**
     * The ID of the referenced collection in the collection table.
     * Use this to navigate to/fetch the full collection.
     * Note: This is different from the inherited 'id' field which is the content table ID.
     */
    private Long referencedCollectionId;

    /**
     * URL slug for routing to this collection.
     * Used for generating frontend URLs (e.g., /collections/seattle-2021)
     */
    private String slug;

    /**
     * Type of this collection (BLOG, PORTFOLIO, ART_GALLERY, etc.).
     * Used for styling/icons on frontend.
     */
    private CollectionType collectionType;

    /**
     * Full cover image model for the collection (includes dimensions, URLs, etc.).
     * Matches CollectionModel.coverImage structure.
     */
    @Valid
    private ContentImageModel coverImage;

    // NOTE: All other fields are inherited from ContentModel:
    // - id (the content table ID - consistent with IMAGE, TEXT, GIF types)
    // - referencedCollectionId (the collection this content points to)
    // - title (the collection's title)
    // - description (the collection's description)
    // - orderIndex (position in parent collection)
    // - visible (visibility in parent collection)
    // - createdAt, updatedAt (timestamps)
    //
    // We intentionally DO NOT include the full nested collection content here
    // to prevent deep nesting and circular reference issues.
    // Frontend should fetch the full collection separately if needed.
    //
    // Note: imageUrl from ContentModel is not populated for COLLECTION content.
    // Use coverImage.imageUrl instead for the cover image URL.
}
