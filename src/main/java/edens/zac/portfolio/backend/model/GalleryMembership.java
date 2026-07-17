package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.types.AccessLevel;

/** A user's effective access to one collection, surfaced by /api/auth/me. */
public record GalleryMembership(Long collectionId, AccessLevel role) {}
