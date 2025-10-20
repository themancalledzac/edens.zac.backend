package edens.zac.portfolio.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing film type information for API responses.
 * Contains the enum name, display name, and default ISO value.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilmTypeDTO {

    /**
     * The enum constant name (e.g., "KODAK_PORTRA_400")
     */
    private String name;

    /**
     * Human-readable display name (e.g., "Kodak Portra 400")
     */
    private String displayName;

    /**
     * Default ISO rating for this film stock
     */
    private Integer defaultIso;
}