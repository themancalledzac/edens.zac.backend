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
 * <p>The rate limit is configurable via {@code app.contact.rate-limit-per-hour}. The filter reads
 * the client IP from {@code X-Real-IP} first, then the first hop of {@code X-Forwarded-For}, then
 * falls back to {@code remoteAddr}.
 *
 * <p>Buckets are stored in a Caffeine cache bounded at 10k entries with a 2-hour idle expiration so
 * the filter cannot be used to exhaust memory by spamming unique IPs.
 */
@Component
@Order(2)
@Slf4j
public class RateLimitFilter implements Filter {

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
   * Applies the rate-limit check for {@code /api/public/**} requests. Requests that exceed the
   * per-IP limit receive a {@code 429 Too Many Requests} response with a {@code Retry-After}
   * header; all other requests are forwarded to the next filter in the chain.
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

    String ip = resolveClientIp(request);
    Bucket bucket = ipBuckets.get(ip, k -> newBucket(rateLimitPerHour));

    if (bucket.tryConsume(1)) {
      chain.doFilter(req, res);
    } else {
      logSampled429(ip);
      response.setStatus(429);
      response.setContentType("application/json");
      long retryAfterSeconds =
          bucket.estimateAbilityToConsume(1).getNanosToWaitForRefill() / 1_000_000_000L;
      response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
      var errorBody =
          new GlobalExceptionHandler.ErrorResponse(
              LocalDateTime.now(),
              429,
              "Too Many Requests",
              "Rate limit exceeded. Please try again later.");
      response.getWriter().write(objectMapper.writeValueAsString(errorBody));
    }
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

  private String resolveClientIp(HttpServletRequest request) {
    String real = request.getHeader("X-Real-IP");
    if (real != null && !real.isBlank()) {
      return real.trim();
    }
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
