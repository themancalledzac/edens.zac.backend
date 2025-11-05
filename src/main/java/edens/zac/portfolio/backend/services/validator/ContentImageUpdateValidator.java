package edens.zac.portfolio.backend.services.validator;

import edens.zac.portfolio.backend.model.ContentImageUpdateRequest;
import edens.zac.portfolio.backend.types.FilmFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Validator for ContentImageUpdateRequest.
 * Centralizes validation logic for image update operations.
 */
@Component
@Slf4j
public class ContentImageUpdateValidator {

    /**
     * Validate a ContentImageUpdateRequest.
     * Throws IllegalArgumentException if validation fails.
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

    /**
     * Validate that filmFormat is provided when isFilm is true.
     * This is a helper method for validation during entity updates.
     *
     * @param isFilm      The isFilm value
     * @param filmFormat  The filmFormat value (may be null if already set on entity)
     * @param entityFilmFormat The filmFormat already set on the entity
     * @throws IllegalArgumentException if validation fails
     */
    public void validateFilmFormatRequired(Boolean isFilm, FilmFormat filmFormat, FilmFormat entityFilmFormat) {
        if (Boolean.TRUE.equals(isFilm)) {
            // Check if filmFormat will be set after this update
            if (entityFilmFormat == null && filmFormat == null) {
                throw new IllegalArgumentException("filmFormat is required when isFilm is true");
            }
        }
    }
}

