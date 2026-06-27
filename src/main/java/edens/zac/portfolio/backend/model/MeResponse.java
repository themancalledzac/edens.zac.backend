package edens.zac.portfolio.backend.model;

import java.util.List;

public record MeResponse(String email, boolean mfaSatisfied, List<GalleryMembership> galleries) {}
