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

  /**
   * Cookie-name prefix for the password-fingerprint shared-unlock cookie. Issued alongside the
   * per-slug cookie when a gallery is unlocked, so any other gallery whose password produces the
   * same fingerprint also passes the gate without re-prompting (e.g. PARENT + propagated children).
   */
  public static final String PASSWORD_COOKIE_PREFIX = "gallery_access_pw_";

  /**
   * Sanitizer for the fingerprint segment of {@link #PASSWORD_COOKIE_PREFIX} cookies. The
   * fingerprint is URL-safe base64 ({@code A-Za-z0-9_-}); any unexpected character is replaced with
   * '_' as defense in depth against malformed input.
   */
  private static final java.util.regex.Pattern UNSAFE_FINGERPRINT_CHARS =
      java.util.regex.Pattern.compile("[^A-Za-z0-9_-]");

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

  /**
   * Per-fingerprint cookie name for the shared-unlock path. The fingerprint comes from {@link
   * ClientGalleryAuthService#passwordFingerprint(String)} and is already URL-safe base64; the
   * sanitizer is a defense-in-depth no-op for valid input.
   */
  public static String passwordCookieName(String fingerprint) {
    if (fingerprint == null || fingerprint.isEmpty()) {
      return PASSWORD_COOKIE_PREFIX;
    }
    return PASSWORD_COOKIE_PREFIX + UNSAFE_FINGERPRINT_CHARS.matcher(fingerprint).replaceAll("_");
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

  /**
   * True iff the request carries either (a) a valid per-slug access cookie or (b) a valid
   * password-fingerprint cookie matching the gallery's current password. The fingerprint variant is
   * what makes a PARENT password unlock its propagated CLIENT_GALLERY children (and vice versa)
   * without re-prompting — they share the password, so they share the cookie.
   *
   * @param request Servlet request
   * @param slug Collection slug for the per-slug cookie lookup
   * @param password Current plaintext password on the gallery being read; null/blank treats the
   *     gallery as unprotected and short-circuits to {@code true}
   * @param auth Auth service for HMAC validation
   */
  public static boolean hasValidAccess(
      HttpServletRequest request, String slug, String password, ClientGalleryAuthService auth) {
    if (password == null || password.isEmpty()) {
      return true;
    }
    if (auth.validateAccessToken(slug, readCookie(request, cookieName(slug)))) {
      return true;
    }
    String fingerprint = auth.passwordFingerprint(password);
    if (fingerprint == null) {
      return false;
    }
    return auth.validatePasswordAccessToken(
        password, readCookie(request, passwordCookieName(fingerprint)));
  }
}
