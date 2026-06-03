package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.LocationRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.model.DownloadResolution;
import edens.zac.portfolio.backend.services.validator.ContentImageUpdateValidator;
import edens.zac.portfolio.backend.services.validator.ContentValidator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for download-resolution logic that lives on {@link ContentService}: format validation,
 * imageUrlOriginal/imageUrlWeb selection, MIME/extension decisions, and the per-image fallback
 * applied to the collection ZIP. Controller-level concerns (auth, S3 streaming, exception-to-status
 * mapping) are tested separately in {@code ContentDownloadControllerProdTest}.
 */
@ExtendWith(MockitoExtension.class)
class ContentServiceDownloadTest {

  private static final String CLOUDFRONT_DOMAIN = "cdn.example.com";

  @Mock private TagRepository tagRepository;
  @Mock private ContentRepository contentRepository;
  @Mock private CollectionRepository collectionRepository;
  @Mock private PersonRepository personRepository;
  @Mock private LocationRepository locationRepository;
  @Mock private ContentMutationUtil contentMutationUtil;
  @Mock private ContentModelConverter contentModelConverter;
  @Mock private ImageProcessingService imageProcessingService;
  @Mock private ContentImageUpdateValidator contentImageUpdateValidator;
  @Mock private ContentValidator contentValidator;
  @Mock private MetadataService metadataService;

