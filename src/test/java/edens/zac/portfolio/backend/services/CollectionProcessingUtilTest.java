package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import edens.zac.portfolio.backend.dao.CollectionPeopleRepository;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.CollectionSiblingRepository;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.LocationRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.ContentTextEntity;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
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

  @Mock private CollectionPeopleRepository collectionPeopleRepository;

  @Mock private ContentRepository contentRepository;

  @Mock private ContentModelConverter contentModelConverter;

  @Mock private LocationRepository locationRepository;

  @Mock private TagRepository tagRepository;

  @Mock private PersonRepository personRepository;

  @Mock private CollectionSiblingRepository collectionSiblingRepository;

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
    testEntity.setVisibility(CollectionVisibility.LISTED);
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
    assertEquals(testEntity.getVisibility(), model.getVisibility());
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
    // Reset to HIDDEN (the field default) so the type-specific override applies.
    entity.setVisibility(CollectionVisibility.HIDDEN);

    // Act
    CollectionEntity result = util.applyTypeSpecificDefaults(entity);

    // Assert
    // Config JSON removed; ensure other defaults still apply
    assertEquals(30, result.getContentPerPage());
    // Client galleries are private by default -> UNLISTED (direct slug access only).
    assertEquals(CollectionVisibility.UNLISTED, result.getVisibility());
  }

  @Test
  void populateSiblings_listedOnlyTrue_resolvesCoverImageUrlInBatch() {
    CollectionModel model = CollectionModel.builder().id(5L).build();
    // Sibling 9 has cover image 100; sibling 11 has none.
    List<Records.SiblingRow> rows =
        List.of(
            new Records.SiblingRow(
                9L, "Dolomites Film", "dolomites-film", CollectionType.PORTFOLIO, 100L),
            new Records.SiblingRow(
                11L, "Alps Digital", "alps-digital", CollectionType.PORTFOLIO, null));
    when(collectionSiblingRepository.findSiblings(5L, true)).thenReturn(rows);

    ContentImageEntity cover =
        ContentImageEntity.builder()
            .id(100L)
            .imageUrlWeb("https://cdn.example.com/dolomites-film-cover.jpg")
            .build();
    when(contentRepository.findImagesByIds(List.of(100L))).thenReturn(List.of(cover));

    util.populateSiblings(model, true);

    assertThat(model.getSiblings()).hasSize(2);
    Records.CollectionList withCover = model.getSiblings().get(0);
    assertThat(withCover.id()).isEqualTo(9L);
    assertThat(withCover.name()).isEqualTo("Dolomites Film");
    assertThat(withCover.slug()).isEqualTo("dolomites-film");
    assertThat(withCover.type()).isEqualTo(CollectionType.PORTFOLIO);
    assertThat(withCover.coverImageUrl())
        .isEqualTo("https://cdn.example.com/dolomites-film-cover.jpg");

    Records.CollectionList withoutCover = model.getSiblings().get(1);
    assertThat(withoutCover.id()).isEqualTo(11L);
    assertThat(withoutCover.coverImageUrl()).isNull();

    verify(collectionSiblingRepository).findSiblings(5L, true);
    verify(contentRepository).findImagesByIds(List.of(100L));
  }

  @Test
  void populateSiblings_noCoverImages_skipsImageLookup() {
    CollectionModel model = CollectionModel.builder().id(5L).build();
    List<Records.SiblingRow> rows =
        List.of(
            new Records.SiblingRow(
                11L, "Alps Digital", "alps-digital", CollectionType.PORTFOLIO, null));
    when(collectionSiblingRepository.findSiblings(5L, false)).thenReturn(rows);

    util.populateSiblings(model, false);

    assertThat(model.getSiblings()).hasSize(1);
    assertThat(model.getSiblings().get(0).coverImageUrl()).isNull();
    // No cover image ids -> no batch image lookup at all.
    verify(contentRepository, never()).findImagesByIds(anyList());
  }

  @Test
  void populateSiblings_nullModel_isNoOp() {
    util.populateSiblings(null, true);
    verifyNoInteractions(collectionSiblingRepository);
  }

  @Test
  void populateSiblings_nullId_isNoOp() {
    util.populateSiblings(CollectionModel.builder().build(), false);
    verifyNoInteractions(collectionSiblingRepository);
  }
}
