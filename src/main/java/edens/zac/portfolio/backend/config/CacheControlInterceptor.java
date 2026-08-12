package edens.zac.portfolio.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Stamps {@code Cache-Control} on the public read surface ({@code /api/read/**}), which previously
 * carried no freshness headers at all. Without them every page open is an origin request running
 * SQL, and Next.js cannot heuristically cache a response that declares no freshness.
 *
 * <p><strong>Default-deny.</strong> Only the routes in {@link #PUBLIC_ROUTES} become shared-
 * cacheable; every other mapped read route, and every non-GET request, is stamped {@code no-store}.
 * A route added later is therefore uncacheable until someone deliberately allow-lists it, which is
 * the correct failure direction: the cost of a missed caching opportunity is a slow endpoint, while
 * the cost of an accidental {@code public} is a shared cache serving one visitor's response to
 * another.
 *
 * <p>Matching is on {@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE} — the bounded route
 * pattern resolved by Spring's handler mapping — rather than the raw URI. This mirrors {@link
 * RequestMetricInterceptor} and means the allow-list holds exact patterns that cannot be widened by
 * a crafted path. A {@link HandlerInterceptor} rather than a servlet {@code Filter} is required for
 * the same reason: the attribute only exists after handler mapping has run.
 *
 * <p><strong>Both slug-resolving collection routes are deliberately absent from the
 * allow-list</strong> ({@code /api/read/collections/{slug}} and {@code .../{slug}/meta}). Resolving
 * a collection by slug is viewer-dependent in three separate ways, and only the first is visible
 * from the response body:
 *
 * <ul>
 *   <li>A password-protected gallery's body varies on the {@code gallery_access_<slug>} cookie.
 *   <li>{@code enforceVisibility} hides HIDDEN collections from anonymous callers via {@code
 *       viewerMaySeeHidden}, so the same URL is a 404 for the public and a 200 with full content
 *       for an admin or a role-granted viewer.
 *   <li>The synthetic {@code all-collections} slug is permission-scoped by verified identity: an
 *       admin receives every visibility, a signed-in user receives their granted galleries, and an
 *       anonymous caller receives LISTED only. It is also built with {@code isPasswordProtected}
 *       unset, so a body-level check reads {@code null} and cannot detect the scoping.
 * </ul>
 *
 * <p>Marking either route {@code public} therefore lets a shared cache store a privileged viewer's
 * response and serve it to the public. They stay {@code no-store} until a resolved-entity check can
 * prove viewer-independence; a slower collection page is a fair trade for not leaking hidden work.
 *
 * <p>Headers are set in {@link #preHandle} so a controller returning an explicit {@code
 * ResponseEntity.cacheControl(..)} still wins: Spring writes entity headers over response headers
 * when the return value is rendered.
 */
public class CacheControlInterceptor implements HandlerInterceptor {

  /**
   * Read routes whose response body is identical for every caller and therefore safe for a shared
   * cache. Values are exact Spring route patterns, matched against the resolved best-matching
   * pattern.
   */
  static final Set<String> PUBLIC_ROUTES =
      Set.of(
          "/api/read/collections",
          "/api/read/collections/location/{slug}",
          "/api/read/content/tags",
          "/api/read/content/people",
          "/api/read/content/cameras",
          "/api/read/content/lenses",
          "/api/read/content/locations",
          "/api/read/content/film-metadata",
          "/api/read/content/images/search");

  private final String publicHeaderValue;
  private final String noStoreHeaderValue;

  CacheControlInterceptor(ReadCachePolicy policy) {
    this.publicHeaderValue = policy.publicRead().getHeaderValue();
    this.noStoreHeaderValue = policy.noStore().getHeaderValue();
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, resolveHeaderValue(request));
    return true;
  }

  private String resolveHeaderValue(HttpServletRequest request) {
    if (!HttpMethod.GET.matches(request.getMethod())) {
      return noStoreHeaderValue;
    }
    String route = bestMatchingPattern(request);
    return route != null && PUBLIC_ROUTES.contains(route) ? publicHeaderValue : noStoreHeaderValue;
  }

  private static String bestMatchingPattern(HttpServletRequest request) {
    Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    return pattern instanceof String s && !s.isBlank() ? s : null;
  }
}
