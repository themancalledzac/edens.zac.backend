package edens.zac.portfolio.backend.controller.prod;

import edens.zac.portfolio.backend.config.DefaultValues;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.LocationPageResponse;
import edens.zac.portfolio.backend.model.PasswordRequest;
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.services.PaginationUtil;
import edens.zac.portfolio.backend.types.CollectionType;
import jakarta.validation.Valid;
import java.util.HashMap;
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
      @RequestParam(defaultValue = "30") int size,
      @RequestParam(required = false) String accessToken) {
    int normalizedPage = PaginationUtil.normalizePage(page);
    int normalizedSize = PaginationUtil.normalizeSize(size, DefaultValues.default_content_per_page);

    CollectionModel collection =
        collectionService.getCollectionWithPagination(slug, normalizedPage, normalizedSize);

    // For password-protected galleries, omit content unless valid accessToken is provided
    if (Boolean.TRUE.equals(collection.getIsPasswordProtected())) {
      if (accessToken == null || !collectionService.validateAccessToken(slug, accessToken)) {
        collection.setContent(null);
        collection.setContentCount(null);
      }
    }

    return ResponseEntity.ok(collection);
  }

  /**
   * Get visible collections by type ordered by collection date (newest first).
   *
   * @param type Collection type (e.g., BLOG, PORTFOLIO, CLIENT_GALLERY, ART_GALLERY)
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

    List<CollectionModel> collections =
        collectionService.findVisibleByTypeOrderByDate(collectionType);
    return ResponseEntity.ok(collections);
  }

  /**
   * Get location page with collections and orphan images.
   *
   * @param name Location name
   * @param collectionPage Collection page number (0-based)
   * @param collectionSize Collections per page
   * @param imagePage Image page number (0-based)
   * @param imageSize Images per page
   * @return ResponseEntity with location page data
   */
  @GetMapping("/location/{name}")
  public ResponseEntity<LocationPageResponse> getLocationPage(
      @PathVariable String name,
      @RequestParam(defaultValue = "0") int collectionPage,
      @RequestParam(defaultValue = "35") int collectionSize,
      @RequestParam(defaultValue = "0") int imagePage,
      @RequestParam(defaultValue = "50") int imageSize) {
    int normCollPage = PaginationUtil.normalizePage(collectionPage);
    int normCollSize = PaginationUtil.normalizeSize(collectionSize, 35);
    int normImgPage = PaginationUtil.normalizePage(imagePage);
    int normImgSize = PaginationUtil.normalizeSize(imageSize, 50);

    LocationPageResponse response =
        collectionService.getLocationPage(
            name, normCollPage, normCollSize, normImgPage, normImgSize);
    return ResponseEntity.ok(response);
  }

  /**
   * Get collection metadata only (no content). Lightweight endpoint for SEO/generateMetadata.
   *
   * @param slug Collection slug
   * @return ResponseEntity with collection metadata
   */
  @GetMapping("/{slug}/meta")
  public ResponseEntity<CollectionModel> getCollectionMeta(@PathVariable String slug) {
    CollectionModel model = collectionService.findMetaBySlug(slug);
    return ResponseEntity.ok(model);
  }

  /**
   * Validate client gallery access with password.
   *
   * @param slug Collection slug
   * @param passwordRequest Request body containing password
   * @return ResponseEntity with access status
   */
  @PostMapping("/{slug}/access")
  public ResponseEntity<Map<String, Object>> validateClientGalleryAccess(
      @PathVariable String slug, @Valid @RequestBody PasswordRequest passwordRequest) {
    boolean hasAccess =
        collectionService.validateClientGalleryAccess(slug, passwordRequest.password());

    if (!hasAccess) {
      log.warn("Failed client gallery access attempt for slug: {}", slug);
    }

    Map<String, Object> response = new HashMap<>();
    response.put("hasAccess", hasAccess);
    if (hasAccess) {
      response.put("accessToken", collectionService.generateAccessToken(slug));
    }
    return ResponseEntity.ok(response);
  }
}
