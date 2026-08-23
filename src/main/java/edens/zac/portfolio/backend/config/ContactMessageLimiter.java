package edens.zac.portfolio.backend.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Rate limiter for the public contact endpoint: a per-email token bucket bounded by a Caffeine
 * cache (10k emails, 2h idle TTL), plus a single global daily bucket.
 *
 * <p>The global bucket is the growth cap on the messages table. Neither of the other two limits can
 * bound total inserts: the per-email key is chosen by the sender, so an attacker rotates it freely,
 * and the per-IP limit in {@link RateLimitFilter} allows 500/h per address across an unbounded
 * number of addresses. The global bucket is the only limit an attacker cannot pick the key for.
 *
 * <p>Enforced at the controller layer (after {@code @RequestBody} parsing) for the public messages
 * endpoint. Per-IP limiting is handled separately by {@link RateLimitFilter}.
 */
@Component
@Slf4j
public class ContactMessageLimiter {

  private final int perHour;
  private final Cache<String, Bucket> buckets =
      Caffeine.newBuilder().expireAfterAccess(Duration.ofHours(2)).maximumSize(10_000).build();

  private final Bucket globalDailyBucket;

  public ContactMessageLimiter(
      @Value("${app.contact.rate-limit-per-email-per-hour:5}") int perHour,
      @Value("${app.contact.rate-limit-global-per-day:1000}") int globalPerDay) {
    this.perHour = perHour;
    this.globalDailyBucket =
        Bucket.builder()
            .addLimit(
                Bandwidth.builder()
                    .capacity(globalPerDay)
                    .refillIntervally(globalPerDay, Duration.ofDays(1))
                    .build())
            .build();
  }

  /**
   * Attempt to consume a token for the given email. Returns {@code true} if allowed, {@code false}
   * if either the global daily cap or the per-email hourly limit has been exceeded.
   *
   * <p>The global cap is checked first. Once the table has taken its day's worth of inserts every
   * caller is refused anyway, so spending per-email tokens on requests that cannot succeed would
   * only leave those buckets drained when the day rolls over.
   *
   * <p>{@code null} or blank emails skip the per-email bucket but still consume from the global one
   * — request validation rejects them downstream, and an unkeyed request must not be a way to
   * bypass the growth cap.
   */
  public boolean tryConsume(String email) {
    if (!globalDailyBucket.tryConsume(1)) {
      log.warn("Global daily contact-message cap reached; refusing further submissions today");
      return false;
    }
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
