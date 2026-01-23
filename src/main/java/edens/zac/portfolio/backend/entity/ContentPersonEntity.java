package edens.zac.portfolio.backend.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Entity representing a person who can be tagged in image content.
 * This allows tracking which people appear in photographs.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentPersonEntity {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContentPersonEntity that)) return false;
        return personName != null && personName.equals(that.personName);
    }

    @Override
    public int hashCode() {
        return personName != null ? personName.hashCode() : 0;
    }

    private Long id;

    @NotBlank
    @Size(min = 1, max = 100)
    private String personName;

    private LocalDateTime createdAt;

    // Many-to-many relationship with ContentImageEntity (mappedBy side - non-owning)
    @Builder.Default
    private Set<ContentImageEntity> contentImages = new HashSet<>();

    // Many-to-many relationship with CollectionEntity (mappedBy side - non-owning)
    @Builder.Default
    private Set<CollectionEntity> collections = new HashSet<>();

    /**
     * Constructor for creating a person with just a name.
     * Useful for quick person creation.
     *
     * @param personName The name of the person
     */
    public ContentPersonEntity(String personName) {
        this.personName = personName;
        this.contentImages = new HashSet<>();
        this.collections = new HashSet<>();
    }

    /**
     * Get the number of images this person appears in.
     *
     * @return The total number of images featuring this person
     */
    public int getImageCount() {
        return contentImages.size();
    }

    /**
     * Get the number of collections this person is tagged in.
     *
     * @return The total number of collections featuring this person
     */
    public int getCollectionCount() {
        return collections.size();
    }

    /**
     * Get the total usage count of this person across all entities.
     *
     * @return The total number of times this person is tagged
     */
    public int getTotalUsageCount() {
        return contentImages.size() + collections.size();
    }
}
