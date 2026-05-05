package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import edens.zac.portfolio.backend.types.ContentType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyntheticCollectionResolverTest {

  @Mock private CollectionRepository collectionRepository;
  @Mock private CollectionProcessingUtil collectionProcessingUtil;

  @InjectMocks private SyntheticCollectionResolver resolver;

  @Test
  void recognizesAllCatalogSlugs() {
    assertThat(resolver.isSyntheticSlug("all-collections")).isTrue();
    assertThat(resolver.isSyntheticSlug("all-blogs")).isTrue();
    assertThat(resolver.isSyntheticSlug("all-portfolios")).isTrue();
    assertThat(resolver.isSyntheticSlug("all-client-galleries")).isTrue();
    assertThat(resolver.isSyntheticSlug("all-art-galleries")).isTrue();
    assertThat(resolver.isSyntheticSlug("all-misc")).isTrue();
  }

  @Test
  void rejectsNonCatalogSlugs() {
    assertThat(resolver.isSyntheticSlug("home")).isFalse();
    assertThat(resolver.isSyntheticSlug("film")).isFalse();
    assertThat(resolver.isSyntheticSlug(null)).isFalse();
    assertThat(resolver.isSyntheticSlug("")).isFalse();
  }

  @Test
  void resolveAllCollectionsInDevAllowsAllVisibilitiesAndReturnsChildrenAsContent() {
    when(collectionRepository.findOrderedByVisibilityIn(
            eq(
                List.of(
                    CollectionVisibility.LISTED,
                    CollectionVisibility.UNLISTED,
                    CollectionVisibility.HIDDEN)),
            eq(null)))
        .thenReturn(List.of(new CollectionEntity(), new CollectionEntity()));
    when(collectionProcessingUtil.batchConvertToBasicModels(any()))
        .thenReturn(
            List.of(
                CollectionModel.builder().id(1L).slug("a").type(CollectionType.PORTFOLIO).build(),
                CollectionModel.builder().id(2L).slug("b").type(CollectionType.BLOG).build()));

    CollectionModel out = resolver.resolve("all-collections", true);

    assertThat(out.getSlug()).isEqualTo("all-collections");
    assertThat(out.getTitle()).isEqualTo("All Collections");
    assertThat(out.getType()).isEqualTo(CollectionType.PARENT);
    assertThat(out.getVisibility()).isEqualTo(CollectionVisibility.LISTED);
    assertThat(out.getContent()).hasSize(2);
    assertThat(out.getContent().get(0)).isInstanceOf(ContentModels.Collection.class);
    ContentModels.Collection first = (ContentModels.Collection) out.getContent().get(0);
    assertThat(first.contentType()).isEqualTo(ContentType.COLLECTION);
    assertThat(first.referencedCollectionId()).isEqualTo(1L);
    assertThat(first.slug()).isEqualTo("a");
    assertThat(first.collectionType()).isEqualTo(CollectionType.PORTFOLIO);
  }

  @Test
  void resolveAllBlogsInProdFiltersToBlogTypeAndListedOnly() {
    when(collectionRepository.findOrderedByVisibilityIn(
            eq(List.of(CollectionVisibility.LISTED)), eq(CollectionType.BLOG)))
        .thenReturn(List.of(new CollectionEntity()));
    when(collectionProcessingUtil.batchConvertToBasicModels(any()))
        .thenReturn(
            List.of(
                CollectionModel.builder().id(11L).slug("trip").type(CollectionType.BLOG).build()));

    CollectionModel out = resolver.resolve("all-blogs", false);

    assertThat(out.getSlug()).isEqualTo("all-blogs");
    assertThat(out.getTitle()).isEqualTo("Blogs");
    assertThat(out.getType()).isEqualTo(CollectionType.PARENT);
    assertThat(out.getContent()).hasSize(1);
  }

  @Test
  void resolveUnknownSlugThrows() {
    assertThatThrownBy(() -> resolver.resolve("home", true))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
