package edens.zac.portfolio.backend.config;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the originating client IP for rate limiting and auth audit logging.
 *
 * <p>Trusts {@code X-Real-IP} when present (injected by the Next.js BFF proxy in prod). Falls back
 * directly to {@link HttpServletRequest#getRemoteAddr()}.
 *
 * <p>{@code X-Forwarded-For} is intentionally ignored: it is trivially spoofable when the backend
 * is reachable directly (bypassing the BFF), which would allow an attacker to rotate their
 * rate-limit identity per request. {@code X-Real-IP} is no less spoofable on its own, so its
 * presence is not a trust signal. What makes the value trustworthy is that the BFF strips any
 * client-supplied copy before re-injecting its own, and that {@link InternalSecretFilter} rejects
 * direct hits under the {@code prod} profile. If {@code X-Real-IP} is absent, {@code
 * getRemoteAddr()} is the only source left.
 */
public final class ClientIp {

  private ClientIp() {}

  /**
   * Returns the client IP this request should be attributed to.
   *
   * @param request the incoming servlet request
   * @return the trimmed {@code X-Real-IP} value, or {@code getRemoteAddr()} when that header is
   *     absent or blank
   */
  public static String resolve(HttpServletRequest request) {
    String realIp = request.getHeader("X-Real-IP");
    if (realIp != null && !realIp.isBlank()) {
      return realIp.trim();
    }
    return request.getRemoteAddr();
  }
}
