package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.Records;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Covers S-33: the "this image also appears in" array on a public collection read listed every
 * collection the image belongs to, unlisted and password-protected ones included. The array is
 * built by {@code CollectionProcessingUtil.populateCollectionsOnContent}, which now takes the same
 * {@code listedOnly} flag its two neighbours carry.
 *
 * <p>This runs against real Postgres and asserts on both sides of the flag from one seeded graph:
 * the public read must show only the public membership, and the admin path must still show all
 * four. A test that only pinned the public side would stay green if the flag were hardcoded true,
 * which would silently break the admin manage payload.
 *
 * <p>Slugs are prefixed {@code s33-} because the shared container does not truncate {@code
 * collection} between test classes.
 */
class CollectionContentMembershipIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private CollectionService collectionService;
  @Autowired private JdbcTemplate jdbc;

  private String publicSlug;
  private String listedPasswordSlug;
  private String unlistedSlug;
  private String invisibleMembershipSlug;

  private Long seedCollection(String visibility, String galleryPassword) {
    String slug = "s33-" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO collection (title, slug, visibility, gallery_password) VALUES (?, ?, ?, ?)",
        slug,
        slug,
        visibility,
        galleryPassword);
    return jdbc.queryForObject("SELECT id FROM collection WHERE slug = ?", Long.class, slug);
  }

  private Long seedImage() {
    Long contentId =
        jdbc.queryForObject(
            "INSERT INTO content (content_type) VALUES ('IMAGE') RETURNING id", Long.class);
    jdbc.update(
        "INSERT INTO content_image (id, title, image_url_web) VALUES (?, ?, ?)",
        contentId,
        "s33 image",
        "https://cdn.example.com/s33-" + UUID.randomUUID() + ".jpg");
    return contentId;
  }

  private void addMembership(Long collectionId, Long contentId, boolean visible) {
    jdbc.update(
        "INSERT INTO collection_content (collection_id, content_id, order_index, visible)"
            + " VALUES (?, ?, 0, ?)",
        collectionId,
        contentId,
        visible);
  }

  private String slugOf(Long collectionId) {
    return jdbc.queryForObject(
        "SELECT slug FROM collection WHERE id = ?", String.class, collectionId);
  }

  /**
   * One image held by four collections: the public one being read, a LISTED gallery carrying a
   * password, an UNLISTED collection, and a LISTED open collection where the join row itself is
   * {@code visible = false}. The last is what pins the {@code cc.visible} term.
   */
  @BeforeEach
  void seedGraph() {
    Long imageId = seedImage();

    Long publicId = seedCollection("LISTED", null);
    Long listedPasswordId = seedCollection("LISTED", "secret");
    Long unlistedId = seedCollection("UNLISTED", null);
    Long invisibleId = seedCollection("LISTED", null);

    addMembership(publicId, imageId, true);
    addMembership(listedPasswordId, imageId, true);
    addMembership(unlistedId, imageId, true);
    addMembership(invisibleId, imageId, false);

    publicSlug = slugOf(publicId);
    listedPasswordSlug = slugOf(listedPasswordId);
    unlistedSlug = slugOf(unlistedId);
    invisibleMembershipSlug = slugOf(invisibleId);
  }

  private List<Records.ChildCollection> membershipsOnFirstImage(CollectionModel model) {
    ContentModels.Image image = (ContentModels.Image) model.getContent().getFirst();
    return image.collections();
  }

  @Test
  void publicReadListsOnlyTheVisibleListedUnprotectedMembership() {
    CollectionModel model = collectionService.getCollectionWithPagination(publicSlug, 0, 10);

    assertThat(membershipsOnFirstImage(model))
        .extracting(Records.ChildCollection::slug)
        .containsExactly(publicSlug);
  }

  @Test
  void publicReadDoesNotLeakThePasswordProtectedOrUnlistedSlug() {
    CollectionModel model = collectionService.getCollectionWithPagination(publicSlug, 0, 10);

    assertThat(membershipsOnFirstImage(model))
        .extracting(Records.ChildCollection::slug)
        .doesNotContain(listedPasswordSlug, unlistedSlug, invisibleMembershipSlug);
  }

  @Test
  void adminReadStillCarriesEveryMembership() {
    CollectionModel model =
        collectionService.findBySlug(publicSlug).orElseThrow(() -> new AssertionError("no model"));

    assertThat(membershipsOnFirstImage(model))
        .extracting(Records.ChildCollection::slug)
        .containsExactlyInAnyOrder(
            publicSlug, listedPasswordSlug, unlistedSlug, invisibleMembershipSlug);
  }
}
