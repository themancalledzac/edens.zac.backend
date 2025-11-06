package edens.zac.portfolio.backend.controller.prod;

import edens.zac.portfolio.backend.model.ContentCameraModel;
import edens.zac.portfolio.backend.model.ContentFilmTypeModel;
import edens.zac.portfolio.backend.model.ContentPersonModel;
import edens.zac.portfolio.backend.model.ContentTagModel;
import edens.zac.portfolio.backend.services.ContentService;
import edens.zac.portfolio.backend.types.FilmFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/read/content")
public class ContentControllerProd {

    private final ContentService contentService;

    /**
     * Get all tags (ordered alphabetically)
     * GET /api/read/content/tags
     *
     * @return ResponseEntity with list of all tags
     */
    @GetMapping("/tags")
    public ResponseEntity<?> getAllTags() {
        try {
            List<ContentTagModel> tags = contentService.getAllTags();
            return ResponseEntity.ok(tags);
        } catch (Exception e) {
            log.error("Error getting all tags: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve tags: " + e.getMessage());
        }
    }

    /**
     * Get all people (ordered alphabetically)
     * GET /api/read/content/people
     *
     * @return ResponseEntity with list of all people
     */
    @GetMapping("/people")
    public ResponseEntity<?> getAllPeople() {
        try {
            List<ContentPersonModel> people = contentService.getAllPeople();
            return ResponseEntity.ok(people);
        } catch (Exception e) {
            log.error("Error getting all people: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve people: " + e.getMessage());
        }
    }

    /**
     * Get all cameras (ordered alphabetically)
     * GET /api/read/content/cameras
     *
     * @return ResponseEntity with list of all cameras
     */
    @GetMapping("/cameras")
    public ResponseEntity<?> getAllCameras() {
        try {
            List<ContentCameraModel> cameras = contentService.getAllCameras();
            return ResponseEntity.ok(cameras);
        } catch (Exception e) {
            log.error("Error getting all cameras: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve cameras: " + e.getMessage());
        }
    }

    /**
     * Get film metadata (film types and formats)
     * GET /api/read/content/film-metadata
     *
     * Returns all available film types with their default ISO values,
     * and all available film formats. Used by the frontend to populate
     * dropdowns in the image editing interface.
     *
     * @return ResponseEntity with film types and formats
     */
    @GetMapping("/film-metadata")
    public ResponseEntity<?> getFilmMetadata() {
        try {
            // Get film types from database
            List<ContentFilmTypeModel> filmTypes = contentService.getAllFilmTypes();

            // Convert FilmFormat enum to response format
            List<FilmFormatResponse> filmFormats = Arrays.stream(FilmFormat.values())
                    .map(filmFormat -> new FilmFormatResponse(
                            filmFormat.name(),
                            filmFormat.getDisplayName()
                    ))
                    .collect(Collectors.toList());

            Map<String, Object> response = Map.of(
                    "filmTypes", filmTypes,
                    "filmFormats", filmFormats
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting film metadata: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve film metadata: " + e.getMessage());
        }
    }

    /**
     * Response DTO for film formats
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class FilmFormatResponse {
        private String name;
        private String displayName;
    }
}
