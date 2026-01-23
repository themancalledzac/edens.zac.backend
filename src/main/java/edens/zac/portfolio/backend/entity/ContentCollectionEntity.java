package edens.zac.portfolio.backend.entity;

import edens.zac.portfolio.backend.types.ContentType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Content entity representing a reference to another collection.
 * This enables hierarchical collections - collections can contain other collections.
 *
 * Example use cases:
 * - Home page collection containing links to blog/portfolio/gallery collections
 * - Portfolio collection containing links to individual project collections
 * - Gallery collection containing links to sub-galleries
 * 
 * Database table: content_collection
 * Primary key: id (inherited from content table, FK to content.id)
 * Indexes:
 *   - idx_content_collection_col (referenced_collection_id)
 * 
 * Foreign keys:
 *   - referenced_collection_id -> collection.id (NOT NULL)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
public class ContentCollectionEntity extends ContentEntity {

    /**
     * Column: referenced_collection_id (BIGINT, NOT NULL, FK to collection.id)
     * The collection being referenced by this content.
     * This creates the hierarchical relationship: Collection -> Content -> Referenced Collection
     */
    @NotNull
    private CollectionEntity referencedCollection;

    @Override
    public ContentType getContentType() {
        return ContentType.COLLECTION;
    }
}
