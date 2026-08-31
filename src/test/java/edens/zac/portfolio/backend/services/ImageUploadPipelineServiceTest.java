package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.DiskUploadRequest;
import edens.zac.portfolio.backend.model.ImageUploadResult;
import edens.zac.portfolio.backend.services.validator.ContentValidator;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class ImageUploadPipelineServiceTest {

  @Mock private CollectionRepository collectionRepository;
  @Mock private PersonRepository personRepository;
  @Mock private ImageProcessingService imageProcessingService;
  @Mock private ContentMutationUtil contentMutationUtil;
  @Mock private ContentModelConverter contentModelConverter;
  @Mock private ContentValidator contentValidator;
  @Mock private CollectionService collectionService;
  @Mock private JobTrackingService jobTrackingService;
  @Mock private CacheManager cacheManager;
  @Mock private ContentService contentService;
  @Mock private TransactionTemplate transactionTemplate;

  @InjectMocks private ImageUploadPipelineService service;

  private CollectionEntity testCollection;

  @BeforeEach
  void setUp() {
    testCollection =
        CollectionEntity.builder()
            .id(1L)
            .title("Test Collection")
            .slug("test-collection")
            .visibility(CollectionVisibility.LISTED)
            .build();
  }

  private ContentModels.Image createImageModel(Long id, Integer rating) {
    return new ContentModels.Image(
        id,
        null,
        "Image " + id,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        rating,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private MockMultipartFile createMockFile(String filename) {
    return new MockMultipartFile("files", filename, "image/jpeg", new byte[] {1, 2, 3, 4});
  }

  @Nested
  class CreateCollectionWithImages {

    @Test
    void createCollectionWithImages_happyPath_returnsResultWithCollectionId() throws Exception {
      // Arrange
      var createRequest = new CollectionRequests.Create("New Album");
      var files = List.<MultipartFile>of(createMockFile("photo1.jpg"));
      Map<String, String> rawMap = Collections.emptyMap();

      var savedCollection =
          CollectionEntity.builder().id(10L).title("New Album").slug("new-album").build();
      var collectionModel =
          CollectionModel.builder().id(10L).title("New Album").slug("new-album").build();
      var updateResponse = new CollectionRequests.UpdateResponse(collectionModel, null);

      when(collectionService.createCollection(createRequest)).thenReturn(updateResponse);
      when(collectionRepository.findById(10L)).thenReturn(Optional.of(savedCollection));
      when(contentService.nextOrderIndex(10L)).thenReturn(0);

      // prepareImageForUpload returns data that enables a successful save
      var preparedData =
          new ImageProcessingService.PreparedImageData(
              "photo1.jpg",
              "https://cdn/full.jpg",
              "https://cdn/web.webp",
              null,
              null,
              Map.of("imageWidth", "800", "imageHeight", "600"),
              List.of(),
              List.of(),
              2026,
              1,
              null,
              null);
      when(imageProcessingService.prepareImageForUpload(any(), any())).thenReturn(preparedData);

      var dedupeResult =
          new ImageProcessingService.DedupeResult(
              edens.zac.portfolio.backend.entity.ContentImageEntity.builder().id(100L).build(),
              ImageProcessingService.DedupeAction.CREATE);
      when(imageProcessingService.savePreparedImageWithDedupe(any(), any()))
          .thenReturn(dedupeResult);

      var imageModel = createImageModel(100L, 5);
      when(contentModelConverter.convertRegularContentEntityToModel(any())).thenReturn(imageModel);

      // Act
      ImageUploadResult result = service.createCollectionWithImages(createRequest, files, rawMap);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.collectionId()).isEqualTo(10L);
      assertThat(result.successful()).hasSize(1);
      assertThat(result.failed()).isEmpty();
    }

    @Test
    void createCollectionWithImages_noSuccessfulImages_skipsPostUploadProcessing()
        throws Exception {
      // Arrange
      var createRequest = new CollectionRequests.Create("Empty Album");
      var files = List.<MultipartFile>of(createMockFile("bad.gif"));
      Map<String, String> rawMap = Collections.emptyMap();

      var collectionModel =
          CollectionModel.builder().id(10L).title("Empty Album").slug("empty-album").build();
      var updateResponse = new CollectionRequests.UpdateResponse(collectionModel, null);
      var savedCollection =
          CollectionEntity.builder().id(10L).title("Empty Album").slug("empty-album").build();

      when(collectionService.createCollection(createRequest)).thenReturn(updateResponse);
      when(collectionRepository.findById(10L)).thenReturn(Optional.of(savedCollection));
      when(contentService.nextOrderIndex(10L)).thenReturn(0);

      // prepareImageForUpload returns null (GIF filtered out in prepareImageAsync)
      when(imageProcessingService.prepareImageForUpload(any(), any()))
          .thenThrow(new RuntimeException("Processing failed"));

      // Act
      ImageUploadResult result = service.createCollectionWithImages(createRequest, files, rawMap);

      // Assert
      assertThat(result.collectionId()).isEqualTo(10L);
      assertThat(result.successful()).isEmpty();
      // postUploadProcessing not called -- no transactionTemplate interaction
      verify(transactionTemplate, never()).executeWithoutResult(any());
    }
  }

  @Nested
  class ProcessFilesFromDisk {

    @Test
    void processFilesFromDisk_happyPath_returnsJobStatus() {
      // Arrange
      Long collectionId = 1L;
      var fileEntry =
          new DiskUploadRequest.FileEntry(
              "/tmp/photo.jpg", "/tmp/photo.cr3", null, null, null, null);
      var request = new DiskUploadRequest(List.of(fileEntry), null);
      var job = new JobTrackingService.JobStatus(UUID.randomUUID(), 1);

      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(jobTrackingService.createJob(1)).thenReturn(job);

      // Act
      var result = service.processFilesFromDisk(collectionId, request);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.totalFiles()).isEqualTo(1);
      // Background thread may have started, be running, or already failed (the test file
      // /tmp/photo.jpg does not exist on most CI runners) — accept any of those states.
      assertThat(result.status()).isIn("PENDING", "PROCESSING", "FAILED");
      verify(jobTrackingService).createJob(1);
    }

    @Test
    void processFilesFromDisk_collectionNotFound_throwsResourceNotFoundException() {
      // Arrange
      Long collectionId = 999L;
      var fileEntry =
          new DiskUploadRequest.FileEntry("/tmp/photo.jpg", null, null, null, null, null);
      var request = new DiskUploadRequest(List.of(fileEntry), null);

      when(collectionRepository.findById(collectionId)).thenReturn(Optional.empty());

      // Act & Assert
      assertThatThrownBy(() -> service.processFilesFromDisk(collectionId, request))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("Collection not found: 999");

      verify(jobTrackingService, never()).createJob(anyInt());
    }

    @Test
    void processFilesFromDisk_withLocationId_setsCollectionLocation() {
      // Arrange
      Long collectionId = 1L;
      var fileEntry =
          new DiskUploadRequest.FileEntry("/tmp/photo.jpg", null, null, null, null, null);
      var request = new DiskUploadRequest(List.of(fileEntry), List.of(42L));
      var job = new JobTrackingService.JobStatus(UUID.randomUUID(), 1);

      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(jobTrackingService.createJob(1)).thenReturn(job);

      // Act
      service.processFilesFromDisk(collectionId, request);

      // Assert
      verify(contentService).setCollectionLocationsIfMissing(collectionId, List.of(42L));
    }

    @Test
    void processFilesFromDisk_withoutLocationId_doesNotSetLocation() {
      // Arrange
      Long collectionId = 1L;
      var fileEntry =
          new DiskUploadRequest.FileEntry("/tmp/photo.jpg", null, null, null, null, null);
      var request = new DiskUploadRequest(List.of(fileEntry), null);
      var job = new JobTrackingService.JobStatus(UUID.randomUUID(), 1);

      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(jobTrackingService.createJob(1)).thenReturn(job);

      // Act
      service.processFilesFromDisk(collectionId, request);

      // Assert
      verify(contentService, never()).setCollectionLocationsIfMissing(anyLong(), any());
    }

    private static final int AWAIT_MILLIS = 5000;

    /** Build a PreparedImageData whose XMP-extracted tags/people are as given. */
    private ImageProcessingService.PreparedImageData prepared(
        String filename, List<String> extractedTags, List<String> extractedPeople) {
      return new ImageProcessingService.PreparedImageData(
          filename,
          "https://cdn/full.jpg",
          "https://cdn/web.webp",
          null,
          null,
          Map.of(),
          extractedTags,
          extractedPeople,
          2024,
          3,
          LocalDate.of(2024, 3, 24).atStartOfDay(),
          LocalDateTime.now());
    }

    private ImageProcessingService.DedupeResult createResult(Long imageId) {
      return new ImageProcessingService.DedupeResult(
          ContentImageEntity.builder().id(imageId).build(),
          ImageProcessingService.DedupeAction.CREATE);
    }

    /** Poll until the job reports a terminal status, so background assertions are deterministic. */
    private void awaitCompletion(JobTrackingService.JobStatus job) throws InterruptedException {
      long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
      while (System.currentTimeMillis() < deadline) {
        if ("COMPLETED".equals(job.status()) || "FAILED".equals(job.status())) {
          return;
        }
        Thread.sleep(20);
      }
    }

    @Test
    void processFilesFromDisk_prefersPluginTagsAndAttachesLocations() throws Exception {
      // Arrange -- plugin sends tags and locations; XMP-extracted tags must be ignored.
      Long collectionId = 1L;
      var request =
          new DiskUploadRequest(
              List.of(
                  new DiskUploadRequest.FileEntry(
                      "/tmp/a.jpg",
                      null,
                      List.of("Alice"),
                      List.of("street", "film"),
                      List.of("Amsterdam"),
                      null)),
              null);
      var job = new JobTrackingService.JobStatus(UUID.randomUUID(), 1);
      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(jobTrackingService.createJob(1)).thenReturn(job);
      when(personRepository.findAllByOrderByPersonNameAsc()).thenReturn(List.of());
      when(contentService.nextOrderIndex(collectionId)).thenReturn(0);
      when(imageProcessingService.prepareImageFromDisk(any(), any()))
          .thenReturn(prepared("a.jpg", List.of("xmpLeak"), List.of()));
      when(imageProcessingService.savePreparedImageWithDedupe(any(), any()))
          .thenReturn(createResult(101L));

      // Act
      service.processFilesFromDisk(collectionId, request);
      awaitCompletion(job);

      // Assert -- plugin tags used (not "xmpLeak"); locations attached per-image.
      verify(contentMutationUtil)
          .associateExtractedKeywords(
              eq(101L), eq(List.of("street", "film")), eq(List.of("Alice")));
      verify(contentMutationUtil).associateLocationsByName(eq(101L), eq(List.of("Amsterdam")));
    }

    /**
     * Pins that a multi-file upload advances the order index per file instead of reusing the seed.
     * Nothing else in the suite fails if the increment is dropped, so both images would land on the
     * same index and silently collide.
     */
    @Test
    void processFilesFromDisk_multipleFiles_getConsecutiveOrderIndexes() throws Exception {
      Long collectionId = 1L;
      var request =
          new DiskUploadRequest(
              List.of(
                  new DiskUploadRequest.FileEntry("/tmp/a.jpg", null, null, null, null, null),
                  new DiskUploadRequest.FileEntry("/tmp/b.jpg", null, null, null, null, null)),
              null);
      var job = new JobTrackingService.JobStatus(UUID.randomUUID(), 2);
      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(jobTrackingService.createJob(2)).thenReturn(job);
      when(personRepository.findAllByOrderByPersonNameAsc()).thenReturn(List.of());
      when(contentService.nextOrderIndex(collectionId)).thenReturn(5);
      when(imageProcessingService.prepareImageFromDisk(any(), any()))
          .thenReturn(prepared("a.jpg", List.of(), List.of()));
      when(imageProcessingService.savePreparedImageWithDedupe(any(), any()))
          .thenReturn(createResult(101L), createResult(102L));

      service.processFilesFromDisk(collectionId, request);
      awaitCompletion(job);

      verify(contentService).linkContentToCollection(collectionId, 101L, 5);
      verify(contentService).linkContentToCollection(collectionId, 102L, 6);
    }

    private ImageProcessingService.DedupeResult skipResult(Long imageId) {
      return new ImageProcessingService.DedupeResult(
          ContentImageEntity.builder().id(imageId).build(),
          ImageProcessingService.DedupeAction.SKIP);
    }

    @Test
    @DisplayName("a skipped duplicate is still linked to the collection it was uploaded to")
    void processFilesFromDisk_dedupeSkip_linksExistingImageToCollection() throws Exception {
      // Before this fix a SKIP only bumped a counter, so re-sending a photo you already had into
      // a new collection reported success and added nothing to that collection.
      Long collectionId = 1L;
      var request =
          new DiskUploadRequest(
              List.of(new DiskUploadRequest.FileEntry("/tmp/a.jpg", null, null, null, null, null)),
              null);
      var job = new JobTrackingService.JobStatus(UUID.randomUUID(), 1);
      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(jobTrackingService.createJob(1)).thenReturn(job);
      when(personRepository.findAllByOrderByPersonNameAsc()).thenReturn(List.of());
      when(contentService.nextOrderIndex(collectionId)).thenReturn(7);
      when(imageProcessingService.prepareImageFromDisk(any(), any()))
          .thenReturn(prepared("a.jpg", List.of(), List.of()));
      when(imageProcessingService.savePreparedImageWithDedupe(any(), any()))
          .thenReturn(skipResult(101L));
      when(collectionRepository.findContentByCollectionIdAndContentId(collectionId, 101L))
          .thenReturn(Optional.empty());

      service.processFilesFromDisk(collectionId, request);
      awaitCompletion(job);

      assertThat(job.skipped().get()).isEqualTo(1);
      verify(contentService).linkContentToCollection(collectionId, 101L, 7);
      // SKIP means the stored image is unchanged: no keyword rewrite, no RAW re-upload.
      verify(contentMutationUtil, never())
          .associateExtractedKeywords(anyLong(), anyList(), anyList());
    }

    @Test
    void processFilesFromDisk_dedupeSkip_doesNotRelinkAnImageAlreadyInTheCollection()
        throws Exception {
      Long collectionId = 1L;
      var request =
          new DiskUploadRequest(
              List.of(new DiskUploadRequest.FileEntry("/tmp/a.jpg", null, null, null, null, null)),
              null);
      var job = new JobTrackingService.JobStatus(UUID.randomUUID(), 1);
      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(jobTrackingService.createJob(1)).thenReturn(job);
      when(personRepository.findAllByOrderByPersonNameAsc()).thenReturn(List.of());
      when(contentService.nextOrderIndex(collectionId)).thenReturn(0);
      when(imageProcessingService.prepareImageFromDisk(any(), any()))
          .thenReturn(prepared("a.jpg", List.of(), List.of()));
      when(imageProcessingService.savePreparedImageWithDedupe(any(), any()))
          .thenReturn(skipResult(101L));
      when(collectionRepository.findContentByCollectionIdAndContentId(collectionId, 101L))
          .thenReturn(Optional.of(CollectionContentEntity.builder().build()));

      service.processFilesFromDisk(collectionId, request);
      awaitCompletion(job);

      assertThat(job.skipped().get()).isEqualTo(1);
      verify(contentService, never()).linkContentToCollection(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("two disk jobs never run the decode step at the same time")
    void processFilesFromDisk_concurrentJobs_serializeThePrepareStep() throws Exception {
      // Bug #2: both background loops submit to an unbounded virtual-thread executor. Without the
      // upload permit, two jobs decode full-resolution JPEGs concurrently -- 130-180 MB of heap
      // each -- which is the OOM shape that took down the 20+15 Lightroom batch.
      Long collectionId = 1L;
      var inFlight = new AtomicInteger();
      var peakInFlight = new AtomicInteger();

      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(personRepository.findAllByOrderByPersonNameAsc()).thenReturn(List.of());
      when(contentService.nextOrderIndex(collectionId)).thenReturn(0);
      when(imageProcessingService.prepareImageFromDisk(any(), any()))
          .thenAnswer(
              invocation -> {
                peakInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                Thread.sleep(120);
                inFlight.decrementAndGet();
                return prepared("a.jpg", List.of(), List.of());
              });
      when(imageProcessingService.savePreparedImageWithDedupe(any(), any()))
          .thenReturn(createResult(101L));

      var jobA = new JobTrackingService.JobStatus(UUID.randomUUID(), 2);
      var jobB = new JobTrackingService.JobStatus(UUID.randomUUID(), 2);
      when(jobTrackingService.createJob(2)).thenReturn(jobA).thenReturn(jobB);

      var request =
          new DiskUploadRequest(
              List.of(
                  new DiskUploadRequest.FileEntry("/tmp/a.jpg", null, null, null, null, null),
                  new DiskUploadRequest.FileEntry("/tmp/b.jpg", null, null, null, null, null)),
              null);

      service.processFilesFromDisk(collectionId, request);
      service.processFilesFromDisk(collectionId, request);
      awaitCompletion(jobA);
      awaitCompletion(jobB);

      assertThat(peakInFlight.get())
          .as("concurrent decodes observed across two disk jobs")
          .isEqualTo(1);
      assertThat(jobA.processed().get() + jobB.processed().get()).isEqualTo(4);
    }

    @Test
    @DisplayName("a keyword failure reaches the job, flips it to FAILED, and still links the image")
    void processFilesFromDisk_keywordFailure_recordedOnJobAndFlipsStatusToFailed()
        throws Exception {
      // Regression pin for the incident this change exists to fix: a duplicate person name made
      // findByPersonNameIgnoreCase throw, ContentMutationUtil swallowed it into a WARN, and the
      // upload reported success while dropping the person.
      //
      // This is the ONLY test that stubs a keyword failure. Every other reference to the mock is a
      // bare verify(), and Mockito returns an empty list for List-returning methods -- so without
      // this, reverting job.errors().addAll(wireImageAfterDedupe(...)) to a bare statement would
      // compile (Java lets you discard a return value), pass checkstyle, and leave the whole suite
      // green while silently restoring the original bug.
      Long collectionId = 1L;
      var request =
          new DiskUploadRequest(
              List.of(new DiskUploadRequest.FileEntry("/tmp/a.jpg", null, null, null, null, null)),
              null);
      var job = new JobTrackingService.JobStatus(UUID.randomUUID(), 1);
      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(jobTrackingService.createJob(1)).thenReturn(job);
      when(personRepository.findAllByOrderByPersonNameAsc()).thenReturn(List.of());
      when(contentService.nextOrderIndex(collectionId)).thenReturn(0);
      when(imageProcessingService.prepareImageFromDisk(any(), any()))
          .thenReturn(prepared("a.jpg", List.of("Rome Italy"), List.of("Tara Edens")));
      when(imageProcessingService.savePreparedImageWithDedupe(any(), any()))
          .thenReturn(createResult(101L));
      when(contentMutationUtil.associateExtractedKeywords(eq(101L), anyList(), anyList()))
          .thenReturn(
              List.of(
                  "image 101: failed to associate people: Incorrect result size: expected 1,"
                      + " actual 2"));

      service.processFilesFromDisk(collectionId, request);
      awaitCompletion(job);

      assertThat(job.errors()).anyMatch(e -> e.contains("failed to associate people"));
      // The deliberate semantic change: a dropped person tag is now a visibly FAILED job rather
      // than a silent success. markCompleted keys status off the error list.
      assertThat(job.status()).isEqualTo("FAILED");
      // ...but the image itself still succeeded. It is counted and still linked to the collection,
      // so a keyword failure degrades the report, not the upload.
      assertThat(job.created().get()).isEqualTo(1);
      verify(contentService).linkContentToCollection(eq(collectionId), eq(101L), anyInt());
    }

    @Test
    void processFilesFromDisk_noPluginTags_fallsBackToXmpExtracted() throws Exception {
      // Arrange -- no plugin tags; XMP-extracted tags are used instead.
      Long collectionId = 1L;
      var request =
          new DiskUploadRequest(
              List.of(new DiskUploadRequest.FileEntry("/tmp/a.jpg", null, null, null, null, null)),
              null);
      var job = new JobTrackingService.JobStatus(UUID.randomUUID(), 1);
      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(jobTrackingService.createJob(1)).thenReturn(job);
      when(personRepository.findAllByOrderByPersonNameAsc()).thenReturn(List.of());
      when(contentService.nextOrderIndex(collectionId)).thenReturn(0);
      when(imageProcessingService.prepareImageFromDisk(any(), any()))
          .thenReturn(prepared("a.jpg", List.of("mountains", "hike"), List.of()));
      when(imageProcessingService.savePreparedImageWithDedupe(any(), any()))
          .thenReturn(createResult(101L));

      // Act
      service.processFilesFromDisk(collectionId, request);
      awaitCompletion(job);

      // Assert -- XMP-extracted tags used since plugin sent none.
      verify(contentMutationUtil)
          .associateExtractedKeywords(eq(101L), eq(List.of("mountains", "hike")), eq(List.of()));
    }

    @Test
    @DisplayName("processFilesFromDisk inspects nothing about the target beyond its existence")
    void processFilesFromDisk_anyExistingCollection_isAccepted() {
      // Scope: the EXISTENCE check only. It cannot pin Rule B -- parent-ness is derived from the
      // collection_content join and collectionRepository is a mock here, so no builder-built
      // fixture is a wrapper. RuleBMixedContentIntegrationTest is the real pin.
      CollectionEntity target =
          CollectionEntity.builder().id(31L).slug("target").title("Target").build();
      when(collectionRepository.findById(31L)).thenReturn(Optional.of(target));

      assertThatCode(
              () -> service.processFilesFromDisk(31L, new DiskUploadRequest(List.of(), null)))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  class CreateImagesParallel {

    @Test
    void createImagesParallel_happyPath_returnsSuccessfulResults() throws Exception {
      // Arrange
      Long collectionId = 1L;
      var file = createMockFile("photo1.jpg");
      List<MultipartFile> files = List.of(file);
      Map<String, String> rawMap = Collections.emptyMap();

      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(contentService.nextOrderIndex(collectionId)).thenReturn(0);

      var preparedData =
          new ImageProcessingService.PreparedImageData(
              "photo1.jpg",
              "https://cdn/full.jpg",
              "https://cdn/web.webp",
              null,
              null,
              Map.of("imageWidth", "800", "imageHeight", "600"),
              List.of(),
              List.of(),
              2026,
              1,
              null,
              null);
      when(imageProcessingService.prepareImageForUpload(any(), any())).thenReturn(preparedData);

      var entity = edens.zac.portfolio.backend.entity.ContentImageEntity.builder().id(100L).build();
      var dedupeResult =
          new ImageProcessingService.DedupeResult(
              entity, ImageProcessingService.DedupeAction.CREATE);
      when(imageProcessingService.savePreparedImageWithDedupe(any(), any()))
          .thenReturn(dedupeResult);

      var imageModel = createImageModel(100L, 5);
      when(contentModelConverter.convertRegularContentEntityToModel(any())).thenReturn(imageModel);

      // Act
      ImageUploadResult result = service.createImagesParallel(collectionId, files, rawMap);

      // Assert
      assertThat(result.successful()).hasSize(1);
      assertThat(result.failed()).isEmpty();
      assertThat(result.skipped()).isEmpty();
      verify(contentValidator).validateFiles(files);
    }

    @Test
    void createImagesParallel_collectionNotFound_throwsResourceNotFoundException() {
      // Arrange
      Long collectionId = 999L;
      var file = createMockFile("photo.jpg");
      List<MultipartFile> files = List.of(file);
      Map<String, String> rawMap = Collections.emptyMap();

      when(collectionRepository.findById(collectionId)).thenReturn(Optional.empty());

      // Act & Assert
      assertThatThrownBy(() -> service.createImagesParallel(collectionId, files, rawMap))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("Collection not found: 999");
    }

    @Test
    void createImagesParallel_validatesFilesBeforeProcessing() {
      // Arrange
      Long collectionId = 1L;
      List<MultipartFile> files = List.of(createMockFile("photo.jpg"));
      Map<String, String> rawMap = Collections.emptyMap();

      doThrow(new IllegalArgumentException("At least one file is required"))
          .when(contentValidator)
          .validateFiles(any());

      // Act & Assert
      assertThatThrownBy(() -> service.createImagesParallel(collectionId, files, rawMap))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("At least one file is required");

      // Should not attempt to find collection
      verify(collectionRepository, never()).findById(any());
    }

    @Test
    void createImagesParallel_preparationFailure_recordsInFailedList() throws Exception {
      // Arrange
      Long collectionId = 1L;
      var file = createMockFile("corrupt.jpg");
      List<MultipartFile> files = List.of(file);
      Map<String, String> rawMap = Collections.emptyMap();

      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(contentService.nextOrderIndex(collectionId)).thenReturn(0);

      // Simulate preparation failure
      when(imageProcessingService.prepareImageForUpload(any(), any()))
          .thenThrow(new RuntimeException("Corrupt image"));

      // Act
      ImageUploadResult result = service.createImagesParallel(collectionId, files, rawMap);

      // Assert
      assertThat(result.successful()).isEmpty();
      assertThat(result.failed()).hasSize(1);
      assertThat(result.failed().getFirst().filename()).isEqualTo("corrupt.jpg");
    }

    @Test
    void createImagesParallel_dedupeSkip_recordsInSkippedList() throws Exception {
      // Arrange
      Long collectionId = 1L;
      var file = createMockFile("duplicate.jpg");
      List<MultipartFile> files = List.of(file);
      Map<String, String> rawMap = Collections.emptyMap();

      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(contentService.nextOrderIndex(collectionId)).thenReturn(0);

      var preparedData =
          new ImageProcessingService.PreparedImageData(
              "duplicate.jpg",
              "https://cdn/full.jpg",
              "https://cdn/web.webp",
              null,
              null,
              Map.of("imageWidth", "800", "imageHeight", "600"),
              List.of(),
              List.of(),
              2026,
              1,
              null,
              null);
      when(imageProcessingService.prepareImageForUpload(any(), any())).thenReturn(preparedData);

      var entity = edens.zac.portfolio.backend.entity.ContentImageEntity.builder().id(100L).build();
      var dedupeResult =
          new ImageProcessingService.DedupeResult(entity, ImageProcessingService.DedupeAction.SKIP);
      when(imageProcessingService.savePreparedImageWithDedupe(any(), any()))
          .thenReturn(dedupeResult);

      // Act
      ImageUploadResult result = service.createImagesParallel(collectionId, files, rawMap);

      // Assert
      assertThat(result.successful()).isEmpty();
      assertThat(result.skipped()).hasSize(1);
      assertThat(result.skipped().getFirst().filename()).isEqualTo("duplicate.jpg");
    }

    @Test
    void createImagesParallel_mixedResults_categorizesProperly() throws Exception {
      // Arrange
      Long collectionId = 1L;
      var goodFile = createMockFile("good.jpg");
      var badFile = createMockFile("bad.jpg");
      List<MultipartFile> files = List.of(goodFile, badFile);
      Map<String, String> rawMap = Collections.emptyMap();

      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(contentService.nextOrderIndex(collectionId)).thenReturn(0);

      var preparedData =
          new ImageProcessingService.PreparedImageData(
              "good.jpg",
              "https://cdn/full.jpg",
              "https://cdn/web.webp",
              null,
              null,
              Map.of("imageWidth", "800", "imageHeight", "600"),
              List.of(),
              List.of(),
              2026,
              1,
              null,
              null);

      // First file succeeds, second file fails during preparation
      when(imageProcessingService.prepareImageForUpload(eq(goodFile), any()))
          .thenReturn(preparedData);
      when(imageProcessingService.prepareImageForUpload(eq(badFile), any()))
          .thenThrow(new RuntimeException("S3 error"));

      var entity = edens.zac.portfolio.backend.entity.ContentImageEntity.builder().id(100L).build();
      var dedupeResult =
          new ImageProcessingService.DedupeResult(
              entity, ImageProcessingService.DedupeAction.CREATE);
      when(imageProcessingService.savePreparedImageWithDedupe(any(), any()))
          .thenReturn(dedupeResult);

      var imageModel = createImageModel(100L, 5);
      when(contentModelConverter.convertRegularContentEntityToModel(any())).thenReturn(imageModel);

      // Act
      ImageUploadResult result = service.createImagesParallel(collectionId, files, rawMap);

      // Assert
      assertThat(result.successful()).hasSize(1);
      assertThat(result.failed()).hasSize(1);
      assertThat(result.failed().getFirst().filename()).isEqualTo("bad.jpg");
    }

    @Test
    void createImagesParallel_releasesSemaphoreOnSuccess() throws Exception {
      // Arrange
      Long collectionId = 1L;
      var file = createMockFile("photo.jpg");
      List<MultipartFile> files = List.of(file);
      Map<String, String> rawMap = Collections.emptyMap();

      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(contentService.nextOrderIndex(collectionId)).thenReturn(0);

      var preparedData =
          new ImageProcessingService.PreparedImageData(
              "photo.jpg",
              "https://cdn/full.jpg",
              "https://cdn/web.webp",
              null,
              null,
              Map.of("imageWidth", "800", "imageHeight", "600"),
              List.of(),
              List.of(),
              2026,
              1,
              null,
              null);
      when(imageProcessingService.prepareImageForUpload(any(), any())).thenReturn(preparedData);

      var entity = edens.zac.portfolio.backend.entity.ContentImageEntity.builder().id(100L).build();
      var dedupeResult =
          new ImageProcessingService.DedupeResult(
              entity, ImageProcessingService.DedupeAction.CREATE);
      when(imageProcessingService.savePreparedImageWithDedupe(any(), any()))
          .thenReturn(dedupeResult);

      var imageModel = createImageModel(100L, 5);
      when(contentModelConverter.convertRegularContentEntityToModel(any())).thenReturn(imageModel);

      // Act - call twice to prove semaphore is released after first call
      service.createImagesParallel(collectionId, files, rawMap);
      service.createImagesParallel(collectionId, files, rawMap);

      // Assert - second call would hang if semaphore not released; reaching here proves release
      assertThat(true).isTrue();
    }

    @Test
    void createImagesParallel_releasesSemaphoreOnError() throws Exception {
      // Arrange
      Long collectionId = 1L;
      var file = createMockFile("photo.jpg");
      List<MultipartFile> files = List.of(file);
      Map<String, String> rawMap = Collections.emptyMap();

      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(contentService.nextOrderIndex(collectionId)).thenReturn(0);

      // All images fail during preparation
      when(imageProcessingService.prepareImageForUpload(any(), any()))
          .thenThrow(new RuntimeException("S3 down"));

      // Act - first call fails but should release semaphore
      service.createImagesParallel(collectionId, files, rawMap);

      // Second call should not hang -- semaphore was released in finally block
      service.createImagesParallel(collectionId, files, rawMap);

      // Assert - if we reach here, semaphore was properly released
      assertThat(true).isTrue();
    }
  }

  @Nested
  class IngestFilesGroupedByDay {

    private static final int AWAIT_MILLIS = 5000;

    /** Build a PreparedImageData whose EXIF capture date is {@code exifCaptureDate}. */
    private ImageProcessingService.PreparedImageData prepared(
        String filename,
        LocalDate exifCaptureDate,
        List<String> extractedTags,
        List<String> extractedPeople) {
      return new ImageProcessingService.PreparedImageData(
          filename,
          "https://cdn/full.jpg",
          "https://cdn/web.webp",
          null,
          null,
          Map.of(),
          extractedTags,
          extractedPeople,
          2024,
          3,
          exifCaptureDate != null ? exifCaptureDate.atStartOfDay() : null,
          LocalDateTime.now());
    }

    private ImageProcessingService.DedupeResult createResult(Long imageId) {
      return new ImageProcessingService.DedupeResult(
          ContentImageEntity.builder().id(imageId).build(),
          ImageProcessingService.DedupeAction.CREATE);
    }

    private CollectionRequests.UpdateResponse blogResponse(Long id, LocalDate day) {
      var model =
          CollectionModel.builder().id(id).title(day.toString()).slug(day.toString()).build();
      return new CollectionRequests.UpdateResponse(model, null);
    }

    /** Poll until the job reports a terminal status, so background assertions are deterministic. */
    private void awaitCompletion(JobTrackingService.JobStatus job) throws InterruptedException {
      long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
      while (System.currentTimeMillis() < deadline) {
        if ("COMPLETED".equals(job.status()) || "FAILED".equals(job.status())) {
          return;
        }
        Thread.sleep(20);
      }
    }

    @Test
    void ingest_multiDayBatch_splitsIntoOneBlogPerDay() throws Exception {
      // Arrange -- two files on two distinct capture days (request-provided captureDate).
      LocalDate day1 = LocalDate.of(2024, 3, 24);
      LocalDate day2 = LocalDate.of(2024, 3, 25);
      var request =
          new DiskUploadRequest(
              List.of(
                  new DiskUploadRequest.FileEntry(
                      "/tmp/a.jpg", null, null, null, null, "2024-03-24"),
                  new DiskUploadRequest.FileEntry(
                      "/tmp/b.jpg", null, null, null, null, "2024-03-25")),
              null);
      var job = new JobTrackingService.JobStatus(UUID.randomUUID(), 2);
      when(jobTrackingService.createJob(2)).thenReturn(job);
      when(personRepository.findAllByOrderByPersonNameAsc()).thenReturn(List.of());
      when(imageProcessingService.prepareImageFromDisk(any(), any()))
          .thenReturn(prepared("a.jpg", day1, List.of(), List.of()))
          .thenReturn(prepared("b.jpg", day2, List.of(), List.of()));
      when(imageProcessingService.savePreparedImageWithDedupe(any(), any()))
          .thenReturn(createResult(101L))
          .thenReturn(createResult(102L));
      when(collectionRepository.findBlogsByCollectionDate(day1)).thenReturn(List.of());
      when(collectionRepository.findBlogsByCollectionDate(day2)).thenReturn(List.of());
      when(collectionService.createCollection(any()))
          .thenReturn(blogResponse(1L, day1))
          .thenReturn(blogResponse(2L, day2));

      // Act
      var result = service.ingestFilesGroupedByDay(request);
      awaitCompletion(job);

      // Assert -- two distinct BLOGs created, one per day; two links.
      assertThat(result.totalFiles()).isEqualTo(2);
      verify(collectionService, times(2)).createCollection(any());
      verify(contentService).linkContentToCollection(eq(1L), eq(101L), anyInt());
      verify(contentService).linkContentToCollection(eq(2L), eq(102L), anyInt());
      assertThat(job.created().get()).isEqualTo(2);
    }

    @Test
    @DisplayName("a skipped duplicate is still linked to that day's blog")
    void ingest_dedupeSkip_linksExistingImageToTheDayBlog() throws Exception {
      LocalDate day = LocalDate.of(2024, 3, 24);
      var request =
          new DiskUploadRequest(
              List.of(
                  new DiskUploadRequest.FileEntry(
                      "/tmp/a.jpg", null, null, null, null, "2024-03-24")),
              null);
      var job = new JobTrackingService.JobStatus(UUID.randomUUID(), 1);
      when(jobTrackingService.createJob(1)).thenReturn(job);
      when(personRepository.findAllByOrderByPersonNameAsc()).thenReturn(List.of());
      when(imageProcessingService.prepareImageFromDisk(any(), any()))
          .thenReturn(prepared("a.jpg", day, List.of(), List.of()));
      when(imageProcessingService.savePreparedImageWithDedupe(any(), any()))
          .thenReturn(
              new ImageProcessingService.DedupeResult(
                  ContentImageEntity.builder().id(101L).build(),
                  ImageProcessingService.DedupeAction.SKIP));
      when(collectionRepository.findBlogsByCollectionDate(day)).thenReturn(List.of());
      when(collectionService.createCollection(any())).thenReturn(blogResponse(1L, day));
      when(contentService.nextOrderIndex(1L)).thenReturn(3);
      when(collectionRepository.findContentByCollectionIdAndContentId(1L, 101L))
          .thenReturn(Optional.empty());

      service.ingestFilesGroupedByDay(request);
      awaitCompletion(job);

      assertThat(job.skipped().get()).isEqualTo(1);
      verify(contentService).linkContentToCollection(1L, 101L, 3);
    }

    @Test
    void ingest_newBlogForDay_isPromotedToListed() throws Exception {
      // Regression pin: the shared create path is privacy-first UNLISTED, and UNLISTED blogs are
      // invisible to findListedBlogsOrdered and to the public /all-blogs listing. Without the
      // explicit promotion the daily-blog pipeline logs success and never publishes anything.
      LocalDate day = LocalDate.of(2024, 3, 24);
      var request =
          new DiskUploadRequest(
              List.of(
                  new DiskUploadRequest.FileEntry(
                      "/tmp/a.jpg", null, null, null, null, "2024-03-24")),
              null);
      var job = new JobTrackingService.JobStatus(UUID.randomUUID(), 1);
      when(jobTrackingService.createJob(1)).thenReturn(job);
      when(personRepository.findAllByOrderByPersonNameAsc()).thenReturn(List.of());
      when(imageProcessingService.prepareImageFromDisk(any(), any()))
          .thenReturn(prepared("a.jpg", day, List.of(), List.of()));
      when(imageProcessingService.savePreparedImageWithDedupe(any(), any()))
          .thenReturn(createResult(101L));
      when(collectionRepository.findBlogsByCollectionDate(day)).thenReturn(List.of());
      when(collectionService.createCollection(any())).thenReturn(blogResponse(1L, day));

      service.ingestFilesGroupedByDay(request);
      awaitCompletion(job);

      verify(collectionRepository).updateVisibility(1L, CollectionVisibility.LISTED);
    }

    @Test
    void ingest_newBlogForDay_createsItWithTheBlogFlagSet() throws Exception {
      // Regression pin for the one token that keeps the day-blog get-after-create round trip
      // closed. findBlogsByCollectionDate reads `is_blog = true` and nothing derives blog-ness
      // from the title or date, so a create that omits isBlog is invisible to the very lookup
      // that runs first next batch -- every batch would mint another slug-suffixed public blog.
      LocalDate day = LocalDate.of(2024, 3, 24);
      var request =
          new DiskUploadRequest(
              List.of(
                  new DiskUploadRequest.FileEntry(
                      "/tmp/a.jpg", null, null, null, null, "2024-03-24")),
              null);
      var job = new JobTrackingService.JobStatus(UUID.randomUUID(), 1);
      when(jobTrackingService.createJob(1)).thenReturn(job);
      when(personRepository.findAllByOrderByPersonNameAsc()).thenReturn(List.of());
      when(imageProcessingService.prepareImageFromDisk(any(), any()))
          .thenReturn(prepared("a.jpg", day, List.of(), List.of()));
      when(imageProcessingService.savePreparedImageWithDedupe(any(), any()))
          .thenReturn(createResult(101L));
      when(collectionRepository.findBlogsByCollectionDate(day)).thenReturn(List.of());
      when(collectionService.createCollection(any())).thenReturn(blogResponse(1L, day));

      service.ingestFilesGroupedByDay(request);
      awaitCompletion(job);

      ArgumentCaptor<CollectionRequests.Create> createCaptor =
          ArgumentCaptor.forClass(CollectionRequests.Create.class);
      verify(collectionService).createCollection(createCaptor.capture());
      assertThat(createCaptor.getValue().isBlog()).isTrue();
      assertThat(createCaptor.getValue().isClient()).isNull();
      assertThat(createCaptor.getValue().collectionDate()).isEqualTo(day);
    }

    @Test
    void ingest_existingBlogForDay_appendsWithoutCreating() throws Exception {
      // Arrange -- a BLOG already exists for the capture day; should append, not create.
      LocalDate day = LocalDate.of(2024, 3, 24);
      var existingBlog =
          CollectionEntity.builder()
              .id(7L)
              .collectionDate(day)
              .visibility(CollectionVisibility.LISTED)
              .build();
      var request =
          new DiskUploadRequest(
              List.of(
                  new DiskUploadRequest.FileEntry(
                      "/tmp/a.jpg", null, null, null, null, "2024-03-24")),
              null);
      var job = new JobTrackingService.JobStatus(UUID.randomUUID(), 1);
      when(jobTrackingService.createJob(1)).thenReturn(job);
      when(personRepository.findAllByOrderByPersonNameAsc()).thenReturn(List.of());
      when(imageProcessingService.prepareImageFromDisk(any(), any()))
          .thenReturn(prepared("a.jpg", day, List.of(), List.of()));
      when(imageProcessingService.savePreparedImageWithDedupe(any(), any()))
          .thenReturn(createResult(101L));
      when(collectionRepository.findBlogsByCollectionDate(day)).thenReturn(List.of(existingBlog));

      // Act
      service.ingestFilesGroupedByDay(request);
      awaitCompletion(job);

      // Assert -- no new collection created; linked to the existing BLOG.
      verify(collectionService, never()).createCollection(any());
      verify(contentService).linkContentToCollection(eq(7L), eq(101L), anyInt());
    }

    @Test
    void ingest_multipleBlogsForDay_usesOldest() throws Exception {
      // Arrange -- two BLOGs exist for the day (finder returns oldest first); use the oldest.
      LocalDate day = LocalDate.of(2024, 3, 24);
      var oldest = CollectionEntity.builder().id(3L).collectionDate(day).build();
      var newer = CollectionEntity.builder().id(9L).collectionDate(day).build();
      var request =
          new DiskUploadRequest(
              List.of(
                  new DiskUploadRequest.FileEntry(
                      "/tmp/a.jpg", null, null, null, null, "2024-03-24")),
              null);
      var job = new JobTrackingService.JobStatus(UUID.randomUUID(), 1);
      when(jobTrackingService.createJob(1)).thenReturn(job);
      when(personRepository.findAllByOrderByPersonNameAsc()).thenReturn(List.of());
      when(imageProcessingService.prepareImageFromDisk(any(), any()))
          .thenReturn(prepared("a.jpg", day, List.of(), List.of()));
      when(imageProcessingService.savePreparedImageWithDedupe(any(), any()))
          .thenReturn(createResult(101L));
      when(collectionRepository.findBlogsByCollectionDate(day)).thenReturn(List.of(oldest, newer));

      // Act
      service.ingestFilesGroupedByDay(request);
      awaitCompletion(job);

      // Assert -- linked to the oldest (id 3), never created.
      verify(collectionService, never()).createCollection(any());
      verify(contentService).linkContentToCollection(eq(3L), eq(101L), anyInt());
    }

    @Test
    void ingest_missingCaptureDate_fallsBackToExif() throws Exception {
      // Arrange -- no request captureDate; EXIF supplies the day.
      LocalDate exifDay = LocalDate.of(2024, 5, 1);
      var request =
          new DiskUploadRequest(
              List.of(new DiskUploadRequest.FileEntry("/tmp/a.jpg", null, null, null, null, null)),
              null);
      var job = new JobTrackingService.JobStatus(UUID.randomUUID(), 1);
      when(jobTrackingService.createJob(1)).thenReturn(job);
      when(personRepository.findAllByOrderByPersonNameAsc()).thenReturn(List.of());
      when(imageProcessingService.prepareImageFromDisk(any(), any()))
          .thenReturn(prepared("a.jpg", exifDay, List.of(), List.of()));
      when(imageProcessingService.savePreparedImageWithDedupe(any(), any()))
          .thenReturn(createResult(101L));
      when(collectionRepository.findBlogsByCollectionDate(exifDay)).thenReturn(List.of());
      when(collectionService.createCollection(any())).thenReturn(blogResponse(1L, exifDay));

      // Act
      service.ingestFilesGroupedByDay(request);
      awaitCompletion(job);

      // Assert -- BLOG created for the EXIF day, image linked.
      verify(collectionService).createCollection(any());
      verify(contentService).linkContentToCollection(eq(1L), eq(101L), anyInt());
      assertThat(job.errors()).isEmpty();
    }

    @Test
    void ingest_noResolvableCaptureDate_recordsFileFailureAndSiblingsSucceed() throws Exception {
      // Arrange -- file A has no request date and no EXIF date (fails); file B succeeds.
      LocalDate day = LocalDate.of(2024, 3, 24);
      var request =
          new DiskUploadRequest(
              List.of(
                  new DiskUploadRequest.FileEntry("/tmp/a.jpg", null, null, null, null, null),
                  new DiskUploadRequest.FileEntry(
                      "/tmp/b.jpg", null, null, null, null, "2024-03-24")),
              null);
      var job = new JobTrackingService.JobStatus(UUID.randomUUID(), 2);
      when(jobTrackingService.createJob(2)).thenReturn(job);
      when(personRepository.findAllByOrderByPersonNameAsc()).thenReturn(List.of());
      when(imageProcessingService.prepareImageFromDisk(any(), any()))
          .thenReturn(prepared("a.jpg", null, List.of(), List.of()))
          .thenReturn(prepared("b.jpg", day, List.of(), List.of()));
      when(imageProcessingService.savePreparedImageWithDedupe(any(), any()))
          .thenReturn(createResult(102L));
      when(collectionRepository.findBlogsByCollectionDate(day)).thenReturn(List.of());
      when(collectionService.createCollection(any())).thenReturn(blogResponse(1L, day));

      // Act
      service.ingestFilesGroupedByDay(request);
      awaitCompletion(job);

      // Assert -- one failure recorded (file a), sibling b still linked & created.
      assertThat(job.errors()).anyMatch(e -> e.contains("/tmp/a.jpg"));
      assertThat(job.created().get()).isEqualTo(1);
      verify(contentService).linkContentToCollection(eq(1L), eq(102L), anyInt());
    }

    @Test
    void ingest_attachesTagsPeopleAndLocationsPerImage() throws Exception {
      // Arrange -- plugin-provided tags/people/locations attach to the saved content row.
      LocalDate day = LocalDate.of(2024, 3, 24);
      var request =
          new DiskUploadRequest(
              List.of(
                  new DiskUploadRequest.FileEntry(
                      "/tmp/a.jpg",
                      null,
                      List.of("Alice"),
                      List.of("street", "film"),
                      List.of("Amsterdam"),
                      "2024-03-24")),
              null);
      var job = new JobTrackingService.JobStatus(UUID.randomUUID(), 1);
      when(jobTrackingService.createJob(1)).thenReturn(job);
      when(personRepository.findAllByOrderByPersonNameAsc()).thenReturn(List.of());
      when(imageProcessingService.prepareImageFromDisk(any(), any()))
          .thenReturn(prepared("a.jpg", day, List.of(), List.of()));
      when(imageProcessingService.savePreparedImageWithDedupe(any(), any()))
          .thenReturn(createResult(101L));
      when(collectionRepository.findBlogsByCollectionDate(day)).thenReturn(List.of());
      when(collectionService.createCollection(any())).thenReturn(blogResponse(1L, day));

      // Act
      service.ingestFilesGroupedByDay(request);
      awaitCompletion(job);

      // Assert -- keywords (tags+people) and locations attach to content 101.
      verify(contentMutationUtil)
          .associateExtractedKeywords(
              eq(101L), eq(List.of("street", "film")), eq(List.of("Alice")));
      verify(contentMutationUtil).associateLocationsByName(eq(101L), eq(List.of("Amsterdam")));
    }

    @Test
    void ingest_filtersKnownPeopleOutOfTags() throws Exception {
      // Arrange -- an existing person "Bob"; the plugin tag "bob" must not become a tag.
      LocalDate day = LocalDate.of(2024, 3, 24);
      var bob = new edens.zac.portfolio.backend.entity.ContentPersonEntity("Bob");
      var request =
          new DiskUploadRequest(
              List.of(
                  new DiskUploadRequest.FileEntry(
                      "/tmp/a.jpg",
                      null,
                      List.of("Bob"),
                      List.of("bob", "street"),
                      null,
                      "2024-03-24")),
              null);
      var job = new JobTrackingService.JobStatus(UUID.randomUUID(), 1);
      when(jobTrackingService.createJob(1)).thenReturn(job);
      when(personRepository.findAllByOrderByPersonNameAsc()).thenReturn(List.of(bob));
      when(imageProcessingService.prepareImageFromDisk(any(), any()))
          .thenReturn(prepared("a.jpg", day, List.of(), List.of()));
      when(imageProcessingService.savePreparedImageWithDedupe(any(), any()))
          .thenReturn(createResult(101L));
      when(collectionRepository.findBlogsByCollectionDate(day)).thenReturn(List.of());
      when(collectionService.createCollection(any())).thenReturn(blogResponse(1L, day));

      // Act
      service.ingestFilesGroupedByDay(request);
      awaitCompletion(job);

      // Assert -- "bob" filtered out of tags (only "street" remains); "Bob" stays a person.
      verify(contentMutationUtil)
          .associateExtractedKeywords(eq(101L), eq(List.of("street")), eq(List.of("Bob")));
    }
  }

  @Nested
  class Shutdown {

    @Test
    void shutdown_waitsForInFlightImageProcessingTask() throws Exception {
      ExecutorService imageProcessingExecutor =
          (ExecutorService) ReflectionTestUtils.getField(service, "imageProcessingExecutor");
      CountDownLatch started = new CountDownLatch(1);
      AtomicBoolean finished = new AtomicBoolean(false);
      imageProcessingExecutor.submit(
          () -> {
            started.countDown();
            Thread.sleep(300);
            finished.set(true);
            return null;
          });
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

      service.shutdown();

      assertThat(finished).isTrue();
      assertThat(imageProcessingExecutor.isTerminated()).isTrue();
    }
  }
}
