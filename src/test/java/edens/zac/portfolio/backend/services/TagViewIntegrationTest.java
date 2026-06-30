package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Real-Postgres integration coverage for the A1 tag-view read model. Exercises the new {@link
 * TagRepository} SQL end-to-end against a Flyway-migrated schema so the multi-column {@code
 * DISTINCT} path in {@link TagRepository#findImageContentByTagId} (the column-count regression that
 * was fixed by hand) cannot recur, and so visibility/ordering semantics are verified against the
 * real planner rather than a mock.
 *
 * <p>The {@code test} profile is not {@code dev}, so {@link
 * CollectionService#getCollectionWithPagination} resolves with the production visibility scope
 * (LISTED only). Repository methods are additionally exercised directly with explicit visibility
 * sets to cover both the prod and local scopes.
 */
class TagViewIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final List<CollectionVisibility> PROD_ALLOWED =
      List.of(CollectionVisibility.LISTED);
  private static final List<CollectionVisibility> LOCAL_ALLOWED =
      List.of(
          CollectionVisibility.LISTED, CollectionVisibility.UNLISTED, CollectionVisibility.HIDDEN);

  @Autowired private TagRepository tagRepository;
  @Autowired private CollectionService collectionService;
  @Autowired private JdbcTemplate jdbc;

  // ---- seed helpers -------------------------------------------------------

  private long seedTag(String name, String slug) {
    jdbc.update("INSERT INTO tag (tag_name, slug) VALUES (?, ?)", name, slug);
    return jdbc.queryForObject("SELECT id FROM tag WHERE slug = ?", Long.class, slug);
  }

  private long seedCollection(String slug, CollectionVisibility visibility, Integer rating) {
    jdbc.update(
        "INSERT INTO collection (title, slug, type, visibility, rating) VALUES (?, ?, 'BLOG', ?, ?)",
        slug,
        slug,
        visibility.name(),
        rating);
    return jdbc.queryForObject("SELECT id FROM collection WHERE slug = ?", Long.class, slug);
  }

  private void tagCollection(long collectionId, long tagId) {
    jdbc.update(
        "INSERT INTO collection_tags (collection_id, tag_id) VALUES (?, ?)", collectionId, tagId);
  }

  /** Seeds a base content row + its content_image child and returns the shared id. */
  private long seedImage(String urlWeb) {
    jdbc.update("INSERT INTO content (content_type) VALUES ('IMAGE')");
    long id = jdbc.queryForObject("SELECT MAX(id) FROM content", Long.class);
    jdbc.update(
        "INSERT INTO content_image (id, title, image_url_web) VALUES (?, ?, ?)",
        id,
        "img-" + id,
        urlWeb);
    return id;
  }

  private void tagContent(long contentId, long tagId) {
    jdbc.update("INSERT INTO content_tags (content_id, tag_id) VALUES (?, ?)", contentId, tagId);
  }

  private void addMembership(long collectionId, long contentId, boolean visible) {
    jdbc.update(
        "INSERT INTO collection_content (collection_id, content_id, visible) VALUES (?, ?, ?)",
        collectionId,
        contentId,
        visible);
  }

  // ---- findCollectionsByTagId --------------------------------------------

  @Test
  void findCollectionsByTagId_filtersByVisibilityAndOrders() {
    long tag = seedTag("Landscape", "landscape-collections");
    // rating asc on purpose so we can assert the rating-desc ordering reorders them.
    long listedLow = seedCollection("listed-low", CollectionVisibility.LISTED, 2);
    long listedHigh = seedCollection("listed-high", CollectionVisibility.LISTED, 4);
    long unlisted = seedCollection("unlisted-c", CollectionVisibility.UNLISTED, 5);
    long hidden = seedCollection("hidden-c", CollectionVisibility.HIDDEN, 5);
    tagCollection(listedLow, tag);
    tagCollection(listedHigh, tag);
    tagCollection(unlisted, tag);
    tagCollection(hidden, tag);

    // Prod scope: only LISTED, ordered rating DESC.
    List<CollectionEntity> prod = tagRepository.findCollectionsByTagId(tag, PROD_ALLOWED);
    assertThat(prod).extracting(CollectionEntity::getId).containsExactly(listedHigh, listedLow);

    // Local scope: all three visibilities surface; high-rated first.
    List<CollectionEntity> local = tagRepository.findCollectionsByTagId(tag, LOCAL_ALLOWED);
    assertThat(local)
        .extracting(CollectionEntity::getVisibility)
        .doesNotContain((CollectionVisibility) null);
    assertThat(local)
        .extracting(CollectionEntity::getId)
        .containsExactlyInAnyOrder(listedLow, listedHigh, unlisted, hidden);
    // rating DESC NULLS LAST, collection_date DESC NULLS LAST — top two are the rating-5 rows.
    assertThat(local.subList(0, 2))
        .extracting(CollectionEntity::getId)
        .containsExactlyInAnyOrder(unlisted, hidden);
    assertThat(local.get(local.size() - 1).getId()).isEqualTo(listedLow);
  }

  @Test
  void findCollectionsByTagId_tagWithNoCollections_returnsEmpty() {
    long tag = seedTag("Orphan", "orphan-collections");
    assertThat(tagRepository.findCollectionsByTagId(tag, LOCAL_ALLOWED)).isEmpty();
  }

  // ---- findImageContentByTagId -------------------------------------------

  @Test
  void findImageContentByTagId_gatesOnVisibleMembershipAndCollectionVisibility() {
    long tag = seedTag("Mountains", "mountains-images");
    long listed = seedCollection("img-listed", CollectionVisibility.LISTED, 1);
    long hidden = seedCollection("img-hidden", CollectionVisibility.HIDDEN, 1);

    // Image A: visible membership in a LISTED collection -> surfaces in prod.
    long imageA = seedImage("https://cdn/a.jpg");
    tagContent(imageA, tag);
    addMembership(listed, imageA, true);

    // Image B: tagged, but only a HIDDEN-collection membership -> hidden in prod, visible local.
    long imageB = seedImage("https://cdn/b.jpg");
    tagContent(imageB, tag);
    addMembership(hidden, imageB, true);

    // Image C: tagged, but its only membership is soft-removed (visible=false) -> never surfaces.
    long imageC = seedImage("https://cdn/c.jpg");
    tagContent(imageC, tag);
    addMembership(listed, imageC, false);

    List<Long> prod = tagRepository.findImageContentByTagId(tag, PROD_ALLOWED);
    assertThat(prod).containsExactly(imageA);
    assertThat(prod).doesNotContain(imageB, imageC);

    List<Long> local = tagRepository.findImageContentByTagId(tag, LOCAL_ALLOWED);
    assertThat(local).containsExactlyInAnyOrder(imageA, imageB);
    assertThat(local).doesNotContain(imageC);
  }

  /**
   * Regression guard for the fixed column-count bug: an image that is a visible member of MULTIPLE
   * LISTED collections must appear exactly once. Against the pre-fix {@code
   * queryForList(Long.class)} implementation this query (DISTINCT c.id, c.created_at) throws — a
   * single-column mapper cannot read the two-column result — so this assertion genuinely exercises
   * the multi-column DISTINCT path.
   */
  @Test
  void findImageContentByTagId_multipleVisibleMemberships_dedupesToOneRow() {
    long tag = seedTag("Featured", "featured-images");
    long c1 = seedCollection("multi-1", CollectionVisibility.LISTED, 1);
    long c2 = seedCollection("multi-2", CollectionVisibility.LISTED, 1);

    long image = seedImage("https://cdn/multi.jpg");
    tagContent(image, tag);
    addMembership(c1, image, true);
    addMembership(c2, image, true);

    List<Long> prod = tagRepository.findImageContentByTagId(tag, PROD_ALLOWED);
    assertThat(prod).containsExactly(image);
    assertThat(prod).hasSize(1);
  }

  @Test
  void findImageContentByTagId_ordersNewestFirst() {
    long tag = seedTag("Ordered", "ordered-images");
    long listed = seedCollection("ord-c", CollectionVisibility.LISTED, 1);

    long older = seedImage("https://cdn/older.jpg");
    long newer = seedImage("https://cdn/newer.jpg");
    // Force created_at ordering deterministically.
    jdbc.update("UPDATE content SET created_at = '2020-01-01 00:00:00' WHERE id = ?", older);
    jdbc.update("UPDATE content SET created_at = '2024-01-01 00:00:00' WHERE id = ?", newer);
    tagContent(older, tag);
    tagContent(newer, tag);
    addMembership(listed, older, true);
    addMembership(listed, newer, true);

    assertThat(tagRepository.findImageContentByTagId(tag, PROD_ALLOWED))
        .containsExactly(newer, older);
  }

  // ---- resolution path through CollectionService -------------------------

  @Test
  void getCollectionWithPagination_tagSlug_returnsParentCollectionsFirstThenImages() {
    long tag = seedTag("Travel", "travel");
    long memberCollection = seedCollection("travel-trip", CollectionVisibility.LISTED, 5);
    tagCollection(memberCollection, tag);

    long listed = seedCollection("travel-host", CollectionVisibility.LISTED, 1);
    long image = seedImage("https://cdn/travel.jpg");
    tagContent(image, tag);
    addMembership(listed, image, true);

    CollectionModel model = collectionService.getCollectionWithPagination("travel", 0, 30);

    assertThat(model.getType()).isEqualTo(CollectionType.PARENT);
    assertThat(model.getSlug()).isEqualTo("travel");
    assertThat(model.getContent()).hasSize(2);
    // Collections render before images.
    assertThat(model.getContent().get(0)).isInstanceOf(ContentModels.Collection.class);
    assertThat(model.getContent().get(1)).isInstanceOf(ContentModels.Image.class);
  }

  @Test
  void getCollectionWithPagination_slugMatchesNothing_throwsNotFound() {
    assertThatThrownBy(
            () -> collectionService.getCollectionWithPagination("no-such-thing-anywhere", 0, 30))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void getCollectionWithPagination_tagWithNoVisibleMembers_throwsNotFound() {
    long tag = seedTag("EmptyTag", "empty-tag");
    // Member exists but only as a HIDDEN collection -> not visible in prod -> 404.
    long hidden = seedCollection("empty-hidden", CollectionVisibility.HIDDEN, 1);
    tagCollection(hidden, tag);

    assertThatThrownBy(() -> collectionService.getCollectionWithPagination("empty-tag", 0, 30))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void getCollectionWithPagination_slugIsBothCollectionAndTag_realCollectionWins() {
    String slug = "collision-slug";
    // A real LISTED collection with this slug.
    seedCollection(slug, CollectionVisibility.LISTED, 1);
    // A tag that also slugs to the same value, with its own visible member.
    long tag = seedTag("Collision", slug);
    long member = seedCollection("collision-member", CollectionVisibility.LISTED, 5);
    tagCollection(member, tag);

    CollectionModel model = collectionService.getCollectionWithPagination(slug, 0, 30);
    // Real collection wins: it is type BLOG, not the synthetic PARENT tag-view.
    assertThat(model.getType()).isEqualTo(CollectionType.BLOG);
    assertThat(model.getSlug()).isEqualTo(slug);
  }
}
