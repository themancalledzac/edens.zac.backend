package edens.zac.portfolio.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.EquipmentRepository;
import edens.zac.portfolio.backend.dao.LocationRepository;
import edens.zac.portfolio.backend.entity.ContentCameraEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.ContentLensEntity;
import edens.zac.portfolio.backend.services.validator.ContentValidator;
import edens.zac.portfolio.backend.types.ContentType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;

@ExtendWith(MockitoExtension.class)
class ImageProcessingServiceTest {

  @Mock private S3Client s3Client;
  @Mock private ContentRepository contentRepository;
  @Mock private EquipmentRepository equipmentRepository;
  @Mock private LocationRepository locationRepository;
  @Mock private ImageMetadataExtractor imageMetadataExtractor;
  @Mock private ContentValidator contentValidator;

  @InjectMocks private ImageProcessingService imageProcessingService;

  private static final String BUCKET_NAME = "test-bucket";
  private static final String CLOUDFRONT_DOMAIN = "test.cloudfront.net";

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(imageProcessingService, "bucketName", BUCKET_NAME);
    ReflectionTestUtils.setField(imageProcessingService, "cloudfrontDomain", CLOUDFRONT_DOMAIN);
  }

  // ============================================================================
  // Tests for savePreparedImageWithDedupe
  // ============================================================================

  private ImageProcessingService.PreparedImageData createPreparedImageData(
      String filename, LocalDateTime captureDate, LocalDateTime lastExportDate) {
    Map<String, String> metadata =
        Map.of(
            "imageWidth", "800",
            "imageHeight", "600",
            "author", "Test Author",
            "fStop", "f/2.8",
            "shutterSpeed", "1/125",
            "iso", "100",
            "focalLength", "50mm");
    return new ImageProcessingService.PreparedImageData(
        filename,
        "https://cdn/full/image.jpg",
        "https://cdn/web/image.webp",
        metadata,
        List.of(),
        List.of(),
        2026,
        1,
        captureDate,
        lastExportDate);
  }

  @Test
  void savePreparedImageWithDedupe_create_whenNoDuplicateExists() {
    // Arrange
    LocalDateTime captureDate = LocalDateTime.of(2026, 1, 15, 14, 23, 5);
    LocalDateTime exportDate = LocalDateTime.of(2026, 1, 15, 10, 0);
    ImageProcessingService.PreparedImageData prepared =
        createPreparedImageData("photo.jpg", captureDate, exportDate);

    when(contentRepository.findByOriginalFilenameAndCaptureDate("photo.jpg", captureDate))
        .thenReturn(Optional.empty());

    ContentImageEntity savedEntity = createContentImageEntity();
    when(contentRepository.saveImage(any(ContentImageEntity.class))).thenReturn(savedEntity);

    // Act
    ImageProcessingService.DedupeResult result =
        imageProcessingService.savePreparedImageWithDedupe(prepared, "Test");

    // Assert
    assertEquals(ImageProcessingService.DedupeAction.CREATE, result.action());
    assertNotNull(result.entity());
    verify(contentRepository).saveImage(any(ContentImageEntity.class));
  }

  @Test
  void savePreparedImageWithDedupe_skip_whenSameExportDate() {
    // Arrange
    LocalDateTime captureDate = LocalDateTime.of(2026, 1, 15, 14, 23, 5);
    LocalDateTime exportDate = LocalDateTime.of(2026, 1, 15, 10, 0);
    ImageProcessingService.PreparedImageData prepared =
        createPreparedImageData("photo.jpg", captureDate, exportDate);

    ContentImageEntity existing = createContentImageEntity();
    existing.setLastExportDate(exportDate); // same export date
    when(contentRepository.findByOriginalFilenameAndCaptureDate("photo.jpg", captureDate))
        .thenReturn(Optional.of(existing));

    // Act
    ImageProcessingService.DedupeResult result =
        imageProcessingService.savePreparedImageWithDedupe(prepared, null);

    // Assert
    assertEquals(ImageProcessingService.DedupeAction.SKIP, result.action());
    assertEquals(existing, result.entity());
    verify(contentRepository, never()).saveImage(any());
  }

  @Test
  void savePreparedImageWithDedupe_skip_whenExistingHasNullExportDate() {
    // Arrange: migrated rows have null lastExportDate -- should SKIP, not UPDATE
    LocalDateTime captureDate = LocalDateTime.of(2026, 1, 15, 14, 23, 5);
    LocalDateTime exportDate = LocalDateTime.of(2026, 1, 15, 10, 0);
    ImageProcessingService.PreparedImageData prepared =
        createPreparedImageData("photo.jpg", captureDate, exportDate);

    ContentImageEntity existing = createContentImageEntity();
    existing.setLastExportDate(null); // migrated row
    when(contentRepository.findByOriginalFilenameAndCaptureDate("photo.jpg", captureDate))
        .thenReturn(Optional.of(existing));

    // Act
    ImageProcessingService.DedupeResult result =
        imageProcessingService.savePreparedImageWithDedupe(prepared, null);

    // Assert
    assertEquals(ImageProcessingService.DedupeAction.SKIP, result.action());
    verify(contentRepository, never()).saveImage(any());
  }

  @Test
  void savePreparedImageWithDedupe_update_whenNewerExportDate() {
    // Arrange
    LocalDateTime captureDate = LocalDateTime.of(2026, 1, 15, 14, 23, 5);
    LocalDateTime oldExportDate = LocalDateTime.of(2026, 1, 15, 10, 0);
    LocalDateTime newExportDate = LocalDateTime.of(2026, 3, 1, 12, 0);
    ImageProcessingService.PreparedImageData prepared =
        createPreparedImageData("photo.jpg", captureDate, newExportDate);

    ContentImageEntity existing = createContentImageEntity();
    existing.setLastExportDate(oldExportDate);
    existing.setImageUrlWeb("https://cdn/old-web.webp");
    existing.setImageUrlOriginal("https://cdn/old-full.jpg");
    when(contentRepository.findByOriginalFilenameAndCaptureDate("photo.jpg", captureDate))
        .thenReturn(Optional.of(existing));
    when(contentRepository.saveImage(any(ContentImageEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    ImageProcessingService.DedupeResult result =
        imageProcessingService.savePreparedImageWithDedupe(prepared, null);

    // Assert
    assertEquals(ImageProcessingService.DedupeAction.UPDATE, result.action());
    assertEquals("https://cdn/web/image.webp", result.entity().getImageUrlWeb());
    assertEquals("https://cdn/full/image.jpg", result.entity().getImageUrlOriginal());
    assertEquals(newExportDate, result.entity().getLastExportDate());
    verify(contentRepository).saveImage(any(ContentImageEntity.class));
  }

  @Test
  void savePreparedImageWithDedupe_create_whenCaptureDateIsNull() {
    // Arrange: no captureDate means dedupe is bypassed entirely
    ImageProcessingService.PreparedImageData prepared =
        createPreparedImageData("photo.jpg", null, LocalDateTime.now());

    ContentImageEntity savedEntity = createContentImageEntity();
    when(contentRepository.saveImage(any(ContentImageEntity.class))).thenReturn(savedEntity);

    // Act
    ImageProcessingService.DedupeResult result =
        imageProcessingService.savePreparedImageWithDedupe(prepared, "Test");

    // Assert
    assertEquals(ImageProcessingService.DedupeAction.CREATE, result.action());
    // Should never attempt dedupe lookup
    verify(contentRepository, never()).findByOriginalFilenameAndCaptureDate(any(), any());
  }

  // Helper methods

  private ContentImageEntity createContentImageEntity() {
    ContentImageEntity entity = new ContentImageEntity();
    entity.setId(1L);
    entity.setContentType(ContentType.IMAGE);
    entity.setCreatedAt(LocalDateTime.now());
    entity.setUpdatedAt(LocalDateTime.now());
    entity.setTitle("Test Image");
    entity.setImageWidth(800);
    entity.setImageHeight(600);
    entity.setIso(100);
    entity.setAuthor("Test Author");
    entity.setRating(5);
    entity.setFStop("f/2.8");
    entity.setLens(new ContentLensEntity("Test Lens"));
    entity.setBlackAndWhite(false);
    entity.setIsFilm(false);
    entity.setShutterSpeed("1/125");
    entity.setCamera(new ContentCameraEntity("Test Camera"));
    entity.setFocalLength("50mm");
    entity.setLocationId(1L);
    entity.setImageUrlWeb("https://example.com/image.jpg");
    entity.setCaptureDate(LocalDateTime.of(2023, 1, 1, 0, 0));
    entity.setOriginalFilename("test-image.jpg");
    return entity;
  }
}