  @InjectMocks private ContentService service;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(service, "cloudfrontDomain", CLOUDFRONT_DOMAIN);
  }

  // ---------------------------------------------------------------------------
  //  Helpers
  // ---------------------------------------------------------------------------

  private static ContentImageEntity image(Long id, String filename, String webSuffix) {
    return ContentImageEntity.builder()
        .id(id)
        .imageUrlWeb("https://" + CLOUDFRONT_DOMAIN + "/Image/Web/2025/01/" + webSuffix)
        .originalFilename(filename)
        .build();
  }

  private static ContentImageEntity imageWithOriginal(
      Long id, String filename, String webSuffix, String origSuffix) {
    return ContentImageEntity.builder()
        .id(id)
        .imageUrlWeb("https://" + CLOUDFRONT_DOMAIN + "/Image/Web/2025/01/" + webSuffix)
        .imageUrlOriginal("https://" + CLOUDFRONT_DOMAIN + "/Image/Original/2025/01/" + origSuffix)
        .originalFilename(filename)
        .build();
  }

  // ---------------------------------------------------------------------------
  //  resolveImageDownload
  // ---------------------------------------------------------------------------

  @Nested
  class ResolveImageDownload {

    @Test
    void web_returnsWebpResolution() {
      ContentImageEntity img = image(10L, "smith-001.jpg", "smith-001.webp");
      when(contentRepository.findImageById(10L)).thenReturn(Optional.of(img));

      DownloadResolution res = service.resolveImageDownload(10L, "web");

      assertThat(res.s3Key()).isEqualTo("Image/Web/2025/01/smith-001.webp");
      assertThat(res.extension()).isEqualTo(".webp");
      assertThat(res.contentType()).isEqualTo("image/webp");
      assertThat(res.filename()).endsWith(".webp");
    }

    @Test
    void original_withOriginalUrl_returnsJpegResolution() {
      ContentImageEntity img =
          imageWithOriginal(10L, "smith-001.jpg", "smith-001.webp", "smith-001.jpg");
      when(contentRepository.findImageById(10L)).thenReturn(Optional.of(img));

      DownloadResolution res = service.resolveImageDownload(10L, "original");

      assertThat(res.s3Key()).isEqualTo("Image/Original/2025/01/smith-001.jpg");
      assertThat(res.extension()).isEqualTo(".jpg");
      assertThat(res.contentType()).isEqualTo("image/jpeg");
      assertThat(res.filename()).endsWith(".jpg");
    }

    @Test
    void original_caseInsensitive_acceptsORIGINAL() {
      ContentImageEntity img =
          imageWithOriginal(10L, "smith-001.jpg", "smith-001.webp", "smith-001.jpg");
      when(contentRepository.findImageById(10L)).thenReturn(Optional.of(img));

      DownloadResolution res = service.resolveImageDownload(10L, "ORIGINAL");

      assertThat(res.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void original_noOriginalUrl_throwsResourceNotFound() {
      ContentImageEntity img = image(10L, "smith-001.jpg", "smith-001.webp");
      when(contentRepository.findImageById(10L)).thenReturn(Optional.of(img));

      assertThatThrownBy(() -> service.resolveImageDownload(10L, "original"))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("No original available");
    }

    @Test
    void unsupportedFormat_throwsIllegalArgument() {
      assertThatThrownBy(() -> service.resolveImageDownload(10L, "raw"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unsupported download format");
    }

    @Test
    void web_nullImageUrlWeb_throwsNotFound() {
      // Defensive: the entity's @NotNull on imageUrlWeb fires only on write, so a legacy
      // row could have a null value. The service must not NPE; it should surface this as 404.
      ContentImageEntity img =
          ContentImageEntity.builder().id(10L).originalFilename("x.jpg").build();
      when(contentRepository.findImageById(10L)).thenReturn(Optional.of(img));

      assertThatThrownBy(() -> service.resolveImageDownload(10L, "web"))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("no resolvable S3 key");
    }

    @Test
    void webUrlMatchesNonConfiguredDomain_throwsNotFound() {
      // Image exists but its imageUrlWeb points at a different CloudFront domain — the
      // extractor returns null and the service surfaces this as 404 rather than streaming
      // a bogus S3 key.
      ContentImageEntity img =
          ContentImageEntity.builder()
              .id(10L)
              .imageUrlWeb("https://other-cdn.example.com/Image/Web/2025/01/x.webp")
              .originalFilename("x.jpg")
              .build();
      when(contentRepository.findImageById(10L)).thenReturn(Optional.of(img));

      assertThatThrownBy(() -> service.resolveImageDownload(10L, "web"))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("no resolvable S3 key");
    }
  }

  // ---------------------------------------------------------------------------
  //  resolveCollectionDownloadEntries
  // ---------------------------------------------------------------------------

  @Nested
  class ResolveCollectionDownloadEntries {

    private void stubCollectionImages(Long collectionId, List<ContentImageEntity> images) {
      List<CollectionContentEntity> joinEntries =
          images.stream()
              .map(
                  img ->
                      CollectionContentEntity.builder()
                          .collectionId(collectionId)
                          .contentId(img.getId())
                          .build())
              .toList();
      when(collectionRepository.findContentByCollectionIdOrderByOrderIndex(collectionId))
          .thenReturn(joinEntries);
      if (!images.isEmpty()) {
        when(contentRepository.findImagesByIds(
                images.stream().map(ContentImageEntity::getId).toList()))
            .thenReturn(images);
      }
    }

    @Test
    void web_returnsAllWebpEntries() {
      stubCollectionImages(
          1L,
          List.of(image(10L, "first.jpg", "first.webp"), image(11L, "second.jpg", "second.webp")));

      List<DownloadResolution> entries = service.resolveCollectionDownloadEntries(1L, "web");

      assertThat(entries).hasSize(2);
      assertThat(entries).allMatch(e -> e.extension().equals(".webp"));
      assertThat(entries).allMatch(e -> e.contentType().equals("image/webp"));
    }

    @Test
    void original_allHaveOriginals_returnsAllJpgEntries() {
      stubCollectionImages(
          1L,
          List.of(
              imageWithOriginal(10L, "first.jpg", "first.webp", "first.jpg"),
              imageWithOriginal(11L, "second.jpg", "second.webp", "second.jpg")));

      List<DownloadResolution> entries = service.resolveCollectionDownloadEntries(1L, "original");

      assertThat(entries).hasSize(2);
      assertThat(entries).allMatch(e -> e.extension().equals(".jpg"));
      assertThat(entries).allMatch(e -> e.contentType().equals("image/jpeg"));
    }

    @Test
    void original_someMissingOriginal_fallsBackToWebPerImage() {
      stubCollectionImages(
          1L,
          List.of(
              imageWithOriginal(10L, "first.jpg", "first.webp", "first.jpg"),
              image(11L, "second.jpg", "second.webp"))); // no imageUrlOriginal

      List<DownloadResolution> entries = service.resolveCollectionDownloadEntries(1L, "original");

      assertThat(entries).hasSize(2);
      assertThat(entries.get(0).extension()).isEqualTo(".jpg");
      assertThat(entries.get(0).contentType()).isEqualTo("image/jpeg");
      assertThat(entries.get(1).extension()).isEqualTo(".webp");
      assertThat(entries.get(1).contentType()).isEqualTo("image/webp");
    }

    @Test
    void emptyCollection_returnsEmptyList() {
      when(collectionRepository.findContentByCollectionIdOrderByOrderIndex(1L))
          .thenReturn(List.of());

      List<DownloadResolution> entries = service.resolveCollectionDownloadEntries(1L, "web");

      assertThat(entries).isEmpty();
    }

    @Test
    void unsupportedFormat_throwsIllegalArgument() {
      assertThatThrownBy(() -> service.resolveCollectionDownloadEntries(1L, "raw"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unsupported download format");
    }

    @Test
    void imageIdsSubset_returnsOnlyRequestedInCollectionOrder() {
      stubCollectionImages(
          1L,
          List.of(
              image(1L, "first.jpg", "first.webp"),
              image(2L, "second.jpg", "second.webp"),
              image(3L, "third.jpg", "third.webp")));

      List<DownloadResolution> entries =
          service.resolveCollectionDownloadEntries(1L, "web", List.of(1L, 3L));

      // Exactly ids 1 and 3, in collection (order_index) order — not request order.
      assertThat(entries).hasSize(2);
      assertThat(entries.get(0).s3Key()).contains("first.webp");
      assertThat(entries.get(1).s3Key()).contains("third.webp");
    }

    @Test
    void imageIdsNull_returnsFullSet() {
      stubCollectionImages(
          1L,
          List.of(image(1L, "first.jpg", "first.webp"), image(2L, "second.jpg", "second.webp")));

      List<DownloadResolution> entries = service.resolveCollectionDownloadEntries(1L, "web", null);

      assertThat(entries).hasSize(2);
    }

    @Test
    void imageIdsEmpty_returnsFullSet() {
      stubCollectionImages(
          1L,
          List.of(image(1L, "first.jpg", "first.webp"), image(2L, "second.jpg", "second.webp")));

      List<DownloadResolution> entries =
          service.resolveCollectionDownloadEntries(1L, "web", List.of());

      assertThat(entries).hasSize(2);
    }

    @Test
    void imageIdsNotInCollection_areDroppedNotLeaked() {
      stubCollectionImages(
          1L,
          List.of(image(1L, "first.jpg", "first.webp"), image(2L, "second.jpg", "second.webp")));

      // id 99 does not belong to this collection: silently dropped, no throw, never leaked.
      List<DownloadResolution> entries =
          service.resolveCollectionDownloadEntries(1L, "web", List.of(1L, 99L));

      assertThat(entries).hasSize(1);
      assertThat(entries.get(0).s3Key()).contains("first.webp");
    }
  }
}
