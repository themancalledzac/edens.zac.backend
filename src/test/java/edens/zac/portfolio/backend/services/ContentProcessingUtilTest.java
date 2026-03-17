package edens.zac.portfolio.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.EquipmentRepository;
import edens.zac.portfolio.backend.dao.LocationRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.*;
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.services.validator.ContentImageUpdateValidator;
import edens.zac.portfolio.backend.services.validator.ContentValidator;
import edens.zac.portfolio.backend.types.ContentType;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
public class ContentProcessingUtilTest {

  @Mock private S3Client s3Client;
  @Mock private ContentRepository contentRepository;
  @Mock private CollectionRepository collectionRepository;
  @Mock private EquipmentRepository equipmentRepository;
  @Mock private TagRepository tagRepository;
  @Mock private PersonRepository personRepository;
  @Mock private LocationRepository locationRepository;
  @Mock private ContentImageUpdateValidator contentImageUpdateValidator;
  @Mock private ContentValidator contentValidator;

  @InjectMocks private ContentProcessingUtil contentProcessingUtil;

  private static final String BUCKET_NAME = "test-bucket";
  private static final String CLOUDFRONT_DOMAIN = "test.cloudfront.net";

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(contentProcessingUtil, "bucketName", BUCKET_NAME);
    ReflectionTestUtils.setField(contentProcessingUtil, "cloudfrontDomain", CLOUDFRONT_DOMAIN);

