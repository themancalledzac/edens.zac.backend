package edens.zac.portfolio.backend.config;

import edens.zac.portfolio.backend.services.ClientGalleryAuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Locale;

/**
 * Single source of truth for the per-gallery access cookie. The cookie name embeds the slug, which
 * cannot be matched by Spring's {@code @CookieValue} — hence these helpers. Every consumer that
 * touches the slug-as-key (controllers, rate limiter) must go through {@link #normalizeSlug} so
 * cookie identity and rate-limit identity stay in sync (the prior duplicated implementations
 * preserved case in the cookie name but lowercased it in the limiter, partially defeating
 * brute-force protection on mixed-case slugs).
 */
public final class GalleryAccessCookies {

  public static final String COOKIE_PREFIX = "gallery_access_";

  private GalleryAccessCookies() {}

  /**
   * Sanitizes a slug for use as a cookie name component. Replaces any character outside
   * [a-zA-Z0-9_-] with '_'.
   *
   * <p>In practice, slugs in the database are constrained to [a-z0-9-], so this method is a no-op
   * for all valid slugs. The substitution is defense-in-depth against future slug schema drift that
   * might introduce characters illegal in cookie names.
   *
   * <p>Note: two hypothetical slugs that differ only by case or underscore vs hyphen (e.g., {@code
   * my-gallery} and {@code my_gallery}) would normalize to the same value, sharing a rate-limit
   * bucket and cookie. This cannot happen today due to DB constraints.
   */
  public static String normalizeSlug(String slug) {
    if (slug == null) {
      return "";
    }
    return slug.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-zA-Z0-9_-]", "_");
  }

  /** Per-slug cookie name, RFC 6265-safe. */
  public static String cookieName(String slug) {
    return COOKIE_PREFIX + normalizeSlug(slug);
  }

  /** Read a single cookie value by name; {@code null} if absent. */
  public static String readCookie(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    return Arrays.stream(cookies)
        .filter(c -> name.equals(c.getName()))
        .map(Cookie::getValue)
        .findFirst()
        .orElse(null);
  }

  /**
   * True iff the request carries a valid per-slug access cookie. Combines {@link #cookieName},
   * {@link #readCookie}, and {@link ClientGalleryAuthService#validateAccessToken} so callers don't
   * re-derive the trio at every gate.
   */
  public static boolean hasValidAccess(
      HttpServletRequest request, String slug, ClientGalleryAuthService auth) {
    return auth.validateAccessToken(slug, readCookie(request, cookieName(slug)));
  }
}
