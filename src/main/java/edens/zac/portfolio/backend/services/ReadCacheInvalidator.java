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
 * <p>Two wildcards cover the entire cacheable surface, which is why {@link #markChanged()} takes no
 * paths. {@code CacheControlInterceptor}'s allow-list is nine routes split across exactly these two
 * prefixes, and every one of them can be affected by a metadata or collection write. Invalidating
 * broadly costs two paths per call against CloudFront's 1000-free-paths-per-month allowance;
 * computing a minimal path set would cost more in complexity and in missed-invalidation bugs than
 * it saves. Note that {@code /api/read/collections/{slug}} is deliberately never cached, so the
 * collection detail page is always fresh and is not what this is for.
 *
 * <p>{@link #invalidatePaths(List)} is the other half and behaves differently on purpose. Media
 * deletes need the specific S3 object keys dropped from the edge, and the two route wildcards above
 * do not match media keys at all. It therefore runs synchronously and takes explicit paths. The two
 * entry points share only the client and the distribution id; do not route one through the other.
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
   * Drop specific objects from the edge, synchronously and outside the event path.
   *
   * <p>For media deletes, where the caller knows the exact S3 keys it removed and the read-surface
   * wildcards would not match them. Each key is prefixed with {@code /} and sent as one
   * invalidation. Best-effort like the rest of this class: failures are logged, never propagated,
   * because a delete that succeeded in S3 must not fail because the CDN was unreachable.
   *
   * @param s3Keys unprefixed S3 object keys, for example {@code Image/Web/2026/01/foo.webp}
   */
  public void invalidatePaths(List<String> s3Keys) {
    if (s3Keys.isEmpty()) {
      return;
    }
    if (distributionId == null || distributionId.isBlank()) {
      log.debug("Skipping CloudFront invalidation: cloudfront.distribution-id is not configured");
      return;
    }
    try {
      List<String> paths = s3Keys.stream().map(k -> "/" + k).toList();
      var response =
          cloudFrontClient.createInvalidation(
              req ->
                  req.distributionId(distributionId)
                      .invalidationBatch(
                          batch ->
                              batch
                                  .paths(p -> p.quantity(paths.size()).items(paths))
                                  .callerReference(UUID.randomUUID().toString())));
      log.info(
          "Created CloudFront invalidation {} for {} path(s)",
          response.invalidation().id(),
          paths.size());
    } catch (Exception e) {
      log.error("Failed to invalidate CloudFront paths {}: {}", s3Keys, e.getMessage());
    }
  }

  /**
   * Issue the invalidation once the write is durable. An unset {@code cloudfront.distribution-id}
   * is expected until the CloudFront API origin is enabled -- the CDN keeps serving its own TTL --
   * so that skip logs at debug rather than warn.
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
