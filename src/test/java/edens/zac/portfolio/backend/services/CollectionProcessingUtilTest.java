package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.ContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentTextEntity;
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.dao.CollectionDao;
import edens.zac.portfolio.backend.dao.CollectionContentDao;
import edens.zac.portfolio.backend.dao.ContentDao;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollectionProcessingUtilTest {

    @Mock
    private CollectionDao collectionDao;

    @Mock
    private CollectionContentDao collectionContentDao;

    @Mock
    private ContentDao contentDao;

    @Mock
    private ContentProcessingUtil contentProcessingUtil;

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
        ContentTextEntity block1 = new ContentTextEntity();
        block1.setId(1L);
        block1.setContentType(ContentType.TEXT);
        block1.setTextContent("Test content 1");

        ContentTextEntity block2 = new ContentTextEntity();
        block2.setId(2L);
        block2.setContentType(ContentType.TEXT);
        block2.setTextContent("Test content 2");

        testBlocks.add(block1);
        testBlocks.add(block2);

        // Note: CollectionContentEntity now uses IDs instead of entity references
        // These are not used in the tests below, but kept for reference
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
        List<CollectionContentEntity> joinEntries = new ArrayList<>();
        CollectionContentEntity join1 = CollectionContentEntity.builder()
                .collectionId(testEntity.getId())
                .contentId(testBlocks.get(0).getId())
                .orderIndex(0)
                .visible(true)
                .build();
        CollectionContentEntity join2 = CollectionContentEntity.builder()
                .collectionId(testEntity.getId())
                .contentId(testBlocks.get(1).getId())
                .orderIndex(1)
                .visible(true)
                .build();
        joinEntries.add(join1);
        joinEntries.add(join2);
        
        when(collectionContentDao.findByCollectionIdOrderByOrderIndex(testEntity.getId()))
                .thenReturn(joinEntries);
        when(contentDao.findAllByIds(anyList()))
                .thenReturn(testBlocks);
        when(contentProcessingUtil.convertBulkLoadedContentToModel(any(ContentEntity.class), any(CollectionContentEntity.class)))
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

    // TODO: Fix
//    @Test
//    void convertToModel_shouldConvertEntityWithPaginatedContentBlocks() {
//        // Arrange
////        when(homeCardRepository.findByReferenceId(any())).thenReturn(Optional.empty());
//        Page<CollectionContentEntity> page = new PageImpl<CollectionContentEntity>(testBlocks, PageRequest.of(0, 10), 2);
//
//        when(contentProcessingUtil.convertToModel(any(ContentEntity.class)))
//                .thenAnswer(invocation -> {
//                    ContentEntity entity = invocation.getArgument(0);
//                    ContentModel model = new ContentModel();
//                    model.setId(entity.getId());
//                    model.setContentType(entity.getContentType());
//                    return model;
//                });
//
//        // Act
//        CollectionModel model = util.convertToModel(testEntity, page);
//
//        // Assert
//        assertNotNull(model);
//        assertNotNull(model.getContent());
//        assertEquals(2, model.getContent().size());
//        assertEquals(page.getNumber(), model.getCurrentPage());
//        assertEquals(page.getTotalPages(), model.getTotalPages());
//        assertEquals((int) page.getTotalElements(), model.getContentCount());
//        assertEquals(page.getSize(), model.getContentPerPage());
//    }

    @Test
    void validateAndEnsureUniqueSlug_shouldReturnOriginalSlugWhenUnique() {
        // Arrange
        when(collectionDao.findBySlug("test-slug"))
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

        when(collectionDao.findBySlug("test-slug"))
                .thenReturn(Optional.of(existingEntity));
        when(collectionDao.findBySlug("test-slug-1"))
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
