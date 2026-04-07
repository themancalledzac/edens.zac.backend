package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.DiskUploadRequest;
import edens.zac.portfolio.backend.model.ImageUploadResult;
import edens.zac.portfolio.backend.services.validator.ContentValidator;
import edens.zac.portfolio.backend.types.CollectionType;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.mock.web.MockMultipartFile;
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
            .type(CollectionType.PORTFOLIO)
            .visible(true)
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
      var createRequest = new CollectionRequests.Create(CollectionType.PORTFOLIO, "New Album");
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
      var createRequest = new CollectionRequests.Create(CollectionType.PORTFOLIO, "Empty Album");
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
      var fileEntry = new DiskUploadRequest.FileEntry("/tmp/photo.jpg", "/tmp/photo.cr3", null);
      var request = new DiskUploadRequest(List.of(fileEntry), null);
      var job = new JobTrackingService.JobStatus(java.util.UUID.randomUUID(), 1);

      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(jobTrackingService.createJob(1)).thenReturn(job);

      // Act
      var result = service.processFilesFromDisk(collectionId, request);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.totalFiles()).isEqualTo(1);
      // Status may be PENDING or PROCESSING depending on background thread timing
      assertThat(result.status()).isIn("PENDING", "PROCESSING");
      verify(jobTrackingService).createJob(1);
    }

    @Test
    void processFilesFromDisk_collectionNotFound_throwsResourceNotFoundException() {
      // Arrange
      Long collectionId = 999L;
      var fileEntry = new DiskUploadRequest.FileEntry("/tmp/photo.jpg", null, null);
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
      var fileEntry = new DiskUploadRequest.FileEntry("/tmp/photo.jpg", null, null);
      var request = new DiskUploadRequest(List.of(fileEntry), List.of(42L));
      var job = new JobTrackingService.JobStatus(java.util.UUID.randomUUID(), 1);

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
      var fileEntry = new DiskUploadRequest.FileEntry("/tmp/photo.jpg", null, null);
      var request = new DiskUploadRequest(List.of(fileEntry), null);
      var job = new JobTrackingService.JobStatus(java.util.UUID.randomUUID(), 1);

      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(jobTrackingService.createJob(1)).thenReturn(job);

      // Act
      service.processFilesFromDisk(collectionId, request);

      // Assert
      verify(contentService, never()).setCollectionLocationsIfMissing(anyLong(), any());
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
}
