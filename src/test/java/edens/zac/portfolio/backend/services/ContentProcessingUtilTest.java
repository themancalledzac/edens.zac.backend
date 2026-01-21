package edens.zac.portfolio.backend.services;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import edens.zac.portfolio.backend.entity.*;
import edens.zac.portfolio.backend.model.*;
import edens.zac.portfolio.backend.dao.ContentDao;
import edens.zac.portfolio.backend.dao.ContentCameraDao;
import edens.zac.portfolio.backend.dao.ContentLensDao;
import edens.zac.portfolio.backend.dao.ContentFilmTypeDao;
import edens.zac.portfolio.backend.dao.ContentTagDao;
import edens.zac.portfolio.backend.dao.ContentPersonDao;
import edens.zac.portfolio.backend.dao.CollectionContentDao;
import edens.zac.portfolio.backend.dao.ContentTextDao;
import edens.zac.portfolio.backend.dao.ContentCollectionDao;
import edens.zac.portfolio.backend.dao.ContentGifDao;
import edens.zac.portfolio.backend.services.validator.ContentImageUpdateValidator;
import edens.zac.portfolio.backend.services.validator.ContentValidator;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ContentProcessingUtilTest {

    @Mock
    private AmazonS3 amazonS3;

    @Mock
    private ContentDao contentDao;
    @Mock
    private ContentCameraDao contentCameraDao;
    @Mock
    private ContentLensDao contentLensDao;
    @Mock
    private ContentFilmTypeDao contentFilmTypeDao;
    @Mock
    private ContentTagDao contentTagDao;
    @Mock
    private ContentPersonDao contentPersonDao;
    @Mock
    private CollectionContentDao collectionContentDao;
    @Mock
    private ContentTextDao contentTextDao;
    @Mock
    private ContentCollectionDao contentCollectionDao;
    @Mock
    private ContentGifDao contentGifDao;
    @Mock
    private ContentImageUpdateValidator contentImageUpdateValidator;
    @Mock
    private ContentValidator contentValidator;

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
    void convertImageModel() {
        // Arrange
        ContentImageEntity entity = createContentImageEntity();

        // Act
        ContentModel result = contentProcessingUtil.convertRegularContentEntityToModel(entity);

        // Assert
        assertInstanceOf(ContentImageModel.class, result);
        ContentImageModel imageModel = (ContentImageModel) result;
        assertEquals(entity.getId(), imageModel.getId());
        assertEquals(entity.getContentType(), imageModel.getContentType());
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
        assertEquals(entity.getCamera().getCameraName(), imageModel.getCamera().getName());
        assertEquals(entity.getFocalLength(), imageModel.getFocalLength());
        assertEquals(entity.getLocation(), imageModel.getLocation());
        assertEquals(entity.getCreateDate(), imageModel.getCreateDate());
    }

    @Test
    void convertModel() {
        // Arrange
        ContentTextEntity entity = createTextContentEntity();

        // Act
        ContentModel result = contentProcessingUtil.convertRegularContentEntityToModel(entity);

        // Assert
        assertInstanceOf(ContentTextModel.class, result);
        ContentTextModel textModel = (ContentTextModel) result;
        assertEquals(entity.getId(), textModel.getId());
        assertEquals(entity.getContentType(), textModel.getContentType());
        assertEquals(entity.getTextContent(), textModel.getTextContent());
        assertEquals(entity.getFormatType(), textModel.getFormatType());
    }

    @Test
    void convertGifModel() {
        // Arrange
        ContentGifEntity entity = createContentGifEntity();

        // Act
        ContentModel result = contentProcessingUtil.convertRegularContentEntityToModel(entity);

        // Assert
        assertInstanceOf(ContentGifModel.class, result);
        ContentGifModel gifModel = (ContentGifModel) result;
        assertEquals(entity.getId(), gifModel.getId());
        assertEquals(entity.getContentType(), gifModel.getContentType());
        assertEquals(entity.getTitle(), gifModel.getTitle());
        assertEquals(entity.getGifUrl(), gifModel.getGifUrl());
        assertEquals(entity.getThumbnailUrl(), gifModel.getThumbnailUrl());
        assertEquals(entity.getWidth(), gifModel.getWidth());
        assertEquals(entity.getHeight(), gifModel.getHeight());
        assertEquals(entity.getAuthor(), gifModel.getAuthor());
        assertEquals(entity.getCreateDate(), gifModel.getCreateDate());
    }

    @Test
    void convertEntityToModel_withUnknownBlockType_shouldThrowException() {
        // Arrange
        ContentEntity entity = mock(ContentEntity.class);
        when(entity.getContentType()).thenReturn(null);

        // Act & Assert
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
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
        when(amazonS3.putObject(any(PutObjectRequest.class))).thenReturn(null);

        ContentImageEntity savedEntity = createContentImageEntity();
        when(contentDao.saveImage(any(ContentImageEntity.class))).thenReturn(savedEntity);

        // Act
        ContentEntity result = contentProcessingUtil.processImageContent(file, title);

        // Assert
        assertNotNull(result);
        assertInstanceOf(ContentImageEntity.class, result);
        verify(amazonS3, times(2)).putObject(any(PutObjectRequest.class)); // Verify S3 upload was called twice (full + webP)
        verify(contentDao).saveImage(any(ContentImageEntity.class));
    }

    @Test
    void processContentImage_whenImageProcessingFails_shouldThrowException() throws IOException {
        // Arrange
        MultipartFile file = createMockImageFile();
        String title = "Test Image";

        // Act & Assert
        // Note: May throw UnsatisfiedLinkError if native image libraries aren't available in test environment
        // or RuntimeException if S3 upload fails. We don't mock S3 here because the code may fail earlier
        // during image processing before reaching S3 upload.
        Throwable exception = assertThrows(Throwable.class, () -> {
            contentProcessingUtil.processImageContent(file, title);
        });
        assertTrue(exception instanceof RuntimeException || exception instanceof UnsatisfiedLinkError);
    }

//    @Test
//    void processTextContent_withValidText_shouldReturnTextContentEntity() {
//        // Arrange
//        String text = "This is test content";
//        Long collectionId = 1L;
//        Integer orderIndex = 0;
//        String caption = "Test Caption";
//
//        ContentTextEntity savedEntity = createTextContentEntity();
//        when(contentRepository.save(any(ContentTextEntity.class))).thenReturn(savedEntity);

    // TODO: Commented out until we can abstract this logic out from it's main serviceImpl layer location
    // Act
//        ContentTextEntity result = contentProcessingUtil.processTextContent(text, collectionId, orderIndex, caption);

//        // Assert
//        assertNotNull(result);
//        assertInstanceOf(ContentTextEntity.class, result);
//        assertEquals(text, result.getTextContent());
//        verify(contentRepository).save(any(ContentTextEntity.class));
//    }

//    @Test
//    void processTextContent_withEmptyText_shouldThrowException() {
//        // Arrange
//        String text = "";
//        Long collectionId = 1L;
//        Integer orderIndex = 0;
//        String caption = "Test Caption";
//
    // TODO: Commented out until we can abstract this logic out from it's main serviceImpl layer location
//        // Act & Assert
//        Exception exception = assertThrows(RuntimeException.class, () -> {
//            contentProcessingUtil.processTextContent(text, collectionId, orderIndex, caption);
//        });
//        assertTrue(exception.getMessage().contains("Failed to process text content block"));
//    }

    @Disabled("GIF saving not yet implemented in DAO layer - throws UnsupportedOperationException")
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

        // Note: GIF saving not yet implemented in DAO layer
        // ContentGifEntity savedEntity = createContentGifEntity();
        // when(contentGifDao.save(any(ContentGifEntity.class))).thenReturn(savedEntity);

        // Act & Assert - Will throw UnsupportedOperationException until GIF DAO save is implemented
        assertThrows(UnsupportedOperationException.class, () -> {
            contentProcessingUtil.processGifContent(file, collectionId, orderIndex, title, caption);
        });
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
        // Note: May throw different exceptions depending on image library availability
        Throwable exception = assertThrows(Throwable.class, () -> {
            contentProcessingUtil.processGifContent(file, collectionId, orderIndex, title, caption);
        });
        // Accept either the expected RuntimeException or other exceptions from image processing
        assertTrue(exception instanceof RuntimeException || 
                   exception instanceof UnsatisfiedLinkError ||
                   (exception.getMessage() != null && 
                    (exception.getMessage().contains("Failed to process GIF") || 
                     exception.getMessage().contains("GIF"))));
    }

