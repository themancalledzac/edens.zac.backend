package edens.zac.portfolio.backend.services;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import edens.zac.portfolio.backend.entity.*;
import edens.zac.portfolio.backend.model.*;
import edens.zac.portfolio.backend.repository.ContentRepository;
import edens.zac.portfolio.backend.types.ContentType;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ContentProcessingUtilTest {

    @Mock
    private AmazonS3 amazonS3;

    @Mock
    private ContentRepository contentRepository;

    @InjectMocks
    private ContentProcessingUtil contentProcessingUtil;

    private static final String BUCKET_NAME = "test-bucket";
    private static final String CLOUDFRONT_DOMAIN = "test.cloudfront.net";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(contentProcessingUtil, "bucketName", BUCKET_NAME);
        ReflectionTestUtils.setField(contentProcessingUtil, "cloudfrontDomain", CLOUDFRONT_DOMAIN);
    }

    @Test
    void convertToModel_withNullEntity_shouldReturnNull() {
        // Act
        ContentModel result = contentProcessingUtil.convertToModel(null);

        // Assert
        assertNull(result);
    }

    @Test
    void convertToModel_withContentImage_shouldReturnContentImageModel() {
        // Arrange
        ContentImageEntity entity = createContentImageEntity();

        // Act
        ContentModel result = contentProcessingUtil.convertToModel(entity);

        // Assert
        assertInstanceOf(ImageContentModel.class, result);
        ImageContentModel imageModel = (ImageContentModel) result;
        assertEquals(entity.getId(), imageModel.getId());
        assertEquals(entity.getCollectionId(), imageModel.getCollectionId());
        assertEquals(entity.getOrderIndex(), imageModel.getOrderIndex());
        assertEquals(entity.getContentType(), imageModel.getContentType());
        assertEquals(entity.getCaption(), imageModel.getCaption());
        assertEquals(entity.getTitle(), imageModel.getTitle());
        assertEquals(entity.getImageWidth(), imageModel.getImageWidth());
        assertEquals(entity.getImageHeight(), imageModel.getImageHeight());
        assertEquals(entity.getIso(), imageModel.getIso());
        assertEquals(entity.getAuthor(), imageModel.getAuthor());
        assertEquals(entity.getRating(), imageModel.getRating());
        assertEquals(entity.getFStop(), imageModel.getFStop());
        assertEquals(entity.getLens().getLensName(), imageModel.getLens().getName());
        assertEquals(entity.getBlackAndWhite(), imageModel.getBlackAndWhite());
        assertEquals(entity.getIsFilm(), imageModel.getIsFilm());
        assertEquals(entity.getShutterSpeed(), imageModel.getShutterSpeed());
        assertEquals(entity.getCamera(), imageModel.getCamera()); // TODO: Fix
        assertEquals(entity.getFocalLength(), imageModel.getFocalLength());
        assertEquals(entity.getLocation(), imageModel.getLocation());
        assertEquals(entity.getImageUrlWeb(), imageModel.getImageUrlWeb());
        assertEquals(entity.getCreateDate(), imageModel.getCreateDate());
    }

    @Test
    void convertToModel_withTextContent_shouldReturnTextContentModel() {
        // Arrange
        TextContentEntity entity = createTextContentEntity();

        // Act
        ContentModel result = contentProcessingUtil.convertToModel(entity);

        // Assert
        assertInstanceOf(TextContentModel.class, result);
        TextContentModel textModel = (TextContentModel) result;
        assertEquals(entity.getId(), textModel.getId());
        assertEquals(entity.getCollectionId(), textModel.getCollectionId());
        assertEquals(entity.getOrderIndex(), textModel.getOrderIndex());
        assertEquals(entity.getContentType(), textModel.getContentType());
        assertEquals(entity.getCaption(), textModel.getCaption());
        assertEquals(entity.getContent(), textModel.getContent());
        assertEquals(entity.getFormatType(), textModel.getFormatType());
    }

    @Test
    void convertToModel_withContentCode_shouldReturnContentCodeModel() {
        // Arrange
        ContentCodeEntity entity = createContentCodeEntity();

        // Act
        ContentModel result = contentProcessingUtil.convertToModel(entity);

        // Assert
        assertInstanceOf(ContentCodeModel.class, result);
        ContentCodeModel codeModel = (ContentCodeModel) result;
        assertEquals(entity.getId(), codeModel.getId());
        assertEquals(entity.getCollectionId(), codeModel.getCollectionId());
        assertEquals(entity.getOrderIndex(), codeModel.getOrderIndex());
        assertEquals(entity.getContentType(), codeModel.getContentType());
        assertEquals(entity.getCaption(), codeModel.getCaption());
        assertEquals(entity.getCode(), codeModel.getCode());
        assertEquals(entity.getLanguage(), codeModel.getLanguage());
        assertEquals(entity.getTitle(), codeModel.getTitle());
        assertTrue(codeModel.getShowLineNumbers()); // Default value
    }

    @Test
    void convertToModel_withContentGif_shouldReturnContentGifModel() {
        // Arrange
        ContentGifEntity entity = createContentGifEntity();

        // Act
        ContentModel result = contentProcessingUtil.convertToModel(entity);

        // Assert
        assertInstanceOf(ContentGifModel.class, result);
        ContentGifModel gifModel = (ContentGifModel) result;
        assertEquals(entity.getId(), gifModel.getId());
        assertEquals(entity.getCollectionId(), gifModel.getCollectionId());
        assertEquals(entity.getOrderIndex(), gifModel.getOrderIndex());
        assertEquals(entity.getContentType(), gifModel.getContentType());
        assertEquals(entity.getCaption(), gifModel.getCaption());
        assertEquals(entity.getTitle(), gifModel.getTitle());
        assertEquals(entity.getGifUrl(), gifModel.getGifUrl());
        assertEquals(entity.getThumbnailUrl(), gifModel.getThumbnailUrl());
        assertEquals(entity.getWidth(), gifModel.getWidth());
        assertEquals(entity.getHeight(), gifModel.getHeight());
        assertEquals(entity.getAuthor(), gifModel.getAuthor());
        assertEquals(entity.getCreateDate(), gifModel.getCreateDate());
    }

    @Test
    void convertToModel_withUnknownBlockType_shouldThrowException() {
        // Arrange
        ContentEntity entity = mock(ContentEntity.class);
        when(entity.getContentType()).thenReturn(null);

        // Act & Assert
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            contentProcessingUtil.convertToModel(entity);
        });
        assertTrue(exception.getMessage().contains("Unknown content block type"));
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
        when(amazonS3.putObject(any(PutObjectRequest.class))).thenReturn(null);

        ContentImageEntity savedEntity = createContentImageEntity();
        when(contentRepository.save(any(ContentImageEntity.class))).thenReturn(savedEntity);

        // Act
        ContentEntity result = contentProcessingUtil.processImageContent(file, collectionId, orderIndex, title, caption);

        // Assert
        assertNotNull(result);
        assertInstanceOf(ContentImageEntity.class, result);
        verify(amazonS3, times(2)).putObject(any(PutObjectRequest.class)); // Verify S3 upload was called twice (full + webP)
        verify(contentRepository).save(any(ContentImageEntity.class));
    }

    @Test
    void processContentImage_whenImageProcessingFails_shouldReturnNull() throws IOException {
        // Arrange
        MultipartFile file = createMockImageFile();
        Long collectionId = 1L;
        Integer orderIndex = 0;
        String title = "Test Image";
        String caption = "Test Caption";

        // Mock S3 to throw an exception (simulating upload failure)
        when(amazonS3.putObject(any(PutObjectRequest.class))).thenThrow(new RuntimeException("S3 upload failed"));

        // Act
        ContentEntity result = contentProcessingUtil.processImageContent(file, collectionId, orderIndex, title, caption);

        // Assert
        assertNull(result); // Should return null when processing fails
    }

    @Test
    void processTextContent_withValidText_shouldReturnTextContentEntity() {
        // Arrange
        String text = "This is test content";
        Long collectionId = 1L;
        Integer orderIndex = 0;
        String caption = "Test Caption";

        TextContentEntity savedEntity = createTextContentEntity();
        when(contentRepository.save(any(TextContentEntity.class))).thenReturn(savedEntity);

        // Act
        TextContentEntity result = contentProcessingUtil.processTextContent(text, collectionId, orderIndex, caption);

        // Assert
        assertNotNull(result);
        assertInstanceOf(TextContentEntity.class, result);
        assertEquals(text, result.getContent());
        verify(contentRepository).save(any(TextContentEntity.class));
    }

    @Test
    void processTextContent_withEmptyText_shouldThrowException() {
        // Arrange
        String text = "";
        Long collectionId = 1L;
        Integer orderIndex = 0;
        String caption = "Test Caption";

        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, () -> {
            contentProcessingUtil.processTextContent(text, collectionId, orderIndex, caption);
        });
        assertTrue(exception.getMessage().contains("Failed to process text content block"));
    }

    @Test
    void processContentCode_withValidCode_shouldReturnCodeContentEntity() {
        // Arrange
        String code = "public class Test { }";
        String language = "java";
        Long collectionId = 1L;
        Integer orderIndex = 0;
        String title = "Test Code";
        String caption = "Test Caption";

        ContentCodeEntity savedEntity = createContentCodeEntity();
        when(contentRepository.save(any(ContentCodeEntity.class))).thenReturn(savedEntity);

        // Act
        ContentCodeEntity result = contentProcessingUtil.processCodeContent(code, language, collectionId, orderIndex, title, caption);

        // Assert
        assertNotNull(result);
        assertInstanceOf(ContentCodeEntity.class, result);
        assertEquals(code, result.getCode());
        assertEquals(language, result.getLanguage());
        verify(contentRepository).save(any(ContentCodeEntity.class));
    }

    @Test
    void processCodeContent_withUnsupportedLanguage_shouldUseDefaultLanguage() {
        // Arrange
        String code = "public class Test { }";
        String language = "unsupported";
        Long collectionId = 1L;
        Integer orderIndex = 0;
        String title = "Test Code";
        String caption = "Test Caption";

        ContentCodeEntity savedEntity = createContentCodeEntity();
        when(contentRepository.save(any(ContentCodeEntity.class))).thenReturn(savedEntity);

        // Act
        ContentEntity result = contentProcessingUtil.processCodeContent(code, language, collectionId, orderIndex, title, caption);

        // Assert
        assertNotNull(result);
        assertInstanceOf(ContentCodeEntity.class, result);
        verify(contentRepository).save(any(ContentCodeEntity.class));
    }

    @Test
    void processContentGif_withValidGif_shouldReturnGifContentEntity() throws IOException {
        // Arrange
        MultipartFile file = createMockGifFile();
        Long collectionId = 1L;
        Integer orderIndex = 0;
        String title = "Test GIF";
        String caption = "Test Caption";

        // Mock S3 upload
        when(amazonS3.putObject(any(PutObjectRequest.class))).thenReturn(null);

        ContentGifEntity savedEntity = createContentGifEntity();
        when(contentRepository.save(any(ContentGifEntity.class))).thenReturn(savedEntity);

        // Act
        ContentEntity result = contentProcessingUtil.processGifContent(file, collectionId, orderIndex, title, caption);

        // Assert
        assertNotNull(result);
        assertInstanceOf(ContentGifEntity.class, result);
        verify(amazonS3, times(2)).putObject(any(PutObjectRequest.class));
        verify(contentRepository).save(any(ContentGifEntity.class));
    }

    @Test
    void processGifContent_withEmptyFile_shouldThrowException() {
        // Arrange
        MultipartFile file = new MockMultipartFile("file", "test.gif", "image/gif", new byte[0]);
        Long collectionId = 1L;
        Integer orderIndex = 0;
        String title = "Test GIF";
        String caption = "Test Caption";

        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, () -> {
            contentProcessingUtil.processGifContent(file, collectionId, orderIndex, title, caption);
        });
        assertTrue(exception.getMessage().contains("Failed to process GIF content block"));
    }

    @Test
    void reorderContent_withValidBlockIds_shouldUpdateOrderIndexes() {
        // Arrange
        Long collectionId = 1L;
        List<Long> blockIds = List.of(3L, 1L, 2L);

        List<ContentEntity> existingBlocks = new ArrayList<>();
        ContentEntity block1 = mock(ContentEntity.class);
        when(block1.getId()).thenReturn(1L);
        ContentEntity block2 = mock(ContentEntity.class);
        when(block2.getId()).thenReturn(2L);
        ContentEntity block3 = mock(ContentEntity.class);
        when(block3.getId()).thenReturn(3L);
        existingBlocks.add(block1);
        existingBlocks.add(block2);
        existingBlocks.add(block3);

        when(contentRepository.findByCollectionIdOrderByOrderIndex(collectionId)).thenReturn(existingBlocks);
        when(contentRepository.saveAll(anyList())).thenReturn(existingBlocks);

        // Act
        List<ContentEntity> result = contentProcessingUtil.reorderContent(collectionId, blockIds);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());
        verify(block3).setOrderIndex(0);
        verify(block1).setOrderIndex(1);
        verify(block2).setOrderIndex(2);
        verify(contentRepository).saveAll(anyList());
    }

    @Test
    void reorderContent_withEmptyBlockIds_shouldThrowException() {
        // Arrange
        Long collectionId = 1L;
        List<Long> blockIds = List.of();

        // Act & Assert
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            contentProcessingUtil.reorderContent(collectionId, blockIds);
        });
        assertTrue(exception.getMessage().contains("Block IDs list cannot be empty"));
    }

    @Test
    void reorderContent_withInvalidBlockId_shouldThrowException() {
        // Arrange
        Long collectionId = 1L;
        List<Long> blockIds = List.of(1L, 2L, 99L); // 99L doesn't exist

        List<ContentEntity> existingBlocks = new ArrayList<>();
        ContentEntity block1 = mock(ContentEntity.class);
        when(block1.getId()).thenReturn(1L);
        ContentEntity block2 = mock(ContentEntity.class);
        when(block2.getId()).thenReturn(2L);
        existingBlocks.add(block1);
        existingBlocks.add(block2);

        when(contentRepository.findByCollectionIdOrderByOrderIndex(collectionId)).thenReturn(existingBlocks);

        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, () -> {
            contentProcessingUtil.reorderContent(collectionId, blockIds);
        });
        assertTrue(exception.getMessage().contains("Failed to reorder content blocks"));
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
        when(amazonS3.putObject(any(PutObjectRequest.class))).thenReturn(null);

        ContentImageEntity imageEntity = createContentImageEntity();
        when(contentRepository.save(any(ContentImageEntity.class))).thenReturn(imageEntity);

        // Act
        ContentEntity result = contentProcessingUtil.processContent(
                file, type, collectionId, orderIndex, content, language, title, caption);

        // Assert
        assertNotNull(result);
        assertInstanceOf(ContentImageEntity.class, result);
        verify(amazonS3, times(2)).putObject(any(PutObjectRequest.class)); // Verify S3 upload was called twice (full + webP)
    }

    @Test
    void processContentBlock_withTextType_shouldCallProcessTextContent() {
        // Arrange
        MultipartFile file = null;
        ContentType type = ContentType.TEXT;
        Long collectionId = 1L;
        Integer orderIndex = 0;
        String content = "This is test content";
        String language = null;
        String title = null;
        String caption = "Test Caption";

        TextContentEntity textEntity = createTextContentEntity();
        when(contentRepository.save(any(TextContentEntity.class))).thenReturn(textEntity);

        // Act
        ContentEntity result = contentProcessingUtil.processContent(
                file, type, collectionId, orderIndex, content, language, title, caption);

        // Assert
        assertNotNull(result);
        assertInstanceOf(TextContentEntity.class, result);
    }

    @Test
    void processContentBlock_withCodeType_shouldCallProcessCodeContent() {
        // Arrange
        MultipartFile file = null;
        ContentType type = ContentType.CODE;
        Long collectionId = 1L;
        Integer orderIndex = 0;
        String content = "public class Test { }";
        String language = "java";
        String title = "Test Code";
        String caption = "Test Caption";

        ContentCodeEntity codeEntity = createContentCodeEntity();
        when(contentRepository.save(any(ContentCodeEntity.class))).thenReturn(codeEntity);

        // Act
        ContentEntity result = contentProcessingUtil.processContent(
                file, type, collectionId, orderIndex, content, language, title, caption);

        // Assert
        assertNotNull(result);
        assertInstanceOf(ContentCodeEntity.class, result);
    }

    @Test
    void processContentBlock_withGifType_shouldCallProcessGifContent() throws IOException {
        // Arrange
        MultipartFile file = createMockGifFile();
        ContentType type = ContentType.GIF;
        Long collectionId = 1L;
        Integer orderIndex = 0;
        String content = null;
        String language = null;
        String title = "Test GIF";
        String caption = "Test Caption";

        // Mock S3 upload
        when(amazonS3.putObject(any(PutObjectRequest.class))).thenReturn(null);

        ContentGifEntity gifEntity = createContentGifEntity();
        when(contentRepository.save(any(ContentGifEntity.class))).thenReturn(gifEntity);

        // Act
        ContentEntity result = contentProcessingUtil.processContent(
                file, type, collectionId, orderIndex, content, language, title, caption);

        // Assert
        assertNotNull(result);
        assertInstanceOf(ContentGifEntity.class, result);
    }

    @Test
    void processContent_withUnknownType_shouldThrowException() {
        // Arrange
        MultipartFile file = null;
        ContentType type = null;
        Long collectionId = 1L;
        Integer orderIndex = 0;
        String content = null;
        String language = null;
        String title = null;
        String caption = null;

        // Act & Assert
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            contentProcessingUtil.processContent(
                    file, type, collectionId, orderIndex, content, language, title, caption);
        });
        assertTrue(exception.getMessage().contains("Unknown content block type"));
    }

    // Helper methods to create test entities and models

    private ContentImageEntity createContentImageEntity() {
        ContentImageEntity entity = new ContentImageEntity();
        entity.setId(1L);
        entity.setCollectionId(1L);
        entity.setOrderIndex(0);
        entity.setContentType(ContentType.IMAGE);
        entity.setCaption("Test Caption");
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
        entity.setLocation("Test Location");
        entity.setImageUrlWeb("https://example.com/image.jpg");
        entity.setCreateDate("2023-01-01");
        return entity;
    }

    private TextContentEntity createTextContentEntity() {
        TextContentEntity entity = new TextContentEntity();
        entity.setId(2L);
        entity.setCollectionId(1L);
        entity.setOrderIndex(1);
        entity.setContentType(ContentType.TEXT);
        entity.setCaption("Test Caption");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setContent("This is test content");
        entity.setFormatType("markdown");
        return entity;
    }

    private ContentCodeEntity createContentCodeEntity() {
        ContentCodeEntity entity = new ContentCodeEntity();
        entity.setId(3L);
        entity.setCollectionId(1L);
        entity.setOrderIndex(2);
        entity.setContentType(ContentType.CODE);
        entity.setCaption("Test Caption");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setCode("public class Test { }");
        entity.setLanguage("java");
        entity.setTitle("Test Code");
        return entity;
    }

    private ContentGifEntity createContentGifEntity() {
        ContentGifEntity entity = new ContentGifEntity();
        entity.setId(4L);
        entity.setCollectionId(1L);
        entity.setOrderIndex(3);
        entity.setContentType(ContentType.GIF);
        entity.setCaption("Test Caption");
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
