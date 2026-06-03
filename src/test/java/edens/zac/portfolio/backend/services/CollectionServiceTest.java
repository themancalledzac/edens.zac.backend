package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.LocationRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.LocationEntity;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.LocationPageResponse;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

  @Mock private CollectionRepository collectionRepository;
  @Mock private ContentRepository contentRepository;
  @Mock private LocationRepository locationRepository;
  @Mock private TagRepository tagRepository;
  @Mock private CollectionProcessingUtil collectionProcessingUtil;
  @Mock private ContentMutationUtil contentMutationUtil;
  @Mock private ContentModelConverter contentModelConverter;
  @Mock private MetadataService metadataService;
  @Mock private SyntheticCollectionResolver syntheticResolver;
  @Mock private ClientGalleryAuthService clientGalleryAuthService;

  @Mock
  private edens.zac.portfolio.backend.dao.CollectionSiblingRepository collectionSiblingRepository;

  @Mock private org.springframework.core.env.Environment springEnv;

  @InjectMocks private CollectionService service;

  @Captor private ArgumentCaptor<Map<Long, Integer>> mapCaptor;

  private CollectionEntity testCollection;

  private void stubEmptyMetadata() {
    when(metadataService.getAllTags()).thenReturn(List.of());
    when(metadataService.getAllPeople()).thenReturn(List.of());
    when(metadataService.getAllLocations()).thenReturn(List.of());
    when(metadataService.getAllCameras()).thenReturn(List.of());
    when(metadataService.getAllLenses()).thenReturn(List.of());
    when(metadataService.getAllFilmTypes()).thenReturn(List.of());
    when(collectionRepository.findIdTitleSlugAndType()).thenReturn(List.of());
  }

  @BeforeEach
  void setUp() {
    testCollection =
        CollectionEntity.builder()
            .id(1L)
            .title("Test Collection")
            .slug("test-collection")
            .type(CollectionType.PORTFOLIO)
            .visibility(CollectionVisibility.LISTED)
            .build();
  }

  @Nested
  class CreateCollection {

    @Test
    void createCollection_happyPath_savesAndReturnsUpdateResponse() {
      CollectionRequests.Create request =
          new CollectionRequests.Create(CollectionType.PORTFOLIO, "New Collection");

      CollectionEntity savedEntity =
          CollectionEntity.builder()
              .id(10L)
              .title("New Collection")
              .slug("new-collection")
              .type(CollectionType.PORTFOLIO)
              .visibility(CollectionVisibility.LISTED)
              .build();

      CollectionModel model =
          CollectionModel.builder().id(10L).title("New Collection").slug("new-collection").build();

      when(collectionProcessingUtil.toEntity(eq(request), anyInt())).thenReturn(savedEntity);
      when(collectionRepository.save(savedEntity)).thenReturn(savedEntity);
      when(collectionRepository.findBySlug("new-collection")).thenReturn(Optional.of(savedEntity));
      when(collectionProcessingUtil.convertToFullModel(savedEntity)).thenReturn(model);
      stubEmptyMetadata();

      CollectionRequests.UpdateResponse result = service.createCollection(request);

      assertThat(result).isNotNull();
      assertThat(result.collection()).isNotNull();
      assertThat(result.collection().getTitle()).isEqualTo("New Collection");
      verify(collectionRepository).save(savedEntity);
    }

    @Test
    void createCollection_verifiesEntityCreatedViaUtil() {
      CollectionRequests.Create request =
          new CollectionRequests.Create(CollectionType.BLOG, "My Blog");

      CollectionEntity entity =
          CollectionEntity.builder()
              .id(5L)
              .title("My Blog")
              .slug("my-blog")
              .type(CollectionType.BLOG)
              .visibility(CollectionVisibility.LISTED)
              .build();

      CollectionModel model =
          CollectionModel.builder().id(5L).title("My Blog").slug("my-blog").build();

      when(collectionProcessingUtil.toEntity(eq(request), anyInt())).thenReturn(entity);
      when(collectionRepository.save(entity)).thenReturn(entity);
      when(collectionRepository.findBySlug("my-blog")).thenReturn(Optional.of(entity));
      when(collectionProcessingUtil.convertToFullModel(entity)).thenReturn(model);
      stubEmptyMetadata();

      service.createCollection(request);

      verify(collectionProcessingUtil).toEntity(eq(request), anyInt());
      verify(collectionRepository).save(entity);
    }
  }

  @Nested
  class DeleteCollection {

    @Test
    void deleteCollection_happyPath_deletesJoinEntriesThenCollection() {
      Long collectionId = 1L;
      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));

      service.deleteCollection(collectionId);

      verify(collectionRepository).deleteContentByCollectionId(collectionId);
      verify(collectionRepository).deleteById(collectionId);
    }

    @Test
    void deleteCollection_notFound_throwsResourceNotFoundException() {
      Long collectionId = 999L;
      when(collectionRepository.findById(collectionId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.deleteCollection(collectionId))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("Collection not found with ID: 999");

      verify(collectionRepository, never()).deleteContentByCollectionId(any());
      verify(collectionRepository, never()).deleteById(any());
    }

    @Test
    void deleteCollection_deletesJoinEntriesBeforeCollection() {
      Long collectionId = 1L;
      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));

      service.deleteCollection(collectionId);

      // Verify order: join entries deleted first, then collection
      var inOrder = org.mockito.Mockito.inOrder(collectionRepository);
      inOrder.verify(collectionRepository).deleteContentByCollectionId(collectionId);
      inOrder.verify(collectionRepository).deleteById(collectionId);
    }
  }

  @Nested
  class UpdateContentWithMetadata {

    @Test
    void updateContentWithMetadata_happyPath_returnsUpdateResponse() {
      Long collectionId = 1L;
      CollectionRequests.Update updateDTO =
          new CollectionRequests.Update(
              collectionId,
              null,
              "Updated Title",
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
              null);

      CollectionModel updatedModel =
          CollectionModel.builder().id(1L).title("Updated Title").slug("test-collection").build();

      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(collectionRepository.countContentByCollectionId(collectionId)).thenReturn(0L);
      when(collectionRepository.save(any(CollectionEntity.class))).thenReturn(testCollection);
      when(collectionProcessingUtil.convertToBasicModel(any(CollectionEntity.class)))
          .thenReturn(updatedModel);
      when(collectionProcessingUtil.convertToFullModel(any(CollectionEntity.class)))
          .thenReturn(updatedModel);
      when(collectionRepository.findBySlug("test-collection"))
          .thenReturn(Optional.of(testCollection));
      stubEmptyMetadata();

      CollectionRequests.UpdateResponse result =
          service.updateContentWithMetadata(collectionId, updateDTO);

      assertThat(result).isNotNull();
      assertThat(result.collection()).isNotNull();
      assertThat(result.metadata()).isNotNull();
    }

    @Test
    void updateContentWithMetadata_collectionNotFound_throwsException() {
      Long collectionId = 999L;
      CollectionRequests.Update updateDTO =
          new CollectionRequests.Update(
              collectionId,
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
              null);

      when(collectionRepository.findById(collectionId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.updateContentWithMetadata(collectionId, updateDTO))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("Collection not found with ID: 999");
    }

    @Test
    void updateContentWithMetadata_appliesBasicUpdatesViaUtil() {
      Long collectionId = 1L;
      CollectionRequests.Update updateDTO =
          new CollectionRequests.Update(
              collectionId,
              null,
              "New Title",
              "new-slug",
              "New desc",
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
              null);

      CollectionModel updatedModel =
          CollectionModel.builder().id(1L).title("New Title").slug("new-slug").build();

      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(collectionRepository.countContentByCollectionId(collectionId)).thenReturn(0L);
      when(collectionRepository.save(any(CollectionEntity.class))).thenReturn(testCollection);
      when(collectionProcessingUtil.convertToBasicModel(any(CollectionEntity.class)))
          .thenReturn(updatedModel);
      when(collectionProcessingUtil.convertToFullModel(any(CollectionEntity.class)))
          .thenReturn(updatedModel);

      when(collectionRepository.findBySlug("new-slug")).thenReturn(Optional.of(testCollection));
      stubEmptyMetadata();

      service.updateContentWithMetadata(collectionId, updateDTO);

      verify(collectionProcessingUtil).applyBasicUpdates(testCollection, updateDTO);
    }
  }

  @Nested
  class GetCollectionWithPagination {

    @Test
    void getCollectionWithPagination_happyPath_returnsPaginatedModel() {
      String slug = "test-collection";
      CollectionModel model =
          CollectionModel.builder().id(1L).title("Test Collection").slug(slug).build();

      when(collectionRepository.findBySlug(slug)).thenReturn(Optional.of(testCollection));
      when(collectionRepository.countContentByCollectionId(1L)).thenReturn(5L);
      when(collectionRepository.findContentByCollectionId(eq(1L), anyInt(), anyInt()))
          .thenReturn(Collections.emptyList());
      when(collectionProcessingUtil.convertToModel(
              eq(testCollection), any(), anyInt(), anyInt(), anyLong()))
          .thenReturn(model);

      CollectionModel result = service.getCollectionWithPagination(slug, 0, 10);

      assertThat(result).isNotNull();
      assertThat(result.getTitle()).isEqualTo("Test Collection");
      verify(collectionProcessingUtil).populateSiblings(model, true);
    }

    @Test
    void getCollectionWithPagination_slugNotFound_throwsException() {
      String slug = "nonexistent";
      when(collectionRepository.findBySlug(slug)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.getCollectionWithPagination(slug, 0, 10))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("Collection not found with slug: nonexistent");
    }

    @Test
    void getCollectionWithPagination_normalizesNegativePage() {
      String slug = "test-collection";
      CollectionModel model = CollectionModel.builder().id(1L).build();

      when(collectionRepository.findBySlug(slug)).thenReturn(Optional.of(testCollection));
      when(collectionRepository.countContentByCollectionId(1L)).thenReturn(0L);
      when(collectionRepository.findContentByCollectionId(eq(1L), anyInt(), eq(0)))
          .thenReturn(Collections.emptyList());
      when(collectionProcessingUtil.convertToModel(
              eq(testCollection), any(), eq(0), anyInt(), anyLong()))
          .thenReturn(model);

      CollectionModel result = service.getCollectionWithPagination(slug, -5, 10);

      assertThat(result).isNotNull();
      // Negative page normalized to 0, so offset = 0
      verify(collectionRepository).findContentByCollectionId(1L, 10, 0);
    }

    @Test
    void getCollectionWithPagination_syntheticSlug_delegatesToResolver() {
      CollectionModel synthetic =
          CollectionModel.builder().slug("all-collections").type(CollectionType.PARENT).build();
      when(syntheticResolver.isSyntheticSlug("all-collections")).thenReturn(true);
      when(syntheticResolver.resolve(eq("all-collections"), anyBoolean())).thenReturn(synthetic);

      CollectionModel out = service.getCollectionWithPagination("all-collections", 0, 10);

      assertThat(out).isSameAs(synthetic);
      verify(collectionRepository, never()).findBySlug(anyString());
    }
  }

  @Nested
  class FindById {

    @Test
    void findById_happyPath_returnsFullModel() {
      CollectionModel model = CollectionModel.builder().id(1L).title("Test Collection").build();

      when(collectionRepository.findById(1L)).thenReturn(Optional.of(testCollection));
      when(collectionProcessingUtil.convertToFullModel(testCollection)).thenReturn(model);

      CollectionModel result = service.findById(1L);

      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void findById_notFound_throwsException() {
      when(collectionRepository.findById(999L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.findById(999L))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("Collection not found with ID: 999");
    }
  }

  @Nested
  class ReorderContent {

    private List<CollectionContentEntity> existingContent;

    @BeforeEach
    void setUp() {
      existingContent =
          List.of(
              CollectionContentEntity.builder()
                  .id(10L)
                  .collectionId(1L)
                  .contentId(100L)
                  .orderIndex(0)
                  .visible(true)
                  .createdAt(LocalDateTime.now())
                  .updatedAt(LocalDateTime.now())
                  .build(),
              CollectionContentEntity.builder()
                  .id(11L)
                  .collectionId(1L)
                  .contentId(101L)
                  .orderIndex(1)
                  .visible(true)
                  .createdAt(LocalDateTime.now())
                  .updatedAt(LocalDateTime.now())
                  .build(),
              CollectionContentEntity.builder()
                  .id(12L)
                  .collectionId(1L)
                  .contentId(102L)
                  .orderIndex(2)
                  .visible(true)
                  .createdAt(LocalDateTime.now())
                  .updatedAt(LocalDateTime.now())
                  .build());
    }

    @Test
    void reorderContent_success_updatesOrderIndexes() {
      Long collectionId = 1L;
      CollectionRequests.Reorder request =
          new CollectionRequests.Reorder(
              List.of(
                  new CollectionRequests.Reorder.ReorderItem(100L, 2),
                  new CollectionRequests.Reorder.ReorderItem(101L, 0),
                  new CollectionRequests.Reorder.ReorderItem(102L, 1)));

      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(collectionRepository.findContentByCollectionIdOrderByOrderIndex(collectionId))
          .thenReturn(existingContent);
      when(collectionRepository.batchUpdateContentOrderIndexes(eq(collectionId), any()))
          .thenReturn(3);

      CollectionModel expectedModel =
          CollectionModel.builder().id(1L).title("Test Collection").build();
      when(collectionProcessingUtil.convertToModel(
              eq(testCollection), any(), anyInt(), anyInt(), anyLong()))
          .thenReturn(expectedModel);

      CollectionModel result = service.reorderContent(collectionId, request);

      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(1L);

      verify(collectionRepository)
          .batchUpdateContentOrderIndexes(eq(collectionId), mapCaptor.capture());
      Map<Long, Integer> capturedMap = mapCaptor.getValue();
      assertThat(capturedMap)
          .containsEntry(100L, 2)
          .containsEntry(101L, 0)
          .containsEntry(102L, 1)
          .hasSize(3);
    }

    @Test
    void reorderContent_collectionNotFound_throwsException() {
      Long collectionId = 999L;
      CollectionRequests.Reorder request =
          new CollectionRequests.Reorder(
              List.of(new CollectionRequests.Reorder.ReorderItem(100L, 0)));

      when(collectionRepository.findById(collectionId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.reorderContent(collectionId, request))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("Collection not found with ID: 999");

      verify(collectionRepository, never()).batchUpdateContentOrderIndexes(any(), any());
    }

    @Test
    void reorderContent_contentNotInCollection_throwsException() {
      Long collectionId = 1L;
      CollectionRequests.Reorder request =
          new CollectionRequests.Reorder(
              List.of(
                  new CollectionRequests.Reorder.ReorderItem(100L, 0),
                  new CollectionRequests.Reorder.ReorderItem(999L, 1)));

      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(collectionRepository.findContentByCollectionIdOrderByOrderIndex(collectionId))
          .thenReturn(existingContent);

      assertThatThrownBy(() -> service.reorderContent(collectionId, request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Content with ID 999 does not belong to collection 1");

      verify(collectionRepository, never()).batchUpdateContentOrderIndexes(any(), any());
    }

    @Test
    void reorderContent_partialReorder_updatesOnlySpecifiedItems() {
      Long collectionId = 1L;
      CollectionRequests.Reorder request =
          new CollectionRequests.Reorder(
              List.of(new CollectionRequests.Reorder.ReorderItem(100L, 5)));

      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(collectionRepository.findContentByCollectionIdOrderByOrderIndex(collectionId))
          .thenReturn(existingContent);
      when(collectionRepository.batchUpdateContentOrderIndexes(eq(collectionId), any()))
          .thenReturn(1);

      CollectionModel expectedModel =
          CollectionModel.builder().id(1L).title("Test Collection").build();
      when(collectionProcessingUtil.convertToModel(
              eq(testCollection), any(), anyInt(), anyInt(), anyLong()))
          .thenReturn(expectedModel);

      CollectionModel result = service.reorderContent(collectionId, request);

      assertThat(result).isNotNull();

      verify(collectionRepository)
          .batchUpdateContentOrderIndexes(eq(collectionId), mapCaptor.capture());
      Map<Long, Integer> capturedMap = mapCaptor.getValue();
      assertThat(capturedMap).containsEntry(100L, 5).hasSize(1);
    }
  }

  @Nested
  class GetLocationPage {

    @Test
    void getLocationPage_shouldReturnCollectionsAndOrphanImages() {
      // Arrange
      String locationName = "Seattle";

      LocationEntity seattleLocation =
          LocationEntity.builder().id(1L).locationName("Seattle").slug("seattle").build();
      when(locationRepository.findByLocationName(locationName))
          .thenReturn(Optional.of(seattleLocation));

      CollectionEntity collectionEntity =
          CollectionEntity.builder()
              .id(10L)
              .title("Seattle Trip")
              .slug("seattle-trip")
              .type(CollectionType.PORTFOLIO)
              .visibility(CollectionVisibility.LISTED)
              .build();

      CollectionModel collectionModel =
          CollectionModel.builder()
              .id(10L)
              .title("Seattle Trip")
              .slug("seattle-trip")
              .locations(List.of(new Records.Location(1L, "Seattle", "seattle")))
              .build();

      ContentImageEntity orphanImage = ContentImageEntity.builder().id(20L).title("Sunset").build();

      ContentModels.Image imageModel =
          new ContentModels.Image(
              20L, null, "Sunset", null, null, null, null, null, null, null, null, null, null, null,
              null, null, null, null, null, null, null, null, null, null, null, null, null, null,
              null, null, null);

      when(collectionRepository.countListedByLocationName(locationName)).thenReturn(1L);
      when(collectionRepository.findListedByLocationName(locationName, 35, 0))
          .thenReturn(List.of(collectionEntity));
      // totalCollections (1) <= collectionSize (35), so IDs are extracted from paginated result
      // — no findListedIdsByLocationName call needed
      when(collectionProcessingUtil.batchConvertToBasicModels(List.of(collectionEntity)))
          .thenReturn(List.of(collectionModel));
      when(contentRepository.findOrphanImagesByLocationName(
              eq(locationName), eq(List.of(10L)), eq(50), eq(0)))
          .thenReturn(List.of(orphanImage));
      when(contentRepository.countOrphanImagesByLocationName(eq(locationName), eq(List.of(10L))))
          .thenReturn(1L);
      when(contentModelConverter.batchConvertImageEntitiesToModels(List.of(orphanImage)))
          .thenReturn(List.of(imageModel));

      // Act
      LocationPageResponse result = service.getLocationPage(locationName, 0, 35, 0, 50);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.location()).isNotNull();
      assertThat(result.collections()).hasSize(1);
      assertThat(result.collections().getFirst().getTitle()).isEqualTo("Seattle Trip");
      assertThat(result.images()).hasSize(1);
      assertThat(result.totalCollections()).isEqualTo(1L);
      assertThat(result.totalImages()).isEqualTo(1L);
    }

    @Test
    void getLocationPage_noCollections_shouldReturnEmptyCollectionsWithOrphans() {
      // Arrange
      String locationName = "Portland";

      when(locationRepository.findByLocationName(locationName)).thenReturn(Optional.empty());

      ContentImageEntity orphanImage = ContentImageEntity.builder().id(30L).title("Bridge").build();

      ContentModels.Image imageModel =
          new ContentModels.Image(
              30L, null, "Bridge", null, null, null, null, null, null, null, null, null, null, null,
              null, null, null, null, null, null, null, null, null, null, null, null, null, null,
              null, null, null);

      when(collectionRepository.countListedByLocationName(locationName)).thenReturn(0L);
      when(collectionRepository.findListedByLocationName(locationName, 35, 0))
          .thenReturn(Collections.emptyList());
      // totalCollections (0) <= collectionSize (35), so IDs extracted from empty paginated result
      when(collectionProcessingUtil.batchConvertToBasicModels(Collections.emptyList()))
          .thenReturn(Collections.emptyList());
      when(contentRepository.findOrphanImagesByLocationName(
              eq(locationName), eq(Collections.emptyList()), eq(50), eq(0)))
          .thenReturn(List.of(orphanImage));
      when(contentRepository.countOrphanImagesByLocationName(
              eq(locationName), eq(Collections.emptyList())))
          .thenReturn(1L);
      when(contentModelConverter.batchConvertImageEntitiesToModels(List.of(orphanImage)))
          .thenReturn(List.of(imageModel));

      // Act
      LocationPageResponse result = service.getLocationPage(locationName, 0, 35, 0, 50);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.location()).isNotNull();
      assertThat(result.location().name()).isEqualTo("Portland");
      assertThat(result.location().id()).isNull();
      assertThat(result.collections()).isEmpty();
      assertThat(result.images()).hasSize(1);
      assertThat(result.images().getFirst().title()).isEqualTo("Bridge");
      assertThat(result.totalCollections()).isEqualTo(0L);
      assertThat(result.totalImages()).isEqualTo(1L);
    }

    @Test
    void getLocationPage_noResults_shouldReturnEmptyResponse() {
      // Arrange
      String locationName = "Nowhere";

      when(locationRepository.findByLocationName(locationName)).thenReturn(Optional.empty());

      when(collectionRepository.countListedByLocationName(locationName)).thenReturn(0L);
      when(collectionRepository.findListedByLocationName(locationName, 35, 0))
          .thenReturn(Collections.emptyList());
      // totalCollections (0) <= collectionSize (35), so IDs extracted from empty paginated result
      when(collectionProcessingUtil.batchConvertToBasicModels(Collections.emptyList()))
          .thenReturn(Collections.emptyList());
      when(contentRepository.findOrphanImagesByLocationName(
              eq(locationName), eq(Collections.emptyList()), eq(50), eq(0)))
          .thenReturn(Collections.emptyList());
      when(contentRepository.countOrphanImagesByLocationName(
              eq(locationName), eq(Collections.emptyList())))
          .thenReturn(0L);

      // Act
      LocationPageResponse result = service.getLocationPage(locationName, 0, 35, 0, 50);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.location()).isNotNull();
      assertThat(result.location().name()).isEqualTo("Nowhere");
      assertThat(result.location().id()).isNull();
      assertThat(result.collections()).isEmpty();
      assertThat(result.images()).isEmpty();
      assertThat(result.totalCollections()).isEqualTo(0L);
      assertThat(result.totalImages()).isEqualTo(0L);
    }
  }

  @Nested
  class FindMetaBySlug {

    @Test
    void findMetaBySlug_existingSlug_shouldReturnBasicModel() {
      // Arrange
      String slug = "test-collection";
      CollectionModel basicModel =
          CollectionModel.builder()
              .id(1L)
              .title("Test Collection")
              .slug(slug)
              .type(CollectionType.PORTFOLIO)
              .build();

      when(collectionRepository.findBySlug(slug)).thenReturn(Optional.of(testCollection));
      when(collectionProcessingUtil.convertToBasicModel(testCollection)).thenReturn(basicModel);

      // Act
      CollectionModel result = service.findMetaBySlug(slug);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.getTitle()).isEqualTo("Test Collection");
      assertThat(result.getSlug()).isEqualTo(slug);
      verify(collectionRepository).findBySlug(slug);
      verify(collectionProcessingUtil).convertToBasicModel(testCollection);
    }

    @Test
    void findMetaBySlug_nonExistentSlug_shouldThrowResourceNotFoundException() {
      // Arrange
      String slug = "non-existent";
      when(collectionRepository.findBySlug(slug)).thenReturn(Optional.empty());

      // Act & Assert
      assertThatThrownBy(() -> service.findMetaBySlug(slug))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("Collection not found with slug: non-existent");
    }
  }

  @Nested
  class ParentTypeCollections {

    @Test
    void getCollectionWithPagination_parentType_filtersToCollectionContentOnly() {
      String slug = "photography";
      CollectionEntity parentCollection =
          CollectionEntity.builder()
              .id(10L)
              .title("Photography")
              .slug(slug)
              .type(CollectionType.PARENT)
              .visibility(CollectionVisibility.LISTED)
              .build();

      List<CollectionContentEntity> collectionContent =
          List.of(
              CollectionContentEntity.builder()
                  .id(1L)
                  .collectionId(10L)
                  .contentId(100L)
                  .orderIndex(0)
                  .visible(true)
                  .build());

      CollectionModel model = CollectionModel.builder().id(10L).title("Photography").build();

      when(collectionRepository.findBySlug(slug)).thenReturn(Optional.of(parentCollection));
      when(collectionRepository.findContentByCollectionIdAndContentType(10L, "COLLECTION"))
          .thenReturn(collectionContent);
      when(collectionProcessingUtil.convertToModel(
              eq(parentCollection), eq(collectionContent), anyInt(), anyInt(), eq(1L)))
          .thenReturn(model);

      CollectionModel result = service.getCollectionWithPagination(slug, 0, 10);

      assertThat(result).isNotNull();
      // Should use content-type filtered query, not the generic one
      verify(collectionRepository).findContentByCollectionIdAndContentType(10L, "COLLECTION");
      verify(collectionRepository, never()).countContentByCollectionId(anyLong());
      verify(collectionRepository, never())
          .findContentByCollectionId(anyLong(), anyInt(), anyInt());
    }

    @Test
    void getCollectionWithPagination_homeType_alsoFiltersToCollectionContent() {
      String slug = "home";
      CollectionEntity homeCollection =
          CollectionEntity.builder()
              .id(1L)
              .title("Home")
              .slug(slug)
              .type(CollectionType.HOME)
              .visibility(CollectionVisibility.LISTED)
              .build();

      CollectionModel model = CollectionModel.builder().id(1L).title("Home").build();

      when(collectionRepository.findBySlug(slug)).thenReturn(Optional.of(homeCollection));
      when(collectionRepository.findContentByCollectionIdAndContentType(1L, "COLLECTION"))
          .thenReturn(Collections.emptyList());
      when(collectionProcessingUtil.convertToModel(
              eq(homeCollection), any(), anyInt(), anyInt(), eq(0L)))
          .thenReturn(model);

      service.getCollectionWithPagination(slug, 0, 10);

      verify(collectionRepository).findContentByCollectionIdAndContentType(1L, "COLLECTION");
      verify(collectionRepository, never()).countContentByCollectionId(anyLong());
    }

    @Test
    void getUpdateCollectionData_parentType_aggregatesChildCollectionImages() {
      String slug = "photography";
      CollectionEntity parentEntity =
          CollectionEntity.builder()
              .id(10L)
              .title("Photography")
              .slug(slug)
              .type(CollectionType.PARENT)
              .visibility(CollectionVisibility.LISTED)
              .build();

      ContentModels.Collection childContent =
          new ContentModels.Collection(
              100L,
              edens.zac.portfolio.backend.types.ContentType.COLLECTION,
              "Portfolio",
              null,
              null,
              0,
              true,
              null,
              null,
              20L,
              "portfolio",
              CollectionType.PORTFOLIO,
              null);

      CollectionModel model =
          CollectionModel.builder()
              .id(10L)
              .title("Photography")
              .slug(slug)
              .content(List.of(childContent))
              .build();

      when(collectionRepository.findBySlug(slug)).thenReturn(Optional.of(parentEntity));
      when(collectionProcessingUtil.convertToFullModel(parentEntity)).thenReturn(model);
      stubEmptyMetadata();
      when(collectionProcessingUtil.loadImagesFromChildCollections(List.of(20L)))
          .thenReturn(List.of());

      CollectionRequests.UpdateResponse result = service.getUpdateCollectionData(slug);

      assertThat(result).isNotNull();
      assertThat(result.childCollectionImages()).isNotNull();
      verify(collectionProcessingUtil).loadImagesFromChildCollections(List.of(20L));
    }

    @Test
    void getUpdateCollectionData_nonParentType_doesNotAggregateChildImages() {
      String slug = "test-collection";

      CollectionModel model =
          CollectionModel.builder().id(1L).title("Test Collection").slug(slug).build();

      when(collectionRepository.findBySlug(slug)).thenReturn(Optional.of(testCollection));
      when(collectionProcessingUtil.convertToFullModel(testCollection)).thenReturn(model);
      stubEmptyMetadata();

      CollectionRequests.UpdateResponse result = service.getUpdateCollectionData(slug);

      assertThat(result).isNotNull();
      assertThat(result.childCollectionImages()).isNull();
      verify(collectionProcessingUtil, never()).loadImagesFromChildCollections(any());
    }

    @Test
    void getUpdateCollectionData_populatesParentsFromInverseJoin() {
      String slug = "child";
      CollectionEntity child =
          CollectionEntity.builder()
              .id(7L)
              .slug(slug)
              .title("Child")
              .type(CollectionType.PORTFOLIO)
              .visibility(CollectionVisibility.LISTED)
              .build();
      CollectionModel model = CollectionModel.builder().id(7L).slug(slug).title("Child").build();
      CollectionEntity parent =
          CollectionEntity.builder()
              .id(42L)
              .title("Parent")
              .slug("parent")
              .type(CollectionType.PARENT)
              .collectionDate(LocalDate.of(2026, 1, 1))
              .build();

      when(collectionRepository.findBySlug(slug)).thenReturn(Optional.of(child));
      when(collectionProcessingUtil.convertToFullModel(child)).thenReturn(model);
      when(collectionRepository.findAllParentCollectionsByChildId(7L)).thenReturn(List.of(parent));
      stubEmptyMetadata();

      CollectionRequests.UpdateResponse response = service.getUpdateCollectionData(slug);

      assertThat(response.collection().getParents())
          .extracting(Records.CollectionList::id)
          .containsExactly(42L);
      assertThat(response.collection().getParents())
          .extracting(Records.CollectionList::collectionDate)
          .containsExactly(LocalDate.of(2026, 1, 1));
    }

    @Test
    void parentOfClientGalleries_keepsUnlistedChildren_dropsHidden() {
      // PARENT containing CLIENT_GALLERY children: viewer is already inside the password-gated
      // parent context, so UNLISTED galleries (the typical visibility for client work) must
      // remain visible. HIDDEN is still excluded.
      String slug = "smith-wedding";
      CollectionEntity parent =
          CollectionEntity.builder()
              .id(50L)
              .slug(slug)
              .type(CollectionType.PARENT)
              .visibility(CollectionVisibility.LISTED)
              .build();

      ContentModels.Collection unlistedGallery =
          childCollectionContent(101L, CollectionType.CLIENT_GALLERY);
      ContentModels.Collection hiddenGallery =
          childCollectionContent(102L, CollectionType.CLIENT_GALLERY);
      ContentModels.Collection listedGallery =
          childCollectionContent(103L, CollectionType.CLIENT_GALLERY);

      CollectionModel model =
          CollectionModel.builder()
              .id(50L)
              .slug(slug)
              .type(CollectionType.PARENT)
              .content(
                  new java.util.ArrayList<>(List.of(unlistedGallery, hiddenGallery, listedGallery)))
              .build();

      when(collectionRepository.findBySlug(slug)).thenReturn(Optional.of(parent));
      when(collectionRepository.findContentByCollectionIdAndContentType(50L, "COLLECTION"))
          .thenReturn(List.of());
      when(collectionProcessingUtil.convertToModel(
              eq(parent), any(), anyInt(), anyInt(), anyLong()))
          .thenReturn(model);
      when(collectionRepository.findByIds(List.of(101L, 102L, 103L)))
          .thenReturn(
              List.of(
                  childEntity(101L, CollectionType.CLIENT_GALLERY, CollectionVisibility.UNLISTED),
                  childEntity(102L, CollectionType.CLIENT_GALLERY, CollectionVisibility.HIDDEN),
                  childEntity(103L, CollectionType.CLIENT_GALLERY, CollectionVisibility.LISTED)));

      CollectionModel result = service.getCollectionWithPagination(slug, 0, 10);

      assertThat(result.getContent())
          .extracting(c -> ((ContentModels.Collection) c).referencedCollectionId())
          .containsExactly(101L, 103L); // UNLISTED kept, HIDDEN dropped
    }

    @Test
    void parentOfPortfolios_keepsListedOnly_dropsUnlistedAndHidden() {
      // PARENT containing non-CLIENT_GALLERY children (e.g. portfolio rollup): public listing
      // semantics apply — only LISTED children appear, UNLISTED is excluded.
      String slug = "photography";
      CollectionEntity parent =
          CollectionEntity.builder()
              .id(60L)
              .slug(slug)
              .type(CollectionType.PARENT)
              .visibility(CollectionVisibility.LISTED)
              .build();

      ContentModels.Collection listed = childCollectionContent(201L, CollectionType.PORTFOLIO);
      ContentModels.Collection unlisted = childCollectionContent(202L, CollectionType.PORTFOLIO);

      CollectionModel model =
          CollectionModel.builder()
              .id(60L)
              .slug(slug)
              .type(CollectionType.PARENT)
              .content(new java.util.ArrayList<>(List.of(listed, unlisted)))
              .build();

      when(collectionRepository.findBySlug(slug)).thenReturn(Optional.of(parent));
      when(collectionRepository.findContentByCollectionIdAndContentType(60L, "COLLECTION"))
          .thenReturn(List.of());
      when(collectionProcessingUtil.convertToModel(
              eq(parent), any(), anyInt(), anyInt(), anyLong()))
          .thenReturn(model);
      when(collectionRepository.findByIds(List.of(201L, 202L)))
          .thenReturn(
              List.of(
                  childEntity(201L, CollectionType.PORTFOLIO, CollectionVisibility.LISTED),
                  childEntity(202L, CollectionType.PORTFOLIO, CollectionVisibility.UNLISTED)));

      CollectionModel result = service.getCollectionWithPagination(slug, 0, 10);

      assertThat(result.getContent())
          .extracting(c -> ((ContentModels.Collection) c).referencedCollectionId())
          .containsExactly(201L); // LISTED kept, UNLISTED dropped (current behavior preserved)
    }

    @Test
    void parentOfMixedClientGalleryAndPortfolio_appliesClientGalleryContextToAllChildren() {
      // Pinning current behavior: a PARENT with at least one CLIENT_GALLERY child flips the entire
      // response into "client gallery context" — only HIDDEN children are dropped, regardless of
      // the sibling child's type. So an UNLISTED PORTFOLIO sibling stays visible because the viewer
      // is already inside the password-gated parent. The alternative (per-child context) is much
      // more complex; this test pins the simpler whole-PARENT rule so a future refactor has to
      // consciously change it.
      String slug = "smith-wedding";
      CollectionEntity parent =
          CollectionEntity.builder()
              .id(70L)
              .slug(slug)
              .type(CollectionType.PARENT)
              .visibility(CollectionVisibility.LISTED)
              .build();

      ContentModels.Collection unlistedGallery =
          childCollectionContent(301L, CollectionType.CLIENT_GALLERY);
      ContentModels.Collection unlistedPortfolio =
          childCollectionContent(302L, CollectionType.PORTFOLIO);
      ContentModels.Collection listedPortfolio =
          childCollectionContent(303L, CollectionType.PORTFOLIO);
      ContentModels.Collection hiddenPortfolio =
          childCollectionContent(304L, CollectionType.PORTFOLIO);

      CollectionModel model =
          CollectionModel.builder()
              .id(70L)
              .slug(slug)
              .type(CollectionType.PARENT)
              .content(
                  new java.util.ArrayList<>(
                      List.of(
                          unlistedGallery, unlistedPortfolio, listedPortfolio, hiddenPortfolio)))
              .build();

      when(collectionRepository.findBySlug(slug)).thenReturn(Optional.of(parent));
      when(collectionRepository.findContentByCollectionIdAndContentType(70L, "COLLECTION"))
          .thenReturn(List.of());
      when(collectionProcessingUtil.convertToModel(
              eq(parent), any(), anyInt(), anyInt(), anyLong()))
          .thenReturn(model);
      when(collectionRepository.findByIds(List.of(301L, 302L, 303L, 304L)))
          .thenReturn(
              List.of(
                  childEntity(301L, CollectionType.CLIENT_GALLERY, CollectionVisibility.UNLISTED),
                  childEntity(302L, CollectionType.PORTFOLIO, CollectionVisibility.UNLISTED),
                  childEntity(303L, CollectionType.PORTFOLIO, CollectionVisibility.LISTED),
                  childEntity(304L, CollectionType.PORTFOLIO, CollectionVisibility.HIDDEN)));

      CollectionModel result = service.getCollectionWithPagination(slug, 0, 10);

      assertThat(result.getContent())
          .extracting(c -> ((ContentModels.Collection) c).referencedCollectionId())
          .containsExactly(
              301L, 302L, 303L); // gallery + both non-hidden portfolios kept; HIDDEN dropped
    }

    private ContentModels.Collection childCollectionContent(Long childId, CollectionType type) {
      return new ContentModels.Collection(
          childId,
          edens.zac.portfolio.backend.types.ContentType.COLLECTION,
          "Child " + childId,
          null,
          null,
          0,
          true,
          null,
          null,
          childId,
          "child-" + childId,
          type,
          null);
    }

    private CollectionEntity childEntity(
        Long id, CollectionType type, CollectionVisibility visibility) {
      return CollectionEntity.builder().id(id).type(type).visibility(visibility).build();
    }
  }

  @Nested
  class FindChildCollectionsForHome {

    @Test
    void returnsEmptyWhenHomeMissing() {
      when(collectionRepository.findBySlug("home")).thenReturn(Optional.empty());

      assertThat(service.findChildCollectionsForHome()).isEmpty();
    }

    @Test
    void returnsBatchConvertedChildrenForVisibleHomeReferences() {
      CollectionEntity home =
          CollectionEntity.builder().id(1L).slug("home").type(CollectionType.HOME).build();
      when(collectionRepository.findBySlug("home")).thenReturn(Optional.of(home));

      CollectionEntity child =
          CollectionEntity.builder().id(11L).visibility(CollectionVisibility.LISTED).build();
      when(collectionRepository.findReferencedCollectionsByParentId(1L)).thenReturn(List.of(child));

      CollectionModel childModel = CollectionModel.builder().id(11L).build();
      when(collectionProcessingUtil.batchConvertToBasicModels(List.of(child)))
          .thenReturn(List.of(childModel));

      assertThat(service.findChildCollectionsForHome())
          .singleElement()
          .satisfies(m -> assertThat(m.getId()).isEqualTo(11L));
    }
  }

  @Nested
  class FindAllVisibleWithCovers {

    @Test
    void delegatesToRepositoryAndConverts() {
      CollectionEntity entity = CollectionEntity.builder().id(1L).build();
      when(collectionRepository.findAllListedWithCovers()).thenReturn(List.of(entity));
      CollectionModel model = CollectionModel.builder().id(1L).build();
      when(collectionProcessingUtil.batchConvertToBasicModels(List.of(entity)))
          .thenReturn(List.of(model));

      assertThat(service.findAllListedWithCovers()).containsExactly(model);
    }
  }

  @Nested
  class EnforceVisibilityVisibilityRules {

    @Test
    void enforceVisibilityHIDDENBlocksProd() {
      CollectionEntity entity =
          CollectionEntity.builder()
              .id(1L)
              .slug("secret")
              .type(CollectionType.PORTFOLIO)
              .visibility(CollectionVisibility.HIDDEN)
              .build();
      when(collectionRepository.findBySlug("secret")).thenReturn(Optional.of(entity));
      when(springEnv.acceptsProfiles(any(org.springframework.core.env.Profiles.class)))
          .thenReturn(false);

      assertThatThrownBy(() -> service.findMetaBySlug("secret"))
          .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void enforceVisibilityHIDDENPassesInDev() {
      CollectionEntity entity =
          CollectionEntity.builder()
              .id(1L)
              .slug("secret")
              .type(CollectionType.PORTFOLIO)
              .visibility(CollectionVisibility.HIDDEN)
              .build();
      when(collectionRepository.findBySlug("secret")).thenReturn(Optional.of(entity));
      when(springEnv.acceptsProfiles(any(org.springframework.core.env.Profiles.class)))
          .thenReturn(true);
      when(collectionProcessingUtil.convertToBasicModel(entity))
          .thenReturn(CollectionModel.builder().id(1L).slug("secret").build());

      assertThat(service.findMetaBySlug("secret")).isNotNull();
    }

    @Test
    void enforceVisibilityUNLISTEDPassesInProd() {
      CollectionEntity entity =
          CollectionEntity.builder()
              .id(1L)
              .slug("private-gallery")
              .type(CollectionType.CLIENT_GALLERY)
              .visibility(CollectionVisibility.UNLISTED)
              .build();
      when(collectionRepository.findBySlug("private-gallery")).thenReturn(Optional.of(entity));
      when(collectionProcessingUtil.convertToBasicModel(entity))
          .thenReturn(CollectionModel.builder().id(1L).slug("private-gallery").build());

      assertThat(service.findMetaBySlug("private-gallery")).isNotNull();
    }
  }

  @Nested
  class SaveGalleryAccessParentPropagation {

    @Test
    void parentTypeWithPropagateTrue_updatesPasswordOnChildClientGalleries() {
      CollectionEntity parent =
          CollectionEntity.builder()
              .id(100L)
              .slug("company-a")
              .type(CollectionType.PARENT)
              .visibility(CollectionVisibility.LISTED)
              .build();
      CollectionEntity child1 =
          CollectionEntity.builder()
              .id(101L)
              .slug("smith-wedding")
              .type(CollectionType.CLIENT_GALLERY)
              .build();
      CollectionEntity child2 =
          CollectionEntity.builder()
              .id(102L)
              .slug("jones-wedding")
              .type(CollectionType.CLIENT_GALLERY)
              .build();

      when(collectionRepository.findById(100L)).thenReturn(Optional.of(parent));
      when(collectionRepository.findAllReferencedCollectionsByParentId(100L))
          .thenReturn(List.of(child1, child2));

      CollectionRequests.GalleryAccessRequest request =
          new CollectionRequests.GalleryAccessRequest("secretpw", List.of(), true);

      service.updateGalleryAccess(100L, request);

      verify(collectionRepository).saveGalleryAccess(100L, "secretpw", List.of());
      verify(collectionRepository).updateGalleryPassword(101L, "secretpw");
      verify(collectionRepository).updateGalleryPassword(102L, "secretpw");
    }

    @Test
    void parentTypeWithPropagateFalse_skipsChildPropagation() {
      CollectionEntity parent =
          CollectionEntity.builder()
              .id(100L)
              .slug("company-a")
              .type(CollectionType.PARENT)
              .visibility(CollectionVisibility.LISTED)
              .build();
      when(collectionRepository.findById(100L)).thenReturn(Optional.of(parent));

      CollectionRequests.GalleryAccessRequest request =
          new CollectionRequests.GalleryAccessRequest("secretpw", List.of(), false);

      service.updateGalleryAccess(100L, request);

      verify(collectionRepository, never()).findAllReferencedCollectionsByParentId(anyLong());
      verify(collectionRepository, never()).updateGalleryPassword(anyLong(), anyString());
    }

    @Test
    void parentTypeWithPropagateNull_skipsChildPropagation() {
      CollectionEntity parent =
          CollectionEntity.builder()
              .id(100L)
              .slug("company-a")
              .type(CollectionType.PARENT)
              .visibility(CollectionVisibility.LISTED)
              .build();
      when(collectionRepository.findById(100L)).thenReturn(Optional.of(parent));

      CollectionRequests.GalleryAccessRequest request =
          new CollectionRequests.GalleryAccessRequest("secretpw", List.of(), null);

      service.updateGalleryAccess(100L, request);

      verify(collectionRepository, never()).findAllReferencedCollectionsByParentId(anyLong());
      verify(collectionRepository, never()).updateGalleryPassword(anyLong(), anyString());
    }

    @Test
    void clientGalleryTypeWithPropagateTrue_skipsChildPropagation() {
      CollectionEntity gallery =
          CollectionEntity.builder()
              .id(100L)
              .slug("smith-wedding")
              .type(CollectionType.CLIENT_GALLERY)
              .visibility(CollectionVisibility.UNLISTED)
              .build();
      when(collectionRepository.findById(100L)).thenReturn(Optional.of(gallery));

      CollectionRequests.GalleryAccessRequest request =
          new CollectionRequests.GalleryAccessRequest("secretpw", List.of(), true);

      service.updateGalleryAccess(100L, request);

      verify(collectionRepository, never()).findAllReferencedCollectionsByParentId(anyLong());
      verify(collectionRepository, never()).updateGalleryPassword(anyLong(), anyString());
    }

    @Test
    void parentTypeWithPropagateTrue_skipsNonClientGalleryChildren() {
      CollectionEntity parent =
          CollectionEntity.builder()
              .id(100L)
              .slug("mixed-parent")
              .type(CollectionType.PARENT)
              .visibility(CollectionVisibility.LISTED)
              .build();
      CollectionEntity clientChild =
          CollectionEntity.builder()
              .id(101L)
              .slug("smith-wedding")
              .type(CollectionType.CLIENT_GALLERY)
              .build();
      CollectionEntity portfolioChild =
          CollectionEntity.builder()
              .id(102L)
              .slug("studio-portfolio")
              .type(CollectionType.PORTFOLIO)
              .build();
      CollectionEntity blogChild =
          CollectionEntity.builder().id(103L).slug("studio-blog").type(CollectionType.BLOG).build();

      when(collectionRepository.findById(100L)).thenReturn(Optional.of(parent));
      when(collectionRepository.findAllReferencedCollectionsByParentId(100L))
          .thenReturn(List.of(clientChild, portfolioChild, blogChild));

      CollectionRequests.GalleryAccessRequest request =
          new CollectionRequests.GalleryAccessRequest("secretpw", List.of(), true);

      service.updateGalleryAccess(100L, request);

      verify(collectionRepository).updateGalleryPassword(101L, "secretpw");
      verify(collectionRepository, never()).updateGalleryPassword(eq(102L), anyString());
      verify(collectionRepository, never()).updateGalleryPassword(eq(103L), anyString());
    }

    @Test
    void parentTypeWithPropagateTrue_propagatesToUnlistedChildren() {
      CollectionEntity parent =
          CollectionEntity.builder()
              .id(100L)
              .slug("company-a")
              .type(CollectionType.PARENT)
              .visibility(CollectionVisibility.LISTED)
              .build();
      CollectionEntity unlistedChild =
          CollectionEntity.builder()
              .id(101L)
              .slug("private-wedding")
              .type(CollectionType.CLIENT_GALLERY)
              .visibility(CollectionVisibility.UNLISTED)
              .build();

      when(collectionRepository.findById(100L)).thenReturn(Optional.of(parent));
      when(collectionRepository.findAllReferencedCollectionsByParentId(100L))
          .thenReturn(List.of(unlistedChild));

      CollectionRequests.GalleryAccessRequest request =
          new CollectionRequests.GalleryAccessRequest("secretpw", List.of(), true);

      service.updateGalleryAccess(100L, request);

      verify(collectionRepository).updateGalleryPassword(101L, "secretpw");
    }
  }

  @Nested
  class IsGalleryAccessAuthorized {

    @Test
    void unprotectedCollection_returnsTrue() {
      CollectionEntity entity =
          CollectionEntity.builder().id(1L).slug("public-gallery").galleryPassword(null).build();
      when(collectionRepository.findBySlug("public-gallery")).thenReturn(Optional.of(entity));
      // Null password short-circuits in hasValidAccess before any cookie is read,
      // so we don't need to stub request.getCookies().
      jakarta.servlet.http.HttpServletRequest request =
          org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);

      assertThat(service.isGalleryAccessAuthorized("public-gallery", request)).isTrue();
    }

    @Test
    void missingCollection_returnsTrue() {
      when(collectionRepository.findBySlug("missing")).thenReturn(Optional.empty());
      jakarta.servlet.http.HttpServletRequest request =
          org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);

      assertThat(service.isGalleryAccessAuthorized("missing", request)).isTrue();
    }

    @Test
    void protectedCollection_validSlugCookie_returnsTrue() {
      CollectionEntity entity =
          CollectionEntity.builder()
              .id(1L)
              .slug("protected-gallery")
              .galleryPassword("secret123")
              .build();
      when(collectionRepository.findBySlug("protected-gallery")).thenReturn(Optional.of(entity));
      jakarta.servlet.http.HttpServletRequest request =
          org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
      when(request.getCookies())
          .thenReturn(
              new jakarta.servlet.http.Cookie[] {
                new jakarta.servlet.http.Cookie("gallery_access_protected-gallery", "valid-token")
              });
      when(clientGalleryAuthService.validateAccessToken("protected-gallery", "valid-token"))
          .thenReturn(true);

      assertThat(service.isGalleryAccessAuthorized("protected-gallery", request)).isTrue();
    }

    @Test
    void protectedCollection_validFingerprintCookie_returnsTrue() {
      CollectionEntity entity =
          CollectionEntity.builder()
              .id(1L)
              .slug("sibling-gallery")
              .galleryPassword("shared-pw")
              .build();
      when(collectionRepository.findBySlug("sibling-gallery")).thenReturn(Optional.of(entity));
      jakarta.servlet.http.HttpServletRequest request =
          org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
      when(request.getCookies())
          .thenReturn(
              new jakarta.servlet.http.Cookie[] {
                new jakarta.servlet.http.Cookie("gallery_access_pw_FP", "group-token")
              });
      when(clientGalleryAuthService.validateAccessToken(
              eq("sibling-gallery"), org.mockito.Mockito.any()))
          .thenReturn(false);
      when(clientGalleryAuthService.passwordFingerprint("shared-pw")).thenReturn("FP");
      when(clientGalleryAuthService.validatePasswordAccessToken("shared-pw", "group-token"))
          .thenReturn(true);

      assertThat(service.isGalleryAccessAuthorized("sibling-gallery", request)).isTrue();
    }

    @Test
    void protectedCollection_noCookies_returnsFalse() {
      CollectionEntity entity =
          CollectionEntity.builder()
              .id(1L)
              .slug("protected-gallery")
              .galleryPassword("secret123")
              .build();
      when(collectionRepository.findBySlug("protected-gallery")).thenReturn(Optional.of(entity));
      jakarta.servlet.http.HttpServletRequest request =
          org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
      when(request.getCookies()).thenReturn(null);
      when(clientGalleryAuthService.validateAccessToken(
              eq("protected-gallery"), org.mockito.Mockito.any()))
          .thenReturn(false);
      when(clientGalleryAuthService.passwordFingerprint("secret123")).thenReturn("FP");
      when(clientGalleryAuthService.validatePasswordAccessToken(
              eq("secret123"), org.mockito.Mockito.any()))
          .thenReturn(false);

      assertThat(service.isGalleryAccessAuthorized("protected-gallery", request)).isFalse();
    }
  }

  @Nested
  class HandleSiblingUpdates {

    private CollectionRequests.Update updateWithSiblings(
        Long id, CollectionRequests.CollectionUpdate siblings) {
      // 18 positional args: id first, siblings last, everything else null
      return new CollectionRequests.Update(
          id, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
          null, /* collections */ null, /* siblings */ siblings);
    }

    @Test
    void addsEachNewValueAndRemovesEachRemoveId() {
      Long parentId = 1L;
      CollectionRequests.CollectionUpdate siblings =
          new CollectionRequests.CollectionUpdate(
              null,
              List.of(
                  new Records.ChildCollection(20L, null, null, null, null, null),
                  new Records.ChildCollection(21L, null, null, null, null, null)),
              List.of(30L, 31L));
      when(collectionRepository.findById(parentId)).thenReturn(Optional.of(testCollection));

      service.updateContent(parentId, updateWithSiblings(parentId, siblings));

      verify(collectionSiblingRepository).addSibling(parentId, 20L);
      verify(collectionSiblingRepository).addSibling(parentId, 21L);
      verify(collectionSiblingRepository).removeSibling(parentId, 30L);
      verify(collectionSiblingRepository).removeSibling(parentId, 31L);
    }

    @Test
    void skipsSelfReferenceInNewValue() {
      Long parentId = 1L;
      CollectionRequests.CollectionUpdate siblings =
          new CollectionRequests.CollectionUpdate(
              null,
              List.of(new Records.ChildCollection(parentId, null, null, null, null, null)),
              null);
      when(collectionRepository.findById(parentId)).thenReturn(Optional.of(testCollection));

      service.updateContent(parentId, updateWithSiblings(parentId, siblings));

      verify(collectionSiblingRepository, never()).addSibling(eq(parentId), eq(parentId));
    }

    @Test
    void nullSiblings_isNoOp() {
      Long parentId = 1L;
      when(collectionRepository.findById(parentId)).thenReturn(Optional.of(testCollection));

      service.updateContent(parentId, updateWithSiblings(parentId, null));

      verify(collectionSiblingRepository, never()).addSibling(anyLong(), anyLong());
      verify(collectionSiblingRepository, never()).removeSibling(anyLong(), anyLong());
    }
  }

  @Nested
  class HandleParentCollectionUpdates {

    private CollectionEntity current;
    private CollectionEntity targetParent;

    @BeforeEach
    void setUpEntities() {
      current = CollectionEntity.builder().id(7L).title("Current").slug("current").build();
      targetParent = CollectionEntity.builder().id(42L).title("Target Parent").build();
    }

    // 19 positional args: id first, parents last, everything else null.
    private CollectionRequests.Update updateWithParents(
        CollectionRequests.CollectionUpdate parents) {
      return new CollectionRequests.Update(
          current.getId(),
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
          null, /* collections */
          null, /* siblings */
          null, /* parents */
          parents);
    }

    @Test
    void addsCurrentAsChildOfEachNewValueParent() {
      // Adding parent 42 means: add "current" (7) as a child of 42. The service inverts the
      // request and delegates to the existing child-collection add path, which reuses the
      // ContentCollectionEntity referencing 7 and inserts a join row into parent 42.
      ContentCollectionEntity currentAsContent =
          ContentCollectionEntity.builder().id(900L).referencedCollection(current).build();
      when(collectionRepository.findById(current.getId())).thenReturn(Optional.of(current));
      when(collectionRepository.findById(42L)).thenReturn(Optional.of(targetParent));
      when(collectionRepository.findAllReferencedCollectionsByParentId(7L)).thenReturn(List.of());
      when(contentRepository.findCollectionContentByReferencedCollectionId(7L))
          .thenReturn(Optional.of(currentAsContent));
      when(collectionRepository.findContentByCollectionIdAndContentId(42L, 900L))
          .thenReturn(Optional.empty());
      when(collectionRepository.countContentByCollectionId(42L)).thenReturn(3L);

      service.updateContent(
          current.getId(),
          updateWithParents(
              new CollectionRequests.CollectionUpdate(
                  null,
                  List.of(new Records.ChildCollection(42L, null, null, null, null, null)),
                  null)));

      ArgumentCaptor<CollectionContentEntity> captor =
          ArgumentCaptor.forClass(CollectionContentEntity.class);
      verify(collectionRepository).saveContent(captor.capture());
      assertThat(captor.getValue().getCollectionId()).isEqualTo(42L);
      assertThat(captor.getValue().getContentId()).isEqualTo(900L);

      ArgumentCaptor<CollectionEntity> savedCaptor =
          ArgumentCaptor.forClass(CollectionEntity.class);
      verify(collectionRepository, times(2)).save(savedCaptor.capture());
      assertThat(savedCaptor.getAllValues())
          .anySatisfy(
              saved -> {
                assertThat(saved.getId()).isEqualTo(42L);
                assertThat(saved.getTotalContent()).isEqualTo(3);
              });
    }

    @Test
    void removesCurrentFromEachRemoveIdParentChildren() {
      // Removing parent 55 means: drop "current" (7) from parent 55's children. The service
      // delegates to the existing child-removal path, which finds the ContentCollectionEntity
      // referencing 7 inside parent 55 and unlinks it.
      CollectionEntity existingParent =
          CollectionEntity.builder().id(55L).title("Existing").build();
      ContentCollectionEntity currentAsContent =
          ContentCollectionEntity.builder().id(900L).referencedCollection(current).build();
      CollectionContentEntity joinRow =
          CollectionContentEntity.builder()
              .id(800L)
              .collectionId(55L)
              .contentId(900L)
              .visible(true)
              .build();
      when(collectionRepository.findById(current.getId())).thenReturn(Optional.of(current));
      when(collectionRepository.findById(55L)).thenReturn(Optional.of(existingParent));
      when(collectionRepository.findContentByCollectionIdOrderByOrderIndex(55L))
          .thenReturn(List.of(joinRow));
      when(contentRepository.findCollectionContentById(900L))
          .thenReturn(Optional.of(currentAsContent));
      when(collectionRepository.countContentByCollectionId(55L)).thenReturn(2L);

      service.updateContent(
          current.getId(),
          updateWithParents(new CollectionRequests.CollectionUpdate(null, null, List.of(55L))));

      verify(collectionRepository).removeContentFromCollection(55L, List.of(900L));

      ArgumentCaptor<CollectionEntity> savedCaptor =
          ArgumentCaptor.forClass(CollectionEntity.class);
      verify(collectionRepository, times(2)).save(savedCaptor.capture());
      assertThat(savedCaptor.getAllValues())
          .anySatisfy(
              saved -> {
                assertThat(saved.getId()).isEqualTo(55L);
                assertThat(saved.getTotalContent()).isEqualTo(2);
              });
    }

    @Test
    void rejectsSelfParent() {
      when(collectionRepository.findById(current.getId())).thenReturn(Optional.of(current));
      when(collectionRepository.findAllReferencedCollectionsByParentId(7L)).thenReturn(List.of());
      CollectionRequests.CollectionUpdate parents =
          new CollectionRequests.CollectionUpdate(
              null,
              List.of(new Records.ChildCollection(current.getId(), null, null, null, null, null)),
              null);

      assertThatThrownBy(() -> service.updateContent(current.getId(), updateWithParents(parents)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("its own parent");
    }

    @Test
    void rejects2Cycle_whenNewValueIdIsAlreadyAChild() {
      CollectionEntity existingChild =
          CollectionEntity.builder().id(99L).title("Existing Child").build();
      when(collectionRepository.findById(current.getId())).thenReturn(Optional.of(current));
      when(collectionRepository.findAllReferencedCollectionsByParentId(7L))
          .thenReturn(List.of(existingChild));
      CollectionRequests.CollectionUpdate parents =
          new CollectionRequests.CollectionUpdate(
              null, List.of(new Records.ChildCollection(99L, null, null, null, null, null)), null);

      assertThatThrownBy(() -> service.updateContent(current.getId(), updateWithParents(parents)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Cycle detected");
    }
  }
}
