package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.LocationPageResponse;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.types.CollectionType;
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
  @Mock private TagRepository tagRepository;
  @Mock private CollectionProcessingUtil collectionProcessingUtil;
  @Mock private ContentMutationUtil contentMutationUtil;
  @Mock private ContentModelConverter contentModelConverter;
  @Mock private MetadataService metadataService;

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
            .visible(true)
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
              .visible(true)
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
              .visible(true)
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

      CollectionEntity collectionEntity =
          CollectionEntity.builder()
              .id(10L)
              .title("Seattle Trip")
              .slug("seattle-trip")
              .type(CollectionType.PORTFOLIO)
              .visible(true)
              .build();

      CollectionModel collectionModel =
          CollectionModel.builder()
              .id(10L)
              .title("Seattle Trip")
              .slug("seattle-trip")
              .location(new Records.Location(1L, "Seattle", "seattle"))
              .build();

      ContentImageEntity orphanImage = ContentImageEntity.builder().id(20L).title("Sunset").build();

      ContentModels.Image imageModel =
          new ContentModels.Image(
              20L, null, "Sunset", null, null, null, null, null, null, null, null, null, null, null,
              null, null, null, null, null, null, null, null, null, null, null, null, null, null);

      when(collectionRepository.countVisibleByLocationName(locationName)).thenReturn(1L);
      when(collectionRepository.findVisibleByLocationName(locationName, 35, 0))
          .thenReturn(List.of(collectionEntity));
      // totalCollections (1) <= collectionSize (35), so IDs are extracted from paginated result
      // — no findVisibleIdsByLocationName call needed
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

      ContentImageEntity orphanImage = ContentImageEntity.builder().id(30L).title("Bridge").build();

      ContentModels.Image imageModel =
          new ContentModels.Image(
              30L, null, "Bridge", null, null, null, null, null, null, null, null, null, null, null,
              null, null, null, null, null, null, null, null, null, null, null, null, null, null);

      when(collectionRepository.countVisibleByLocationName(locationName)).thenReturn(0L);
      when(collectionRepository.findVisibleByLocationName(locationName, 35, 0))
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

      when(collectionRepository.countVisibleByLocationName(locationName)).thenReturn(0L);
      when(collectionRepository.findVisibleByLocationName(locationName, 35, 0))
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
}