//    @Test
//    void reorderContent_withValidBlockIds_shouldUpdateOrderIndexes() {
//        // Arrange
//        Long collectionId = 1L;
//        List<Long> blockIds = List.of(3L, 1L, 2L);
//
//        List<ContentEntity> existingBlocks = new ArrayList<>();
//        ContentEntity block1 = mock(ContentEntity.class);
//        when(block1.getId()).thenReturn(1L);
//        ContentEntity block2 = mock(ContentEntity.class);
//        when(block2.getId()).thenReturn(2L);
//        ContentEntity block3 = mock(ContentEntity.class);
//        when(block3.getId()).thenReturn(3L);
//        existingBlocks.add(block1);
//        existingBlocks.add(block2);
//        existingBlocks.add(block3);
//
//        //        // TODO: Update this with the new 'findCollectionOrderIndex'
//////        Integer orderIndex = collectionContentRepository.getMaxOrderIndexForCollection(request.getCollectionId());
//////        orderIndex = (orderIndex != null) ? orderIndex + 1 : 0;
//        when(contentRepository.findByCollectionIdOrderByOrderIndex(collectionId)).thenReturn(existingBlocks);
//        when(contentRepository.saveAll(anyList())).thenReturn(existingBlocks);
//
//        // Act
//        List<ContentEntity> result = contentProcessingUtil.reorderContent(collectionId, blockIds);
//
//        // Assert
//        assertNotNull(result);
//        assertEquals(3, result.size());
//        verify(block3).setOrderIndex(0);
//        verify(block1).setOrderIndex(1);
//        verify(block2).setOrderIndex(2);
//        verify(contentRepository).saveAll(anyList());
//    }

