package edens.zac.portfolio.backend.model;

/**
 * One item of {@code GET /api/read/user/ratings?collectionId=} — the caller's override rating for
 * one image.
 *
 * @param contentId the image
 * @param rating the override rating, 0-5
 */
public record UserRatingOverrideResponse(Long contentId, Integer rating) {}
