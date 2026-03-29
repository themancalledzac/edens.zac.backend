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
import edens.zac.portfolio.backend.entity.ContentTextEntity;
import edens.zac.portfolio.backend.entity.LocationEntity;
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.types.ContentType;
import java.time.LocalDateTime;
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
