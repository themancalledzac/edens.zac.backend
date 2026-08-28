package edens.zac.portfolio.backend.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Rate limiter for {@code POST /api/read/user/share/email}: a per-sender token bucket bounded by a
 * Caffeine cache, plus a single global daily bucket.
 *
 * <p>The endpoint is authenticated, which is why it had no limiter -- {@link RateLimitFilter}
 * covers {@code /api/public/} only, and a signed-in user was treated as trusted. They are not
 * trusted with this one: each call is an SES send to an arbitrary address from {@code
 * no-reply@zacedens.com}, DKIM-signed by the real domain and carrying a genuine clean-reputation
 * link, with part of the subject line coming from the sender's own display name. Unbounded, that is
 * an open mail relay wearing this domain's reputation.
 *
 * <p>Keyed on the sender's user id and nothing else. Keying on (sender, recipient) would bound
 * repeat mail to one victim while leaving a blast across many addresses unbounded, which is the
 * shape that costs the domain its reputation.
 *
 * <p>The global daily bucket is here for the same reason {@link ContactMessageLimiter} has one:
 * <b>the damage is shared</b>. An SES suspension takes the invite email and the gallery-password
 * email down with it, so the per-sender limit alone -- which bounds each account but scales with
 * the number of accounts -- does not protect the resource actually at risk. Accounts here are
 * invite-only, so that scaling is slow rather than free, but the cap costs nothing and is the only
 * limit whose key a caller cannot pick.
 *
 * <p>Defaults to 5 sends per sender per hour and 200 globally per day, configurable via {@code
 * app.share.email-per-sender-per-hour} and {@code app.share.email-global-per-day}.
 */
@Component
@Slf4j
public class ShareEmailLimiter {

  private final int perSenderPerWindow;
  private final Duration window;
  private final Cache<Long, Bucket> buckets;
  private final Bucket globalDailyBucket;

  /**
   * {@code @Autowired} disambiguates the two constructors, as in {@link
   * ClientGalleryAccessLimiter}.
   */
  @Autowired
  public ShareEmailLimiter(
      @Value("${app.share.email-per-sender-per-hour:5}") int perSenderPerHour,
      @Value("${app.share.email-global-per-day:200}") int globalPerDay) {
    this(perSenderPerHour, Duration.ofHours(1), globalPerDay, Duration.ofDays(1));
  }

  /**
   * Test-only constructor taking arbitrary {@link Duration}s so refill-timing tests can use
   * sub-second windows instead of sleeping for an hour.
   */
  ShareEmailLimiter(
      int perSenderPerWindow, Duration window, int globalPerPeriod, Duration globalPeriod) {
    this.perSenderPerWindow = perSenderPerWindow;
    this.window = window;
    // Idle expiry longer than the window so a sender cannot reset their count by pausing for
    // exactly the refill interval. 10k entries caps memory, mirroring the other two limiters.
    this.buckets =
        Caffeine.newBuilder()
            .expireAfterAccess(window.plus(Duration.ofMinutes(15)))
            .maximumSize(10_000)
            .build();
    this.globalDailyBucket = newBucket(globalPerPeriod, globalPeriod);
  }

  /**
   * Attempt to consume one send for the given sender. Returns {@code true} if allowed, {@code
   * false} if either the global daily cap or the sender's own hourly limit is exhausted.
   *
   * <p>The global cap is checked first: once it is spent every caller is refused anyway, so
   * draining per-sender buckets on requests that cannot succeed would only leave legitimate senders
   * short when the day rolls over. {@link ContactMessageLimiter#tryConsume} orders it the same way.
   *
   * <p>A null sender consumes from the global bucket and is then allowed through. The route
   * requires a session so this cannot happen in practice, but an unkeyed request must not become
   * the way around the cap.
   */
  public boolean allow(Long userId) {
    if (!globalDailyBucket.tryConsume(1)) {
      log.warn("Global daily share-email cap reached; refusing further sends today");
      return false;
    }
    if (userId == null) {
      return true;
    }
    return buckets.get(userId, k -> newBucket(perSenderPerWindow, window)).tryConsume(1);
  }

  private static Bucket newBucket(int capacity, Duration refillInterval) {
    return Bucket.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, refillInterval)
                .build())
        .build();
  }
}
