package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.types.CollectionType;
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
 * - id: The referenced collection's ID
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
     * URL slug for routing to this collection.
     * Used for generating frontend URLs (e.g., /collections/seattle-2021)
     */
    private String slug;

    /**
     * Type of this collection (BLOG, PORTFOLIO, ART_GALLERY, etc.).
     * Used for styling/icons on frontend.
     */
    private CollectionType collectionType;

    // NOTE: All other fields are inherited from ContentModel:
    // - id (the collection's ID)
    // - title (the collection's title)
    // - description (the collection's description)
    // - imageUrl (the collection's cover image URL)
    // - orderIndex (position in parent collection)
    // - visible (visibility in parent collection)
    // - createdAt, updatedAt (timestamps)
    //
    // We intentionally DO NOT include the full nested collection content here
    // to prevent deep nesting and circular reference issues.
    // Frontend should fetch the full collection separately if needed.
}
