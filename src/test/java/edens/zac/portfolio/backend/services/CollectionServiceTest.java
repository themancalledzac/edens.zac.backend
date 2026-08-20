package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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
import edens.zac.portfolio.backend.entity.TagEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.LocationPageResponse;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.types.AccessLevel;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

  @Mock private CollectionRepository collectionRepository;

  @Mock
  private edens.zac.portfolio.backend.dao.CollectionPeopleRepository collectionPeopleRepository;

  @Mock private ContentRepository contentRepository;
  @Mock private LocationRepository locationRepository;
  @Mock private TagRepository tagRepository;
  @Mock private CollectionProcessingUtil collectionProcessingUtil;
  @Mock private ContentMutationUtil contentMutationUtil;
  @Mock private ContentModelConverter contentModelConverter;
  @Mock private MetadataService metadataService;
  @Mock private edens.zac.portfolio.backend.services.EmailService emailService;
  @Mock private SyntheticCollectionResolver syntheticResolver;
  @Mock private TagViewResolver tagViewResolver;
  @Mock private ClientGalleryAuthService clientGalleryAuthService;
  @Mock private CollectionAccessService collectionAccessService;
  @Mock private RoleGrantPropagationService roleGrantPropagationService;

  @Mock
  private edens.zac.portfolio.backend.dao.CollectionSiblingRepository collectionSiblingRepository;

  @Mock private org.springframework.core.env.Environment springEnv;
  @Mock private CacheManager cacheManager;
  @Mock private ReadCacheInvalidator readCacheInvalidator;
  @Mock private ObjectProvider<CollectionService> selfProvider;

  @InjectMocks private CollectionService service;

  @Captor private ArgumentCaptor<Map<Long, Integer>> mapCaptor;
  @Captor private ArgumentCaptor<List<Long>> tagIdsCaptor;

  private CollectionEntity testCollection;

  private void stubEmptyMetadata() {
    when(metadataService.getAllTags()).thenReturn(List.of());
    when(metadataService.getAllPeople()).thenReturn(List.of());
    when(metadataService.getAllLocations()).thenReturn(List.of());
    when(metadataService.getAllCameras()).thenReturn(List.of());
    when(metadataService.getAllLenses()).thenReturn(List.of());
    when(metadataService.getAllFilmTypes()).thenReturn(List.of());
    when(collectionRepository.findCollectionListEntries()).thenReturn(List.of());
  }

  @BeforeEach
  void setUp() {
    testCollection =
        CollectionEntity.builder()
            .id(1L)
            .title("Test Collection")
            .slug("test-collection")
            .visibility(CollectionVisibility.LISTED)
            .build();

    // Route the metadata proxy call back to the service under test. Lenient because not every
    // test exercises the getGeneralMetadata path.
    lenient().when(selfProvider.getObject()).thenReturn(service);
  }

  @Nested
  class CreateCollection {

    @Test
    void createCollection_happyPath_savesAndReturnsUpdateResponse() {
      CollectionRequests.Create request = new CollectionRequests.Create("New Collection");

      CollectionEntity savedEntity =
          CollectionEntity.builder()
              .id(10L)
              .title("New Collection")
              .slug("new-collection")
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
      CollectionRequests.Create request = new CollectionRequests.Create("My Blog");

      CollectionEntity entity =
          CollectionEntity.builder()
              .id(5L)
              .title("My Blog")
              .slug("my-blog")
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
    void deleteCollection_happyPath_disassociatesAllRelationshipsThenDeletes() {
      Long collectionId = 1L;
      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));

      service.deleteCollection(collectionId);

      verify(contentRepository).deleteContentCollectionsReferencing(collectionId);
      verify(collectionRepository).deleteContentByCollectionId(collectionId);
      verify(tagRepository).deleteCollectionTags(collectionId);
      verify(collectionRepository).deleteById(collectionId);
    }

    @Test
    void deleteCollection_notFound_throwsResourceNotFoundException() {
      Long collectionId = 999L;
      when(collectionRepository.findById(collectionId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.deleteCollection(collectionId))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("Collection not found with ID: 999");

      verify(contentRepository, never()).deleteContentCollectionsReferencing(any());
      verify(collectionRepository, never()).deleteContentByCollectionId(any());
      verify(tagRepository, never()).deleteCollectionTags(any());
      verify(collectionRepository, never()).deleteById(any());
    }

    @Test
    void deleteCollection_removesBackReferencesAndTagsBeforeDeletingCollection() {
      Long collectionId = 1L;
      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));

      service.deleteCollection(collectionId);

      // Back-references, own content, and tags must all be cleared before the collection row.
      var inOrder =
          org.mockito.Mockito.inOrder(collectionRepository, contentRepository, tagRepository);
      inOrder.verify(contentRepository).deleteContentCollectionsReferencing(collectionId);
      inOrder.verify(collectionRepository).deleteContentByCollectionId(collectionId);
      inOrder.verify(tagRepository).deleteCollectionTags(collectionId);
      inOrder.verify(collectionRepository).deleteById(collectionId);
    }

    @Test
    void deleteCollection_withParentReferences_recountsEachParentTotalContent() {
      Long collectionId = 1L;
      CollectionEntity parentA = CollectionEntity.builder().id(10L).totalContent(99).build();
      CollectionEntity parentB = CollectionEntity.builder().id(11L).totalContent(99).build();
      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(collectionRepository.findAllParentCollectionsByChildId(collectionId))
          .thenReturn(List.of(parentA, parentB));
      when(collectionRepository.countContentByCollectionId(10L)).thenReturn(3L);
      when(collectionRepository.countContentByCollectionId(11L)).thenReturn(5L);

      service.deleteCollection(collectionId);

      // Back-references removed, then each parent's stored count recomputed and persisted.
      verify(contentRepository).deleteContentCollectionsReferencing(collectionId);
      assertThat(parentA.getTotalContent()).isEqualTo(3);
      assertThat(parentB.getTotalContent()).isEqualTo(5);
      verify(collectionRepository).save(parentA);
      verify(collectionRepository).save(parentB);
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

    @Test
    void updateContentWithMetadata_identityUnchanged_doesNotEvictMetadataCache() {
      Long collectionId = 1L;
      // Description-only update: title and slug are null, so identity does not change and the
      // shared generalMetadata cache must be left intact (this is the hot-path optimization).
      CollectionRequests.Update updateDTO =
          new CollectionRequests.Update(
              collectionId,
              null,
              null,
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

      CollectionModel model =
          CollectionModel.builder().id(1L).title("Test Collection").slug("test-collection").build();

      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(collectionRepository.countContentByCollectionId(collectionId)).thenReturn(0L);
      when(collectionRepository.save(any(CollectionEntity.class))).thenReturn(testCollection);
      when(collectionProcessingUtil.convertToBasicModel(any(CollectionEntity.class)))
          .thenReturn(model);
      when(collectionProcessingUtil.convertToFullModel(any(CollectionEntity.class)))
          .thenReturn(model);
      when(collectionRepository.findBySlug("test-collection"))
          .thenReturn(Optional.of(testCollection));
      stubEmptyMetadata();

      service.updateContentWithMetadata(collectionId, updateDTO);

      verify(cacheManager, never()).getCache(anyString());
    }

    @Test
    void updateContentWithMetadata_titleOrSlugChanged_evictsMetadataCache() {
      Long collectionId = 1L;
      CollectionRequests.Update updateDTO =
          new CollectionRequests.Update(
              collectionId,
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

      CollectionModel model =
          CollectionModel.builder().id(1L).title("New Title").slug("new-slug").build();
      Cache metadataCache = mock(Cache.class);

      // applyBasicUpdates is mocked, so make it actually mutate identity to simulate a rename.
      doAnswer(
              invocation -> {
                testCollection.setTitle("New Title");
                testCollection.setSlug("new-slug");
                return null;
              })
          .when(collectionProcessingUtil)
          .applyBasicUpdates(eq(testCollection), any(CollectionRequests.Update.class));

      when(cacheManager.getCache("generalMetadata")).thenReturn(metadataCache);
      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(collectionRepository.countContentByCollectionId(collectionId)).thenReturn(0L);
      when(collectionRepository.save(any(CollectionEntity.class))).thenReturn(testCollection);
      when(collectionProcessingUtil.convertToBasicModel(any(CollectionEntity.class)))
          .thenReturn(model);
      when(collectionProcessingUtil.convertToFullModel(any(CollectionEntity.class)))
          .thenReturn(model);
      when(collectionRepository.findBySlug("new-slug")).thenReturn(Optional.of(testCollection));
      stubEmptyMetadata();

      service.updateContentWithMetadata(collectionId, updateDTO);

      verify(metadataCache).clear();
      // A rename is what the cached collection list renders, so the CDN copy must be dropped too.
      // This path carries no @CacheEvict, so it is the one a annotation-driven scheme would miss.
      verify(readCacheInvalidator).markChanged();
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
      when(tagViewResolver.resolveTagView(eq(slug), anyBoolean())).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.getCollectionWithPagination(slug, 0, 10))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("Collection not found with slug: nonexistent");
    }

    @Test
    void getCollectionWithPagination_realCollectionWinsOnSlugCollision() {
      // A slug that is BOTH a real collection and a plausible tag must resolve to the real
      // collection — findBySlug is checked before the tag-view fallback, which is never consulted.
      String slug = "chamonix";
      CollectionModel model = CollectionModel.builder().id(1L).title("Chamonix").slug(slug).build();

      when(collectionRepository.findBySlug(slug)).thenReturn(Optional.of(testCollection));
      when(collectionRepository.countContentByCollectionId(1L)).thenReturn(0L);
      when(collectionRepository.findContentByCollectionId(eq(1L), anyInt(), anyInt()))
          .thenReturn(Collections.emptyList());
      when(collectionProcessingUtil.convertToModel(
              eq(testCollection), any(), anyInt(), anyInt(), anyLong()))
          .thenReturn(model);

      CollectionModel result = service.getCollectionWithPagination(slug, 0, 10);

      assertThat(result).isSameAs(model);
      verify(tagViewResolver, never()).resolveTagView(anyString(), anyBoolean());
    }

    @Test
    void getCollectionWithPagination_tagFallbackHit_returnsTagView() {
      String slug = "travel";
      CollectionModel tagView = CollectionModel.builder().slug(slug).build();

      when(collectionRepository.findBySlug(slug)).thenReturn(Optional.empty());
      when(tagViewResolver.resolveTagView(eq(slug), anyBoolean())).thenReturn(Optional.of(tagView));

      CollectionModel result = service.getCollectionWithPagination(slug, 0, 10);

      assertThat(result).isSameAs(tagView);
    }

    @Test
    void getCollectionWithPagination_zeroMemberTag_throwsNotFound() {
      String slug = "orphan";
      when(collectionRepository.findBySlug(slug)).thenReturn(Optional.empty());
      when(tagViewResolver.resolveTagView(eq(slug), anyBoolean())).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.getCollectionWithPagination(slug, 0, 10))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("Collection not found with slug: orphan");
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
      CollectionModel synthetic = CollectionModel.builder().slug("all-collections").build();
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
          CollectionModel.builder().id(1L).title("Test Collection").slug(slug).build();

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
  class ParentCollections {

    @Test
    void getUpdateCollectionData_derivedParent_aggregatesChildCollectionImages() {
      String slug = "photography";
      CollectionEntity parentEntity =
          CollectionEntity.builder()
              .id(10L)
              .title("Photography")
              .slug(slug)
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
              false,
              false,
              null,
              null,
              null,
              List.of(),
              edens.zac.portfolio.backend.types.CollectionVisibility.LISTED);

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
              .visibility(CollectionVisibility.LISTED)
              .build();
      CollectionModel model = CollectionModel.builder().id(7L).slug(slug).title("Child").build();
      CollectionEntity parent =
          CollectionEntity.builder()
              .id(42L)
              .title("Parent")
              .slug("parent")
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
              .visibility(CollectionVisibility.LISTED)
              .build();

      ContentModels.Collection unlistedGallery = childCollectionContent(101L);
      ContentModels.Collection hiddenGallery = childCollectionContent(102L);
      ContentModels.Collection listedGallery = childCollectionContent(103L);

      CollectionModel model =
          CollectionModel.builder()
              .id(50L)
              .slug(slug)
              .content(
                  new java.util.ArrayList<>(List.of(unlistedGallery, hiddenGallery, listedGallery)))
              .build();

      when(collectionRepository.findBySlug(slug)).thenReturn(Optional.of(parent));
      when(collectionRepository.countContentByCollectionId(50L)).thenReturn(0L);
      when(collectionRepository.findContentByCollectionId(eq(50L), anyInt(), anyInt()))
          .thenReturn(List.of());
      when(collectionProcessingUtil.convertToModel(
              eq(parent), any(), anyInt(), anyInt(), anyLong()))
          .thenReturn(model);
      when(collectionRepository.findByIds(List.of(101L, 102L, 103L)))
          .thenReturn(
              List.of(
                  childEntity(101L, true, CollectionVisibility.UNLISTED),
                  childEntity(102L, true, CollectionVisibility.HIDDEN),
                  childEntity(103L, true, CollectionVisibility.LISTED)));

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
              .visibility(CollectionVisibility.LISTED)
              .build();

      ContentModels.Collection listed = childCollectionContent(201L);
      ContentModels.Collection unlisted = childCollectionContent(202L);

      CollectionModel model =
          CollectionModel.builder()
              .id(60L)
              .slug(slug)
              .content(new java.util.ArrayList<>(List.of(listed, unlisted)))
              .build();

      when(collectionRepository.findBySlug(slug)).thenReturn(Optional.of(parent));
      when(collectionRepository.countContentByCollectionId(60L)).thenReturn(0L);
      when(collectionRepository.findContentByCollectionId(eq(60L), anyInt(), anyInt()))
          .thenReturn(List.of());
      when(collectionProcessingUtil.convertToModel(
              eq(parent), any(), anyInt(), anyInt(), anyLong()))
          .thenReturn(model);
      when(collectionRepository.findByIds(List.of(201L, 202L)))
          .thenReturn(
              List.of(
                  childEntity(201L, false, CollectionVisibility.LISTED),
                  childEntity(202L, false, CollectionVisibility.UNLISTED)));

      CollectionModel result = service.getCollectionWithPagination(slug, 0, 10);

      assertThat(result.getContent())
          .extracting(c -> ((ContentModels.Collection) c).referencedCollectionId())
          .containsExactly(201L); // LISTED kept, UNLISTED dropped (current behavior preserved)
    }

    @Test
    void parentOfMixedClientGalleryAndPortfolio_dropsUnlistedNonClientSibling() {
      // INVERTED PIN (was appliesClientGalleryContextToAllChildren). One client-gallery child used
      // to flip the whole response into "keep UNLISTED children" mode, so linking a single gallery
      // under a wrapper un-hid every unrelated work-in-progress sibling. The relaxation is now per
      // child: an UNLISTED child survives only if it is itself a client gallery.
      String slug = "smith-wedding";
      CollectionEntity parent =
          CollectionEntity.builder()
              .id(70L)
              .slug(slug)
              .visibility(CollectionVisibility.LISTED)
              .build();

      ContentModels.Collection unlistedGallery = childCollectionContent(301L);
      ContentModels.Collection unlistedPortfolio = childCollectionContent(302L);
      ContentModels.Collection listedPortfolio = childCollectionContent(303L);
      ContentModels.Collection hiddenPortfolio = childCollectionContent(304L);

      CollectionModel model =
          CollectionModel.builder()
              .id(70L)
              .slug(slug)
              .content(
                  new java.util.ArrayList<>(
                      List.of(
                          unlistedGallery, unlistedPortfolio, listedPortfolio, hiddenPortfolio)))
              .build();

      when(collectionRepository.findBySlug(slug)).thenReturn(Optional.of(parent));
      when(collectionRepository.countContentByCollectionId(70L)).thenReturn(0L);
      when(collectionRepository.findContentByCollectionId(eq(70L), anyInt(), anyInt()))
          .thenReturn(List.of());
      when(collectionProcessingUtil.convertToModel(
              eq(parent), any(), anyInt(), anyInt(), anyLong()))
          .thenReturn(model);
      when(collectionRepository.findByIds(List.of(301L, 302L, 303L, 304L)))
          .thenReturn(
              List.of(
                  childEntity(301L, true, CollectionVisibility.UNLISTED),
                  childEntity(302L, false, CollectionVisibility.UNLISTED),
                  childEntity(303L, false, CollectionVisibility.LISTED),
                  childEntity(304L, false, CollectionVisibility.HIDDEN)));

      CollectionModel result = service.getCollectionWithPagination(slug, 0, 10);

      assertThat(result.getContent())
          .extracting(c -> ((ContentModels.Collection) c).referencedCollectionId())
          .containsExactly(301L, 303L); // client gallery + LISTED sibling; UNLISTED and HIDDEN gone
    }

    @Test
    void nonParentWrapperOfClientGalleries_keepsUnlistedChildren() {
      // Regression pin: findClientGalleriesAndQualifyingParents admits ANY collection with a
      // visible client child, applying no discriminator to the parent side, so the render path
      // must key the client-gallery context on the child flags too. A parent-side gate stripped
      // every UNLISTED child out of an ordinary wrapper carrying neither flag (e.g. the
      // auto-linked 'staging' collection) and rendered an empty tile.
      String slug = "staging";
      CollectionEntity wrapper =
          CollectionEntity.builder()
              .id(80L)
              .slug(slug)
              .visibility(CollectionVisibility.LISTED)
              .build();

      ContentModels.Collection unlistedGallery = childCollectionContent(401L);
      ContentModels.Collection hiddenGallery = childCollectionContent(402L);

      CollectionModel model =
          CollectionModel.builder()
              .id(80L)
              .slug(slug)
              .isClient(false)
              .content(new java.util.ArrayList<>(List.of(unlistedGallery, hiddenGallery)))
              .build();

      when(collectionRepository.findBySlug(slug)).thenReturn(Optional.of(wrapper));
      when(collectionProcessingUtil.convertToModel(
              eq(wrapper), any(), anyInt(), anyInt(), anyLong()))
          .thenReturn(model);
      when(collectionRepository.findByIds(List.of(401L, 402L)))
          .thenReturn(
              List.of(
                  childEntity(401L, true, CollectionVisibility.UNLISTED),
                  childEntity(402L, true, CollectionVisibility.HIDDEN)));

      CollectionModel result = service.getCollectionWithPagination(slug, 0, 10);

      assertThat(result.getContent())
          .extracting(c -> ((ContentModels.Collection) c).referencedCollectionId())
          .containsExactly(401L); // UNLISTED kept even though the wrapper is not a PARENT
    }

    @Test
    void unprotectedParent_dropsPasswordProtectedChild() {
      // S3: ContentModels.Collection carries title, description, coverImage and dates but no
      // isPasswordProtected, so the frontend cannot render a locked tile. An unprotected,
      // crawlable, hour-cached parent page would publish the gallery's cover and client name.
      String slug = "public-wrapper";
      CollectionEntity parent =
          CollectionEntity.builder()
              .id(90L)
              .slug(slug)
              .visibility(CollectionVisibility.LISTED)
              .build();

      ContentModels.Collection lockedChild = childCollectionContent(501L);
      ContentModels.Collection openChild = childCollectionContent(502L);

      CollectionModel model =
          CollectionModel.builder()
              .id(90L)
              .slug(slug)
              .isPasswordProtected(false)
              .content(new java.util.ArrayList<>(List.of(lockedChild, openChild)))
              .build();

      when(collectionRepository.findBySlug(slug)).thenReturn(Optional.of(parent));
      when(collectionProcessingUtil.convertToModel(
              eq(parent), any(), anyInt(), anyInt(), anyLong()))
          .thenReturn(model);
      when(collectionRepository.findByIds(List.of(501L, 502L)))
          .thenReturn(
              List.of(
                  childEntity(501L, true, CollectionVisibility.UNLISTED, "sunshine"),
                  childEntity(502L, true, CollectionVisibility.UNLISTED, null)));

      CollectionModel result = service.getCollectionWithPagination(slug, 0, 10);

      assertThat(result.getContent())
          .extracting(c -> ((ContentModels.Collection) c).referencedCollectionId())
          .containsExactly(502L);
    }

    @Test
    void protectedParent_keepsPasswordProtectedChild() {
      // The viewer is already behind the parent's own gate, so its protected children stay.
      String slug = "locked-wrapper";
      CollectionEntity parent =
          CollectionEntity.builder()
              .id(91L)
              .slug(slug)
              .visibility(CollectionVisibility.LISTED)
              .build();

      ContentModels.Collection lockedChild = childCollectionContent(511L);
      ContentModels.Collection openChild = childCollectionContent(512L);

      CollectionModel model =
          CollectionModel.builder()
              .id(91L)
              .slug(slug)
              .isPasswordProtected(true)
              .content(new java.util.ArrayList<>(List.of(lockedChild, openChild)))
              .build();

      when(collectionRepository.findBySlug(slug)).thenReturn(Optional.of(parent));
      when(collectionProcessingUtil.convertToModel(
              eq(parent), any(), anyInt(), anyInt(), anyLong()))
          .thenReturn(model);
      when(collectionRepository.findByIds(List.of(511L, 512L)))
          .thenReturn(
              List.of(
                  childEntity(511L, true, CollectionVisibility.UNLISTED, "sunshine"),
                  childEntity(512L, true, CollectionVisibility.UNLISTED, null)));

      CollectionModel result = service.getCollectionWithPagination(slug, 0, 10);

      assertThat(result.getContent())
          .extracting(c -> ((ContentModels.Collection) c).referencedCollectionId())
          .containsExactly(511L, 512L);
    }

    @Test
    void clientGalleryWrapper_dropsUnlistedNonClientChild() {
      // S4 from the other direction: the parent is itself a client gallery, which used to be
      // enough to keep every UNLISTED child. Only the client-gallery child survives now.
      String slug = "gallery-wrapper";
      CollectionEntity parent =
          CollectionEntity.builder()
              .id(92L)
              .slug(slug)
              .visibility(CollectionVisibility.LISTED)
              .build();

      ContentModels.Collection unlistedPortfolio = childCollectionContent(521L);
      ContentModels.Collection unlistedGallery = childCollectionContent(522L);

      CollectionModel model =
          CollectionModel.builder()
              .id(92L)
              .slug(slug)
              .isClient(true)
              .content(new java.util.ArrayList<>(List.of(unlistedPortfolio, unlistedGallery)))
              .build();

      when(collectionRepository.findBySlug(slug)).thenReturn(Optional.of(parent));
      when(collectionProcessingUtil.convertToModel(
              eq(parent), any(), anyInt(), anyInt(), anyLong()))
          .thenReturn(model);
      when(collectionRepository.findByIds(List.of(521L, 522L)))
          .thenReturn(
              List.of(
                  childEntity(521L, false, CollectionVisibility.UNLISTED),
                  childEntity(522L, true, CollectionVisibility.UNLISTED)));

      CollectionModel result = service.getCollectionWithPagination(slug, 0, 10);

      assertThat(result.getContent())
          .extracting(c -> ((ContentModels.Collection) c).referencedCollectionId())
          .containsExactly(522L);
    }

    private ContentModels.Collection childCollectionContent(Long childId) {
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
          false,
          false,
          null,
          null,
          null,
          List.of(),
          edens.zac.portfolio.backend.types.CollectionVisibility.LISTED);
    }

    private CollectionEntity childEntity(
        Long id, boolean isClient, CollectionVisibility visibility) {
      return childEntity(id, isClient, visibility, null);
    }

    private CollectionEntity childEntity(
        Long id, boolean isClient, CollectionVisibility visibility, String galleryPassword) {
      // is_client is the storage truth for client-gallery status.
      return CollectionEntity.builder()
          .id(id)
          .isClient(isClient)
          .visibility(visibility)
          .galleryPassword(galleryPassword)
          .build();
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
      CollectionEntity home = CollectionEntity.builder().id(1L).slug("home").build();
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

    @AfterEach
    void clearSecurityContext() {
      SecurityContextHolder.clearContext();
    }

    private void setPrincipal(AuthPrincipal principal) {
      var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
      SecurityContext context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(auth);
      SecurityContextHolder.setContext(context);
    }

    @Test
    void enforceVisibilityHIDDENBlocksProd() {
      CollectionEntity entity =
          CollectionEntity.builder()
              .id(1L)
              .slug("secret")
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
    void enforceVisibilityHIDDENPassesInProdForAdmin() {
      AuthPrincipal admin = new AuthPrincipal(1L, "admin@ezac.com", true, true);
      setPrincipal(admin);
      CollectionEntity entity =
          CollectionEntity.builder()
              .id(1L)
              .slug("secret")
              .visibility(CollectionVisibility.HIDDEN)
              .build();
      when(collectionRepository.findBySlug("secret")).thenReturn(Optional.of(entity));
      when(springEnv.acceptsProfiles(any(org.springframework.core.env.Profiles.class)))
          .thenReturn(false);
      when(collectionAccessService.hasAtLeast(eq(admin), eq(1L), eq(AccessLevel.GENERAL)))
          .thenReturn(true);
      when(collectionProcessingUtil.convertToBasicModel(entity))
          .thenReturn(CollectionModel.builder().id(1L).slug("secret").build());

      assertThat(service.findMetaBySlug("secret")).isNotNull();
    }

    @Test
    void enforceVisibilityHIDDENPassesInProdForGrantedUser() {
      AuthPrincipal client = AuthPrincipal.client(42L, "client@ezac.com", true);
      setPrincipal(client);
      CollectionEntity entity =
          CollectionEntity.builder()
              .id(9L)
              .slug("their-gallery")
              .visibility(CollectionVisibility.HIDDEN)
              .build();
      when(collectionRepository.findBySlug("their-gallery")).thenReturn(Optional.of(entity));
      when(springEnv.acceptsProfiles(any(org.springframework.core.env.Profiles.class)))
          .thenReturn(false);
      when(collectionAccessService.hasAtLeast(eq(client), eq(9L), eq(AccessLevel.GENERAL)))
          .thenReturn(true);
      when(collectionProcessingUtil.convertToBasicModel(entity))
          .thenReturn(CollectionModel.builder().id(9L).slug("their-gallery").build());

      assertThat(service.findMetaBySlug("their-gallery")).isNotNull();
    }

    @Test
    void enforceVisibilityHIDDENStillBlocksProdForSignedInStranger() {
      AuthPrincipal stranger = AuthPrincipal.client(43L, "stranger@ezac.com", true);
      setPrincipal(stranger);
      CollectionEntity entity =
          CollectionEntity.builder()
              .id(9L)
              .slug("their-gallery")
              .visibility(CollectionVisibility.HIDDEN)
              .build();
      when(collectionRepository.findBySlug("their-gallery")).thenReturn(Optional.of(entity));
      when(springEnv.acceptsProfiles(any(org.springframework.core.env.Profiles.class)))
          .thenReturn(false);
      when(collectionAccessService.hasAtLeast(eq(stranger), eq(9L), eq(AccessLevel.GENERAL)))
          .thenReturn(false);

      assertThatThrownBy(() -> service.findMetaBySlug("their-gallery"))
          .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void enforceVisibilityUNLISTEDPassesInProd() {
      CollectionEntity entity =
          CollectionEntity.builder()
              .id(1L)
              .slug("private-gallery")
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
    void wrapperWithClientGalleryChildren_propagateTrue_updatesPasswordOnEachClientChild() {
      // The R1/S2 scenario: the wrapper is NOT itself a client gallery. Eligibility comes from
      // hasClientGalleryChildren; propagation is gated on the request flag alone.
      CollectionEntity parent =
          CollectionEntity.builder()
              .id(100L)
              .slug("company-a")
              .isClient(false)
              .visibility(CollectionVisibility.LISTED)
              .build();
      CollectionEntity child1 =
          CollectionEntity.builder().id(101L).slug("smith-wedding").isClient(true).build();
      CollectionEntity child2 =
          CollectionEntity.builder().id(102L).slug("jones-wedding").isClient(true).build();

      when(collectionRepository.findById(100L)).thenReturn(Optional.of(parent));
      when(collectionRepository.hasClientGalleryChildren(100L)).thenReturn(true);
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
    void wrapperWithClientGalleryChildren_propagateFalse_skipsChildPropagation() {
      CollectionEntity parent =
          CollectionEntity.builder()
              .id(100L)
              .slug("company-a")
              .isClient(false)
              .visibility(CollectionVisibility.LISTED)
              .build();
      when(collectionRepository.findById(100L)).thenReturn(Optional.of(parent));
      when(collectionRepository.hasClientGalleryChildren(100L)).thenReturn(true);

      CollectionRequests.GalleryAccessRequest request =
          new CollectionRequests.GalleryAccessRequest("secretpw", List.of(), false);

      service.updateGalleryAccess(100L, request);

      verify(collectionRepository, never()).findAllReferencedCollectionsByParentId(anyLong());
      verify(collectionRepository, never()).updateGalleryPassword(anyLong(), anyString());
    }

    @Test
    void wrapperWithClientGalleryChildren_propagateNull_skipsChildPropagation() {
      CollectionEntity parent =
          CollectionEntity.builder()
              .id(100L)
              .slug("company-a")
              .isClient(false)
              .visibility(CollectionVisibility.LISTED)
              .build();
      when(collectionRepository.findById(100L)).thenReturn(Optional.of(parent));
      when(collectionRepository.hasClientGalleryChildren(100L)).thenReturn(true);

      CollectionRequests.GalleryAccessRequest request =
          new CollectionRequests.GalleryAccessRequest("secretpw", List.of(), null);

      service.updateGalleryAccess(100L, request);

      verify(collectionRepository, never()).findAllReferencedCollectionsByParentId(anyLong());
      verify(collectionRepository, never()).updateGalleryPassword(anyLong(), anyString());
    }

    @Test
    void standaloneClientGallery_isEligibleWithoutQueryingChildren() {
      // isClient short-circuits the OR, so the EXISTS query is never issued for a plain gallery.
      CollectionEntity gallery =
          CollectionEntity.builder()
              .id(100L)
              .slug("smith-wedding")
              .isClient(true)
              .visibility(CollectionVisibility.UNLISTED)
              .build();
      when(collectionRepository.findById(100L)).thenReturn(Optional.of(gallery));

      CollectionRequests.GalleryAccessRequest request =
          new CollectionRequests.GalleryAccessRequest("secretpw", List.of(), false);

      service.updateGalleryAccess(100L, request);

      verify(collectionRepository).saveGalleryAccess(100L, "secretpw", List.of());
      verify(collectionRepository, never()).hasClientGalleryChildren(anyLong());
    }

    @Test
    void standaloneClientGallery_propagateTrue_writesNoChildPasswordsWhenItHasNoChildren() {
      // The parent-side type gate is gone, so propagation now runs for any eligible target. A
      // childless gallery simply finds nothing to write.
      CollectionEntity gallery =
          CollectionEntity.builder()
              .id(100L)
              .slug("smith-wedding")
              .isClient(true)
              .visibility(CollectionVisibility.UNLISTED)
              .build();
      when(collectionRepository.findById(100L)).thenReturn(Optional.of(gallery));
      when(collectionRepository.findAllReferencedCollectionsByParentId(100L)).thenReturn(List.of());

      CollectionRequests.GalleryAccessRequest request =
          new CollectionRequests.GalleryAccessRequest("secretpw", List.of(), true);

      service.updateGalleryAccess(100L, request);

      verify(collectionRepository, never()).updateGalleryPassword(anyLong(), anyString());
    }

    @Test
    void propagateTrue_skipsNonClientChildren() {
      CollectionEntity parent =
          CollectionEntity.builder()
              .id(100L)
              .slug("mixed-parent")
              .isClient(false)
              .visibility(CollectionVisibility.LISTED)
              .build();
      CollectionEntity clientChild =
          CollectionEntity.builder().id(101L).slug("smith-wedding").isClient(true).build();
      CollectionEntity portfolioChild =
          CollectionEntity.builder().id(102L).slug("studio-portfolio").isClient(false).build();
      CollectionEntity blogChild =
          CollectionEntity.builder().id(103L).slug("studio-blog").isBlog(true).build();

      when(collectionRepository.findById(100L)).thenReturn(Optional.of(parent));
      when(collectionRepository.hasClientGalleryChildren(100L)).thenReturn(true);
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
    void propagateTrue_propagatesToUnlistedChildren() {
      CollectionEntity parent =
          CollectionEntity.builder()
              .id(100L)
              .slug("company-a")
              .isClient(false)
              .visibility(CollectionVisibility.LISTED)
              .build();
      CollectionEntity unlistedChild =
          CollectionEntity.builder()
              .id(101L)
              .slug("private-wedding")
              .isClient(true)
              .visibility(CollectionVisibility.UNLISTED)
              .build();

      when(collectionRepository.findById(100L)).thenReturn(Optional.of(parent));
      when(collectionRepository.hasClientGalleryChildren(100L)).thenReturn(true);
      when(collectionRepository.findAllReferencedCollectionsByParentId(100L))
          .thenReturn(List.of(unlistedChild));

      CollectionRequests.GalleryAccessRequest request =
          new CollectionRequests.GalleryAccessRequest("secretpw", List.of(), true);

      service.updateGalleryAccess(100L, request);

      verify(collectionRepository).updateGalleryPassword(101L, "secretpw");
    }

    @Test
    void neitherClientNorParentOfClientGalleries_returnsNotEligible() {
      CollectionEntity plain =
          CollectionEntity.builder()
              .id(100L)
              .slug("just-a-portfolio")
              .isClient(false)
              .visibility(CollectionVisibility.LISTED)
              .build();
      when(collectionRepository.findById(100L)).thenReturn(Optional.of(plain));
      when(collectionRepository.hasClientGalleryChildren(100L)).thenReturn(false);

      CollectionRequests.GalleryAccessResponse response =
          service.updateGalleryAccess(
              100L, new CollectionRequests.GalleryAccessRequest("secretpw", List.of(), true));

      assertThat(response.saved()).isFalse();
      assertThat(response.reason()).isEqualTo("not-eligible-type");
      verify(collectionRepository, never()).saveGalleryAccess(anyLong(), anyString(), any());
      verify(collectionRepository, never()).updateGalleryPassword(anyLong(), anyString());
    }
  }

  @Nested
  class GalleryAccessClearPath {

    @Test
    void ineligibleCollectionWithAStrandedPassword_canStillBeCleared() {
      // D8. CollectionRepository.save omits gallery_password on UPDATE, so this endpoint is the
      // only writer that can clear one. If eligibility gated the clear path, a collection that
      // holds an enforced password but is neither a client gallery nor a parent of one -- the
      // gallery_password IS NOT NULL AND is_client = false population -- would be permanently
      // locked behind a password nothing could remove.
      CollectionEntity stranded =
          CollectionEntity.builder()
              .id(100L)
              .slug("orphaned-wrapper")
              .isClient(false)
              .galleryPassword("stale-pw")
              .visibility(CollectionVisibility.UNLISTED)
              .build();
      when(collectionRepository.findById(100L)).thenReturn(Optional.of(stranded));

      CollectionRequests.GalleryAccessResponse response =
          service.updateGalleryAccess(
              100L, new CollectionRequests.GalleryAccessRequest(null, List.of(), false));

      assertThat(response.saved()).isTrue();
      assertThat(response.reason()).isNull();
      verify(collectionRepository).saveGalleryAccess(100L, null, List.of());
      // The clear path returns before the gate, so the EXISTS query is never issued.
      verify(collectionRepository, never()).hasClientGalleryChildren(anyLong());
    }

    @Test
    void clearingAnEligibleClientGalleryStillWorks() {
      // Regression pin: moving the clear branch above the gate must not change the eligible case.
      CollectionEntity gallery =
          CollectionEntity.builder()
              .id(101L)
              .slug("smith-wedding")
              .isClient(true)
              .galleryPassword("secretpw")
              .visibility(CollectionVisibility.UNLISTED)
              .build();
      when(collectionRepository.findById(101L)).thenReturn(Optional.of(gallery));

      CollectionRequests.GalleryAccessResponse response =
          service.updateGalleryAccess(
              101L, new CollectionRequests.GalleryAccessRequest(null, List.of(), false));

      assertThat(response.saved()).isTrue();
      assertThat(response.password()).isNull();
      verify(collectionRepository).saveGalleryAccess(101L, null, List.of());
    }

    @Test
    void clearingNeverPropagatesToChildrenEvenWhenPropagateIsTrue() {
      // A clear is not a set. propagateToChildren must not waterfall a null.
      CollectionEntity wrapper =
          CollectionEntity.builder()
              .id(102L)
              .slug("company-a")
              .isClient(true)
              .galleryPassword("secretpw")
              .visibility(CollectionVisibility.LISTED)
              .build();
      when(collectionRepository.findById(102L)).thenReturn(Optional.of(wrapper));

      service.updateGalleryAccess(
          102L, new CollectionRequests.GalleryAccessRequest(null, List.of(), true));

      verify(collectionRepository, never()).findAllReferencedCollectionsByParentId(anyLong());
      verify(collectionRepository, never()).updateGalleryPassword(anyLong(), anyString());
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
      // 17 positional args (the back-compat overload): id first, siblings last, everything else
      // null
      return new CollectionRequests.Update(
          id, null, null, null, null, null, null, null, null, null, null, null, null, null,
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

      verify(collectionSiblingRepository).setSibling(parentId, 20L, true);
      verify(collectionSiblingRepository).setSibling(parentId, 21L, true);
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

      verify(collectionSiblingRepository, never())
          .setSibling(eq(parentId), eq(parentId), anyBoolean());
    }

    @Test
    void nullSiblings_isNoOp() {
      Long parentId = 1L;
      when(collectionRepository.findById(parentId)).thenReturn(Optional.of(testCollection));

      service.updateContent(parentId, updateWithSiblings(parentId, null));

      verify(collectionSiblingRepository, never()).setSibling(anyLong(), anyLong(), anyBoolean());
      verify(collectionSiblingRepository, never()).removeSibling(anyLong(), anyLong());
    }

    @Test
    void mutualFalse_writesOneWayLink() {
      Long parentId = 1L;
      CollectionRequests.CollectionUpdate siblings =
          new CollectionRequests.CollectionUpdate(
              null,
              List.of(new Records.ChildCollection(20L, null, null, null, null, null, false)),
              null);
      when(collectionRepository.findById(parentId)).thenReturn(Optional.of(testCollection));

      service.updateContent(parentId, updateWithSiblings(parentId, siblings));

      verify(collectionSiblingRepository).setSibling(parentId, 20L, false);
    }

    @Test
    void mutualNull_defaultsToMutual() {
      Long parentId = 1L;
      CollectionRequests.CollectionUpdate siblings =
          new CollectionRequests.CollectionUpdate(
              null, List.of(new Records.ChildCollection(21L, null, null, null, null, null)), null);
      when(collectionRepository.findById(parentId)).thenReturn(Optional.of(testCollection));

      service.updateContent(parentId, updateWithSiblings(parentId, siblings));

      verify(collectionSiblingRepository).setSibling(parentId, 21L, true);
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

    private CollectionRequests.Update updateWithParents(
        CollectionRequests.CollectionUpdate parents) {
      // Canonical 22-arg constructor: id + 20 nulls + parents (last).
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
          null,
          null,
          null,
          null,
          null,
          null,
          parents);
    }

    @Test
    void addsCurrentAsChildOfEachNewValueParent() {
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

  @Nested
  class MixedCollectionReadPath {

    @Test
    @DisplayName("a collection holding children reads through the paginated content query")
    void getCollectionWithPagination_wrapper_usesPaginatedQuery() {
      CollectionEntity wrapper =
          CollectionEntity.builder()
              .id(21L)
              .slug("wrapper")
              .title("Wrapper")
              .visibility(CollectionVisibility.LISTED)
              .contentPerPage(30)
              .build();
      CollectionModel model =
          CollectionModel.builder().id(21L).slug("wrapper").title("Wrapper").build();

      when(collectionRepository.findBySlug("wrapper")).thenReturn(Optional.of(wrapper));
      when(collectionRepository.countContentByCollectionId(21L)).thenReturn(3L);
      when(collectionRepository.findContentByCollectionId(eq(21L), anyInt(), anyInt()))
          .thenReturn(Collections.emptyList());
      when(collectionProcessingUtil.convertToModel(
              eq(wrapper), any(), anyInt(), anyInt(), anyLong()))
          .thenReturn(model);

      service.getCollectionWithPagination("wrapper", 0, 30);

      verify(collectionRepository).countContentByCollectionId(21L);
      verify(collectionRepository).findContentByCollectionId(eq(21L), anyInt(), anyInt());
      verify(collectionRepository, never())
          .findContentByCollectionIdAndContentType(anyLong(), anyString());
    }
  }

  @Nested
  class DerivedParentNessOnManagePayload {

    @Test
    @DisplayName("getUpdateCollectionData reports hasChildren and the full child id list")
    void getUpdateCollectionData_reportsDerivedChildren() {
      CollectionEntity entity =
          CollectionEntity.builder()
              .id(7L)
              .slug("wrapper")
              .title("Wrapper")
              .visibility(CollectionVisibility.LISTED)
              .build();
      CollectionModel model =
          CollectionModel.builder().id(7L).slug("wrapper").title("Wrapper").build();

      when(collectionRepository.findBySlug("wrapper")).thenReturn(Optional.of(entity));
      when(collectionProcessingUtil.convertToFullModel(entity)).thenReturn(model);
      stubEmptyMetadata();
      when(collectionRepository.findAllReferencedCollectionIdsByParentId(7L))
          .thenReturn(List.of(11L, 12L));

      CollectionRequests.UpdateResponse response = service.getUpdateCollectionData("wrapper");

      assertThat(response.hasChildren()).isTrue();
      assertThat(response.childCollectionIds()).containsExactly(11L, 12L);
    }

    @Test
    @DisplayName("getUpdateCollectionData reports hasChildren=false and an empty id list")
    void getUpdateCollectionData_noChildren() {
      CollectionEntity entity =
          CollectionEntity.builder()
              .id(8L)
              .slug("leaf")
              .title("Leaf")
              .visibility(CollectionVisibility.LISTED)
              .build();
      CollectionModel model = CollectionModel.builder().id(8L).slug("leaf").title("Leaf").build();

      when(collectionRepository.findBySlug("leaf")).thenReturn(Optional.of(entity));
      when(collectionProcessingUtil.convertToFullModel(entity)).thenReturn(model);
      stubEmptyMetadata();
      when(collectionRepository.findAllReferencedCollectionIdsByParentId(8L)).thenReturn(List.of());

      CollectionRequests.UpdateResponse response = service.getUpdateCollectionData("leaf");

      assertThat(response.hasChildren()).isFalse();
      assertThat(response.childCollectionIds()).isEmpty();
    }

    @Test
    @DisplayName("childCollectionImages is aggregated for any collection holding child blocks")
    void getUpdateCollectionData_aggregatesChildImagesWithoutTypeGate() {
      CollectionEntity entity =
          CollectionEntity.builder()
              .id(13L)
              .slug("mixed")
              .title("Mixed")
              .visibility(CollectionVisibility.LISTED)
              .build();

      ContentModels.Collection childBlock =
          new ContentModels.Collection(
              900L,
              edens.zac.portfolio.backend.types.ContentType.COLLECTION,
              "Child",
              null,
              null,
              0,
              true,
              null,
              null,
              51L,
              "child",
              false,
              false,
              null,
              null,
              null,
              List.of(),
              edens.zac.portfolio.backend.types.CollectionVisibility.LISTED);

      CollectionModel model =
          CollectionModel.builder()
              .id(13L)
              .slug("mixed")
              .title("Mixed")
              .content(List.of(childBlock))
              .build();

      when(collectionRepository.findBySlug("mixed")).thenReturn(Optional.of(entity));
      when(collectionProcessingUtil.convertToFullModel(entity)).thenReturn(model);
      stubEmptyMetadata();
      when(collectionRepository.findAllReferencedCollectionIdsByParentId(13L))
          .thenReturn(List.of(51L));
      when(collectionProcessingUtil.loadImagesFromChildCollections(List.of(51L)))
          .thenReturn(List.of());

      CollectionRequests.UpdateResponse response = service.getUpdateCollectionData("mixed");

      assertThat(response.childCollectionImages()).isNotNull();
      verify(collectionProcessingUtil).loadImagesFromChildCollections(List.of(51L));
    }
  }

  /**
   * Regression coverage for the tag-dedupe defect: editing a collection that already had tags threw
   * {@code DataIntegrityViolationException} on the {@code collection_tags} primary key, and because
   * {@code updateContent} is {@code @Transactional} the rollback discarded the whole save, not just
   * the tags.
   *
   * <p>These tests drive the REAL {@link ContentMutationUtil#updateTags} through the mocked
   * collaborator, because the defect only appears at that seam: the service supplied {@code
   * currentTags} whose members were missing {@code tagName}, and {@link
   * edens.zac.portfolio.backend.entity.TagEntity} keys equality on the name. A stubbed {@code
   * updateTags} would hide the bug entirely.
   */
  @Nested
  @DisplayName("updateCollectionTags")
  class UpdateCollectionTags {

    private CollectionRequests.Update updateWithTags(Long id, CollectionRequests.TagUpdate tags) {
      return new CollectionRequests.Update(
          id, null, null, null, null, null, null, null, null, null, null, null, null, tags, null,
          null, null);
    }

    /**
     * Route {@code contentMutationUtil.updateTags} to a real instance backed by the same mocked
     * repositories, so the prev/new/remove merge and the entity equality it depends on are the
     * production ones.
     */
    private void delegateUpdateTagsToRealUtil() {
      ContentMutationUtil real =
          new ContentMutationUtil(
              contentRepository,
              collectionRepository,
              tagRepository,
              mock(edens.zac.portfolio.backend.dao.PersonRepository.class),
              locationRepository);
      when(contentMutationUtil.updateTags(any(), any(), any()))
          .thenAnswer(
              invocation ->
                  real.updateTags(
                      invocation.getArgument(0),
                      invocation.getArgument(1),
                      invocation.getArgument(2)));
    }

    @Test
    @DisplayName("a retained tag is written exactly once, not duplicated")
    void updateContent_retainedTag_writesEachTagIdExactlyOnce() {
      Long collectionId = 1L;
      TagEntity landscape = TagEntity.builder().id(72L).tagName("landscape").build();

      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(tagRepository.findCollectionTags(collectionId)).thenReturn(List.of(landscape));
      when(tagRepository.findById(72L)).thenReturn(Optional.of(landscape));
      delegateUpdateTagsToRealUtil();

      service.updateContent(
          collectionId,
          updateWithTags(collectionId, new CollectionRequests.TagUpdate(List.of(72L), null, null)));

      verify(tagRepository).saveCollectionTags(eq(collectionId), tagIdsCaptor.capture());
      assertThat(tagIdsCaptor.getValue()).containsExactly(72L);
    }

    @Test
    @DisplayName("a retained tag alongside an added tag keeps both, each once")
    void updateContent_retainedTagPlusNewTag_writesBothExactlyOnce() {
      Long collectionId = 1L;
      TagEntity landscape = TagEntity.builder().id(72L).tagName("landscape").build();
      TagEntity portrait = TagEntity.builder().id(9L).tagName("portrait").build();

      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(tagRepository.findCollectionTags(collectionId)).thenReturn(List.of(landscape));
      when(tagRepository.findById(72L)).thenReturn(Optional.of(landscape));
      when(tagRepository.findByTagNameIgnoreCase("portrait")).thenReturn(Optional.of(portrait));
      delegateUpdateTagsToRealUtil();

      service.updateContent(
          collectionId,
          updateWithTags(
              collectionId,
              new CollectionRequests.TagUpdate(List.of(72L), List.of("portrait"), null)));

      verify(tagRepository).saveCollectionTags(eq(collectionId), tagIdsCaptor.capture());
      assertThat(tagIdsCaptor.getValue()).containsExactlyInAnyOrder(72L, 9L);
    }

    @Test
    @DisplayName("removing one of two attached tags leaves only the survivor")
    void updateContent_removesTag_writesOnlySurvivor() {
      Long collectionId = 1L;
      TagEntity landscape = TagEntity.builder().id(72L).tagName("landscape").build();
      TagEntity portrait = TagEntity.builder().id(9L).tagName("portrait").build();

      when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(testCollection));
      when(tagRepository.findCollectionTags(collectionId)).thenReturn(List.of(landscape, portrait));
      when(tagRepository.findById(72L)).thenReturn(Optional.of(landscape));
      delegateUpdateTagsToRealUtil();

      service.updateContent(
          collectionId,
          updateWithTags(
              collectionId, new CollectionRequests.TagUpdate(List.of(72L), null, List.of(9L))));

      verify(tagRepository).saveCollectionTags(eq(collectionId), tagIdsCaptor.capture());
      assertThat(tagIdsCaptor.getValue()).containsExactly(72L);
    }
  }
}
