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

import edens.zac.portfolio.backend.dao.CollectionContentDao;
import edens.zac.portfolio.backend.dao.CollectionDao;
import edens.zac.portfolio.backend.dao.ContentCollectionDao;
import edens.zac.portfolio.backend.dao.ContentDao;
import edens.zac.portfolio.backend.dao.ContentTextDao;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.CollectionReorderRequest;
import edens.zac.portfolio.backend.types.CollectionType;
import java.time.LocalDateTime;
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
class CollectionServiceImplTest {

    @Mock
    private CollectionDao collectionDao;
    @Mock
    private CollectionContentDao collectionContentDao;
    @Mock
    private ContentDao contentDao;
    @Mock
    private ContentCollectionDao contentCollectionDao;
    @Mock
    private ContentTextDao contentTextDao;
    @Mock
    private CollectionProcessingUtil collectionProcessingUtil;
    @Mock
    private ContentProcessingUtil contentProcessingUtil;
    @Mock
    private ContentService contentService;

    @InjectMocks
    private CollectionServiceImpl service;

    @Captor
    private ArgumentCaptor<Map<Long, Integer>> mapCaptor;

    @Nested
    class ReorderContent {

        private CollectionEntity collection;
        private List<CollectionContentEntity> existingContent;

        @BeforeEach
        void setUp() {
            collection = CollectionEntity.builder()
                    .id(1L)
                    .title("Test Collection")
                    .slug("test-collection")
                    .type(CollectionType.PORTFOLIO)
                    .visible(true)
                    .build();

            existingContent = List.of(
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
            // Arrange
            Long collectionId = 1L;
            CollectionReorderRequest request = new CollectionReorderRequest(
                    List.of(
                            new CollectionReorderRequest.ReorderItem(100L, 2),
                            new CollectionReorderRequest.ReorderItem(101L, 0),
                            new CollectionReorderRequest.ReorderItem(102L, 1)));

            when(collectionDao.findById(collectionId)).thenReturn(Optional.of(collection));
            when(collectionContentDao.findByCollectionIdOrderByOrderIndex(collectionId))
                    .thenReturn(existingContent);
            when(collectionContentDao.batchUpdateOrderIndexes(eq(collectionId), any())).thenReturn(3);

            CollectionModel expectedModel = CollectionModel.builder().id(1L).title("Test Collection").build();
            when(collectionProcessingUtil.convertToModel(
                    eq(collection), any(), anyInt(), anyInt(), anyLong()))
                    .thenReturn(expectedModel);

            // Act
            CollectionModel result = service.reorderContent(collectionId, request);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);

            verify(collectionContentDao).batchUpdateOrderIndexes(eq(collectionId), mapCaptor.capture());
            Map<Long, Integer> capturedMap = mapCaptor.getValue();
            assertThat(capturedMap)
                    .containsEntry(100L, 2)
                    .containsEntry(101L, 0)
                    .containsEntry(102L, 1)
                    .hasSize(3);
        }

        @Test
        void reorderContent_collectionNotFound_throwsException() {
            // Arrange
            Long collectionId = 999L;
            CollectionReorderRequest request = new CollectionReorderRequest(
                    List.of(new CollectionReorderRequest.ReorderItem(100L, 0)));

            when(collectionDao.findById(collectionId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> service.reorderContent(collectionId, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Collection not found with ID: 999");

            verify(collectionContentDao, never()).batchUpdateOrderIndexes(any(), any());
        }

        @Test
        void reorderContent_contentNotInCollection_throwsException() {
            // Arrange
            Long collectionId = 1L;
            CollectionReorderRequest request = new CollectionReorderRequest(
                    List.of(
                            new CollectionReorderRequest.ReorderItem(100L, 0),
                            new CollectionReorderRequest.ReorderItem(999L, 1)));

            when(collectionDao.findById(collectionId)).thenReturn(Optional.of(collection));
            when(collectionContentDao.findByCollectionIdOrderByOrderIndex(collectionId))
                    .thenReturn(existingContent);

            // Act & Assert
            assertThatThrownBy(() -> service.reorderContent(collectionId, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Content with ID 999 does not belong to collection 1");

            verify(collectionContentDao, never()).batchUpdateOrderIndexes(any(), any());
        }

        @Test
        void reorderContent_partialReorder_updatesOnlySpecifiedItems() {
            // Arrange
            Long collectionId = 1L;
            CollectionReorderRequest request = new CollectionReorderRequest(
                    List.of(new CollectionReorderRequest.ReorderItem(100L, 5)));

            when(collectionDao.findById(collectionId)).thenReturn(Optional.of(collection));
            when(collectionContentDao.findByCollectionIdOrderByOrderIndex(collectionId))
                    .thenReturn(existingContent);
            when(collectionContentDao.batchUpdateOrderIndexes(eq(collectionId), any())).thenReturn(1);

            CollectionModel expectedModel = CollectionModel.builder().id(1L).title("Test Collection").build();
            when(collectionProcessingUtil.convertToModel(
                    eq(collection), any(), anyInt(), anyInt(), anyLong()))
                    .thenReturn(expectedModel);

            // Act
            CollectionModel result = service.reorderContent(collectionId, request);

            // Assert
            assertThat(result).isNotNull();

            verify(collectionContentDao).batchUpdateOrderIndexes(eq(collectionId), mapCaptor.capture());
            Map<Long, Integer> capturedMap = mapCaptor.getValue();
            assertThat(capturedMap).containsEntry(100L, 5).hasSize(1);
        }
    }
}
