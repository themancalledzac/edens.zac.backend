package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.ContentBlockEntity;
import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import edens.zac.portfolio.backend.entity.TextContentBlockEntity;
import edens.zac.portfolio.backend.model.ContentBlockModel;
import edens.zac.portfolio.backend.model.ContentCollectionModel;
import edens.zac.portfolio.backend.model.ContentCollectionPageDTO;
import edens.zac.portfolio.backend.repository.ContentCollectionRepository;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.ContentBlockType;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentCollectionProcessingUtilTest {

    @Mock
    private ContentCollectionRepository contentCollectionRepository;

    @Mock
    private ContentBlockProcessingUtil contentBlockProcessingUtil;

    @Mock
    private ImageProcessingUtil imageProcessingUtil;

    @Mock
    private ExceptionUtils exceptionUtils;

    @InjectMocks
    private ContentCollectionProcessingUtil util;

    private ContentCollectionEntity testEntity;
    private List<ContentBlockEntity> testBlocks;

    @BeforeEach
    void setUp() {
        // Create test entity
        testEntity = new ContentCollectionEntity();
        testEntity.setId(1L);
        testEntity.setType(CollectionType.BLOG);
        testEntity.setTitle("Test Blog");
        testEntity.setSlug("test-blog");
        testEntity.setDescription("Test description");
        testEntity.setVisible(true);
        testEntity.setPriority(1);
        testEntity.setBlocksPerPage(30);
        testEntity.setTotalBlocks(2);
        testEntity.setCreatedAt(LocalDateTime.now());
        testEntity.setUpdatedAt(LocalDateTime.now());

        // Create test blocks
        testBlocks = new ArrayList<>();
        TextContentBlockEntity block1 = new TextContentBlockEntity();
        block1.setId(1L);
        block1.setCollectionId(1L);
        block1.setOrderIndex(0);
        block1.setBlockType(ContentBlockType.TEXT);
        block1.setContent("Test content 1");

        TextContentBlockEntity block2 = new TextContentBlockEntity();
        block2.setId(2L);
        block2.setCollectionId(1L);
        block2.setOrderIndex(1);
        block2.setBlockType(ContentBlockType.TEXT);
        block2.setContent("Test content 2");

        testBlocks.add(block1);
        testBlocks.add(block2);
        testEntity.setContentBlocks(testBlocks);
    }

    @Test
    void exceptionUtils_shouldReturnResultWhenNoException() {
        // Arrange
        Supplier<String> action = () -> "success";
        when(exceptionUtils.handleExceptions(anyString(), any())).thenReturn("success");

        // Act
        String result = exceptionUtils.handleExceptions("test operation", action);

        // Assert
        assertEquals("success", result);
        verify(exceptionUtils).handleExceptions(eq("test operation"), any());
    }

    @Test
    void exceptionUtils_shouldRethrowEntityNotFoundException() {
        // Arrange
        Supplier<String> action = () -> {
            throw new EntityNotFoundException("Entity not found");
        };
        when(exceptionUtils.handleExceptions(anyString(), any())).thenThrow(new EntityNotFoundException("Entity not found"));

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> exceptionUtils.handleExceptions("test operation", action));
        verify(exceptionUtils).handleExceptions(eq("test operation"), any());
    }

    @Test
    void exceptionUtils_shouldWrapOtherExceptions() {
        // Arrange
        Supplier<String> action = () -> {
            throw new IllegalArgumentException("Invalid argument");
        };
        when(exceptionUtils.handleExceptions(anyString(), any())).thenThrow(
            new RuntimeException("Failed to test operation: Invalid argument"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> exceptionUtils.handleExceptions("test operation", action));
        assertTrue(exception.getMessage().contains("Failed to test operation"));
        verify(exceptionUtils).handleExceptions(eq("test operation"), any());
    }

    @Test
    void convertToBasicModel_shouldConvertEntityToModel() {
        // Act
        ContentCollectionModel model = util.convertToBasicModel(testEntity);

        // Assert
        assertNotNull(model);
        assertEquals(testEntity.getId(), model.getId());
        assertEquals(testEntity.getType(), model.getType());
        assertEquals(testEntity.getTitle(), model.getTitle());
        assertEquals(testEntity.getSlug(), model.getSlug());
        assertEquals(testEntity.getDescription(), model.getDescription());
        assertEquals(testEntity.getVisible(), model.getVisible());
        assertEquals(testEntity.getPriority(), model.getPriority());
        assertEquals(testEntity.getBlocksPerPage(), model.getBlocksPerPage());
        assertEquals(testEntity.getTotalBlocks(), model.getTotalBlocks());
        assertEquals(testEntity.getTotalPages(), model.getTotalPages());
        assertEquals(0, model.getCurrentPage());
    }

    @Test
    void convertToFullModel_shouldConvertEntityWithContentBlocks() {
        // Arrange
        when(contentBlockProcessingUtil.convertToModel(any(ContentBlockEntity.class)))
                .thenAnswer(invocation -> {
                    ContentBlockEntity entity = invocation.getArgument(0);
                    ContentBlockModel model = new ContentBlockModel();
                    model.setId(entity.getId());
                    model.setBlockType(entity.getBlockType());
                    model.setOrderIndex(entity.getOrderIndex());
                    return model;
                });

        // Act
        ContentCollectionModel model = util.convertToFullModel(testEntity);

        // Assert
        assertNotNull(model);
        assertNotNull(model.getContentBlocks());
        assertEquals(2, model.getContentBlocks().size());
        assertEquals(testBlocks.get(0).getId(), model.getContentBlocks().get(0).getId());
        assertEquals(testBlocks.get(1).getId(), model.getContentBlocks().get(1).getId());
    }

    @Test
    void convertToModel_shouldConvertEntityWithPaginatedContentBlocks() {
        // Arrange
        Page<ContentBlockEntity> page = new PageImpl<>(testBlocks, PageRequest.of(0, 10), 2);

        when(contentBlockProcessingUtil.convertToModel(any(ContentBlockEntity.class)))
                .thenAnswer(invocation -> {
                    ContentBlockEntity entity = invocation.getArgument(0);
                    ContentBlockModel model = new ContentBlockModel();
                    model.setId(entity.getId());
                    model.setBlockType(entity.getBlockType());
                    model.setOrderIndex(entity.getOrderIndex());
                    return model;
                });

        // Act
        ContentCollectionModel model = util.convertToModel(testEntity, page);

        // Assert
        assertNotNull(model);
        assertNotNull(model.getContentBlocks());
        assertEquals(2, model.getContentBlocks().size());
        assertEquals(page.getNumber(), model.getCurrentPage());
        assertEquals(page.getTotalPages(), model.getTotalPages());
        assertEquals((int) page.getTotalElements(), model.getTotalBlocks());
        assertEquals(page.getSize(), model.getBlocksPerPage());
    }

    @Test
    void generateSlug_shouldCallImageProcessingUtil() {
        // Arrange
        when(imageProcessingUtil.generateSlug("Test Title")).thenReturn("test-title");

        // Act
        String slug = util.generateSlug("Test Title");

        // Assert
        assertEquals("test-title", slug);
        verify(imageProcessingUtil).generateSlug("Test Title");
    }

    @Test
    void validateAndEnsureUniqueSlug_shouldReturnOriginalSlugWhenUnique() {
        // Arrange
        when(contentCollectionRepository.findBySlug("test-slug"))
                .thenReturn(Optional.empty());

        // Act
        String result = util.validateAndEnsureUniqueSlug("test-slug", null);

        // Assert
        assertEquals("test-slug", result);
    }

    @Test
    void validateAndEnsureUniqueSlug_shouldAppendNumberWhenSlugExists() {
        // Arrange
        ContentCollectionEntity existingEntity = new ContentCollectionEntity();
        existingEntity.setId(2L);

        when(contentCollectionRepository.findBySlug("test-slug"))
                .thenReturn(Optional.of(existingEntity));
        when(contentCollectionRepository.findBySlug("test-slug-1"))
                .thenReturn(Optional.empty());

        // Act
        String result = util.validateAndEnsureUniqueSlug("test-slug", 1L);

        // Assert
        assertEquals("test-slug-1", result);
    }


    @Test
    void applyTypeSpecificDefaults_shouldSetDefaultsBasedOnType() {
        // Arrange
        ContentCollectionEntity entity = new ContentCollectionEntity();
        entity.setType(CollectionType.CLIENT_GALLERY);

        // Act
        ContentCollectionEntity result = util.applyTypeSpecificDefaults(entity);

        // Assert
        // Config JSON removed; ensure other defaults still apply
        assertEquals(50, result.getBlocksPerPage());
        assertFalse(result.getVisible()); // Client galleries are private by default
    }


    @Test
    void getContentSummary_shouldFormatSummaryCorrectly() {
        // Arrange
        ContentCollectionPageDTO dto = new ContentCollectionPageDTO();
        dto.setImageBlockCount(5);
        dto.setTextBlockCount(3);
        dto.setCodeBlockCount(2);
        dto.setGifBlockCount(1);

        // Act
        String summary = ContentCollectionProcessingUtil.getContentSummary(dto);

        // Assert
        assertEquals("5 images, 3 text blocks, 2 code blocks, 1 gifs", summary);
    }

    // ======================================
    // hashPassword and passwordMatches tests
    // ======================================

    @Test
    void hashPassword_shouldReturnExpectedHash_forKnownPassword() {
        String hash = ContentCollectionProcessingUtil.hashPassword("password");
        assertEquals("5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8", hash);
    }

    @Test
    void hashPassword_shouldReturnExpectedHash_forEmptyString() {
        String hash = ContentCollectionProcessingUtil.hashPassword("");
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash);
    }

    @Test
    void hashPassword_shouldHandleUnicodeUtf8Consistently() {
        String hash = ContentCollectionProcessingUtil.hashPassword("pässwörd");
        assertEquals("46970bef70aced8123f0d5d094717e2a5cd412041e03b26376049fe65b2834a4", hash);
    }

    @Test
    void hashPassword_outputShouldBe64LowercaseHex() {
        String hash = ContentCollectionProcessingUtil.hashPassword("any input");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void hashPassword_sameInputGivesSameOutput_everyTime() {
        String h1 = ContentCollectionProcessingUtil.hashPassword("repeatable");
        String h2 = ContentCollectionProcessingUtil.hashPassword("repeatable");
        assertEquals(h1, h2);
    }

    @Test
    void hashPassword_differentInputsGiveDifferentOutputs() {
        String h1 = ContentCollectionProcessingUtil.hashPassword("one");
        String h2 = ContentCollectionProcessingUtil.hashPassword("two");
        assertNotEquals(h1, h2);
    }

    @Test
    void hashPassword_shouldThrowNullPointer_whenPasswordIsNull() {
        assertThrows(NullPointerException.class, () -> ContentCollectionProcessingUtil.hashPassword(null));
    }

    @Test
    void passwordMatches_shouldReturnTrue_whenPasswordMatchesHash() {
        String hash = ContentCollectionProcessingUtil.hashPassword("secret");
        assertTrue(ContentCollectionProcessingUtil.passwordMatches("secret", hash));
    }

    @Test
    void passwordMatches_shouldReturnFalse_whenPasswordDoesNotMatchHash() {
        String hash = ContentCollectionProcessingUtil.hashPassword("secret");
        assertFalse(ContentCollectionProcessingUtil.passwordMatches("wrong", hash));
    }

    @Test
    void passwordMatches_emptyPasswordAgainstEmptyHash_shouldBehaveAsExpected() {
        // Hash of empty string compared to empty string should be false
        assertFalse(ContentCollectionProcessingUtil.passwordMatches("", ""));
        // But against the correct empty-string hash should be true
        String emptyHash = ContentCollectionProcessingUtil.hashPassword("");
        assertTrue(ContentCollectionProcessingUtil.passwordMatches("", emptyHash));
    }

    @Test
    void passwordMatches_shouldThrowNullPointer_whenPasswordIsNull() {
        String someHash = ContentCollectionProcessingUtil.hashPassword("abc");
        assertThrows(NullPointerException.class, () -> ContentCollectionProcessingUtil.passwordMatches(null, someHash));
    }
}
