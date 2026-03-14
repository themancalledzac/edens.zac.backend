package edens.zac.portfolio.backend.controller.prod;

import edens.zac.portfolio.backend.model.ContentFilmTypeModel;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.services.MetadataService;
import edens.zac.portfolio.backend.types.FilmFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Production controller for Content read operations. Exception handling is delegated to
 * GlobalExceptionHandler.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/read/content")
public class ContentControllerProd {

  private final MetadataService metadataService;

  /**
   * Get all tags (ordered alphabetically) GET /api/read/content/tags
   *
   * @return ResponseEntity with list of all tags
   */
  @GetMapping("/tags")
  public ResponseEntity<List<Records.Tag>> getAllTags() {
    List<Records.Tag> tags = metadataService.getAllTags();
    return ResponseEntity.ok(tags);
  }

  /**
   * Get all people (ordered alphabetically) GET /api/read/content/people
   *
   * @return ResponseEntity with list of all people
   */
  @GetMapping("/people")
  public ResponseEntity<List<Records.Person>> getAllPeople() {
    List<Records.Person> people = metadataService.getAllPeople();
    return ResponseEntity.ok(people);
  }

  /**
   * Get all cameras (ordered alphabetically) GET /api/read/content/cameras
   *
   * @return ResponseEntity with list of all cameras
   */
  @GetMapping("/cameras")
  public ResponseEntity<List<Records.Camera>> getAllCameras() {
    List<Records.Camera> cameras = metadataService.getAllCameras();
    return ResponseEntity.ok(cameras);
  }

  /**
   * Get film metadata (film types and formats) GET /api/read/content/film-metadata
   *
   * <p>Returns all available film types with their default ISO values, and all available film
   * formats. Used by the frontend to populate dropdowns in the image editing interface.
   *
   * @return ResponseEntity with film types and formats
   */
  @GetMapping("/film-metadata")
  public ResponseEntity<Map<String, Object>> getFilmMetadata() {
    List<ContentFilmTypeModel> filmTypes = metadataService.getAllFilmTypes();

    List<FilmFormatResponse> filmFormats =
        Arrays.stream(FilmFormat.values())
            .map(format -> new FilmFormatResponse(format.name(), format.getDisplayName()))
            .toList();

    return ResponseEntity.ok(Map.of("filmTypes", filmTypes, "filmFormats", filmFormats));
  }

  /** Response DTO for film formats. */
  @lombok.Data
  @lombok.AllArgsConstructor
  public static class FilmFormatResponse {
    private String name;
    private String displayName;
  }
}
