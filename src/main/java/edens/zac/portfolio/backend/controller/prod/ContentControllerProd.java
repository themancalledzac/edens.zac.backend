package edens.zac.portfolio.backend.controller.prod;

import edens.zac.portfolio.backend.model.ContentFilmTypeModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.ImageSearchFilter;
import edens.zac.portfolio.backend.model.PagedResponse;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.services.ContentService;
import edens.zac.portfolio.backend.services.MetadataService;
import edens.zac.portfolio.backend.types.FilmFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Production controller for Content read operations. Exception handling is delegated to
 * GlobalExceptionHandler.
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/read/content")
public class ContentControllerProd {

  private final ContentService contentService;
  private final MetadataService metadataService;

  /**
   * Search images with optional filters. GET /api/read/content/images/search
   *
   * @return ResponseEntity with paginated search results
   */
  @GetMapping("/images/search")
  public ResponseEntity<PagedResponse<ContentModels.Image>> searchImages(
      @ModelAttribute ImageSearchFilter filter,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "30") @Min(1) @Max(200) int size) {
    PagedResponse<ContentModels.Image> response =
        contentService.searchImages(filter.toRequest(page, size));
    return ResponseEntity.ok(response);
  }

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
   * Get all lenses (ordered alphabetically) GET /api/read/content/lenses
   *
   * @return ResponseEntity with list of all lenses
   */
  @GetMapping("/lenses")
  public ResponseEntity<List<Records.Lens>> getAllLenses() {
    List<Records.Lens> lenses = metadataService.getAllLenses();
    return ResponseEntity.ok(lenses);
  }

  /**
   * Get all locations with visible content, including count hints for clickability. GET
   * /api/read/content/locations
   *
   * @return ResponseEntity with list of locations and their collection/image counts
   */
  @GetMapping("/locations")
  public ResponseEntity<List<Records.LocationWithCounts>> getLocationsWithCounts() {
    List<Records.LocationWithCounts> locations = metadataService.getLocationsWithCounts();
    return ResponseEntity.ok(locations);
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

    List<Records.FilmFormatOption> filmFormats =
        Arrays.stream(FilmFormat.values()).map(Records.FilmFormatOption::of).toList();

    return ResponseEntity.ok(Map.of("filmTypes", filmTypes, "filmFormats", filmFormats));
  }
}
