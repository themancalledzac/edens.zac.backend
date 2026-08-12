package edens.zac.portfolio.backend.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for the HTTP freshness policy applied to the public read surface ({@code
 * /api/read/**}).
 *
 * <p>Two consumers share it so the TTL is never defined twice: {@link CacheControlInterceptor},
 * which stamps the default on every mapped read route, and {@code CollectionControllerProd}, which
 * overrides per-response for the one route whose cacheability depends on the entity it just loaded.
 *
 * <p>{@code max-age} governs private (browser) caches; {@code s-maxage} governs shared caches
 * (CloudFront). The shared value is deliberately the longer of the two: a CDN edge absorbing repeat
 * traffic is the whole point, while a browser re-validating sooner keeps a single user's view
 * closer to live. {@code stale-while-revalidate} lets an edge serve the expired copy while it
 * refreshes in the background, so origin latency never lands on a visitor.
 *
 * <p>Setting {@code app.cache.read-max-age-seconds} to {@code 0} collapses the whole policy to
 * {@code no-store}. The dev profile does exactly that: a 60-second stale read is actively confusing
 * when you have just uploaded an image and are reloading to see it.
 */
@Component
public class ReadCachePolicy {

  private final CacheControl publicRead;

  public ReadCachePolicy(
      @Value("${app.cache.read-max-age-seconds:60}") long maxAgeSeconds,
      @Value("${app.cache.read-shared-max-age-seconds:300}") long sharedMaxAgeSeconds) {
    this.publicRead =
        maxAgeSeconds <= 0
            ? CacheControl.noStore()
            : CacheControl.maxAge(Duration.ofSeconds(maxAgeSeconds))
                .cachePublic()
                .sMaxAge(Duration.ofSeconds(sharedMaxAgeSeconds))
                .staleWhileRevalidate(Duration.ofSeconds(sharedMaxAgeSeconds));
  }

  /**
   * Freshness for anonymous reads whose body is identical for every caller. Degrades to {@code
   * no-store} when caching is disabled by configuration.
   *
   * @return the shared-cacheable policy
   */
  public CacheControl publicRead() {
    return publicRead;
  }

  /**
   * Freshness for anything user-, session-, or cookie-specific. Never stored by any cache, shared
   * or private.
   *
   * @return the no-store policy
   */
  public CacheControl noStore() {
    return CacheControl.noStore();
  }
}
