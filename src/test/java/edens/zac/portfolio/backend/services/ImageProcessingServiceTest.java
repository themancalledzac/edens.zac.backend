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
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.s3.S3Client;

@ExtendWith(MockitoExtension.class)
class ImageProcessingServiceTest {

  @Mock private S3Client s3Client;
  @Mock private CloudFrontClient cloudFrontClient;
  @Mock private ContentRepository contentRepository;
  @Mock private EquipmentRepository equipmentRepository;
  @Mock private LocationRepository locationRepository;
  @Mock private ImageMetadataExtractor imageMetadataExtractor;
  @Mock private ContentValidator contentValidator;

  private ImageProcessingService imageProcessingService;

  private static final String BUCKET_NAME = "test-bucket";
  private static final String CLOUDFRONT_DOMAIN = "test.cloudfront.net";
  private static final String CLOUDFRONT_DISTRIBUTION_ID = "";

  @BeforeEach
  void setUp() {
    imageProcessingService =
        new ImageProcessingService(
            s3Client,
            cloudFrontClient,
            contentRepository,
            equipmentRepository,
            locationRepository,
            imageMetadataExtractor,
            contentValidator,
            BUCKET_NAME,
            CLOUDFRONT_DOMAIN,
            CLOUDFRONT_DISTRIBUTION_ID);
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
        null,
        null,
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
  void savePreparedImageWithDedupe_update_whenExistingHasNullExportDate() {
    // Arrange: migrated rows have null lastExportDate -- should UPDATE (null = "unknown/old")
    LocalDateTime captureDate = LocalDateTime.of(2026, 1, 15, 14, 23, 5);
    LocalDateTime exportDate = LocalDateTime.of(2026, 1, 15, 10, 0);
    ImageProcessingService.PreparedImageData prepared =
        createPreparedImageData("photo.jpg", captureDate, exportDate);

    ContentImageEntity existing = createContentImageEntity();
    existing.setLastExportDate(null); // migrated row — treat as old
    when(contentRepository.findByOriginalFilenameAndCaptureDate("photo.jpg", captureDate))
        .thenReturn(Optional.of(existing));
    when(contentRepository.saveImage(any())).thenReturn(existing);

    // Act
    ImageProcessingService.DedupeResult result =
        imageProcessingService.savePreparedImageWithDedupe(prepared, null);

    // Assert: null export date = always update
    assertEquals(ImageProcessingService.DedupeAction.UPDATE, result.action());
    verify(contentRepository).saveImage(any());
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

  @Test
  void savePreparedImageWithDedupe_create_passesNullCreatedAtToDao_whenExifCreateDateAbsent() {
    // Regression for film scans: no EXIF DateTimeOriginal means
    // parseExifDateToLocalDateTime(null) returns null. The service hands a null createdAt
    // to ContentRepository.saveImage, which is responsible for falling back to upload time
    // so the NOT NULL constraint on content.created_at is satisfied.
    ImageProcessingService.PreparedImageData prepared =
        createPreparedImageData("film_scan.jpg", null, LocalDateTime.now());
    when(imageMetadataExtractor.parseExifDateToLocalDateTime(null)).thenReturn(null);

    ContentImageEntity savedEntity = createContentImageEntity();
    when(contentRepository.saveImage(any(ContentImageEntity.class))).thenReturn(savedEntity);

    ImageProcessingService.DedupeResult result =
        imageProcessingService.savePreparedImageWithDedupe(prepared, "Film Scan");

    ArgumentCaptor<ContentImageEntity> captor = ArgumentCaptor.forClass(ContentImageEntity.class);
    verify(contentRepository).saveImage(captor.capture());
    assertNull(
        captor.getValue().getCreatedAt(),
        "absent EXIF createDate -> null entity.createdAt -> DAO fills with upload time");
    assertEquals(ImageProcessingService.DedupeAction.CREATE, result.action());
  }

  @Test
  void savePreparedImageWithDedupe_update_preservesExistingRating_whenExportOmitsRatingTag() {
    // A re-export that omits the rating tag must not clobber the curated rating (existing = 5; the
    // prepared metadata carries no "rating" key).
    LocalDateTime captureDate = LocalDateTime.of(2026, 1, 15, 14, 23, 5);
    LocalDateTime oldExportDate = LocalDateTime.of(2026, 1, 15, 10, 0);
    LocalDateTime newExportDate = LocalDateTime.of(2026, 3, 1, 12, 0);
    ImageProcessingService.PreparedImageData prepared =
        createPreparedImageData("photo.jpg", captureDate, newExportDate);

    ContentImageEntity existing = createContentImageEntity(); // rating = 5
    existing.setLastExportDate(oldExportDate);
    when(contentRepository.findByOriginalFilenameAndCaptureDate("photo.jpg", captureDate))
        .thenReturn(Optional.of(existing));
    when(contentRepository.saveImage(any(ContentImageEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ImageProcessingService.DedupeResult result =
        imageProcessingService.savePreparedImageWithDedupe(prepared, null);

    assertEquals(ImageProcessingService.DedupeAction.UPDATE, result.action());
    assertEquals(
        Integer.valueOf(5),
        result.entity().getRating(),
        "re-export without a rating tag must preserve the existing curated rating");
  }

  // ============================================================================
  // Tests for recordRenditionDimensions (re-upload dimension fix)
  // ============================================================================

  @Test
  void recordRenditionDimensions_overridesStaleExifDimensions() {
    // Stale EXIF dims vs a differently-shaped web rendition: stored dims must follow the rendition.
    Map<String, String> metadata = new HashMap<>();
    metadata.put("imageWidth", "2500");
    metadata.put("imageHeight", "2143");
    BufferedImage rendition = new BufferedImage(2500, 1250, BufferedImage.TYPE_INT_RGB);

    imageProcessingService.recordRenditionDimensions(rendition, metadata);

    assertEquals("2500", metadata.get("imageWidth"));
    assertEquals("1250", metadata.get("imageHeight"));
  }

  @Test
  void recordRenditionDimensions_populatesDimensions_whenAbsent() {
    Map<String, String> metadata = new HashMap<>();
    BufferedImage rendition = new BufferedImage(400, 600, BufferedImage.TYPE_INT_RGB);

    imageProcessingService.recordRenditionDimensions(rendition, metadata);

    assertEquals("400", metadata.get("imageWidth"));
    assertEquals("600", metadata.get("imageHeight"));
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
    entity.setImageUrlWeb("https://example.com/image.jpg");
    entity.setCaptureDate(LocalDateTime.of(2023, 1, 1, 0, 0));
    entity.setOriginalFilename("test-image.jpg");
    return entity;
  }

  // ============================================================================
  // Tests for content-addressed web keys (hashedWebFilename / contentHash)
  // ============================================================================

  @Test
  void hashedWebFilename_embedsHashAndKeepsSingleWebExtension() {
    String name = imageProcessingService.hashedWebFilename("DSC_0559.jpg", "bytes".getBytes());
    assertTrue(
        name.matches("DSC_0559\\.[0-9a-f]{12}\\.webp"),
        "expected DSC_0559.<12-hex>.webp but was: " + name);
  }

  @Test
  void hashedWebFilename_stripsExistingWebpExtensionWithoutDoubling() {
    String name = imageProcessingService.hashedWebFilename("DSC_0559.webp", "bytes".getBytes());
    assertTrue(name.matches("DSC_0559\\.[0-9a-f]{12}\\.webp"), name);
    assertFalse(name.contains(".webp.webp"), "must not double the extension");
  }

  @Test
  void hashedWebFilename_differentBytesProduceDifferentKeys() {
    String a = imageProcessingService.hashedWebFilename("DSC_0559.jpg", "version-a".getBytes());
    String b = imageProcessingService.hashedWebFilename("DSC_0559.jpg", "version-b".getBytes());
    assertNotEquals(a, b, "different bytes must yield different content-addressed keys");
  }

  @Test
  void hashedWebFilename_identicalBytesAreIdempotent() {
    byte[] bytes = "same-export".getBytes();
    String a = imageProcessingService.hashedWebFilename("DSC_0559.jpg", bytes);
    String b = imageProcessingService.hashedWebFilename("DSC_0559.jpg", bytes);
    assertEquals(a, b, "identical bytes must yield the identical key (idempotent re-export)");
  }

  @Test
  void contentHash_isTwelveLowercaseHexChars() {
    String h = imageProcessingService.contentHash("anything".getBytes());
    assertTrue(h.matches("[0-9a-f]{12}"), "expected 12 lowercase hex chars but was: " + h);
  }
}
