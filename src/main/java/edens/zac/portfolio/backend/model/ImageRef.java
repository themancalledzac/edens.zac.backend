package edens.zac.portfolio.backend.model;

/**
 * Minimal immutable image reference with URL and optional intrinsic dimensions.
 * Used for coverImage on collections.
 */
public record ImageRef(
        String url,
        Integer width,
        Integer height
) {}
