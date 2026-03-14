package edens.zac.portfolio.backend.model;

/**
 * DTO representing film type information for API responses. Contains the enum name, display name,
 * and default ISO value.
 */
public record FilmTypeDTO(
    /** The enum constant name (e.g., "KODAK_PORTRA_400") */
    String name,
    /** Human-readable display name (e.g., "Kodak Portra 400") */
    String displayName,
    /** Default ISO rating for this film stock */
    Integer defaultIso) {}
