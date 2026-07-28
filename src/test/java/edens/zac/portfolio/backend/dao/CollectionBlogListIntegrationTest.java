package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Real-Postgres coverage for the is_blog re-key of findNonEmptyOrderedByVisibilityIn. Mocked
 * JdbcTemplate cannot catch text-block syntax errors, and this query's WHERE clause is assembled by
 * StringBuilder concatenation, which is exactly where a missing space breaks silently.
 */
class CollectionBlogListIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private CollectionRepository collectionRepository;
  @Autowired private ContentRepository contentRepository;

  private CollectionEntity saveWithOneChild(String slug, boolean isBlog) {
    CollectionEntity parent =
        collectionRepository.save(
            CollectionEntity.builder()
                .isClient(false)
                .isBlog(isBlog)
                .title("Blog list " + slug)
                .slug(slug)
                .visibility(CollectionVisibility.LISTED)
                .totalContent(0)
                .build());
    CollectionEntity child =
        collectionRepository.save(
            CollectionEntity.builder()
                .isClient(false)
                .isBlog(false)
                .title("Blog list child " + slug)
                .slug(slug + "-child")
                .visibility(CollectionVisibility.LISTED)
                .totalContent(0)
                .build());
    ContentCollectionEntity ref =
        contentRepository.saveCollectionContent(
            ContentCollectionEntity.builder().referencedCollection(child).build());
    collectionRepository.saveContent(
        CollectionContentEntity.builder()
            .collectionId(parent.getId())
            .contentId(ref.getId())
            .orderIndex(0)
            .visible(true)
            .build());
    return parent;
  }

  @Test
  @DisplayName("blogsOnly=true returns is_blog rows and excludes non-blog rows")
  void blogsOnly_keysOnIsBlogFlag() {
    CollectionEntity blog = saveWithOneChild("blog-list-yes", true);
    CollectionEntity notBlog = saveWithOneChild("blog-list-no", false);

    List<CollectionEntity> rows =
        collectionRepository.findNonEmptyOrderedByVisibilityIn(
            List.of(CollectionVisibility.LISTED), true);

    assertThat(rows).extracting(CollectionEntity::getId).contains(blog.getId());
    assertThat(rows).extracting(CollectionEntity::getId).doesNotContain(notBlog.getId());
  }

  @Test
  @DisplayName("blogsOnly=false returns both blog and non-blog rows")
  void blogsOnly_false_returnsEverything() {
    CollectionEntity blog = saveWithOneChild("blog-list-all-yes", true);
    CollectionEntity notBlog = saveWithOneChild("blog-list-all-no", false);

    List<CollectionEntity> rows =
        collectionRepository.findNonEmptyOrderedByVisibilityIn(
            List.of(CollectionVisibility.LISTED), false);

    assertThat(rows).extracting(CollectionEntity::getId).contains(blog.getId(), notBlog.getId());
  }
}
