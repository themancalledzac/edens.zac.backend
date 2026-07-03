package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.TagEntity;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.SaveAsCollectionRequest;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

  @Mock private TagRepository tagRepository;
  @Mock private CollectionRepository collectionRepository;
  @Mock private CollectionService collectionService;

  @InjectMocks private TagService tagService;

  private TagEntity unconvertedTag() {
    return TagEntity.builder().id(5L).tagName("Landscape").slug("landscape").build();
  }

  private CollectionRequests.UpdateResponse responseWithId(Long id, String slug) {
    CollectionModel model = CollectionModel.builder().id(id).slug(slug).build();
    return new CollectionRequests.UpdateResponse(model, null);
  }

  @Test
  void convertTagToCollection_createsCollectionWithTagSlug_copiesMembers_flagsTag() {
    TagEntity tag = unconvertedTag();
    when(tagRepository.findById(5L)).thenReturn(Optional.of(tag));
    when(collectionRepository.findBySlug("landscape")).thenReturn(Optional.empty());

    when(collectionService.createCollection(any())).thenReturn(responseWithId(99L, "landscape-1"));

    CollectionEntity created = new CollectionEntity();
    created.setId(99L);
    created.setSlug("landscape-1");
    when(collectionRepository.findById(99L)).thenReturn(Optional.of(created));

    CollectionEntity memberCollection = new CollectionEntity();
    memberCollection.setId(200L);
    when(tagRepository.findCollectionsByTagId(eq(5L), anyList()))
        .thenReturn(List.of(memberCollection));
    when(tagRepository.findImageContentByTagId(eq(5L), anyList())).thenReturn(List.of(300L, 301L));
    when(collectionRepository.getMaxOrderIndexForCollection(99L)).thenReturn(0);

    when(collectionService.getUpdateCollectionData("landscape"))
        .thenReturn(responseWithId(99L, "landscape"));

    CollectionRequests.UpdateResponse result =
        tagService.convertTagToCollection(
            5L,
            new SaveAsCollectionRequest(
                CollectionType.PORTFOLIO, CollectionVisibility.UNLISTED, null));

    // Create used the tag NAME as title.
    ArgumentCaptor<CollectionRequests.Create> createCaptor =
        ArgumentCaptor.forClass(CollectionRequests.Create.class);
    verify(collectionService).createCollection(createCaptor.capture());
    assertThat(createCaptor.getValue().title()).isEqualTo("Landscape");
    assertThat(createCaptor.getValue().type()).isEqualTo(CollectionType.PORTFOLIO);

    // New collection took over the tag slug and requested visibility.
    ArgumentCaptor<CollectionEntity> saveCaptor = ArgumentCaptor.forClass(CollectionEntity.class);
    verify(collectionRepository).save(saveCaptor.capture());
    assertThat(saveCaptor.getValue().getSlug()).isEqualTo("landscape");
    assertThat(saveCaptor.getValue().getVisibility()).isEqualTo(CollectionVisibility.UNLISTED);

    // Member collection linked; two images snapshotted with incrementing order indexes.
    verify(collectionService).linkCollectionToParent(99L, 200L);
    ArgumentCaptor<CollectionContentEntity> contentCaptor =
        ArgumentCaptor.forClass(CollectionContentEntity.class);
    verify(collectionRepository, org.mockito.Mockito.times(2)).saveContent(contentCaptor.capture());
    assertThat(contentCaptor.getAllValues())
        .extracting(CollectionContentEntity::getContentId)
        .containsExactly(300L, 301L);
    assertThat(contentCaptor.getAllValues())
        .extracting(CollectionContentEntity::getOrderIndex)
        .containsExactly(1, 2);

    // Tag flagged converted; response is the real collection at the tag slug.
    verify(tagRepository).updateConvertedCollectionId(5L, 99L);
    assertThat(result.collection().getSlug()).isEqualTo("landscape");
  }

  @Test
  void convertTagToCollection_defaultsTypeAndVisibility_whenRequestNull() {
    TagEntity tag = unconvertedTag();
    when(tagRepository.findById(5L)).thenReturn(Optional.of(tag));
    when(collectionRepository.findBySlug("landscape")).thenReturn(Optional.empty());
    when(collectionService.createCollection(any())).thenReturn(responseWithId(99L, "landscape-1"));
    CollectionEntity created = new CollectionEntity();
    created.setId(99L);
    when(collectionRepository.findById(99L)).thenReturn(Optional.of(created));
    when(tagRepository.findCollectionsByTagId(eq(5L), anyList())).thenReturn(List.of());
    when(tagRepository.findImageContentByTagId(eq(5L), anyList())).thenReturn(List.of());
    when(collectionService.getUpdateCollectionData("landscape"))
        .thenReturn(responseWithId(99L, "landscape"));

    tagService.convertTagToCollection(5L, null);

    ArgumentCaptor<CollectionRequests.Create> createCaptor =
        ArgumentCaptor.forClass(CollectionRequests.Create.class);
    verify(collectionService).createCollection(createCaptor.capture());
    assertThat(createCaptor.getValue().type()).isEqualTo(CollectionType.PORTFOLIO);

    ArgumentCaptor<CollectionEntity> saveCaptor = ArgumentCaptor.forClass(CollectionEntity.class);
    verify(collectionRepository).save(saveCaptor.capture());
    assertThat(saveCaptor.getValue().getVisibility()).isEqualTo(CollectionVisibility.UNLISTED);
  }

  @Test
  void convertTagToCollection_defaultScope_excludesHiddenAndSkipsPasswordGatedMembers() {
    TagEntity tag = unconvertedTag();
    when(tagRepository.findById(5L)).thenReturn(Optional.of(tag));
    when(collectionRepository.findBySlug("landscape")).thenReturn(Optional.empty());
    when(collectionService.createCollection(any())).thenReturn(responseWithId(99L, "landscape-1"));
    CollectionEntity created = new CollectionEntity();
    created.setId(99L);
    when(collectionRepository.findById(99L)).thenReturn(Optional.of(created));

    // One plain member collection and one password-gated member collection.
    CollectionEntity plain = new CollectionEntity();
    plain.setId(200L);
    CollectionEntity gated = new CollectionEntity();
    gated.setId(201L);
    gated.setGalleryPassword("secret");

    ArgumentCaptor<List<CollectionVisibility>> scopeCaptor = scopeCaptor();
    when(tagRepository.findCollectionsByTagId(eq(5L), scopeCaptor.capture()))
        .thenReturn(List.of(plain, gated));
    when(tagRepository.findImageContentByTagId(eq(5L), anyList())).thenReturn(List.of());
    when(collectionService.getUpdateCollectionData("landscape"))
        .thenReturn(responseWithId(99L, "landscape"));

    tagService.convertTagToCollection(
        5L,
        new SaveAsCollectionRequest(CollectionType.PORTFOLIO, CollectionVisibility.UNLISTED, null));

    // Default scope is LISTED + UNLISTED (HIDDEN excluded).
    assertThat(scopeCaptor.getValue())
        .containsExactlyInAnyOrder(CollectionVisibility.LISTED, CollectionVisibility.UNLISTED)
        .doesNotContain(CollectionVisibility.HIDDEN);
    // Password-gated member collection is skipped; only the plain one is linked.
    verify(collectionService).linkCollectionToParent(99L, 200L);
    verify(collectionService, never()).linkCollectionToParent(99L, 201L);
  }

  @Test
  void convertTagToCollection_includeHidden_widensScopeAndKeepsPasswordGatedMembers() {
    TagEntity tag = unconvertedTag();
    when(tagRepository.findById(5L)).thenReturn(Optional.of(tag));
    when(collectionRepository.findBySlug("landscape")).thenReturn(Optional.empty());
    when(collectionService.createCollection(any())).thenReturn(responseWithId(99L, "landscape-1"));
    CollectionEntity created = new CollectionEntity();
    created.setId(99L);
    when(collectionRepository.findById(99L)).thenReturn(Optional.of(created));

    CollectionEntity gated = new CollectionEntity();
    gated.setId(201L);
    gated.setGalleryPassword("secret");

    ArgumentCaptor<List<CollectionVisibility>> scopeCaptor = scopeCaptor();
    when(tagRepository.findCollectionsByTagId(eq(5L), scopeCaptor.capture()))
        .thenReturn(List.of(gated));
    when(tagRepository.findImageContentByTagId(eq(5L), anyList())).thenReturn(List.of());
    when(collectionService.getUpdateCollectionData("landscape"))
        .thenReturn(responseWithId(99L, "landscape"));

    tagService.convertTagToCollection(
        5L,
        new SaveAsCollectionRequest(CollectionType.PORTFOLIO, CollectionVisibility.UNLISTED, true));

    // Opt-in widens scope to include HIDDEN and keeps the password-gated member.
    assertThat(scopeCaptor.getValue())
        .containsExactlyInAnyOrder(
            CollectionVisibility.LISTED,
            CollectionVisibility.UNLISTED,
            CollectionVisibility.HIDDEN);
    verify(collectionService).linkCollectionToParent(99L, 201L);
  }

  @SuppressWarnings("unchecked")
  private ArgumentCaptor<List<CollectionVisibility>> scopeCaptor() {
    return ArgumentCaptor.forClass(List.class);
  }

  @Test
  void convertTagToCollection_missingTag_throwsNotFound() {
    when(tagRepository.findById(5L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> tagService.convertTagToCollection(5L, null))
        .isInstanceOf(edens.zac.portfolio.backend.config.ResourceNotFoundException.class);
    verify(collectionService, never()).createCollection(any());
  }

  @Test
  void convertTagToCollection_alreadyConverted_isRejected() {
    TagEntity tag = TagEntity.builder().id(5L).tagName("Landscape").slug("landscape").build();
    tag.setConvertedCollectionId(99L);
    when(tagRepository.findById(5L)).thenReturn(Optional.of(tag));

    assertThatThrownBy(() -> tagService.convertTagToCollection(5L, null))
        .isInstanceOf(IllegalStateException.class);
    verify(collectionService, never()).createCollection(any());
  }

  @Test
  void convertTagToCollection_slugCollision_isRejected() {
    TagEntity tag = unconvertedTag();
    when(tagRepository.findById(5L)).thenReturn(Optional.of(tag));
    when(collectionRepository.findBySlug("landscape"))
        .thenReturn(Optional.of(new CollectionEntity()));

    assertThatThrownBy(() -> tagService.convertTagToCollection(5L, null))
        .isInstanceOf(IllegalStateException.class);
    verify(collectionService, never()).createCollection(any());
  }
}
