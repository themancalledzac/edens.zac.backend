package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.LocationRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentGifEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import edens.zac.portfolio.backend.entity.ContentTextEntity;
import edens.zac.portfolio.backend.entity.LocationEntity;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.ContentImageUpdateRequest;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.ContentRequests;
import edens.zac.portfolio.backend.model.ImageSearchRequest;
import edens.zac.portfolio.backend.model.ImageSearchResponse;
import edens.zac.portfolio.backend.services.validator.ContentImageUpdateValidator;
import edens.zac.portfolio.backend.services.validator.ContentValidator;
import edens.zac.portfolio.backend.types.ContentType;
import edens.zac.portfolio.backend.types.TextFormType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
  @DisplayName("searchImages converts via the batch path, never the per-image N+1 path")
  void searchImages_usesBatchConverter() {
    ImageSearchRequest request =
        new ImageSearchRequest(null, null, null, null, null, null, null, null, null, null, 0, 50);

    ContentImageEntity image1 =
        ContentImageEntity.builder().id(1L).contentType(ContentType.IMAGE).build();
    ContentImageEntity image2 =
        ContentImageEntity.builder().id(2L).contentType(ContentType.IMAGE).build();
    List<ContentImageEntity> entities = List.of(image1, image2);

    when(contentRepository.searchImages(request, 50, 0)).thenReturn(entities);
    when(contentRepository.countSearchImages(request)).thenReturn(2L);
    when(contentModelConverter.batchConvertImageEntitiesToModels(entities))
        .thenReturn(List.of(stubImageModel(1L), stubImageModel(2L)));

    ImageSearchResponse response = service.searchImages(request);

    assertThat(response.content()).hasSize(2);
    assertThat(response.totalElements()).isEqualTo(2L);
    assertThat(response.totalPages()).isEqualTo(1);

    // Batch conversion (3 queries total) must be used; the per-image path (3 queries/image) must
    // never be hit, otherwise a page of N images costs 3N queries against the remote database.
    verify(contentModelConverter).batchConvertImageEntitiesToModels(entities);
    verify(contentModelConverter, never()).convertImageEntityToModel(any());
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

  @Test
  @DisplayName("updateGif persists people + locations to the content-level joins")
  void updateGif_persistsPeopleAndLocations() {
    Long gifId = 77L;
    ContentGifEntity existing =
        ContentGifEntity.builder()
            .id(gifId)
            .contentType(ContentType.GIF)
            .gifUrl("s3://gif/full.mp4")
            .build();

    ContentPersonEntity person = ContentPersonEntity.builder().id(7L).personName("Ansel").build();
    LocationEntity location = LocationEntity.builder().id(3L).locationName("Yosemite").build();

    when(contentRepository.findGifById(gifId)).thenReturn(Optional.of(existing));
    when(personRepository.findContentPeople(gifId)).thenReturn(List.of());
    when(locationRepository.findLocationsByContentIds(List.of(gifId))).thenReturn(Map.of());
    when(contentMutationUtil.updatePeople(any(), any(), any())).thenReturn(Set.of(person));
    when(contentMutationUtil.updateLocations(any(), any(), any())).thenReturn(Set.of(location));
    when(contentRepository.saveGif(existing)).thenReturn(existing);
    when(contentModelConverter.convertEntityToModel(any(CollectionContentEntity.class)))
        .thenReturn(stubGifModel(gifId));

    ContentRequests.UpdateGif update =
        new ContentRequests.UpdateGif(
            null,
            null,
            null,
            null,
            new CollectionRequests.PersonUpdate(List.of(7L), null, null),
            new CollectionRequests.LocationUpdate(List.of(3L), null, null),
            null);

    service.updateGif(gifId, update);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Long>> personIdsCaptor = ArgumentCaptor.forClass(List.class);
    verify(contentRepository).saveContentPeople(eq(gifId), personIdsCaptor.capture());
    assertThat(personIdsCaptor.getValue()).containsExactly(7L);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Long>> locationIdsCaptor = ArgumentCaptor.forClass(List.class);
    verify(locationRepository).saveContentLocations(eq(gifId), locationIdsCaptor.capture());
    assertThat(locationIdsCaptor.getValue()).containsExactly(3L);
  }

  @Test
  @DisplayName("updateGif applies captureDate to the saved entity")
  void updateGif_appliesCaptureDate() {
    Long gifId = 42L;
    ContentGifEntity existing = ContentGifEntity.builder().id(gifId).gifUrl("u").build();
    when(contentRepository.findGifById(gifId)).thenReturn(Optional.of(existing));
    when(contentRepository.saveGif(any(ContentGifEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(contentModelConverter.convertEntityToModel(any(CollectionContentEntity.class)))
        .thenReturn(stubGifModel(gifId));

    LocalDateTime date = LocalDateTime.of(2024, 6, 14, 0, 0);
    ContentRequests.UpdateGif request =
        new ContentRequests.UpdateGif(null, null, date, null, null, null, null);

    service.updateGif(gifId, request);

    ArgumentCaptor<ContentGifEntity> captor = ArgumentCaptor.forClass(ContentGifEntity.class);
    verify(contentRepository).saveGif(captor.capture());
    assertThat(captor.getValue().getCaptureDate()).isEqualTo(date);
  }

  /** Minimal Gif model stub; the test asserts on the repository invocations, not this return. */
  private ContentModels.Gif stubGifModel(Long id) {
    return new ContentModels.Gif(
        id,
        ContentType.GIF,
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
        List.of(),
        List.of(),
        List.of(),
        List.of());
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

  @Test
  @DisplayName("linkContentToCollection inspects nothing about the target beyond its existence")
  void linkContentToCollection_anyExistingCollection_isPersisted() {
    // Scope: this pins the EXISTENCE check only. It cannot pin Rule B -- parent-ness is derived
    // from the collection_content join and collectionRepository is a mock here, so no builder-built
    // fixture is a wrapper. RuleBMixedContentIntegrationTest is the real pin.
    CollectionEntity target =
        CollectionEntity.builder().id(1L).slug("target").title("Target").build();
    when(collectionRepository.findById(1L)).thenReturn(Optional.of(target));

    service.linkContentToCollection(1L, 42L, 3, true);

    ArgumentCaptor<CollectionContentEntity> captor =
        ArgumentCaptor.forClass(CollectionContentEntity.class);
    verify(collectionRepository).saveContent(captor.capture());
    assertThat(captor.getValue().getContentId()).isEqualTo(42L);
    assertThat(captor.getValue().getOrderIndex()).isEqualTo(3);
  }

  @Test
  @DisplayName("deleteImages routes GIF ids to the gif delete path instead of reporting not-found")
  void deleteImages_gifId_deletesGif() {
    ContentGifEntity gif =
        ContentGifEntity.builder().id(2076L).contentType(ContentType.GIF).build();
    when(contentRepository.findContentTypeById(2076L)).thenReturn(Optional.of(ContentType.GIF));
    when(contentRepository.findGifById(2076L)).thenReturn(Optional.of(gif));

    Map<String, Object> result = service.deleteImages(List.of(2076L));

    assertThat(result.get("deletedIds")).isEqualTo(List.of(2076L));
    assertThat((List<?>) result.get("errors")).isEmpty();
    verify(imageProcessingService).deleteGifFromS3(gif);
    verify(contentRepository).deleteGifById(2076L);
    verify(contentRepository, never()).deleteImageById(any());
  }

  @Test
  @DisplayName("deleteImages still deletes image ids through the image path")
  void deleteImages_imageId_deletesImage() {
    ContentImageEntity image =
        ContentImageEntity.builder().id(10L).contentType(ContentType.IMAGE).build();
    when(contentRepository.findContentTypeById(10L)).thenReturn(Optional.of(ContentType.IMAGE));
    when(contentRepository.findImageById(10L)).thenReturn(Optional.of(image));

    Map<String, Object> result = service.deleteImages(List.of(10L));

    assertThat(result.get("deletedIds")).isEqualTo(List.of(10L));
    verify(imageProcessingService).deleteImageFromS3(image);
    verify(contentRepository).deleteImageById(10L);
    verify(contentRepository, never()).deleteGifById(any());
  }

  @Test
  @DisplayName("deleteImages reports unknown ids as not found without deleting anything")
  void deleteImages_missingId_reportsError() {
    when(contentRepository.findContentTypeById(999L)).thenReturn(Optional.empty());

    Map<String, Object> result = service.deleteImages(List.of(999L));

    assertThat((List<?>) result.get("deletedIds")).isEmpty();
    assertThat(result.get("errors")).isEqualTo(List.of("Content not found: 999"));
  }

  // ============== createTextContent format type ==============

  @Test
  void createTextContent_storesTheLowercaseWireValueNotTheEnumName() {
    // TextFormType serializes getValue() ("markdown") and every read path hands format_type
    // straight to the client, but this path stored name() ("MARKDOWN"). V57 folds the rows that
    // were already written that way.
    var request =
        new ContentRequests.CreateTextContent(
            5L, "Title", null, "  some body  ", TextFormType.MARKDOWN);
    when(collectionRepository.findById(5L))
        .thenReturn(Optional.of(CollectionEntity.builder().id(5L).build()));
    when(contentRepository.saveText(any()))
        .thenAnswer(
            invocation -> {
              ContentTextEntity saved = invocation.getArgument(0);
              saved.setId(99L);
              return saved;
            });
    when(contentModelConverter.convertEntityToModel(any())).thenReturn(textModel());

    service.createTextContent(request);

    ArgumentCaptor<ContentTextEntity> captor = ArgumentCaptor.forClass(ContentTextEntity.class);
    verify(contentRepository).saveText(captor.capture());
    assertThat(captor.getValue().getFormatType()).isEqualTo("markdown");
    assertThat(captor.getValue().getTextContent()).isEqualTo("some body");
  }

  @Test
  void createTextContent_defaultsToTheLowercasePlainWireValue() {
    var request = new ContentRequests.CreateTextContent(5L, "Title", null, "body", null);
    when(collectionRepository.findById(5L))
        .thenReturn(Optional.of(CollectionEntity.builder().id(5L).build()));
    when(contentRepository.saveText(any()))
        .thenAnswer(
            invocation -> {
              ContentTextEntity saved = invocation.getArgument(0);
              saved.setId(99L);
              return saved;
            });
    when(contentModelConverter.convertEntityToModel(any())).thenReturn(textModel());

    service.createTextContent(request);

    ArgumentCaptor<ContentTextEntity> captor = ArgumentCaptor.forClass(ContentTextEntity.class);
    verify(contentRepository).saveText(captor.capture());
    assertThat(captor.getValue().getFormatType()).isEqualTo("plain");
  }

  private static ContentModels.Text textModel() {
    return new ContentModels.Text(
        99L, ContentType.TEXT, "Title", null, null, 0, true, null, null, "some body", "markdown");
  }
}
