package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.types.CollectionRole;

/** A user's membership in one collection, surfaced by /api/auth/me. */
public record GalleryMembership(Long collectionId, CollectionRole role) {}
