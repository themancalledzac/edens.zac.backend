package edens.zac.portfolio.backend.services.validator;

import edens.zac.portfolio.backend.model.ContentImageUpdateRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Validator for ContentImageUpdateRequest. Centralizes validation logic for image update
 * operations.
 */
@Component
@Slf4j
public class ContentImageUpdateValidator {

  /**
   * Validate a ContentImageUpdateRequest. Throws IllegalArgumentException if validation fails.
   *
   * @param request The update request to validate
   * @throws IllegalArgumentException if validation fails
   */
  public void validate(ContentImageUpdateRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("Update request cannot be null");
    }

    if (request.getId() == null) {
      throw new IllegalArgumentException("Image ID is required for updates");
    }

    // Validate: if isFilm is being set to true, filmFormat must also be provided
    if (Boolean.TRUE.equals(request.getIsFilm())) {
      if (request.getFilmFormat() == null) {
        throw new IllegalArgumentException("filmFormat is required when isFilm is true");
      }
    }
  }
}
