package edens.zac.portfolio.backend.model;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Stable JSON envelope for paginated endpoints. Returning {@link Page} directly triggers Spring's
 * "Serializing PageImpl instances as-is is not supported" warning because the wire shape is not
 * guaranteed across versions. This DTO pins the keys our frontend reads ({@code content}, {@code
 * totalElements}, {@code totalPages}, {@code number}, {@code last}) and drops the Spring-internal
 * fields (pageable, sort, numberOfElements, empty, first, size) that no client relies on.
 */
public record PagedResponse<T>(
    List<T> content, long totalElements, int totalPages, int number, boolean last) {

  public static <T> PagedResponse<T> from(Page<T> page) {
    return new PagedResponse<>(
        page.getContent(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getNumber(),
        page.isLast());
  }
}