    // Mock tagRepository.findContentTags() to return empty lists for all content IDs
    // Using lenient() because not all tests use this mock
    lenient()
        .when(tagRepository.findContentTags(anyLong()))
        .thenReturn(java.util.Collections.emptyList());
  }

  @Test
  void convertImageModel() {
    // Arrange
    ContentImageEntity entity = createContentImageEntity();
    // Mock location lookup
    LocationEntity locationEntity =
        LocationEntity.builder().id(1L).locationName("Test Location").build();
    when(locationRepository.findById(1L)).thenReturn(java.util.Optional.of(locationEntity));

    // Act
    ContentModel result = contentProcessingUtil.convertRegularContentEntityToModel(entity);

    // Assert
    assertInstanceOf(ContentModels.Image.class, result);
    ContentModels.Image imageModel = (ContentModels.Image) result;
    assertEquals(entity.getId(), imageModel.id());
    assertEquals(entity.getContentType(), imageModel.contentType());
    assertEquals(entity.getTitle(), imageModel.title());
    assertEquals(entity.getImageWidth(), imageModel.imageWidth());
    assertEquals(entity.getImageHeight(), imageModel.imageHeight());
    assertEquals(entity.getIso(), imageModel.iso());
    assertEquals(entity.getAuthor(), imageModel.author());
    assertEquals(entity.getRating(), imageModel.rating());
    assertEquals(entity.getFStop(), imageModel.fStop());
    assertEquals(entity.getLens().getLensName(), imageModel.lens().name());
    assertEquals(entity.getBlackAndWhite(), imageModel.blackAndWhite());
    assertEquals(entity.getIsFilm(), imageModel.isFilm());
    assertEquals(entity.getShutterSpeed(), imageModel.shutterSpeed());
    assertEquals(entity.getCamera().getCameraName(), imageModel.camera().name());
    assertEquals(entity.getFocalLength(), imageModel.focalLength());
    assertNotNull(imageModel.location());
    assertEquals("Test Location", imageModel.location().name());
    assertEquals(1L, imageModel.location().id());
    assertEquals(entity.getCaptureDate(), imageModel.captureDate());
  }

  @Test
  void convertModel() {
    // Arrange
    ContentTextEntity entity = createTextContentEntity();

    // Act
    ContentModel result = contentProcessingUtil.convertRegularContentEntityToModel(entity);

    // Assert
    assertInstanceOf(ContentModels.Text.class, result);
    ContentModels.Text textModel = (ContentModels.Text) result;
    assertEquals(entity.getId(), textModel.id());
    assertEquals(entity.getContentType(), textModel.contentType());
    assertEquals(entity.getTextContent(), textModel.textContent());
    assertEquals(entity.getFormatType(), textModel.formatType());
  }

  @Test
  void convertGifModel() {
    // Arrange
    ContentGifEntity entity = createContentGifEntity();

    // Act
    ContentModel result = contentProcessingUtil.convertRegularContentEntityToModel(entity);

    // Assert
    assertInstanceOf(ContentModels.Gif.class, result);
    ContentModels.Gif gifModel = (ContentModels.Gif) result;
    assertEquals(entity.getId(), gifModel.id());
    assertEquals(entity.getContentType(), gifModel.contentType());
    assertEquals(entity.getTitle(), gifModel.title());
    assertEquals(entity.getGifUrl(), gifModel.gifUrl());
    assertEquals(entity.getThumbnailUrl(), gifModel.thumbnailUrl());
    assertEquals(entity.getWidth(), gifModel.width());
    assertEquals(entity.getHeight(), gifModel.height());
    assertEquals(entity.getAuthor(), gifModel.author());
    assertEquals(entity.getCreateDate(), gifModel.createDate());
  }

  @Test
  void convertEntityToModel_withUnknownBlockType_shouldThrowException() {
    // Arrange
    ContentEntity entity = mock(ContentEntity.class);
    when(entity.getContentType()).thenReturn(null);

    // Act & Assert
    Exception exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              contentProcessingUtil.convertRegularContentEntityToModel(entity);
            });
    assertTrue(exception.getMessage().contains("Unknown content type"));
  }

  @Disabled
  @Test
  void processContentImage_withValidImage_shouldReturnImageContentEntity() throws IOException {
    // Arrange
    MultipartFile file = createMockImageFile();
    Long collectionId = 1L;
    Integer orderIndex = 0;
    String title = "Test Image";
    String caption = "Test Caption";

    // Mock S3 upload (new implementation uploads directly to S3)
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());

    ContentImageEntity savedEntity = createContentImageEntity();
    when(contentRepository.saveImage(any(ContentImageEntity.class))).thenReturn(savedEntity);

    // Act
    ContentEntity result = contentProcessingUtil.processImageContent(file, title);

    // Assert
    assertNotNull(result);
    assertInstanceOf(ContentImageEntity.class, result);
    verify(s3Client, times(2))
        .putObject(any(PutObjectRequest.class), any(RequestBody.class)); // Verify
    // S3
    // upload
    // was
    // called
    // twice
    // (full
    // +
    // webP)
    verify(contentRepository).saveImage(any(ContentImageEntity.class));
  }

  @Test
  void processContentImage_whenImageProcessingFails_shouldThrowException() throws IOException {
    // Arrange
    MultipartFile file = createMockImageFile();
    String title = "Test Image";

    // Act & Assert
    // Note: May throw UnsatisfiedLinkError if native image libraries aren't
    // available in test
    // environment
    // or RuntimeException if S3 upload fails. We don't mock S3 here because the
    // code may fail
    // earlier
    // during image processing before reaching S3 upload.
    Throwable exception =
        assertThrows(
            Throwable.class,
            () -> {
              contentProcessingUtil.processImageContent(file, title);
            });
    assertTrue(exception instanceof RuntimeException || exception instanceof UnsatisfiedLinkError);
  }

  @Disabled("WebP native library architecture mismatch in local test environment")
  @Test
  void processGifContent_withValidGif_shouldSaveAndReturnEntity() throws IOException {
    // Arrange
    MultipartFile file = createMockGifFile();
    String title = "Test GIF";

    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());

    ContentGifEntity savedEntity = createContentGifEntity();
    when(contentRepository.saveGif(any(ContentGifEntity.class))).thenReturn(savedEntity);

    // Act
    ContentGifEntity result = contentProcessingUtil.processGifContent(file, title);

    // Assert
    assertNotNull(result);
    assertEquals(savedEntity.getId(), result.getId());
    verify(contentRepository).saveGif(any(ContentGifEntity.class));
  }

  @Test
  void processGifContent_withEmptyFile_shouldThrowException() {
    // Arrange
    MultipartFile file = new MockMultipartFile("file", "test.gif", "image/gif", new byte[0]);

    doThrow(new IllegalArgumentException("GIF/MP4 file cannot be empty"))
        .when(contentValidator)
        .validateGifFile(file);

    // Act & Assert
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          contentProcessingUtil.processGifContent(file, "Test GIF");
        });
  }

  @Disabled
  @Test
  void processContentBlock_withImageType_shouldCallProcessImageContent() throws IOException {
    // Arrange
    MultipartFile file = createMockImageFile();
    ContentType type = ContentType.IMAGE;
    Long collectionId = 1L;
    Integer orderIndex = 0;
    String content = null;
    String language = null;
    String title = "Test Image";
    String caption = "Test Caption";

    // Mock S3 upload (new implementation uploads directly to S3)
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());

    ContentImageEntity imageEntity = createContentImageEntity();
    when(contentRepository.saveImage(any(ContentImageEntity.class))).thenReturn(imageEntity);

    // Act
    ContentEntity result = contentProcessingUtil.processImageContent(file, title);

    // Assert
    assertNotNull(result);
    assertInstanceOf(ContentImageEntity.class, result);
    verify(s3Client, times(2))
        .putObject(any(PutObjectRequest.class), any(RequestBody.class)); // Verify
    // S3
    // upload
    // was
    // called
    // twice
    // (full
    // +
    // webP)
    verify(contentRepository).saveImage(any(ContentImageEntity.class));
  }

  // ============================================================================
  // Tests for parseCaptureDateToLocalDate
  // ============================================================================

  @Test
  void parseCaptureDateToLocalDate_withExifFormat_shouldReturnLocalDate() {
    LocalDate result = contentProcessingUtil.parseCaptureDateToLocalDate("2026:01:26 17:48:38");
    assertEquals(LocalDate.of(2026, 1, 26), result);
  }

  @Test
  void parseCaptureDateToLocalDate_withIsoFormat_shouldReturnLocalDate() {
    LocalDate result = contentProcessingUtil.parseCaptureDateToLocalDate("2026-01-26");
    assertEquals(LocalDate.of(2026, 1, 26), result);
  }

  @Test
  void parseCaptureDateToLocalDate_withNull_shouldReturnNull() {
    assertNull(contentProcessingUtil.parseCaptureDateToLocalDate(null));
  }

  @Test
  void parseCaptureDateToLocalDate_withEmptyString_shouldReturnNull() {
    assertNull(contentProcessingUtil.parseCaptureDateToLocalDate(""));
  }

  @Test
  void parseCaptureDateToLocalDate_withMalformedString_shouldReturnNull() {
    assertNull(contentProcessingUtil.parseCaptureDateToLocalDate("not-a-date"));
  }

  @Test
  void parseCaptureDateToLocalDate_withShortString_shouldReturnNull() {
    assertNull(contentProcessingUtil.parseCaptureDateToLocalDate("2026"));
  }

  // ============================================================================
  // Tests for savePreparedImageWithDedupe
  // ============================================================================

  private ContentProcessingUtil.PreparedImageData createPreparedImageData(
      String filename, LocalDate captureDate, LocalDateTime lastExportDate) {
    Map<String, String> metadata =
        Map.of(
            "imageWidth", "800",
            "imageHeight", "600",
            "author", "Test Author",
            "fStop", "f/2.8",
            "shutterSpeed", "1/125",
            "iso", "100",
            "focalLength", "50mm");
    return new ContentProcessingUtil.PreparedImageData(
        filename,
        "https://cdn/full/image.jpg",
        "https://cdn/web/image.webp",
        metadata,
        2026,
        1,
        captureDate,
        lastExportDate);
  }

  @Test
  void savePreparedImageWithDedupe_create_whenNoDuplicateExists() {
    // Arrange
    LocalDate captureDate = LocalDate.of(2026, 1, 15);
    LocalDateTime exportDate = LocalDateTime.of(2026, 1, 15, 10, 0);
    ContentProcessingUtil.PreparedImageData prepared =
        createPreparedImageData("photo.jpg", captureDate, exportDate);

    when(contentRepository.findByOriginalFilenameAndCaptureDate("photo.jpg", captureDate))
        .thenReturn(Optional.empty());

    ContentImageEntity savedEntity = createContentImageEntity();
    when(contentRepository.saveImage(any(ContentImageEntity.class))).thenReturn(savedEntity);

    // Act
    ContentProcessingUtil.DedupeResult result =
        contentProcessingUtil.savePreparedImageWithDedupe(prepared, "Test");

    // Assert
    assertEquals(ContentProcessingUtil.DedupeAction.CREATE, result.action());
    assertNotNull(result.entity());
    verify(contentRepository).saveImage(any(ContentImageEntity.class));
  }

  @Test
  void savePreparedImageWithDedupe_skip_whenSameExportDate() {
    // Arrange
    LocalDate captureDate = LocalDate.of(2026, 1, 15);
    LocalDateTime exportDate = LocalDateTime.of(2026, 1, 15, 10, 0);
    ContentProcessingUtil.PreparedImageData prepared =
        createPreparedImageData("photo.jpg", captureDate, exportDate);

    ContentImageEntity existing = createContentImageEntity();
    existing.setLastExportDate(exportDate); // same export date
    when(contentRepository.findByOriginalFilenameAndCaptureDate("photo.jpg", captureDate))
        .thenReturn(Optional.of(existing));

    // Act
    ContentProcessingUtil.DedupeResult result =
        contentProcessingUtil.savePreparedImageWithDedupe(prepared, null);

    // Assert
    assertEquals(ContentProcessingUtil.DedupeAction.SKIP, result.action());
    assertEquals(existing, result.entity());
    verify(contentRepository, never()).saveImage(any());
  }

  @Test
  void savePreparedImageWithDedupe_skip_whenExistingHasNullExportDate() {
    // Arrange: migrated rows have null lastExportDate -- should SKIP, not UPDATE
    LocalDate captureDate = LocalDate.of(2026, 1, 15);
    LocalDateTime exportDate = LocalDateTime.of(2026, 1, 15, 10, 0);
    ContentProcessingUtil.PreparedImageData prepared =
        createPreparedImageData("photo.jpg", captureDate, exportDate);

    ContentImageEntity existing = createContentImageEntity();
    existing.setLastExportDate(null); // migrated row
    when(contentRepository.findByOriginalFilenameAndCaptureDate("photo.jpg", captureDate))
        .thenReturn(Optional.of(existing));

    // Act
    ContentProcessingUtil.DedupeResult result =
        contentProcessingUtil.savePreparedImageWithDedupe(prepared, null);

    // Assert
    assertEquals(ContentProcessingUtil.DedupeAction.SKIP, result.action());
    verify(contentRepository, never()).saveImage(any());
  }

  @Test
  void savePreparedImageWithDedupe_update_whenNewerExportDate() {
    // Arrange
    LocalDate captureDate = LocalDate.of(2026, 1, 15);
    LocalDateTime oldExportDate = LocalDateTime.of(2026, 1, 15, 10, 0);
    LocalDateTime newExportDate = LocalDateTime.of(2026, 3, 1, 12, 0);
    ContentProcessingUtil.PreparedImageData prepared =
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
    ContentProcessingUtil.DedupeResult result =
        contentProcessingUtil.savePreparedImageWithDedupe(prepared, null);

    // Assert
    assertEquals(ContentProcessingUtil.DedupeAction.UPDATE, result.action());
    assertEquals("https://cdn/web/image.webp", result.entity().getImageUrlWeb());
    assertEquals("https://cdn/full/image.jpg", result.entity().getImageUrlOriginal());
    assertEquals(newExportDate, result.entity().getLastExportDate());
    verify(contentRepository).saveImage(any(ContentImageEntity.class));
  }

  @Test
  void savePreparedImageWithDedupe_create_whenCaptureDateIsNull() {
    // Arrange: no captureDate means dedupe is bypassed entirely
    ContentProcessingUtil.PreparedImageData prepared =
        createPreparedImageData("photo.jpg", null, LocalDateTime.now());

    ContentImageEntity savedEntity = createContentImageEntity();
    when(contentRepository.saveImage(any(ContentImageEntity.class))).thenReturn(savedEntity);

    // Act
    ContentProcessingUtil.DedupeResult result =
        contentProcessingUtil.savePreparedImageWithDedupe(prepared, "Test");

    // Assert
    assertEquals(ContentProcessingUtil.DedupeAction.CREATE, result.action());
    // Should never attempt dedupe lookup
    verify(contentRepository, never()).findByOriginalFilenameAndCaptureDate(any(), any());
  }

  // Helper methods to create test entities and models

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
    entity.setCaptureDate(java.time.LocalDate.of(2023, 1, 1));
    entity.setOriginalFilename("test-image.jpg");
    return entity;
  }

  private ContentTextEntity createTextContentEntity() {
    ContentTextEntity entity = new ContentTextEntity();
    entity.setId(2L);
    entity.setContentType(ContentType.TEXT);
    entity.setCreatedAt(LocalDateTime.now());
    entity.setUpdatedAt(LocalDateTime.now());
    entity.setTextContent("This is test content");
    entity.setFormatType("markdown");
    return entity;
  }

  private ContentGifEntity createContentGifEntity() {
    ContentGifEntity entity = new ContentGifEntity();
    entity.setId(4L);
    entity.setContentType(ContentType.GIF);
    entity.setCreatedAt(LocalDateTime.now());
    entity.setUpdatedAt(LocalDateTime.now());
    entity.setTitle("Test GIF");
    entity.setGifUrl("https://example.com/test.gif");
    entity.setThumbnailUrl("https://example.com/test-thumbnail.jpg");
    entity.setWidth(400);
    entity.setHeight(300);
    entity.setAuthor("Test Author");
    entity.setCreateDate("2023-01-01");
    return entity;
  }

  private MultipartFile createMockImageFile() throws IOException {
    // Create a simple 10x10 image as WebP to avoid conversion issues in tests
    BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    // Use PNG as a proxy for WebP in tests since WebP writer may not be available
    ImageIO.write(image, "png", baos);
    return new MockMultipartFile("file", "test.webp", "image/webp", baos.toByteArray());
  }

  private MultipartFile createMockGifFile() throws IOException {
    // Create a simple 10x10 image as a mock GIF
    BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(image, "gif", baos);
    return new MockMultipartFile("file", "test.gif", "image/gif", baos.toByteArray());
  }
}
