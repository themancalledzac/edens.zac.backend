package edens.zac.portfolio.backend.model;

import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Image filter query parameters, bound once as a {@code @ModelAttribute} by both the admin and the
 * public image endpoints. Paging is deliberately not part of this record: the two endpoints apply
 * different size defaults and limits, so each declares {@code page} and {@code size} itself.
 */
public record ImageSearchFilter(
    List<Long> personIds,
    List<Long> tagIds,
    Long cameraId,
    Long locationId,
    Long lensId,
    Integer minRating,
    Boolean isFilm,
    Boolean blackAndWhite,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate captureStartDate,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate captureEndDate) {

  /** Combines these filters with the caller's already-resolved paging into a service request. */
  public ImageSearchRequest toRequest(int page, int size) {
    return new ImageSearchRequest(
        personIds,
        tagIds,
        cameraId,
        locationId,
        lensId,
        minRating,
        isFilm,
        blackAndWhite,
        captureStartDate,
        captureEndDate,
        page,
        size);
  }
}
