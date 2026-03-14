package edens.zac.portfolio.backend.model;

import java.util.List;

/**
 * Model representing a film stock type for API responses. Contains the film type's ID, name,
 * display name, default ISO, and related image content block IDs.
 */
public record ContentFilmTypeModel(
    Long id,
    /** Technical name for the film type (e.g., "KODAK_PORTRA_400"). */
    String filmTypeName,
    /** Human-readable display name (e.g., "Kodak Portra 400"). */
    String name,
    /** Default ISO value for this film stock. */
    Integer defaultIso,
    /** List of content image IDs that use this film type. */
    List<Long> contentImageIds) {}
