package edens.zac.portfolio.backend.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Entity representing a film stock type.
 * Film types can be associated with ContentImages via foreign key relationship.
 * This replaces the static FilmType enum to allow dynamic film type management.
 * 
 * Database table: content_film_types
 * Indexes:
 *   - idx_content_film_type_name (film_type_name, unique)
 */
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

    /** Column: id (BIGINT, PRIMARY KEY, auto-generated) */
    private Long id;

    /** Column: film_type_name (VARCHAR(100), NOT NULL, UNIQUE) */
    @NotBlank
    @Size(min = 1, max = 100)
    private String filmTypeName;

    /** Column: display_name (VARCHAR(100), NOT NULL) */
    @NotBlank
    @Size(min = 1, max = 100)
    private String displayName;

    /** Column: default_iso (INTEGER, NOT NULL) */
    @NotNull
    @Positive
    private Integer defaultIso;

    /** Column: created_at (TIMESTAMP, NOT NULL) */
    private LocalDateTime createdAt;

    /** Relationship: One-to-many with ContentImageEntity (via content_image.film_type_id) */
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
