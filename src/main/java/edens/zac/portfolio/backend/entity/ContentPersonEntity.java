package edens.zac.portfolio.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Entity representing a person who can be tagged in image content blocks.
 * This allows tracking which people appear in photographs.
 */
@Entity
@Table(
        name = "content_people",
        indexes = {
                @Index(name = "idx_content_person_name", columnList = "person_name", unique = true)
        }
)
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(min = 1, max = 100)
    @Column(name = "person_name", unique = true, nullable = false, length = 100)
    private String personName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Many-to-many relationship with ImageContent (mappedBy side - non-owning)
    @ManyToMany(mappedBy = "people", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<ContentImageEntity> ContentImage = new HashSet<>();

    /**
     * Constructor for creating a person with just a name.
     * Useful for quick person creation.
     *
     * @param personName The name of the person
     */
    public ContentPersonEntity(String personName) {
        this.personName = personName;
        this.ContentImage = new HashSet<>();
    }

    /**
     * Get the number of images this person appears in.
     *
     * @return The total number of images featuring this person
     */
    public int getImageCount() {
        return ContentImage.size();
    }
}
