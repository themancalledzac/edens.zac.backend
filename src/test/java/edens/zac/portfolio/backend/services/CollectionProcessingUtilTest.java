package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.ContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.TextContentEntity;
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.CollectionPageDTO;
import edens.zac.portfolio.backend.repository.CollectionRepository;
import edens.zac.portfolio.backend.repository.ContentRepository;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.ContentType;
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
class CollectionProcessingUtilTest {

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private ContentProcessingUtil contentProcessingUtil;

    @Mock
    private ExceptionUtils exceptionUtils;

//    @Mock
//    private edens.zac.portfolio.backend.repository.ContentCollectionHomeCardRepository homeCardRepository;

    @InjectMocks
    private CollectionProcessingUtil util;

    private CollectionEntity testEntity;
    private List<ContentEntity> testBlocks;

    @BeforeEach
    void setUp() {
        // Create test entity
        testEntity = new CollectionEntity();
        testEntity.setId(1L);
        testEntity.setType(CollectionType.BLOG);
        testEntity.setTitle("Test Blog");
        testEntity.setSlug("test-blog");
        testEntity.setDescription("Test description");
        testEntity.setVisible(true);
        testEntity.setContentPerPage(30);
        testEntity.setTotalContent(2);
        testEntity.setCreatedAt(LocalDateTime.now());
        testEntity.setUpdatedAt(LocalDateTime.now());

        // Create test content blocks
        testBlocks = new ArrayList<>();
        TextContentEntity block1 = new TextContentEntity();
        block1.setId(1L);
        block1.setContentType(ContentType.TEXT);
        block1.setContent("Test content 1");

        TextContentEntity block2 = new TextContentEntity();
        block2.setId(2L);
        block2.setContentType(ContentType.TEXT);
        block2.setContent("Test content 2");

        testBlocks.add(block1);
        testBlocks.add(block2);

        // Create join table entities (new architecture)
        CollectionContentEntity cc1 = CollectionContentEntity.builder()
                .collection(testEntity)
                .content(block1)
                .orderIndex(0)
                .caption(null)
                .visible(true)
                .build();

        CollectionContentEntity cc2 = CollectionContentEntity.builder()
                .collection(testEntity)
                .content(block2)
                .orderIndex(1)
                .caption(null)
                .visible(true)
                .build();

        // Add to collection using the join table
        testEntity.getCollectionContent().add(cc1);
        testEntity.getCollectionContent().add(cc2);
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
        // Arrange
//        when(homeCardRepository.findByReferenceId(any())).thenReturn(Optional.empty());

        // Act
        CollectionModel model = util.convertToBasicModel(testEntity);

        // Assert
        assertNotNull(model);
        assertEquals(testEntity.getId(), model.getId());
        assertEquals(testEntity.getType(), model.getType());
        assertEquals(testEntity.getTitle(), model.getTitle());
        assertEquals(testEntity.getSlug(), model.getSlug());
        assertEquals(testEntity.getDescription(), model.getDescription());
        assertEquals(testEntity.getVisible(), model.getVisible());
        assertEquals(testEntity.getContentPerPage(), model.getContentPerPage());
        assertEquals(testEntity.getTotalContent(), model.getContentCount());
        assertEquals(testEntity.getTotalPages(), model.getTotalPages());
        assertEquals(0, model.getCurrentPage());
    }

    @Test
    void convertToFullModel_shouldConvertEntityWithContentBlocks() {
        // Arrange
//        when(homeCardRepository.findByReferenceId(any())).thenReturn(Optional.empty());
        when(contentRepository.findByCollectionIdOrderByOrderIndex(any())).thenReturn(testBlocks);
        when(contentProcessingUtil.convertToModel(any(ContentEntity.class)))
                .thenAnswer(invocation -> {
                    ContentEntity entity = invocation.getArgument(0);
                    ContentModel model = new ContentModel();
                    model.setId(entity.getId());
                    model.setContentType(entity.getContentType());
                    return model;
                });

        // Act
        CollectionModel model = util.convertToFullModel(testEntity);

        // Assert
        assertNotNull(model);
        assertNotNull(model.getContent());
        assertEquals(2, model.getContent().size());
        assertEquals(testBlocks.get(0).getId(), model.getContent().get(0).getId());
        assertEquals(testBlocks.get(1).getId(), model.getContent().get(1).getId());
    }

    @Test
    void convertToModel_shouldConvertEntityWithPaginatedContentBlocks() {
        // Arrange
//        when(homeCardRepository.findByReferenceId(any())).thenReturn(Optional.empty());
        Page<ContentEntity> page = new PageImpl<>(testBlocks, PageRequest.of(0, 10), 2);

        when(contentProcessingUtil.convertToModel(any(ContentEntity.class)))
                .thenAnswer(invocation -> {
                    ContentEntity entity = invocation.getArgument(0);
                    ContentModel model = new ContentModel();
                    model.setId(entity.getId());
                    model.setContentType(entity.getContentType());
                    return model;
                });

        // Act
        CollectionModel model = util.convertToModel(testEntity, page);

        // Assert
        assertNotNull(model);
        assertNotNull(model.getContent());
        assertEquals(2, model.getContent().size());
        assertEquals(page.getNumber(), model.getCurrentPage());
        assertEquals(page.getTotalPages(), model.getTotalPages());
        assertEquals((int) page.getTotalElements(), model.getContentCount());
        assertEquals(page.getSize(), model.getContentPerPage());
    }

