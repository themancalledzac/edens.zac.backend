package edens.zac.portfolio.backend.controller.prod;

import edens.zac.portfolio.backend.config.DefaultValues;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.services.PaginationUtil;
import edens.zac.portfolio.backend.types.CollectionType;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Production controller for Collection read operations. Exception handling is delegated to
 * GlobalExceptionHandler.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/read/collections")
public class CollectionControllerProd {

  private final CollectionService collectionService;

  /**
   * Get all collections with basic info (paginated).
   *
   * @param page Page number (0-based)
   * @param size Page size
   * @return ResponseEntity with paginated collections
   */
  @GetMapping
  public ResponseEntity<Page<CollectionModel>> getAllCollections(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    Pageable pageable = PaginationUtil.normalizeCollectionPageable(page, size);
    Page<CollectionModel> collections = collectionService.getAllCollections(pageable);
    return ResponseEntity.ok(collections);
  }

  /**
   * Get collection by slug with paginated content.
   *
   * @param slug Collection slug
   * @param page Page number (0-based)
   * @param size Page size
   * @return ResponseEntity with collection and paginated content
   */
  @GetMapping("/{slug}")
  public ResponseEntity<CollectionModel> getCollectionBySlug(
      @PathVariable String slug,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "30") int size) {
    int normalizedPage = PaginationUtil.normalizePage(page);
    int normalizedSize = PaginationUtil.normalizeSize(size, DefaultValues.default_content_per_page);

    CollectionModel collection =
        collectionService.getCollectionWithPagination(slug, normalizedPage, normalizedSize);
    return ResponseEntity.ok(collection);
  }

  /**
   * Get visible collections by type ordered by collection date (newest first). Currently only
   * accepts BLOG type.
   *
   * @param type Collection type (currently only BLOG is supported)
   * @return ResponseEntity with list of visible collections of the specified type
   */
  @GetMapping("/type/{type}")
  public ResponseEntity<List<CollectionModel>> getCollectionsByType(@PathVariable String type) {
    CollectionType collectionType;
    try {
      collectionType = CollectionType.valueOf(type.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid collection type: " + type);
    }

    if (collectionType != CollectionType.BLOG) {
      throw new IllegalArgumentException(
          "Only BLOG collection type is currently supported. Received: " + type);
    }

    List<CollectionModel> collections =
        collectionService.findVisibleByTypeOrderByDate(collectionType);
    return ResponseEntity.ok(collections);
  }

  /**
   * Validate client gallery access with password.
   *
   * @param slug Collection slug
   * @param passwordRequest Request body containing password
   * @return ResponseEntity with access status
   */
  @PostMapping("/{slug}/access")
  public ResponseEntity<Map<String, Boolean>> validateClientGalleryAccess(
      @PathVariable String slug, @RequestBody Map<String, String> passwordRequest) {
    String password = passwordRequest.get("password");

    if (password == null) {
      throw new IllegalArgumentException("Password is required");
    }

    boolean hasAccess = collectionService.validateClientGalleryAccess(slug, password);
    return ResponseEntity.ok(Map.of("hasAccess", hasAccess));
  }
}
