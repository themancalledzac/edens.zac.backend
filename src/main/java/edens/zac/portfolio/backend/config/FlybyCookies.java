package edens.zac.portfolio.backend.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.http.ResponseCookie;

/**
 * The share-link cookie. Its value is the same raw token that appears in the shared URL -- one
 * secret with two carriers. The URL is the durable one; this cookie only spares the recipient from
 * needing the token in every subsequent request while they browse.
 *
 * <p>Because the two carriers share a secret, rotating {@code share_link.token_hash} invalidates
 * the sent URL and every live cookie in a single write. That is the whole of "reset link".
 */
public final class FlybyCookies {

  public static final String COOKIE_NAME = "ezac_flyby";

  /**
   * Rolling window, refreshed whenever the recipient re-enters the shared view. Expiry is a
   * non-event by design: the link itself never expires, so a recipient whose cookie lapsed simply
   * clicks the original link again.
   */
  public static final Duration MAX_AGE = Duration.ofDays(30);

  private FlybyCookies() {}

  /**
   * Build the cookie issued when a share token is exchanged.
   *
   * <p>{@code SameSite=Lax} is load-bearing and deliberately differs from the {@code Strict} used
   * by the gallery-access cookies. A shared link is nearly always opened from a text message or an
   * email, which is a cross-site navigation; {@code Strict} withholds the cookie on exactly that
   * request. The first hit would still succeed because the token is in the URL, but any later deep
   * link would silently degrade to the anonymous view -- the "why can't I get back in" failure this
   * feature exists to prevent.
   *
   * <p>This does not weaken the CSRF posture {@link SecurityConfig} describes. {@code Lax} is
   * withheld on cross-site POST, and beyond that the cookie authorizes reads only: a flyby
   * principal is refused by every write endpoint, so there is no state-changing request to forge.
   *
   * @param rawToken the share's raw token -- never its stored hash
   * @param secure whether to mark the cookie Secure (false only for plain-HTTP local dev)
   */
  public static ResponseCookie build(String rawToken, boolean secure) {
    return ResponseCookie.from(COOKIE_NAME, rawToken)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Lax")
        .path("/")
        .maxAge(MAX_AGE)
        .build();
  }

  /** Build the cookie that clears an existing share session. */
  public static ResponseCookie clear(boolean secure) {
    return ResponseCookie.from(COOKIE_NAME, "")
        .httpOnly(true)
        .secure(secure)
        .sameSite("Lax")
        .path("/")
        .maxAge(0)
        .build();
  }

  /** Read the raw share token from the request; {@code null} when absent. */
  public static String read(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    return Arrays.stream(cookies)
        .filter(c -> COOKIE_NAME.equals(c.getName()))
        .map(Cookie::getValue)
        .findFirst()
        .orElse(null);
  }
}
