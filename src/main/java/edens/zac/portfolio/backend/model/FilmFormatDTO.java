package edens.zac.portfolio.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing film format information for API responses.
 * Contains the enum name and display name.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilmFormatDTO {

    /**
     * The enum constant name (e.g., "MM_35")
     */
    private String name;

    /**
     * Human-readable display name (e.g., "35mm")
     */
    private String displayName;
}