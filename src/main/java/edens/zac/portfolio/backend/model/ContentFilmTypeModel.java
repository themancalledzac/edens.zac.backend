package edens.zac.portfolio.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Model representing a film stock type for API responses.
 * Contains the film type's ID, name, display name, default ISO, and related image content block IDs.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentFilmTypeModel {

    private Long id;

    /**
     * Technical name for the film type (e.g., "KODAK_PORTRA_400").
     */
    private String filmTypeName;

    /**
     * Human-readable display name (e.g., "Kodak Portra 400").
     */
    private String displayName;

    /**
     * Default ISO value for this film stock.
     */
    private Integer defaultIso;

    /**
     * List of image content block IDs that use this film type.
     */
    @Builder.Default
    private List<Long> imageContentBlockIds = new ArrayList<>();
}
