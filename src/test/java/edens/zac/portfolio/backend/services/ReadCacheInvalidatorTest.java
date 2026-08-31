package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CloudFrontException;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationRequest;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationResponse;
import software.amazon.awssdk.services.cloudfront.model.Invalidation;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReadCacheInvalidatorTest {

  @Mock private CloudFrontClient cloudFrontClient;
  @Mock private ApplicationEventPublisher eventPublisher;

  private ReadCacheInvalidator invalidatorWith(String distributionId) {
    return new ReadCacheInvalidator(cloudFrontClient, eventPublisher, distributionId);
  }

  private void stubSuccessfulInvalidation() {
    when(cloudFrontClient.createInvalidation(any(Consumer.class)))
        .thenReturn(
            CreateInvalidationResponse.builder()
                .invalidation(Invalidation.builder().id("I-TEST").build())
                .build());
  }

  /**
   * The point of the event indirection: an invalidation issued mid-transaction races the commit and
   * lets CloudFront re-cache pre-commit state.
   */
  @Test
  @DisplayName("markChanged only publishes: nothing reaches CloudFront before commit")
  void markChangedDoesNotCallCloudFrontDirectly() {
    ReadCacheInvalidator invalidator = invalidatorWith("E2SR03MLB2ZFMR");

    invalidator.markChanged();

    verify(cloudFrontClient, never()).createInvalidation(any(Consumer.class));
    verify(eventPublisher).publishEvent(any(ReadCacheInvalidator.ReadSurfaceChanged.class));
  }

  @Test
  @DisplayName("after commit, a configured distribution is invalidated")
  void invalidatesWhenDistributionConfigured() {
    stubSuccessfulInvalidation();
    ReadCacheInvalidator invalidator = invalidatorWith("E2SR03MLB2ZFMR");

    invalidator.onReadSurfaceChanged(new ReadCacheInvalidator.ReadSurfaceChanged());

    verify(cloudFrontClient).createInvalidation(any(Consumer.class));
  }

  @Test
  @DisplayName("an unset distribution id is a silent no-op, not a failure")
  void skipsWhenDistributionNotConfigured() {
    ReadCacheInvalidator invalidator = invalidatorWith("");

    invalidator.onReadSurfaceChanged(new ReadCacheInvalidator.ReadSurfaceChanged());

    verify(cloudFrontClient, never()).createInvalidation(any(Consumer.class));
  }

  @Test
  @DisplayName("a null distribution id is also a no-op")
  void skipsWhenDistributionIsNull() {
    ReadCacheInvalidator invalidator = invalidatorWith(null);

    invalidator.onReadSurfaceChanged(new ReadCacheInvalidator.ReadSurfaceChanged());

    verify(cloudFrontClient, never()).createInvalidation(any(Consumer.class));
  }

  /**
   * Failing an admin's save because the CDN was unreachable would be strictly worse than serving a
   * stale tile until the TTL expires on its own.
   */
  @Test
  @DisplayName("a CloudFront failure never propagates to the caller")
  void swallowsCloudFrontFailures() {
    when(cloudFrontClient.createInvalidation(any(Consumer.class)))
        .thenThrow(CloudFrontException.builder().message("throttled").build());
    ReadCacheInvalidator invalidator = invalidatorWith("E2SR03MLB2ZFMR");

    assertThatCode(
            () -> invalidator.onReadSurfaceChanged(new ReadCacheInvalidator.ReadSurfaceChanged()))
        .doesNotThrowAnyException();
  }

  /**
   * Replays the builder consumer to read back the paths actually requested. Every route in {@code
   * CacheControlInterceptor.PUBLIC_ROUTES} sits under one of these two prefixes.
   */
  @Test
  @DisplayName("the invalidation covers both cacheable read prefixes")
  void invalidationCoversTheWholeCacheableSurface() {
    stubSuccessfulInvalidation();
    ReadCacheInvalidator invalidator = invalidatorWith("E2SR03MLB2ZFMR");

    invalidator.onReadSurfaceChanged(new ReadCacheInvalidator.ReadSurfaceChanged());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Consumer<CreateInvalidationRequest.Builder>> captor =
        ArgumentCaptor.forClass(Consumer.class);
    verify(cloudFrontClient).createInvalidation(captor.capture());

    CreateInvalidationRequest.Builder builder = CreateInvalidationRequest.builder();
    captor.getValue().accept(builder);
    CreateInvalidationRequest request = builder.build();

    assertThat(request.invalidationBatch().paths().items())
        .containsExactlyInAnyOrder("/api/read/collections*", "/api/read/content*");
    assertThat(request.distributionId()).isEqualTo("E2SR03MLB2ZFMR");
  }

  /**
   * The guard on the whole delegation. Routing media deletes through {@code markChanged()} would
   * send the two read-surface wildcards instead, which match API routes and not media keys, so the
   * deleted bytes would keep being served from the edge until their own TTL expired. This test
   * fails if anyone makes that swap.
   */
  @Test
  @DisplayName("invalidatePaths sends the specific media keys, never the read-surface wildcards")
  void invalidatePathsSendsSpecificKeys() {
    stubSuccessfulInvalidation();
    ReadCacheInvalidator invalidator = invalidatorWith("E2SR03MLB2ZFMR");

    invalidator.invalidatePaths(
        List.of("Image/Web/2026/01/foo.webp", "Image/Original/2026/01/foo.jpg"));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Consumer<CreateInvalidationRequest.Builder>> captor =
        ArgumentCaptor.forClass(Consumer.class);
    verify(cloudFrontClient).createInvalidation(captor.capture());

    CreateInvalidationRequest.Builder builder = CreateInvalidationRequest.builder();
    captor.getValue().accept(builder);
    CreateInvalidationRequest request = builder.build();

    assertThat(request.invalidationBatch().paths().items())
        .containsExactly("/Image/Web/2026/01/foo.webp", "/Image/Original/2026/01/foo.jpg");
    assertThat(request.invalidationBatch().paths().quantity()).isEqualTo(2);
    assertThat(request.distributionId()).isEqualTo("E2SR03MLB2ZFMR");
  }

  @Test
  @DisplayName("invalidatePaths reaches CloudFront directly, without waiting for a commit")
  void invalidatePathsDoesNotGoThroughTheEventPath() {
    stubSuccessfulInvalidation();
    ReadCacheInvalidator invalidator = invalidatorWith("E2SR03MLB2ZFMR");

    invalidator.invalidatePaths(List.of("Image/Web/2026/01/foo.webp"));

    verify(cloudFrontClient).createInvalidation(any(Consumer.class));
    verify(eventPublisher, never()).publishEvent(any(Object.class));
  }

  @Test
  @DisplayName("invalidatePaths with no keys makes no CloudFront call at all")
  void invalidatePathsSkipsEmptyList() {
    ReadCacheInvalidator invalidator = invalidatorWith("E2SR03MLB2ZFMR");

    invalidator.invalidatePaths(List.of());

    verify(cloudFrontClient, never()).createInvalidation(any(Consumer.class));
  }

  @Test
  @DisplayName("invalidatePaths is a no-op when no distribution id is configured")
  void invalidatePathsSkipsWhenDistributionNotConfigured() {
    ReadCacheInvalidator invalidator = invalidatorWith("");

    invalidator.invalidatePaths(List.of("Image/Web/2026/01/foo.webp"));

    verify(cloudFrontClient, never()).createInvalidation(any(Consumer.class));
  }

  /** A delete that already succeeded in S3 must not fail because the CDN was unreachable. */
  @Test
  @DisplayName("invalidatePaths never propagates a CloudFront failure")
  void invalidatePathsSwallowsCloudFrontFailures() {
    when(cloudFrontClient.createInvalidation(any(Consumer.class)))
        .thenThrow(CloudFrontException.builder().message("throttled").build());
    ReadCacheInvalidator invalidator = invalidatorWith("E2SR03MLB2ZFMR");

    assertThatCode(() -> invalidator.invalidatePaths(List.of("Image/Web/2026/01/foo.webp")))
        .doesNotThrowAnyException();
  }
}
