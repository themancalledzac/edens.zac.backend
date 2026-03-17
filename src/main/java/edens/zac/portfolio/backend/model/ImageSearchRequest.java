package edens.zac.portfolio.backend.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;

/**
 * Request model for image search with optional filters. All filter fields are nullable; only
 * non-null fields participate in the query.
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
    @Min(1) @Max(200) int size) {}
