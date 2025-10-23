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
 * Entity representing a camera lens model.
 * Lenses can be associated with ImageContentBlocks.
 * New lenses are automatically created when an image is updated with a new lens name.
 */
@Entity
@Table(
        name = "content_lenses",
        indexes = {
                @Index(name = "idx_content_lens_name", columnList = "lens_name", unique = true)
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentLensEntity {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContentLensEntity that)) return false;
        return lensName != null && lensName.equals(that.lensName);
    }

    @Override
    public int hashCode() {
        return lensName != null ? lensName.hashCode() : 0;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(min = 1, max = 100)
    @Column(name = "lens_name", unique = true, nullable = false, length = 100)
    private String lensName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // One-to-many relationship with ImageContentBlocks (mappedBy side)
    @OneToMany(mappedBy = "lens", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<ImageContentBlockEntity> imageContentBlocks = new HashSet<>();

    /**
     * Constructor for creating a lens with just a name.
     * Useful for quick lens creation during image updates.
     *
     * @param lensName The name of the lens
     */
    public ContentLensEntity(String lensName) {
        this.lensName = lensName;
        this.imageContentBlocks = new HashSet<>();
    }

    /**
     * Get the number of images using this lens.
     *
     * @return The number of images associated with this lens
     */
    public int getImageCount() {
        return imageContentBlocks.size();
    }
}
