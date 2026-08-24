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
 * rate-limit identity per request. Only requests that flow through the known BFF proxy will carry
 * {@code X-Real-IP}, so its presence is the trust signal. If {@code X-Real-IP} is absent, the
 * request did not come through the proxy and {@code getRemoteAddr()} is the only reliable source of
 * truth.
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
