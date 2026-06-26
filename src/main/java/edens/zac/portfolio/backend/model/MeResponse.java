package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.types.Role;
import java.util.List;

public record MeResponse(
    String email, Role role, boolean mfaSatisfied, List<GalleryMembership> galleries) {}
