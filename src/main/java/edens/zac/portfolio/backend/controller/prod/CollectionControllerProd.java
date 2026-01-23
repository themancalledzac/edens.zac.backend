package edens.zac.portfolio.backend.controller.prod;

import edens.zac.portfolio.backend.config.DefaultValues;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.services.PaginationUtil;
import edens.zac.portfolio.backend.types.CollectionType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// TODO: Probably update 'read' endpoints to just '/api/collections'
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/read/collections")
public class CollectionControllerProd {

  private final CollectionService collectionService;

  /**
   * Get all collections with basic info (paginated)
   *
   * @param page Page number (0-based)
   * @param size Page size
   * @return ResponseEntity with paginated collections
   */
  @GetMapping
  public ResponseEntity<?> getAllCollections(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    try {
      Pageable pageable = PaginationUtil.normalizeCollectionPageable(page, size);
      Page<CollectionModel> collections = collectionService.getAllCollections(pageable);

      return ResponseEntity.ok(collections);
    } catch (Exception e) {
      log.error("Error getting all collections: {}", e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Failed to retrieve collections: " + e.getMessage());
    }
  }

  /**
   * Get collection by slug with paginated content TODO: Need to work on a 'admin' vs 'regular'
   * endpoint here, where: - regular would check if 'hasAccess' is true. if true, we would simply
   * return a response body of 'no access' - admin would simply return all regardless. - Curious if
   * we can just extend this, or otherwise duplicate it, while keeping 'local' dev development truly
   * local only - Might be easiest to simply have a CollectionControllerDev endpoint to save time
   *
   * @param slug Collection slug
   * @param page Page number (0-based)
   * @param size Page size
   * @return ResponseEntity with collection and paginated content
   */
  @GetMapping("/{slug}")
  public ResponseEntity<?> getCollectionBySlug(
      @PathVariable String slug,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "30") int size) {
    try {
      // Normalize pagination parameters
      int normalizedPage = PaginationUtil.normalizePage(page);
      int normalizedSize =
          PaginationUtil.normalizeSize(size, DefaultValues.default_content_per_page);

      CollectionModel collection =
          collectionService.getCollectionWithPagination(slug, normalizedPage, normalizedSize);
      return ResponseEntity.ok(collection);
    } catch (IllegalArgumentException e) {
      log.warn("Collection not found: {}", slug);
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body("Collection with slug: " + slug + " not found");
    } catch (Exception e) {
      log.error("Error getting collection {}: {}", slug, e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Failed to retrieve collection: " + e.getMessage());
    }
  }

  /**
   * Get visible collections by type ordered by collection date (newest first) Currently only
   * accepts BLOG type.
   *
   * @param type Collection type (currently only BLOG is supported)
   * @return ResponseEntity with list of visible collections of the specified type as
   *     CollectionModel objects
   */
  @GetMapping("/type/{type}")
  public ResponseEntity<?> getCollectionsByType(@PathVariable String type) {
    try {
      // Convert string type to enum
      CollectionType collectionType;
      try {
        collectionType = CollectionType.valueOf(type.toUpperCase());
      } catch (IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body("Invalid collection type: " + type);
      }

      // Currently only BLOG type is supported
      if (collectionType != CollectionType.BLOG) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body("Only BLOG collection type is currently supported. Received: " + type);
      }

      // Get visible collections ordered by date (newest first)
      List<CollectionModel> collections =
          collectionService.findVisibleByTypeOrderByDate(collectionType);

      return ResponseEntity.ok(collections);
    } catch (Exception e) {
      log.error("Error getting collections by type {}: {}", type, e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Failed to retrieve collections: " + e.getMessage());
    }
  }

  /**
   * Validate client gallery access with password
   *
   * @param slug Collection slug
   * @param passwordRequest Request body containing password
   * @return ResponseEntity with access status
   */
  @PostMapping("/{slug}/access")
  public ResponseEntity<?> validateClientGalleryAccess(
      @PathVariable String slug, @RequestBody Map<String, String> passwordRequest) {
    try {
      String password = passwordRequest.get("password");

      if (password == null) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Password is required");
      }

      boolean hasAccess = collectionService.validateClientGalleryAccess(slug, password);

      Map<String, Object> response = new HashMap<>();
      response.put("hasAccess", hasAccess);

      return ResponseEntity.ok(response);
    } catch (IllegalArgumentException e) {
      log.warn("Collection not found: {}", slug);
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body("Collection with slug: " + slug + " not found");
    } catch (Exception e) {
      log.error("Error validating access for collection {}: {}", slug, e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Failed to validate access: " + e.getMessage());
    }
  }
}
