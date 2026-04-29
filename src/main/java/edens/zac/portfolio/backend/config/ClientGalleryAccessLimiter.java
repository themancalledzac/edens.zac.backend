package edens.zac.portfolio.backend.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Per-(IP, slug) token-bucket rate limiter for the client-gallery <code>/access</code> endpoint,
 * bounded by a Caffeine cache (10k entries, 30-minute idle TTL).
 *
 * <p>Mirrors {@link ContactMessageLimiter} in shape: Caffeine-backed Bucket4j buckets with bounded
 * memory. Defaults to 5 attempts per 15 minutes, configurable via {@code
 * app.client-gallery.access-attempts-per-window} and {@code
 * app.client-gallery.access-window-minutes}.
 *
 * <p>The cache key is {@code "<ip>|<slug>"} so a brute-force attempt on one slug from one IP does
 * not impact other slugs or other IPs.
 */
@Component
public class ClientGalleryAccessLimiter {

  private final int attemptsPerWindow;
  private final Duration window;
  private final Cache<String, Bucket> buckets;

  @Autowired
  public ClientGalleryAccessLimiter(
      @Value("${app.client-gallery.access-attempts-per-window:5}") int attemptsPerWindow,
      @Value("${app.client-gallery.access-window-minutes:15}") int windowMinutes) {
    this(attemptsPerWindow, Duration.ofMinutes(windowMinutes));
  }

  /**
   * Test-only constructor that accepts an arbitrary {@link Duration} so refill-timing tests can use
   * sub-second windows without sleeping for minutes.
   */
  ClientGalleryAccessLimiter(int attemptsPerWindow, Duration window) {
    this.attemptsPerWindow = attemptsPerWindow;
    this.window = window;
    // Idle expiry slightly larger than the window so an attacker cannot reset their count by
    // pausing for exactly the refill interval. 10k entries caps memory.
    this.buckets =
        Caffeine.newBuilder()
            .expireAfterAccess(window.plus(Duration.ofMinutes(15)))
            .maximumSize(10_000)
            .build();
  }

  /**
   * Attempt to consume one token for the given (ip, slug) pair. Returns {@code true} if allowed,
   * {@code false} if the rate limit has been exceeded.
   *
   * <p>{@code null} or blank ip/slug pass through (returns {@code true}); upstream validation will
   * reject them — the limiter should not pretend to gate requests with no usable key.
   *
   * @param ip the client IP, e.g. resolved from {@code X-Real-IP}
   * @param slug the gallery slug being attacked
   */
  public boolean allow(String ip, String slug) {
    if (ip == null || ip.isBlank() || slug == null || slug.isBlank()) {
      return true;
    }
    String key = ip.trim() + "|" + GalleryAccessCookies.normalizeSlug(slug);
    Bucket bucket = buckets.get(key, k -> newBucket());
    return bucket.tryConsume(1);
  }

  private Bucket newBucket() {
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(attemptsPerWindow)
            .refillIntervally(attemptsPerWindow, window)
            .build();
    return Bucket.builder().addLimit(limit).build();
  }
}
