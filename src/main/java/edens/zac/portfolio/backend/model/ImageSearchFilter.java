package edens.zac.portfolio.backend.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Image filter and paging query parameters, bound once as a {@code @ModelAttribute} by both the
 * admin and the public image endpoints. Both endpoints page 50 at a time and reject a {@code size}
 * outside 1-200 with a 400; the compact constructor supplies those defaults when the caller omits
 * the parameters, so the constraints below see a resolved value either way.
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
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate captureEndDate,
    @Min(0) Integer page,
    @Min(1) @Max(200) Integer size) {

  private static final int DEFAULT_PAGE = 0;
  private static final int DEFAULT_SIZE = 50;

  public ImageSearchFilter {
    page = page == null ? DEFAULT_PAGE : page;
    size = size == null ? DEFAULT_SIZE : size;
  }

  /** Combines these filters with the resolved paging into a service request. */
  public ImageSearchRequest toRequest() {
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
