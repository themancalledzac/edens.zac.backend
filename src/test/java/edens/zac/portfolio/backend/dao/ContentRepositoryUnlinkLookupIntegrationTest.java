package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Covers {@link ContentRepository#findCollectionContentInCollectionMatching}, the single query that
 * replaced the per-content-block lookup behind "remove a sub-collection".
 *
 * <p>Runs against real Postgres because every property here lives in the SQL. A Mockito test of
 * {@code CollectionService} can prove the service calls the repository once; only this can prove
 * the query returns the right rows -- and the parent-scoping test below is the one that matters,
 * since matching on the content ids alone would compile, pass a mocked test, and silently reach
 * across into another parent's links.
 *
 * <p>Slugs are prefixed {@code unlinklookup-} because the shared Testcontainers Postgres does NOT
 * truncate {@code collection} between test classes.
 */
class ContentRepositoryUnlinkLookupIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private ContentRepository contentRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private Long seedCollection() {
    String slug = "unlinklookup-" + UUID.randomUUID();
    return jdbcTemplate.queryForObject(
        "INSERT INTO collection (title, slug, visibility) VALUES (?, ?, 'LISTED') RETURNING id",
        Long.class,
        slug,
        slug);
  }

  /** A COLLECTION content block pointing at {@code referencedCollectionId}. */
  private Long seedCollectionBlock(Long referencedCollectionId) {
    Long contentId =
        jdbcTemplate.queryForObject(
            "INSERT INTO content (content_type) VALUES ('COLLECTION') RETURNING id", Long.class);
    jdbcTemplate.update(
        "INSERT INTO content_collection (id, referenced_collection_id) VALUES (?, ?)",
        contentId,
        referencedCollectionId);
    return contentId;
  }

  private Long seedImageBlock() {
    Long contentId =
        jdbcTemplate.queryForObject(
            "INSERT INTO content (content_type) VALUES ('IMAGE') RETURNING id", Long.class);
    jdbcTemplate.update(
        "INSERT INTO content_image (id, title, image_url_web) VALUES (?, ?, ?)",
        contentId,
        "img",
        "https://cdn.example.com/unlinklookup-" + UUID.randomUUID() + ".jpg");
    return contentId;
  }

  private void link(Long parentId, Long contentId, int orderIndex, boolean visible) {
    jdbcTemplate.update(
        "INSERT INTO collection_content (collection_id, content_id, order_index, visible)"
            + " VALUES (?, ?, ?, ?)",
        parentId,
        contentId,
        orderIndex,
        visible);
  }

  private static List<Long> idsOf(List<ContentCollectionEntity> blocks) {
    return blocks.stream().map(ContentCollectionEntity::getId).toList();
  }

  @Test
  void matchesOnTheContentBlocksOwnId() {
    Long parent = seedCollection();
    Long child = seedCollection();
    Long block = seedCollectionBlock(child);
    link(parent, block, 0, true);

    assertThat(
            idsOf(
                contentRepository.findCollectionContentInCollectionMatching(
                    parent, List.of(block))))
        .containsExactly(block);
  }

  @Test
  void matchesOnTheReferencedCollectionId() {
    Long parent = seedCollection();
    Long child = seedCollection();
    Long block = seedCollectionBlock(child);
    link(parent, block, 0, true);

    // The API response carries both ids and callers send either, so both must resolve.
    List<ContentCollectionEntity> found =
        contentRepository.findCollectionContentInCollectionMatching(parent, List.of(child));

    assertThat(idsOf(found)).containsExactly(block);
    assertThat(found.getFirst().getReferencedCollection())
        .extracting(CollectionEntity::getId)
        .isEqualTo(child);
  }

  @Test
  void doesNotReachIntoADifferentParentsLinks() {
    Long parent = seedCollection();
    Long otherParent = seedCollection();
    Long child = seedCollection();
    Long block = seedCollectionBlock(child);
    // The block exists and matches both id kinds -- it is simply linked somewhere else.
    link(otherParent, block, 0, true);

    assertThat(contentRepository.findCollectionContentInCollectionMatching(parent, List.of(block)))
        .isEmpty();
    assertThat(contentRepository.findCollectionContentInCollectionMatching(parent, List.of(child)))
        .isEmpty();
  }

  @Test
  void findsAHiddenLinkToo() {
    Long parent = seedCollection();
    Long child = seedCollection();
    Long block = seedCollectionBlock(child);
    link(parent, block, 0, false);

    // Unlinking has to reach a hidden link; the replaced loop did not filter on visible either.
    assertThat(
            idsOf(
                contentRepository.findCollectionContentInCollectionMatching(
                    parent, List.of(block))))
        .containsExactly(block);
  }

  @Test
  void ignoresTheParentsImages() {
    Long parent = seedCollection();
    Long child = seedCollection();
    Long block = seedCollectionBlock(child);
    Long image = seedImageBlock();
    link(parent, image, 0, true);
    link(parent, block, 1, true);

    // Images in the parent are what the old loop spent a query each on, for an empty result.
    List<ContentCollectionEntity> found =
        contentRepository.findCollectionContentInCollectionMatching(
            parent, List.of(block, image, child));

    assertThat(idsOf(found)).containsExactly(block);
  }

  @Test
  void returnsMatchesInTheParentsDisplayOrder() {
    Long parent = seedCollection();
    Long first = seedCollectionBlock(seedCollection());
    Long second = seedCollectionBlock(seedCollection());
    link(parent, second, 5, true);
    link(parent, first, 1, true);

    assertThat(
            idsOf(
                contentRepository.findCollectionContentInCollectionMatching(
                    parent, List.of(first, second))))
        .containsExactly(first, second);
  }

  @Test
  void emptyOrNullInputsQueryNothing() {
    Long parent = seedCollection();

    assertThat(contentRepository.findCollectionContentInCollectionMatching(parent, List.of()))
        .isEmpty();
    assertThat(contentRepository.findCollectionContentInCollectionMatching(parent, null)).isEmpty();
    assertThat(contentRepository.findCollectionContentInCollectionMatching(null, List.of(1L)))
        .isEmpty();
  }
}
