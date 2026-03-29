package edens.zac.portfolio.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.LocationRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.ContentCameraEntity;
import edens.zac.portfolio.backend.entity.ContentEntity;
import edens.zac.portfolio.backend.entity.ContentGifEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.ContentLensEntity;
import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import edens.zac.portfolio.backend.entity.ContentTextEntity;
import edens.zac.portfolio.backend.entity.LocationEntity;
import edens.zac.portfolio.backend.entity.TagEntity;
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.types.ContentType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ContentModelConverterTest {

  @Mock private ContentRepository contentRepository;
  @Mock private CollectionRepository collectionRepository;
  @Mock private TagRepository tagRepository;
  @Mock private PersonRepository personRepository;
  @Mock private LocationRepository locationRepository;

  @InjectMocks private ContentModelConverter contentModelConverter;

  @BeforeEach
  void setUp() {
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
    ContentModel result = contentModelConverter.convertRegularContentEntityToModel(entity);

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
    ContentModel result = contentModelConverter.convertRegularContentEntityToModel(entity);

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
    ContentModel result = contentModelConverter.convertRegularContentEntityToModel(entity);

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
              contentModelConverter.convertRegularContentEntityToModel(entity);
            });
    assertTrue(exception.getMessage().contains("Unknown content type"));
  }

  // =============================================================================
  // Tests for batchConvertImageEntitiesToModels
  // =============================================================================

  @Test
  void batchConvertImageEntitiesToModels_emptyList_returnsEmptyList() {
    // Act
    List<ContentModels.Image> result =
        contentModelConverter.batchConvertImageEntitiesToModels(List.of());

    // Assert
    assertTrue(result.isEmpty());
    verifyNoInteractions(tagRepository, personRepository, locationRepository);
  }

  @Test
  void batchConvertImageEntitiesToModels_nullList_returnsEmptyList() {
    // Act
    List<ContentModels.Image> result =
        contentModelConverter.batchConvertImageEntitiesToModels(null);

    // Assert
    assertTrue(result.isEmpty());
  }

  @Test
  void batchConvertImageEntitiesToModels_multipleEntities_batchLoadsRelatedData() {
    // Arrange
    ContentImageEntity entity1 = createContentImageEntity();
    entity1.setId(10L);
    entity1.setTitle("Image One");
    entity1.setLocationId(100L);

    ContentImageEntity entity2 = createContentImageEntity();
    entity2.setId(20L);
    entity2.setTitle("Image Two");
    entity2.setLocationId(200L);

    List<Long> contentIds = List.of(10L, 20L);

    TagEntity tag1 = new TagEntity("landscape");
    tag1.setId(1L);
    TagEntity tag2 = new TagEntity("portrait");
    tag2.setId(2L);

    ContentPersonEntity person1 = new ContentPersonEntity("Alice");
    person1.setId(1L);

    LocationEntity loc100 = LocationEntity.builder().id(100L).locationName("Seattle").build();
    LocationEntity loc200 = LocationEntity.builder().id(200L).locationName("Portland").build();

    when(tagRepository.findTagsByContentIds(contentIds))
        .thenReturn(Map.of(10L, List.of(tag1), 20L, List.of(tag2)));
    when(personRepository.findPeopleByContentIds(contentIds))
        .thenReturn(Map.of(10L, List.of(person1)));
    when(locationRepository.findByIds(anyList())).thenReturn(Map.of(100L, loc100, 200L, loc200));

    // Act
    List<ContentModels.Image> result =
        contentModelConverter.batchConvertImageEntitiesToModels(List.of(entity1, entity2));

    // Assert
    assertEquals(2, result.size());

    ContentModels.Image model1 = result.get(0);
    assertEquals(10L, model1.id());
    assertEquals("Image One", model1.title());
    assertEquals("Seattle", model1.location().name());
    assertEquals(1, model1.tags().size());
    assertEquals("landscape", model1.tags().get(0).name());
    assertEquals(1, model1.people().size());
    assertEquals("Alice", model1.people().get(0).name());

    ContentModels.Image model2 = result.get(1);
    assertEquals(20L, model2.id());
    assertEquals("Image Two", model2.title());
    assertEquals("Portland", model2.location().name());
    assertEquals(1, model2.tags().size());
    assertEquals("portrait", model2.tags().get(0).name());
    assertTrue(model2.people().isEmpty());

    // Verify batch queries were used (one call each, not per-entity)
    verify(tagRepository).findTagsByContentIds(contentIds);
    verify(personRepository).findPeopleByContentIds(contentIds);
    verify(locationRepository).findByIds(anyList());
  }

  // =============================================================================
  // Tests for buildImageModelWithBatchData
  // =============================================================================

  @Test
  void buildImageModelWithBatchData_nullEntity_returnsNull() {
    // Act
    ContentModels.Image result =
        contentModelConverter.buildImageModelWithBatchData(
            null, null, null, Map.of(), Map.of(), Map.of());

    // Assert
    assertNull(result);
  }

  @Test
  void buildImageModelWithBatchData_withOrderIndexAndVisible_populatesFields() {
    // Arrange
    ContentImageEntity entity = createContentImageEntity();
    entity.setLocationId(null); // no location

    TagEntity tag = new TagEntity("nature");
    tag.setId(1L);
    ContentPersonEntity person = new ContentPersonEntity("Bob");
    person.setId(1L);

    Map<Long, List<TagEntity>> tagMap = Map.of(entity.getId(), List.of(tag));
    Map<Long, List<ContentPersonEntity>> peopleMap = Map.of(entity.getId(), List.of(person));

    // Act
    ContentModels.Image result =
        contentModelConverter.buildImageModelWithBatchData(
            entity, 3, true, tagMap, peopleMap, Map.of());

    // Assert
    assertEquals(3, result.orderIndex());
    assertEquals(true, result.visible());
    assertNull(result.location());
    assertEquals(1, result.tags().size());
    assertEquals("nature", result.tags().get(0).name());
    assertEquals(1, result.people().size());
    assertEquals("Bob", result.people().get(0).name());
  }

  @Test
  void buildImageModelWithBatchData_noMatchingRelatedData_returnsEmptyCollections() {
    // Arrange
    ContentImageEntity entity = createContentImageEntity();
    entity.setId(99L);
    entity.setLocationId(null);

    // Act - maps don't contain entity's ID
    ContentModels.Image result =
        contentModelConverter.buildImageModelWithBatchData(
            entity, null, null, Map.of(), Map.of(), Map.of());

    // Assert
    assertTrue(result.tags().isEmpty());
    assertTrue(result.people().isEmpty());
    assertNull(result.location());
  }

  // Helper methods to create test entities

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
}
