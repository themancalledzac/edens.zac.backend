package edens.zac.portfolio.backend.entity;

import edens.zac.portfolio.backend.types.ContentType;
import jakarta.persistence.*;
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
 */
@Entity
@Table(
    name = "content_collection_ref",
    indexes = {
        @Index(name = "idx_content_collection_ref_col", columnList = "referenced_collection_id")
    }
)
@PrimaryKeyJoinColumn(name = "content_id")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
public class ContentCollectionEntity extends ContentEntity {

    /**
     * The collection being referenced by this content.
     * This creates the hierarchical relationship: Collection -> Content -> Referenced Collection
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referenced_collection_id", nullable = false)
    private CollectionEntity referencedCollection;

    @Override
    public ContentType getContentType() {
        return ContentType.COLLECTION;
    }
}
