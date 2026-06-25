package edens.zac.portfolio.backend.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code PUT /api/read/user/ratings} — the caller's rating override for one image scoped to
 * a collection's view.
 *
 * @param collectionId the collection whose view this override belongs to
 * @param contentId the image being rated
 * @param rating the override rating, 0-5
 */
public record UserRatingOverrideRequest(
    @NotNull Long collectionId, @NotNull Long contentId, @NotNull @Min(0) @Max(5) Integer rating) {}