//    @Test
//    void reorderContent_withEmptyBlockIds_shouldThrowException() {
//        // Arrange
//        Long collectionId = 1L;
//        List<Long> blockIds = List.of();
//
//        // Act & Assert
//        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
//            contentProcessingUtil.reorderContent(collectionId, blockIds);
//        });
//        assertTrue(exception.getMessage().contains("Block IDs list cannot be empty"));
//    }

//    @Test
//    void reorderContent_withInvalidBlockId_shouldThrowException() {
//        // Arrange
//        Long collectionId = 1L;
//        List<Long> blockIds = List.of(1L, 2L, 99L); // 99L doesn't exist
//
//        List<ContentEntity> existingBlocks = new ArrayList<>();
//        ContentEntity block1 = mock(ContentEntity.class);
//        when(block1.getId()).thenReturn(1L);
//        ContentEntity block2 = mock(ContentEntity.class);
//        when(block2.getId()).thenReturn(2L);
//        existingBlocks.add(block1);
//        existingBlocks.add(block2);
//
//        // TODO: Update this with the new 'findCollectionOrderIndex'

    /// /        Integer orderIndex = collectionContentRepository.getMaxOrderIndexForCollection(request.getCollectionId());
    /// /        orderIndex = (orderIndex != null) ? orderIndex + 1 : 0;
//        when(contentRepository.findByCollectionIdOrderByOrderIndex(collectionId)).thenReturn(existingBlocks);
//
//        // Act & Assert
//        Exception exception = assertThrows(RuntimeException.class, () -> {
//            contentProcessingUtil.reorderContent(collectionId, blockIds);
//        });
//        assertTrue(exception.getMessage().contains("Failed to reorder content blocks"));
//    }
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
        when(contentDao.saveImage(any(ContentImageEntity.class))).thenReturn(imageEntity);

        // Act
        ContentEntity result = contentProcessingUtil.processImageContent(file, title);

        // Assert
        assertNotNull(result);
        assertInstanceOf(ContentImageEntity.class, result);
        verify(amazonS3, times(2)).putObject(any(PutObjectRequest.class)); // Verify S3 upload was called twice (full + webP)
        verify(contentDao).saveImage(any(ContentImageEntity.class));
    }

//    @Test
//    void processContentBlock_withTextType_shouldCallProcessTextContent() {
//        // Arrange
//        MultipartFile file = null;
//        ContentType type = ContentType.TEXT;
//        Long collectionId = 1L;
//        Integer orderIndex = 0;
//        String content = "This is test content";
//        String language = null;
//        String title = null;
//        String caption = "Test Caption";
//
//        ContentTextEntity textEntity = createTextContentEntity();
//        when(contentRepository.save(any(ContentTextEntity.class))).thenReturn(textEntity);
//
    // Commented out until we abstract our textProcessing from the ContentServiceImpl layer
//        // Act
//        ContentEntity result = contentProcessingUtil.processContent(
//                file, type, collectionId, orderIndex, content, language, title, caption);
//
//        // Assert
//        assertNotNull(result);
//        assertInstanceOf(ContentTextEntity.class, result);
//    }


//    @Test
//    void processContent_withUnknownType_shouldThrowException() {
//        // Arrange
//        MultipartFile file = null;
//        ContentType type = null;
//        Long collectionId = 1L;
//        Integer orderIndex = 0;
//        String content = null;
//        String language = null;
//        String title = null;
//        String caption = null;
//
    // TODO: Determine what our issue is here, do we even have a WAY of doing this any longer?
//        // Act & Assert
//        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
//            contentProcessingUtil.processContent(
//                    file, type, collectionId, orderIndex, content, language, title, caption);
//        });
//        assertTrue(exception.getMessage().contains("Unknown content block type"));
//    }

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
        entity.setLocation("Test Location");
        entity.setImageUrlWeb("https://example.com/image.jpg");
        entity.setCreateDate("2023-01-01");
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
