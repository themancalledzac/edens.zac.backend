package edens.zac.portfolio.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Servlet filter that enforces per-IP rate limiting on {@code /api/public/**} endpoints using the
 * Bucket4j token-bucket algorithm. All other paths are passed through without any rate-limit check.
 *
 * <p>The rate limit is configurable via {@code app.contact.rate-limit-per-hour}. The client IP is
 * resolved by {@link ClientIp}, which trusts {@code X-Real-IP} and otherwise falls back to {@code
 * remoteAddr}.
 *
 * <p>Buckets are stored in a Caffeine cache bounded at 10k entries with a 2-hour idle expiration so
 * the filter cannot be used to exhaust memory by spamming unique IPs.
 *
 * <p>The filter also caps request body size on the same paths. Bean Validation bounds the contact
 * payload to 5320 characters, but {@code @Valid} runs after Jackson has already materialised the
 * whole body, so without this check a caller could push Jackson's 20MB string default through the
 * parser on every one of their 500 hourly requests. The cap is enforced from {@code Content-Length}
 * and therefore does not cover chunked requests, which arrive without one; those still reach
 * Jackson, bounded only by the container's own post limit.
 */
@Component
@Order(2)
@Slf4j
public class RateLimitFilter implements Filter {

  /** Largest request body accepted on {@code /api/public/**}, in bytes. */
  static final int MAX_PUBLIC_BODY_BYTES = 16 * 1024;

  private final int rateLimitPerHour;
  private final ObjectMapper objectMapper;

  private final Cache<String, Bucket> ipBuckets =
      Caffeine.newBuilder().expireAfterAccess(Duration.ofHours(2)).maximumSize(10_000).build();

  /**
   * Tracks IPs that have already triggered a 429 WARN log within the current hour, so that a
   * sustained flood from one IP only logs once per hour at WARN level.
   */
  private final Cache<String, Boolean> recentlyLoggedIps =
      Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(1)).maximumSize(10_000).build();

  /**
   * Creates a {@code RateLimitFilter} with the configured hourly rate limit.
   *
   * @param rateLimitPerHour maximum requests per IP per hour; read from {@code
   *     app.contact.rate-limit-per-hour} (default 500)
   * @param objectMapper Jackson mapper used to serialise 429 error responses
   */
  public RateLimitFilter(
      @Value("${app.contact.rate-limit-per-hour:500}") int rateLimitPerHour,
      ObjectMapper objectMapper) {
    this.rateLimitPerHour = rateLimitPerHour;
    this.objectMapper = objectMapper;
  }

  /**
   * Applies the rate-limit and body-size checks for {@code /api/public/**} requests. Requests that
   * exceed the per-IP limit receive a {@code 429 Too Many Requests} response with a {@code
   * Retry-After} header, and requests declaring a body over {@link #MAX_PUBLIC_BODY_BYTES} receive
   * a {@code 413 Payload Too Large}; all other requests are forwarded to the next filter.
   *
   * <p>The rate limit is consumed before the size check so that oversized requests still count
   * against the sender's hourly budget rather than being rejected for free.
   *
   * @param req the incoming servlet request
   * @param res the outgoing servlet response
   * @param chain the remaining filter chain
   * @throws IOException if writing the 429 response body fails
   * @throws ServletException if the downstream filter chain throws
   */
  @Override
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    var request = (HttpServletRequest) req;
    var response = (HttpServletResponse) res;

    if (!request.getRequestURI().startsWith("/api/public/")) {
      chain.doFilter(req, res);
      return;
    }

    String ip = ClientIp.resolve(request);
    Bucket bucket = ipBuckets.get(ip, k -> newBucket(rateLimitPerHour));

    if (bucket.tryConsume(1)) {
      if (request.getContentLengthLong() > MAX_PUBLIC_BODY_BYTES) {
        log.warn(
            "Rejecting oversized public request from IP {}: {} bytes",
            ip,
            request.getContentLengthLong());
        writeError(
            response, 413, "Payload Too Large", "Request body exceeds the maximum accepted size.");
        return;
      }
      chain.doFilter(req, res);
    } else {
      logSampled429(ip);
      long retryAfterSeconds =
          bucket.estimateAbilityToConsume(1).getNanosToWaitForRefill() / 1_000_000_000L;
      response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
      writeError(
          response, 429, "Too Many Requests", "Rate limit exceeded. Please try again later.");
    }
  }

  private void writeError(HttpServletResponse response, int status, String error, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    var errorBody =
        new GlobalExceptionHandler.ErrorResponse(LocalDateTime.now(), status, error, message);
    response.getWriter().write(objectMapper.writeValueAsString(errorBody));
  }

  private void logSampled429(String ip) {
    if (recentlyLoggedIps.getIfPresent(ip) == null) {
      log.warn("Rate limit exceeded for IP: {}", ip);
      recentlyLoggedIps.put(ip, Boolean.TRUE);
    } else {
      log.debug("Rate limit exceeded (sampled, repeat) for IP: {}", ip);
    }
  }

  private Bucket newBucket(int perHour) {
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(perHour)
            .refillIntervally(perHour, Duration.ofHours(1))
            .build();
    return Bucket.builder().addLimit(limit).build();
  }
}
