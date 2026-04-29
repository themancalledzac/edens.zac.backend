package edens.zac.portfolio.backend.controller.prod;

import static edens.zac.portfolio.backend.config.GalleryAccessCookies.cookieName;
import static edens.zac.portfolio.backend.config.GalleryAccessCookies.readCookie;

import edens.zac.portfolio.backend.config.ClientGalleryAccessLimiter;
import edens.zac.portfolio.backend.config.DefaultValues;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.LocationPageResponse;
import edens.zac.portfolio.backend.model.PasswordRequest;
import edens.zac.portfolio.backend.services.ClientGalleryAuthService;
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.services.PaginationUtil;
import edens.zac.portfolio.backend.types.CollectionType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
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

  private static final Duration GALLERY_COOKIE_MAX_AGE = Duration.ofHours(24);

  private final ClientGalleryAuthService clientGalleryAuthService;
  private final CollectionService collectionService;
  private final ClientGalleryAccessLimiter accessLimiter;

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
    Page<CollectionModel> collections = collectionService.getVisibleCollections(pageable);
    return ResponseEntity.ok(collections);
  }

  /**
   * Get collection by slug with paginated content.
   *
   * <p>For password-protected client galleries, the access token is read from the {@code
   * gallery_access_<slug>} HttpOnly cookie set by {@link #validateClientGalleryAccess}. Without a
   * valid cookie, content and contentCount are stripped from the response.
   *
   * @param slug Collection slug
   * @param page Page number (0-based)
   * @param size Page size
   * @param request Servlet request, used to read the per-slug access cookie
   * @return ResponseEntity with collection and paginated content
   */
  @GetMapping("/{slug}")
  public ResponseEntity<CollectionModel> getCollectionBySlug(
      @PathVariable String slug,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "30") int size,
      HttpServletRequest request) {
    int normalizedPage = PaginationUtil.normalizePage(page);
    int normalizedSize = PaginationUtil.normalizeSize(size, DefaultValues.default_content_per_page);

    CollectionModel collection =
        collectionService.getCollectionWithPagination(slug, normalizedPage, normalizedSize);

    // For password-protected galleries, omit content (and the cover image) unless a valid cookie
    // is supplied. The cover image is treated as private metadata for protected galleries; it
    // would otherwise leak in the SSR HTML, the OpenGraph meta tags, and any pre-rendered
    // children of the frontend's ClientGalleryGate.
    //
    // Note: coverImage is also stripped centrally in CollectionProcessingUtil.buildBasicModel
    // for all list endpoints (getAllCollections, getCollectionsByType). The setCoverImage(null)
    // call below is intentional defense-in-depth (belt + suspenders).
    if (Boolean.TRUE.equals(collection.getIsPasswordProtected())) {
      String cookieToken = readCookie(request, cookieName(slug));
      if (cookieToken == null || !clientGalleryAuthService.validateAccessToken(slug, cookieToken)) {
        collection.setContent(null);
        collection.setContentCount(null);
        collection.setCoverImage(null);
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
   * @param slug Location slug
   * @param collectionPage Collection page number (0-based)
   * @param collectionSize Collections per page
   * @param imagePage Image page number (0-based)
   * @param imageSize Images per page
   * @return ResponseEntity with location page data
   */
  @GetMapping("/location/{slug}")
  public ResponseEntity<LocationPageResponse> getLocationPage(
      @PathVariable String slug,
      @RequestParam(defaultValue = "0") int collectionPage,
      @RequestParam(defaultValue = "35") int collectionSize,
      @RequestParam(defaultValue = "0") int imagePage,
      @RequestParam(defaultValue = "50") int imageSize) {
    int normCollPage = PaginationUtil.normalizePage(collectionPage);
    int normCollSize = PaginationUtil.normalizeSize(collectionSize, 35);
    int normImgPage = PaginationUtil.normalizePage(imagePage);
    int normImgSize = PaginationUtil.normalizeSize(imageSize, 50);

    LocationPageResponse response =
        collectionService.getLocationPageBySlug(
            slug, normCollPage, normCollSize, normImgPage, normImgSize);
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
   * <p>On success, sets a {@code gallery_access_<slug>} HttpOnly+Secure+SameSite=Strict cookie
   * carrying the HMAC access token. The cookie is sent automatically by the browser on subsequent
   * GET /collections/{slug} requests, so the access token never needs to be exposed to JavaScript.
   *
   * <p>Rate-limited per {@code (ip, slug)} pair via {@link ClientGalleryAccessLimiter} (default 5
   * attempts / 15 min). Returns 429 once the limit is exceeded.
   *
   * @param slug Collection slug
   * @param passwordRequest Request body containing password
   * @param request Servlet request, used to read the client IP
   * @param response Servlet response, used to set the access cookie
   * @return ResponseEntity with {@code {hasAccess: true|false}}
   */
  @PostMapping("/{slug}/access")
  public ResponseEntity<Map<String, Object>> validateClientGalleryAccess(
      @PathVariable String slug,
      @Valid @RequestBody PasswordRequest passwordRequest,
      HttpServletRequest request,
      HttpServletResponse response) {

    String clientIp = resolveClientIp(request);
    if (!accessLimiter.allow(clientIp, slug)) {
      log.warn("Client gallery /access rate-limited for slug={} ip={}", slug, clientIp);
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
          .body(Map.of("hasAccess", false, "reason", "rate-limited"));
    }

    boolean hasAccess =
        clientGalleryAuthService.validateClientGalleryAccess(slug, passwordRequest.password());

    if (!hasAccess) {
      log.warn("Failed client gallery access attempt for slug={} ip={}", slug, clientIp);
      return ResponseEntity.ok(Map.of("hasAccess", false));
    }

    String token = clientGalleryAuthService.generateAccessToken(slug);
    ResponseCookie cookie =
        ResponseCookie.from(cookieName(slug), token)
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/")
            .maxAge(GALLERY_COOKIE_MAX_AGE)
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

    return ResponseEntity.ok(Map.of("hasAccess", true));
  }

  // =============================================================================
  // INTERNAL HELPERS
  // =============================================================================

  /**
   * Resolve the client IP. Trusts {@code X-Real-IP} when present (injected by the Next.js BFF proxy
   * in prod). Falls back directly to {@link HttpServletRequest#getRemoteAddr()}.
   *
   * <p>{@code X-Forwarded-For} is intentionally ignored: it is trivially spoofable when the backend
   * is reachable directly (bypassing the BFF), which would allow an attacker to rotate their
   * rate-limit identity per request. Only requests that flow through the known BFF proxy will carry
   * {@code X-Real-IP}, so its presence is the trust signal. If {@code X-Real-IP} is absent, the
   * request did not come through the proxy and {@code getRemoteAddr()} is the only reliable source
   * of truth.
   */
  private static String resolveClientIp(HttpServletRequest request) {
    String realIp = request.getHeader("X-Real-IP");
    if (realIp != null && !realIp.isBlank()) {
      return realIp.trim();
    }
    // X-Forwarded-For without X-Real-IP = direct request, not through proxy — ignore it
    return request.getRemoteAddr();
  }
}