    @Test
    void validateAndEnsureUniqueSlug_shouldReturnOriginalSlugWhenUnique() {
        // Arrange
        when(collectionRepository.findBySlug("test-slug"))
                .thenReturn(Optional.empty());

        // Act
        String result = util.validateAndEnsureUniqueSlug("test-slug", null);

        // Assert
        assertEquals("test-slug", result);
    }

    @Test
    void validateAndEnsureUniqueSlug_shouldAppendNumberWhenSlugExists() {
        // Arrange
        CollectionEntity existingEntity = new CollectionEntity();
        existingEntity.setId(2L);

        when(collectionRepository.findBySlug("test-slug"))
                .thenReturn(Optional.of(existingEntity));
        when(collectionRepository.findBySlug("test-slug-1"))
                .thenReturn(Optional.empty());

        // Act
        String result = util.validateAndEnsureUniqueSlug("test-slug", 1L);

        // Assert
        assertEquals("test-slug-1", result);
    }


    @Test
    void applyTypeSpecificDefaults_shouldSetDefaultsBasedOnType() {
        // Arrange
        CollectionEntity entity = new CollectionEntity();
        entity.setType(CollectionType.CLIENT_GALLERY);
        entity.setVisible(null); // Reset to null to test default behavior

        // Act
        CollectionEntity result = util.applyTypeSpecificDefaults(entity);

        // Assert
        // Config JSON removed; ensure other defaults still apply
        assertEquals(50, result.getContentPerPage());
        assertFalse(result.getVisible()); // Client galleries are private by default
    }


    @Test
    void getContentSummary_shouldFormatSummaryCorrectly() {
        // Arrange
        CollectionPageDTO dto = new CollectionPageDTO();
        dto.setImageBlockCount(5);
        dto.setTextBlockCount(3);
        dto.setCodeBlockCount(2);
        dto.setGifBlockCount(1);

        // Act
        String summary = CollectionProcessingUtil.getContentSummary(dto);

        // Assert
        assertEquals("5 images, 3 text blocks, 2 code blocks, 1 gifs", summary);
    }

    // ======================================
    // hashPassword and passwordMatches tests
    // ======================================

    @Test
    void hashPassword_shouldReturnExpectedHash_forKnownPassword() {
        String hash = CollectionProcessingUtil.hashPassword("password");
        assertEquals("5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8", hash);
    }

    @Test
    void hashPassword_shouldReturnExpectedHash_forEmptyString() {
        String hash = CollectionProcessingUtil.hashPassword("");
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash);
    }

    @Test
    void hashPassword_shouldHandleUnicodeUtf8Consistently() {
        String hash = CollectionProcessingUtil.hashPassword("pässwörd");
        assertEquals("46970bef70aced8123f0d5d094717e2a5cd412041e03b26376049fe65b2834a4", hash);
    }

    @Test
    void hashPassword_outputShouldBe64LowercaseHex() {
        String hash = CollectionProcessingUtil.hashPassword("any input");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void hashPassword_sameInputGivesSameOutput_everyTime() {
        String h1 = CollectionProcessingUtil.hashPassword("repeatable");
        String h2 = CollectionProcessingUtil.hashPassword("repeatable");
        assertEquals(h1, h2);
    }

    @Test
    void hashPassword_differentInputsGiveDifferentOutputs() {
        String h1 = CollectionProcessingUtil.hashPassword("one");
        String h2 = CollectionProcessingUtil.hashPassword("two");
        assertNotEquals(h1, h2);
    }

    @Test
    void hashPassword_shouldThrowNullPointer_whenPasswordIsNull() {
        assertThrows(NullPointerException.class, () -> CollectionProcessingUtil.hashPassword(null));
    }

    @Test
    void passwordMatches_shouldReturnTrue_whenPasswordMatchesHash() {
        String hash = CollectionProcessingUtil.hashPassword("secret");
        assertTrue(CollectionProcessingUtil.passwordMatches("secret", hash));
    }

    @Test
    void passwordMatches_shouldReturnFalse_whenPasswordDoesNotMatchHash() {
        String hash = CollectionProcessingUtil.hashPassword("secret");
        assertFalse(CollectionProcessingUtil.passwordMatches("wrong", hash));
    }

    @Test
    void passwordMatches_emptyPasswordAgainstEmptyHash_shouldBehaveAsExpected() {
        // Hash of empty string compared to empty string should be false
        assertFalse(CollectionProcessingUtil.passwordMatches("", ""));
        // But against the correct empty-string hash should be true
        String emptyHash = CollectionProcessingUtil.hashPassword("");
        assertTrue(CollectionProcessingUtil.passwordMatches("", emptyHash));
    }

    @Test
    void passwordMatches_shouldThrowNullPointer_whenPasswordIsNull() {
        String someHash = CollectionProcessingUtil.hashPassword("abc");
        assertThrows(NullPointerException.class, () -> CollectionProcessingUtil.passwordMatches(null, someHash));
    }
}
