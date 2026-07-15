package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.TagEntity;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import edens.zac.portfolio.backend.types.ContentType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration coverage for the two collections-list read helpers added for the client-side
 * filtering feature: {@link TagRepository#findTagsByCollectionIds} (batch tag-name load that backs
 * client-side tag filtering) and {@link CollectionRepository#findNonEmptyByVisibilityInOrderByDate}
 * (chronological, non-empty {@code all-collections} ordering). Requires Docker (Testcontainers
 * Postgres); runs the real migrations on top of {@code test-base-schema.sql} exactly as in prod.
 */
class CollectionListReadRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private CollectionRepository collectionRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private ContentRepository contentRepository;

  private CollectionEntity saveCollection(String slug, LocalDate date, CollectionVisibility vis) {
    return collectionRepository.save(
        CollectionEntity.builder()
            .type(CollectionType.BLOG)
            .title("List Read " + slug)
            .slug(slug)
            .collectionDate(date)
            .visibility(vis)
            .totalContent(0)
            .build());
  }

  /** Give a collection one visible content row so the non-empty EXISTS guard passes. */
  private void attachVisibleContent(Long collectionId) {
    ContentImageEntity image =
        contentRepository.saveImage(
            ContentImageEntity.builder()
                .contentType(ContentType.IMAGE)
                .title("img-" + collectionId)
                .imageUrlWeb("https://cdn.example.com/img-" + collectionId + ".jpg")
                .build());
    collectionRepository.saveContent(
        CollectionContentEntity.builder()
            .collectionId(collectionId)
            .contentId(image.getId())
            .orderIndex(0)
            .visible(true)
            .build());
  }

  @Test
  void findTagsByCollectionIds_returnsNamesPerCollectionOrderedAndAbsentWhenNone() {
    CollectionEntity tagged = saveCollection("clr-tags-a", LocalDate.of(2026, 1, 1), null);
    CollectionEntity untagged = saveCollection("clr-tags-b", LocalDate.of(2026, 1, 2), null);

    TagEntity mountains = tagRepository.save(TagEntity.builder().tagName("mountains").build());
    TagEntity italy = tagRepository.save(TagEntity.builder().tagName("italy").build());
    tagRepository.saveCollectionTags(tagged.getId(), List.of(mountains.getId(), italy.getId()));

    Map<Long, List<TagEntity>> result =
        tagRepository.findTagsByCollectionIds(List.of(tagged.getId(), untagged.getId()));

    // Tags come back ordered by tag_name ASC (italy before mountains), as full entities.
    assertThat(result.get(tagged.getId()))
        .extracting(TagEntity::getTagName)
        .containsExactly("italy", "mountains");
    // Untagged collection is simply absent from the map (not an empty list).
    assertThat(result).doesNotContainKey(untagged.getId());
  }

  @Test
  void findTagsByCollectionIds_emptyInputReturnsEmptyMap() {
    assertThat(tagRepository.findTagsByCollectionIds(List.of())).isEmpty();
  }

  @Test
  void findNonEmptyByVisibilityInOrderByDate_ordersNewestFirstAndExcludesEmpty() {
    CollectionEntity older =
        saveCollection("clr-ord-older", LocalDate.of(2026, 2, 1), CollectionVisibility.LISTED);
    CollectionEntity newer =
        saveCollection("clr-ord-newer", LocalDate.of(2026, 2, 9), CollectionVisibility.LISTED);
    CollectionEntity empty =
        saveCollection("clr-ord-empty", LocalDate.of(2026, 2, 20), CollectionVisibility.LISTED);
    attachVisibleContent(older.getId());
    attachVisibleContent(newer.getId());
    // 'empty' intentionally gets no content and must not appear.

    List<CollectionEntity> rows =
        collectionRepository.findNonEmptyByVisibilityInOrderByDate(
            List.of(CollectionVisibility.LISTED));

    List<Long> ids = rows.stream().map(CollectionEntity::getId).toList();
    assertThat(ids).contains(older.getId(), newer.getId());
    assertThat(ids).doesNotContain(empty.getId());
    // Newer collection_date sorts before older (DESC).
    assertThat(ids.indexOf(newer.getId())).isLessThan(ids.indexOf(older.getId()));
  }

  @Test
  void findNonEmptyByVisibilityInOrderByDate_respectsVisibilityScope() {
    CollectionEntity hidden =
        saveCollection("clr-vis-hidden", LocalDate.of(2026, 3, 1), CollectionVisibility.HIDDEN);
    attachVisibleContent(hidden.getId());

    List<Long> listedOnly =
        collectionRepository
            .findNonEmptyByVisibilityInOrderByDate(List.of(CollectionVisibility.LISTED))
            .stream()
            .map(CollectionEntity::getId)
            .toList();
    assertThat(listedOnly).doesNotContain(hidden.getId());

    List<Long> includingHidden =
        collectionRepository
            .findNonEmptyByVisibilityInOrderByDate(
                List.of(CollectionVisibility.LISTED, CollectionVisibility.HIDDEN))
            .stream()
            .map(CollectionEntity::getId)
            .toList();
    assertThat(includingHidden).contains(hidden.getId());
  }
}
