package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.TagEntity;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import edens.zac.portfolio.backend.types.ContentType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TagViewResolverTest {

  @Mock private TagRepository tagRepository;
  @Mock private ContentRepository contentRepository;
  @Mock private CollectionProcessingUtil collectionProcessingUtil;
  @Mock private ContentModelConverter contentModelConverter;

  @InjectMocks private TagViewResolver resolver;

  private static final List<CollectionVisibility> DEV_VISIBILITIES =
      List.of(
          CollectionVisibility.LISTED, CollectionVisibility.UNLISTED, CollectionVisibility.HIDDEN);
  private static final List<CollectionVisibility> PROD_VISIBILITIES =
      List.of(CollectionVisibility.LISTED);

  private TagEntity tag(long id, String name, String slug) {
    return TagEntity.builder().id(id).tagName(name).slug(slug).build();
  }

  private ContentModels.Image imageModel(long id) {
    return new ContentModels.Image(
        id,
        ContentType.IMAGE,
        null,
        null,
        null,
        null,
        "https://cdn/" + id + ".webp",
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
        List.of(),
        List.of());
  }

  @Test
  void unknownSlug_returnsEmpty() {
    when(tagRepository.findBySlug("nope")).thenReturn(Optional.empty());

    assertThat(resolver.resolveTagView("nope", true)).isEmpty();
  }

  @Test
  void tagWithCollectionsAndImages_returnsParentModelWithCollectionsFirst() {
    TagEntity travel = tag(7L, "Travel", "travel");
    when(tagRepository.findBySlug("travel")).thenReturn(Optional.of(travel));

    when(tagRepository.findCollectionsByTagId(eq(7L), eq(PROD_VISIBILITIES)))
        .thenReturn(List.of(new CollectionEntity(), new CollectionEntity()));
    CollectionModel coll1 =
        CollectionModel.builder()
            .id(1L)
            .slug("chamonix")
            .type(CollectionType.BLOG)
            .coverImage(imageModel(100L))
            .build();
    CollectionModel coll2 =
        CollectionModel.builder().id(2L).slug("iceland").type(CollectionType.PORTFOLIO).build();
    when(collectionProcessingUtil.batchConvertToBasicModels(any()))
        .thenReturn(List.of(coll1, coll2));

    when(tagRepository.findImageContentByTagId(eq(7L), eq(PROD_VISIBILITIES)))
        .thenReturn(List.of(200L, 201L));
    ContentImageEntity img1 = ContentImageEntity.builder().id(200L).build();
    ContentImageEntity img2 = ContentImageEntity.builder().id(201L).build();
    when(contentRepository.findImagesByIds(eq(List.of(200L, 201L))))
        .thenReturn(List.of(img1, img2));
    when(contentModelConverter.batchConvertImageEntitiesToModels(eq(List.of(img1, img2))))
        .thenReturn(List.of(imageModel(200L), imageModel(201L)));

    CollectionModel out = resolver.resolveTagView("travel", false).orElseThrow();

    assertThat(out.getSlug()).isEqualTo("travel");
    assertThat(out.getTitle()).isEqualTo("Travel");
    assertThat(out.getType()).isEqualTo(CollectionType.PARENT);
    assertThat(out.getVisibility()).isEqualTo(CollectionVisibility.LISTED);

    // collections-first ordering: 2 collection blocks then 2 image blocks
    List<ContentModel> content = out.getContent();
    assertThat(content).hasSize(4);
    assertThat(content.get(0)).isInstanceOf(ContentModels.Collection.class);
    assertThat(content.get(1)).isInstanceOf(ContentModels.Collection.class);
    assertThat(content.get(2)).isInstanceOf(ContentModels.Image.class);
    assertThat(content.get(3)).isInstanceOf(ContentModels.Image.class);

    ContentModels.Collection firstColl = (ContentModels.Collection) content.get(0);
    assertThat(firstColl.contentType()).isEqualTo(ContentType.COLLECTION);
    assertThat(firstColl.referencedCollectionId()).isEqualTo(1L);
    assertThat(firstColl.slug()).isEqualTo("chamonix");

    // cover falls back to the first member collection's cover image
    assertThat(out.getCoverImage()).isNotNull();
    assertThat(out.getCoverImage().id()).isEqualTo(100L);
  }

  @Test
  void tagWithOnlyImages_rendersImageSectionAndCoverFromFirstImage() {
    TagEntity film = tag(9L, "Film", "film");
    when(tagRepository.findBySlug("film")).thenReturn(Optional.of(film));
    when(tagRepository.findCollectionsByTagId(eq(9L), eq(PROD_VISIBILITIES))).thenReturn(List.of());
    when(collectionProcessingUtil.batchConvertToBasicModels(any())).thenReturn(List.of());

    when(tagRepository.findImageContentByTagId(eq(9L), eq(PROD_VISIBILITIES)))
        .thenReturn(List.of(300L));
    ContentImageEntity img = ContentImageEntity.builder().id(300L).build();
    when(contentRepository.findImagesByIds(eq(List.of(300L)))).thenReturn(List.of(img));
    when(contentModelConverter.batchConvertImageEntitiesToModels(eq(List.of(img))))
        .thenReturn(List.of(imageModel(300L)));

    CollectionModel out = resolver.resolveTagView("film", false).orElseThrow();

    assertThat(out.getContent()).hasSize(1);
    assertThat(out.getContent().get(0)).isInstanceOf(ContentModels.Image.class);
    assertThat(out.getCoverImage()).isNotNull();
    assertThat(out.getCoverImage().id()).isEqualTo(300L);
  }

  @Test
  void zeroMembers_returnsEmpty() {
    TagEntity orphan = tag(11L, "Orphan", "orphan");
    when(tagRepository.findBySlug("orphan")).thenReturn(Optional.of(orphan));
    when(tagRepository.findCollectionsByTagId(eq(11L), eq(PROD_VISIBILITIES)))
        .thenReturn(List.of());
    when(collectionProcessingUtil.batchConvertToBasicModels(any())).thenReturn(List.of());
    when(tagRepository.findImageContentByTagId(eq(11L), eq(PROD_VISIBILITIES)))
        .thenReturn(List.of());

    assertThat(resolver.resolveTagView("orphan", false)).isEmpty();
  }

  @Test
  void localEnvironment_expandsAllowedVisibilities() {
    TagEntity travel = tag(7L, "Travel", "travel");
    when(tagRepository.findBySlug("travel")).thenReturn(Optional.of(travel));
    when(tagRepository.findCollectionsByTagId(eq(7L), eq(DEV_VISIBILITIES)))
        .thenReturn(List.of(new CollectionEntity()));
    when(collectionProcessingUtil.batchConvertToBasicModels(any()))
        .thenReturn(
            List.of(CollectionModel.builder().id(1L).slug("c").type(CollectionType.BLOG).build()));
    when(tagRepository.findImageContentByTagId(eq(7L), eq(DEV_VISIBILITIES))).thenReturn(List.of());

    CollectionModel out = resolver.resolveTagView("travel", true).orElseThrow();

    assertThat(out.getContent()).hasSize(1);
  }
}
