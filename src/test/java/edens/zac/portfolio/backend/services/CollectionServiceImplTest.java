package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.model.CollectionCreateRequest;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.CollectionUpdateDTO;
import edens.zac.portfolio.backend.model.CollectionUpdateResponseDTO;
import edens.zac.portfolio.backend.repository.ContentRepository;
import edens.zac.portfolio.backend.repository.CollectionRepository;
import edens.zac.portfolio.backend.types.CollectionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollectionServiceImplTest {

    @Mock private CollectionRepository collectionRepository;
    @Mock private ContentRepository contentRepository;
    @Mock private ContentProcessingUtil contentProcessingUtil;
    @Mock private CollectionProcessingUtil collectionProcessingUtil;
    @Mock private HomeService homeService;
    @Mock private ContentService contentService;

    @InjectMocks
    private CollectionServiceImpl service;

    @BeforeEach
    void setup() {
        // Nothing required; @InjectMocks wires constructor
    }

    @Test
    void createCollection_minimalRequest_persistsAndReturnsModel_noHomeCardInteraction() {
        // Arrange
        CollectionCreateRequest dto = new CollectionCreateRequest();
        dto.setType(CollectionType.BLOG);
        dto.setTitle("My Blog");

        CollectionEntity unsaved = new CollectionEntity();
        unsaved.setTitle("My Blog");
        unsaved.setSlug("my-blog");
        unsaved.setCreatedAt(LocalDateTime.now());

        CollectionEntity saved = new CollectionEntity();
        saved.setId(42L);
        saved.setTitle(unsaved.getTitle());
        saved.setSlug(unsaved.getSlug());
        saved.setPriority(unsaved.getPriority());
        saved.setCoverImageId(unsaved.getCoverImageId());
        saved.setCreatedAt(unsaved.getCreatedAt());

        CollectionModel mockModel = new CollectionModel();
        mockModel.setId(42L);
        mockModel.setTitle("My Blog");
        mockModel.setSlug("my-blog");

        when(collectionProcessingUtil.toEntity(eq(dto), anyInt())).thenReturn(unsaved);
        when(collectionRepository.save(unsaved)).thenReturn(saved);
        when(collectionRepository.findBySlug("my-blog")).thenReturn(java.util.Optional.of(saved));
        when(contentRepository.findByCollectionIdOrderByOrderIndex(42L)).thenReturn(Collections.emptyList());
        when(collectionProcessingUtil.convertToBasicModel(saved)).thenReturn(mockModel);

        // Mock dependencies for getUpdateCollectionData
        when(contentService.getAllTags()).thenReturn(Collections.emptyList());
        when(contentService.getAllPeople()).thenReturn(Collections.emptyList());
        when(contentService.getAllCameras()).thenReturn(Collections.emptyList());
        when(contentService.getAllLenses()).thenReturn(Collections.emptyList());
        when(contentService.getAllFilmTypes()).thenReturn(Collections.emptyList());
        when(collectionRepository.findAll()).thenReturn(Collections.emptyList());

        // Act
        CollectionUpdateResponseDTO result = service.createCollection(dto);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getCollection()).isNotNull();
        assertThat(result.getCollection().getId()).isEqualTo(42L);
        // No home card interactions on create
        verifyNoInteractions(homeService);
    }

    @Test
    void updateContent_homeCardEnabledFalse_callsUpsertDisabled() {
        // Arrange
        long id = 100L;
        CollectionUpdateDTO updateDTO = CollectionUpdateDTO.builder()
                .homeCardEnabled(false)
                .priority(3)
                .homeCardText("irrelevant when disabled")
                .build();

        CollectionEntity existing = new CollectionEntity();
        existing.setId(id);
        existing.setTitle("Existing");
        existing.setSlug("existing");
        existing.setPriority(2);
        existing.setCreatedAt(LocalDateTime.now());

        when(collectionRepository.findById(id)).thenReturn(Optional.of(existing));
        // Basic updates do nothing in this test
        doNothing().when(collectionProcessingUtil).applyBasicUpdates(existing, updateDTO);
        // No removals
        when(collectionProcessingUtil.handleNewTextContentReturnIds(eq(id), eq(updateDTO)))
                .thenReturn(Collections.emptyList());
        doNothing().when(collectionProcessingUtil)
                .handleContentReordering(eq(id), eq(updateDTO), anyList());
        when(collectionRepository.save(existing)).thenReturn(existing);
        when(contentRepository.countByCollectionId(id)).thenReturn(0L);
        when(collectionProcessingUtil.convertToBasicModel(existing)).thenReturn(new CollectionModel());

        // Act
        CollectionModel result = service.updateContent(id, updateDTO);

        // Assert
        assertThat(result).isNotNull();
        verify(homeService).upsertHomeCardForCollection(
                eq(existing), eq(false), eq(3), eq("irrelevant when disabled")
        );
        verify(homeService, never()).syncHomeCardOnCollectionUpdate(any());
    }
}
