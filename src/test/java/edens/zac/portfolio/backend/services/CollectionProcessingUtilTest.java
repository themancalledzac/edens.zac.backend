package edens.zac.portfolio.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.LocationRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentEntity;
import edens.zac.portfolio.backend.entity.ContentTextEntity;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.ContentType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CollectionProcessingUtilTest {

  @Mock private CollectionRepository collectionRepository;

  @Mock private ContentRepository contentRepository;

  @Mock private ContentProcessingUtil contentProcessingUtil;

  @Mock private LocationRepository locationRepository;

  @Mock private TagRepository tagRepository;

  @Mock private PersonRepository personRepository;

  @InjectMocks private CollectionProcessingUtil util;

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
  void validateAndEnsureUniqueSlug_shouldReturnOriginalSlugWhenUnique() {
    // Arrange
    when(collectionRepository.findBySlug("test-slug")).thenReturn(Optional.empty());

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

    when(collectionRepository.findBySlug("test-slug")).thenReturn(Optional.of(existingEntity));
    when(collectionRepository.findBySlug("test-slug-1")).thenReturn(Optional.empty());

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
    assertEquals(30, result.getContentPerPage());
    assertFalse(result.getVisible()); // Client galleries are private by default
  }

  // ======================================
  // hashPassword and passwordMatches tests (BCrypt)
  // ======================================

  @Test
  void hashPassword_shouldReturnBCryptHash() {
    String hash = CollectionProcessingUtil.hashPassword("password");
    // BCrypt hashes start with $2a$ (or $2b$, $2y$)
    assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$"));
  }

  @Test
  void hashPassword_differentCallsProduceDifferentHashes() {
    // BCrypt uses random salt, so same input produces different hashes
    String h1 = CollectionProcessingUtil.hashPassword("repeatable");
    String h2 = CollectionProcessingUtil.hashPassword("repeatable");
    assertNotEquals(h1, h2);
  }

  @Test
  void hashPassword_shouldHandleUnicode() {
    String hash = CollectionProcessingUtil.hashPassword("passwörd");
    assertNotNull(hash);
    assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$"));
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
  void passwordMatches_nullPassword_returnsFalse() {
    String hash = CollectionProcessingUtil.hashPassword("abc");
    assertFalse(CollectionProcessingUtil.passwordMatches(null, hash));
  }

  @Test
  void passwordMatches_nullHash_returnsFalse() {
    assertFalse(CollectionProcessingUtil.passwordMatches("password", null));
  }

  @Test
  void passwordMatches_bothNull_returnsFalse() {
    assertFalse(CollectionProcessingUtil.passwordMatches(null, null));
  }
}
