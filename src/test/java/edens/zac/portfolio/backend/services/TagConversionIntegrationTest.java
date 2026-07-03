package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.SaveAsCollectionRequest;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Real-Postgres coverage for A3 tag-to-collection conversion: the snapshot lands real {@code
 * collection_content} rows, the new collection takes over the tag's slug so it wins resolution, and
 * a converted tag no longer renders a tag-view.
 */
class TagConversionIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TagService tagService;
  @Autowired private CollectionService collectionService;
  @Autowired private JdbcTemplate jdbc;

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

  private void tagCollection(long collectionId, long tagId) {
    jdbc.update(
        "INSERT INTO collection_tags (collection_id, tag_id) VALUES (?, ?)", collectionId, tagId);
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

  @Test
  void convert_promotesTagIntoCollectionThatOwnsSlugAndSnapshotsMembers() {
    long tag = seedTag("Sunsets", "sunsets");
    long memberCollection = seedCollection("sunset-trip", CollectionVisibility.LISTED, 5);
    tagCollection(memberCollection, tag);

    long host = seedCollection("sunset-host", CollectionVisibility.LISTED, 1);
    long image = seedImage("https://cdn/sunset.jpg");
    tagContent(image, tag);
    addMembership(host, image, true);

    CollectionRequests.UpdateResponse response =
        tagService.convertTagToCollection(
            tag,
            new SaveAsCollectionRequest(
                CollectionType.PORTFOLIO, CollectionVisibility.LISTED, null));

    Long newCollectionId = response.collection().getId();

    // (a) A real collection owns the tag's slug.
    Long slugOwnerId =
        jdbc.queryForObject("SELECT id FROM collection WHERE slug = ?", Long.class, "sunsets");
    assertThat(slugOwnerId).isEqualTo(newCollectionId);

    // (b) collection_content holds the snapshot: the member collection (via its content row) +
    // image.
    Integer contentCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM collection_content WHERE collection_id = ?",
            Integer.class,
            newCollectionId);
    assertThat(contentCount).isEqualTo(2);
    Integer imageRows =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM collection_content WHERE collection_id = ? AND content_id = ?",
            Integer.class,
            newCollectionId,
            image);
    assertThat(imageRows).isEqualTo(1);

    // (c) Resolution returns the real collection (not the synthetic tag-view).
    CollectionModel resolved = collectionService.getCollectionWithPagination("sunsets", 0, 30);
    assertThat(resolved.getId()).isEqualTo(newCollectionId);
    assertThat(resolved.getType()).isEqualTo(CollectionType.PORTFOLIO);
    assertThat(resolved.isDerived()).isFalse();

    // (d) The tag is flagged converted and no longer resolves a tag-view.
    Long convertedCollectionId =
        jdbc.queryForObject(
            "SELECT converted_collection_id FROM tag WHERE id = ?", Long.class, tag);
    assertThat(convertedCollectionId).isEqualTo(newCollectionId);

    // (e) Re-convert is rejected.
    assertThatThrownBy(() -> tagService.convertTagToCollection(tag, null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void convert_defaultScope_doesNotCopyHiddenMembers() {
    long tag = seedTag("Private", "private");

    // A HIDDEN member collection tagged directly, plus a LISTED one.
    long listedMember = seedCollection("listed-member", CollectionVisibility.LISTED, 5);
    long hiddenMember = seedCollection("hidden-member", CollectionVisibility.HIDDEN, 5);
    tagCollection(listedMember, tag);
    tagCollection(hiddenMember, tag);

    // A tagged image visible ONLY through a HIDDEN collection membership.
    long hiddenHost = seedCollection("hidden-host", CollectionVisibility.HIDDEN, 1);
    long hiddenImage = seedImage("https://cdn/hidden.jpg");
    tagContent(hiddenImage, tag);
    addMembership(hiddenHost, hiddenImage, true);

    // A tagged image visible through a LISTED collection membership.
    long listedHost = seedCollection("listed-host", CollectionVisibility.LISTED, 1);
    long listedImage = seedImage("https://cdn/listed.jpg");
    tagContent(listedImage, tag);
    addMembership(listedHost, listedImage, true);

    CollectionRequests.UpdateResponse response =
        tagService.convertTagToCollection(
            tag,
            new SaveAsCollectionRequest(
                CollectionType.PORTFOLIO, CollectionVisibility.LISTED, null));
    Long newCollectionId = response.collection().getId();

    // The HIDDEN-only image is NOT snapshotted.
    Integer hiddenImageRows =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM collection_content WHERE collection_id = ? AND content_id = ?",
            Integer.class,
            newCollectionId,
            hiddenImage);
    assertThat(hiddenImageRows).isZero();

    // The LISTED image IS snapshotted.
    Integer listedImageRows =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM collection_content WHERE collection_id = ? AND content_id = ?",
            Integer.class,
            newCollectionId,
            listedImage);
    assertThat(listedImageRows).isEqualTo(1);

    // Exactly two content rows total: 1 LISTED member-collection wrapper + 1 LISTED image. The
    // HIDDEN member collection and the HIDDEN-only image are both excluded. We assert the total
    // count (not a per-member-id lookup) because linkCollectionToParent stores the child's
    // content-wrapper id in content_id, not the child collection id.
    Integer totalRows =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM collection_content WHERE collection_id = ?",
            Integer.class,
            newCollectionId);
    assertThat(totalRows).isEqualTo(2);

    // And the LISTED member collection's content-wrapper is linked (proves the LISTED path works).
    Integer listedChildRows =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM collection_content cc
            JOIN content_collection ccn ON ccn.id = cc.content_id
            WHERE cc.collection_id = ? AND ccn.referenced_collection_id = ?
            """,
            Integer.class,
            newCollectionId,
            listedMember);
    assertThat(listedChildRows).isEqualTo(1);
  }

  @Test
  void convert_rejectedWhenRealCollectionAlreadyOwnsSlug() {
    seedCollection("already-taken", CollectionVisibility.LISTED, 1);
    long tag = seedTag("AlreadyTaken", "already-taken");
    long member = seedCollection("at-member", CollectionVisibility.LISTED, 5);
    tagCollection(member, tag);

    assertThatThrownBy(() -> tagService.convertTagToCollection(tag, null))
        .isInstanceOf(IllegalStateException.class);
  }
}
