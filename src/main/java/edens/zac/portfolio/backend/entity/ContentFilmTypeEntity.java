package edens.zac.portfolio.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
 * Entity representing a film stock type.
 * Film types can be associated with ContentImages via many-to-many relationship.
 * This replaces the static FilmType enum to allow dynamic film type management.
 */
@Entity
@Table(
        name = "content_film_types",
        indexes = {
                @Index(name = "idx_content_film_type_name", columnList = "film_type_name", unique = true)
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentFilmTypeEntity {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContentFilmTypeEntity that)) return false;
        return filmTypeName != null && filmTypeName.equals(that.filmTypeName);
    }

    @Override
    public int hashCode() {
        return filmTypeName != null ? filmTypeName.hashCode() : 0;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(min = 1, max = 100)
    @Column(name = "film_type_name", unique = true, nullable = false, length = 100)
    private String filmTypeName;

    @NotBlank
    @Size(min = 1, max = 100)
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @NotNull
    @Positive
    @Column(name = "default_iso", nullable = false)
    private Integer defaultIso;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // One-to-many relationship with ContentImages (mappedBy side)
    @OneToMany(mappedBy = "filmType", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<ContentImageEntity> contentImages = new HashSet<>();

    /**
     * Constructor for creating a film type with name, display name, and ISO.
     * Useful for migration from the old FilmType enum.
     *
     * @param filmTypeName The technical name (e.g., "KODAK_PORTRA_400")
     * @param displayName The human-readable name (e.g., "Kodak Portra 400")
     * @param defaultIso The default ISO value for this film stock
     */
    public ContentFilmTypeEntity(String filmTypeName, String displayName, Integer defaultIso) {
        this.filmTypeName = filmTypeName;
        this.displayName = displayName;
        this.defaultIso = defaultIso;
        this.contentImages = new HashSet<>();
    }

    /**
     * Get the number of images using this film type.
     *
     * @return The number of images associated with this film type
     */
    public int getImageCount() {
        return contentImages.size();
    }
}
