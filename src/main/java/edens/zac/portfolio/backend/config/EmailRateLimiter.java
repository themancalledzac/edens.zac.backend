package edens.zac.portfolio.backend.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Per-email token-bucket rate limiter, bounded by a Caffeine cache (10k emails, 2h idle TTL).
 *
 * <p>Enforced at the controller layer (after {@code @RequestBody} parsing) for the public messages
 * endpoint. Per-IP limiting is handled separately by {@link RateLimitFilter}.
 */
@Component
public class EmailRateLimiter {

  private final int perHour;
  private final Cache<String, Bucket> buckets =
      Caffeine.newBuilder().expireAfterAccess(Duration.ofHours(2)).maximumSize(10_000).build();

  public EmailRateLimiter(@Value("${app.contact.rate-limit-per-email-per-hour:5}") int perHour) {
    this.perHour = perHour;
  }

  /**
   * Attempt to consume a token for the given email. Returns {@code true} if allowed, {@code false}
   * if the rate limit has been exceeded.
   *
   * <p>{@code null} or blank emails pass through (returns {@code true}) — request validation will
   * reject them upstream/downstream.
   */
  public boolean tryConsume(String email) {
    if (email == null || email.isBlank()) {
      return true;
    }
    String key = email.trim().toLowerCase(java.util.Locale.ROOT);
    Bucket bucket =
        buckets.get(
            key,
            k ->
                Bucket.builder()
                    .addLimit(
                        Bandwidth.builder()
                            .capacity(perHour)
                            .refillIntervally(perHour, Duration.ofHours(1))
                            .build())
                    .build());
    return bucket.tryConsume(1);
  }
}
