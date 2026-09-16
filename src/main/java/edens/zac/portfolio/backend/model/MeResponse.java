package edens.zac.portfolio.backend.model;

import java.util.List;

/**
 * Session identity for the frontend. `passkeyCount` is the account's `webauthn_credential` rows.
 */
public record MeResponse(
    String email,
    boolean isAdmin,
    boolean mfaSatisfied,
    List<GalleryMembership> galleries,
    int passkeyCount) {}
