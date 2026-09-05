package edens.zac.portfolio.backend.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;

/**
 * Request model for image search with optional filters. All filter fields are nullable; only
 * non-null fields participate in the query.
 *
 * <p>{@code publicOnly} restricts results to content with a visible membership in a LISTED
 * collection that has no gallery password. It is set by the controller from the route, never bound
 * from the request: {@code /api/read/content/images/search} is anonymous and shared-cacheable, so a
 * caller-supplied value would let anyone opt out of the filter, and a viewer-dependent one would
 * poison the CloudFront cache.
 */
public record ImageSearchRequest(
    List<Long> personIds,
    List<Long> tagIds,
    Long cameraId,
    Long locationId,
    Long lensId,
    Integer minRating,
    Boolean isFilm,
    Boolean blackAndWhite,
    LocalDate captureStartDate,
    LocalDate captureEndDate,
    @Min(0) int page,
    @Min(1) @Max(200) int size,
    boolean publicOnly) {}
