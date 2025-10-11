package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import edens.zac.portfolio.backend.model.ContentCollectionCreateRequest;
import edens.zac.portfolio.backend.model.ContentCollectionModel;
import edens.zac.portfolio.backend.model.ContentCollectionUpdateDTO;
import edens.zac.portfolio.backend.repository.ContentBlockRepository;
import edens.zac.portfolio.backend.repository.ContentCollectionRepository;
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
class ContentCollectionServiceImplTest {

    @Mock private ContentCollectionRepository contentCollectionRepository;
    @Mock private ContentBlockRepository contentBlockRepository;
    @Mock private ContentBlockProcessingUtil contentBlockProcessingUtil;
    @Mock private ContentCollectionProcessingUtil contentCollectionProcessingUtil;
    @Mock private HomeService homeService;

    @InjectMocks
    private ContentCollectionServiceImpl service;

    @BeforeEach
    void setup() {
        // Nothing required; @InjectMocks wires constructor
    }

    @Test
    void createCollection_minimalRequest_persistsAndReturnsModel_noHomeCardInteraction() {
        // Arrange
        ContentCollectionCreateRequest dto = new ContentCollectionCreateRequest();
        dto.setType(CollectionType.BLOG);
        dto.setTitle("My Blog");

        ContentCollectionEntity unsaved = new ContentCollectionEntity();
        unsaved.setTitle("My Blog");
        unsaved.setSlug("my-blog");
        unsaved.setCreatedAt(LocalDateTime.now());

        ContentCollectionEntity saved = new ContentCollectionEntity();
        saved.setId(42L);
        saved.setTitle(unsaved.getTitle());
        saved.setSlug(unsaved.getSlug());
        saved.setPriority(unsaved.getPriority());
        saved.setCoverImageBlockId(unsaved.getCoverImageBlockId());
        saved.setCreatedAt(unsaved.getCreatedAt());

        when(contentCollectionProcessingUtil.toEntity(eq(dto), anyInt())).thenReturn(unsaved);
        when(contentCollectionRepository.save(unsaved)).thenReturn(saved);
        when(contentBlockRepository.findByCollectionIdOrderByOrderIndex(42L)).thenReturn(Collections.emptyList());

        // Act
        ContentCollectionModel result = service.createCollection(dto);

        // Assert
        assertThat(result).isNotNull();
        // No home card interactions on create
        verifyNoInteractions(homeService);
    }

    @Test
    void updateContent_homeCardEnabledFalse_callsUpsertDisabled() {
        // Arrange
        long id = 100L;
        ContentCollectionUpdateDTO updateDTO = ContentCollectionUpdateDTO.builder()
                .homeCardEnabled(false)
                .priority(3)
                .homeCardText("irrelevant when disabled")
                .build();

        ContentCollectionEntity existing = new ContentCollectionEntity();
        existing.setId(id);
        existing.setTitle("Existing");
        existing.setSlug("existing");
        existing.setPriority(2);
        existing.setCreatedAt(LocalDateTime.now());

        when(contentCollectionRepository.findById(id)).thenReturn(Optional.of(existing));
        // Basic updates do nothing in this test
        doNothing().when(contentCollectionProcessingUtil).applyBasicUpdates(existing, updateDTO);
        // No removals
        when(contentCollectionProcessingUtil.handleNewTextBlocksReturnIds(eq(id), eq(updateDTO)))
                .thenReturn(Collections.emptyList());
        doNothing().when(contentCollectionProcessingUtil)
                .handleContentBlockReordering(eq(id), eq(updateDTO), anyList());
        when(contentCollectionRepository.save(existing)).thenReturn(existing);
        when(contentBlockRepository.countByCollectionId(id)).thenReturn(0L);
        when(contentCollectionProcessingUtil.convertToBasicModel(existing)).thenReturn(new ContentCollectionModel());

        // Act
        ContentCollectionModel result = service.updateContent(id, updateDTO);

        // Assert
        assertThat(result).isNotNull();
        verify(homeService).upsertHomeCardForCollection(
                eq(existing), eq(false), eq(3), eq("irrelevant when disabled")
        );
        verify(homeService, never()).syncHomeCardOnCollectionUpdate(any());
    }
}
