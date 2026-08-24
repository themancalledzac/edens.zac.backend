package edens.zac.portfolio.backend.services;

import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;

/**
 * Drops the CDN's copy of the public read surface after an admin write changes it.
 *
 * <p>Responses on the cacheable read routes carry {@code s-maxage=300}, so without this a title
 * change stays invisible on the home page and collection list for up to five minutes (ten counting
 * {@code stale-while-revalidate}). The frontend cannot fix that on its own: {@code revalidateTag}
 * clears Next's data cache but has no reach into CloudFront, so the refetch it triggers is served
 * the stale edge copy.
 *
 * <p><strong>Fires after commit, never inside the transaction.</strong> An invalidation issued
 * mid-transaction races the commit: CloudFront can refetch, read pre-commit state, and cache the
 * very staleness the invalidation was meant to clear. {@link TransactionalEventListener} with
 * {@link TransactionPhase#AFTER_COMMIT} closes that window, and it also means a rolled-back write
 * issues no invalidation at all. {@code fallbackExecution} is on so callers outside a transaction
 * still work.
 *
 * <p>Two wildcards cover the entire cacheable surface, which is why this does not take per-slug
 * paths. {@code CacheControlInterceptor}'s allow-list is nine routes split across exactly these two
 * prefixes, and every one of them can be affected by a metadata or collection write. Invalidating
 * broadly costs two paths per call against CloudFront's 1000-free-paths-per-month allowance;
 * computing a minimal path set would cost more in complexity and in missed-invalidation bugs than
 * it saves. Note that {@code /api/read/collections/{slug}} is deliberately never cached, so the
 * collection detail page is always fresh and is not what this is for.
 *
 * <p>Best-effort by design: a CDN invalidation failure is logged, never propagated. Failing an
 * admin's save because the CDN was unreachable would be strictly worse than serving a stale tile
 * until the TTL expires on its own.
 */
@Slf4j
@Service
public class ReadCacheInvalidator {

  /** Wildcards covering every route in {@code CacheControlInterceptor.PUBLIC_ROUTES}. */
  private static final List<String> READ_SURFACE_PATHS =
      List.of("/api/read/collections*", "/api/read/content*");

  private final CloudFrontClient cloudFrontClient;
  private final ApplicationEventPublisher eventPublisher;
  private final String distributionId;

  ReadCacheInvalidator(
      CloudFrontClient cloudFrontClient,
      ApplicationEventPublisher eventPublisher,
      @Value("${cloudfront.distribution-id:}") String distributionId) {
    this.cloudFrontClient = cloudFrontClient;
    this.eventPublisher = eventPublisher;
    this.distributionId = distributionId;
  }

  /** Signals that an admin write changed data served by the cacheable read routes. */
  public record ReadSurfaceChanged() {}

  /**
   * Record that the public read surface changed. Safe to call more than once per transaction and
   * safe to call from inside one: nothing reaches CloudFront until the transaction commits.
   */
  public void markChanged() {
    eventPublisher.publishEvent(new ReadSurfaceChanged());
  }

  /**
   * Issue the invalidation once the write is durable.
   *
   * <p>An unset {@code cloudfront.distribution-id} is expected until the CloudFront API origin is
   * enabled; the CDN simply keeps serving its own TTL out. That skip logs at debug rather than warn
   * so it is not noise in every dev run.
   *
   * @param event the marker published by {@link #markChanged()}
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onReadSurfaceChanged(ReadSurfaceChanged event) {
    if (distributionId == null || distributionId.isBlank()) {
      log.debug("Skipping read-surface invalidation: cloudfront.distribution-id is not configured");
      return;
    }
    try {
      var response =
          cloudFrontClient.createInvalidation(
              req ->
                  req.distributionId(distributionId)
                      .invalidationBatch(
                          batch ->
                              batch
                                  .paths(
                                      p ->
                                          p.quantity(READ_SURFACE_PATHS.size())
                                              .items(READ_SURFACE_PATHS))
                                  .callerReference(UUID.randomUUID().toString())));
      log.info(
          "Created CloudFront read-surface invalidation {} for {}",
          response.invalidation().id(),
          READ_SURFACE_PATHS);
    } catch (Exception e) {
      log.error("Failed to invalidate CloudFront read surface: {}", e.getMessage());
    }
  }
}
