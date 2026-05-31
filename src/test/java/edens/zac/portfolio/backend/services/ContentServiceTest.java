package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.LocationRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.model.ContentImageUpdateRequest;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.services.validator.ContentImageUpdateValidator;
import edens.zac.portfolio.backend.services.validator.ContentValidator;
import edens.zac.portfolio.backend.types.ContentType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Service-level tests for {@link ContentService#updateImages}. Verifies that editable metadata
 * applied to the in-memory entity is the actual entity handed to the repository for persistence.
 */
@ExtendWith(MockitoExtension.class)
class ContentServiceTest {

  @Mock private TagRepository tagRepository;
  @Mock private ContentRepository contentRepository;
  @Mock private CollectionRepository collectionRepository;
  @Mock private PersonRepository personRepository;
  @Mock private LocationRepository locationRepository;
  @Mock private ContentMutationUtil contentMutationUtil;
  @Mock private ContentModelConverter contentModelConverter;
  @Mock private ImageProcessingService imageProcessingService;
  @Mock private ContentImageUpdateValidator contentImageUpdateValidator;
  @Mock private ContentValidator contentValidator;
  @Mock private MetadataService metadataService;

  private ContentService service;

  @BeforeEach
  void setUp() {
    service =
        new ContentService(
            tagRepository,
            contentRepository,
            collectionRepository,
            personRepository,
            locationRepository,
            contentMutationUtil,
            contentModelConverter,
            imageProcessingService,
            contentImageUpdateValidator,
            contentValidator,
            metadataService,
            "cloudfront.example.com");
  }

  @Test
  @DisplayName("updateImages applies caption + alt to the persisted entity")
  void updateImages_persistsCaptionAndAlt() {
    Long imageId = 42L;
    ContentImageEntity existing =
        ContentImageEntity.builder().id(imageId).contentType(ContentType.IMAGE).build();

    when(contentRepository.findImagesByIds(List.of(imageId))).thenReturn(List.of(existing));
    when(tagRepository.findTagsByContentIds(List.of(imageId))).thenReturn(Map.of());
    when(personRepository.findPeopleByContentIds(List.of(imageId))).thenReturn(Map.of());
    when(locationRepository.findLocationsByContentIds(List.of(imageId))).thenReturn(Map.of());
    when(contentModelConverter.convertRegularContentEntityToModel(existing))
        .thenReturn(stubImageModel(imageId));

    ContentImageUpdateRequest update =
        ContentImageUpdateRequest.builder()
            .id(imageId)
            .caption("On the ridge")
            .alt("hiker on a ridge")
            .build();

    service.updateImages(List.of(update));

    ArgumentCaptor<ContentImageEntity> captor = ArgumentCaptor.forClass(ContentImageEntity.class);
    verify(contentRepository).saveImage(captor.capture());

    ContentImageEntity saved = captor.getValue();
    assertThat(saved.getCaption()).isEqualTo("On the ridge");
    assertThat(saved.getAlt()).isEqualTo("hiker on a ridge");
  }

  /** Minimal model stub; the test asserts on the captured entity, not this return value. */
  private ContentModels.Image stubImageModel(Long id) {
    return new ContentModels.Image(
        id,
        ContentType.IMAGE,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of());
  }
}
