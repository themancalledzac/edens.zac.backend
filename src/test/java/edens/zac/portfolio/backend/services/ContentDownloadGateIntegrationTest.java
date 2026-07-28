package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * S1: the per-image download gate must see EVERY password-protected parent of an image, not the
 * arbitrary first row of an unordered join. Runs against real Postgres because the join query is a
 * hand-written string; a mocked JdbcTemplate cannot catch a malformed ORDER BY clause.
 *
 * <p>Rows seeded here live in the SHARED singleton container (only auth tables are truncated), so
 * every slug carries an s1- prefix and assertions never use exact global counts.
 */
class ContentDownloadGateIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private ContentService contentService;
  @Autowired private CollectionRepository collectionRepository;
  @Autowired private JdbcTemplate jdbc;

  /**
   * A bare IMAGE content row. The download gate joins collection_content only, never content_image,
   * so no child row is needed.
   */
  private Long seedImageContent() {
    return jdbc.queryForObject(
        "INSERT INTO content (content_type, created_at, updated_at)"
            + " VALUES ('IMAGE', NOW(), NOW()) RETURNING id",
        Long.class);
  }

  /**
   * A full IMAGE row (content + content_image). The collection-download gate resolves the images
   * the ZIP would serve through {@code findImagesForCollection}, which joins content_image, so the
   * bare content row above is not enough here.
   */
  private Long seedImage(String title) {
    Long contentId = seedImageContent();
    jdbc.update(
        "INSERT INTO content_image (id, title, image_url_web) VALUES (?, ?, ?)",
        contentId,
        title,
        "https://cdn.example.com/Image/Web/" + title + ".webp");
    return contentId;
  }

  private Long seedCollection(String slug, String galleryPassword) {
    jdbc.update(
        "INSERT INTO collection (title, slug, visibility, is_client, is_blog,"
            + " gallery_password) VALUES (?, ?, 'LISTED', false, false, ?)",
        slug,
        slug,
        galleryPassword);
    return jdbc.queryForObject("SELECT id FROM collection WHERE slug = ?", Long.class, slug);
  }

  private void link(Long collectionId, Long contentId) {
    collectionRepository.saveContent(
        CollectionContentEntity.builder()
            .collectionId(collectionId)
            .contentId(contentId)
            .orderIndex(0)
            .visible(true)
            .build());
  }

  @Test
  void findContentByContentIdsIn_ordersByCollectionId() {
    Long contentId = seedImageContent();
    Long lower = seedCollection("s1-order-lower", null);
    Long higher = seedCollection("s1-order-higher", null);
    // Insert the HIGHER collection id first so insertion order and id order disagree.
    link(higher, contentId);
    link(lower, contentId);

    assertThat(collectionRepository.findContentByContentIdsIn(List.of(contentId)))
        .extracting(CollectionContentEntity::getCollectionId)
        .containsExactly(lower, higher);
  }

  @Test
  void findProtectedCollectionsForImage_findsGalleryWhenThePublicWrapperSortsFirst() {
    // The exact S1 exposure: image in protected 'smith-wedding-full' AND public 'weddings'.
    // The public wrapper has the lower id, so it is the row an unordered LIMIT-1 style read wins.
    Long contentId = seedImageContent();
    Long publicWrapper = seedCollection("s1-public-first", null);
    Long protectedGallery = seedCollection("s1-protected-second", "sunshine");
    link(publicWrapper, contentId);
    link(protectedGallery, contentId);

    assertThat(contentService.findProtectedCollectionsForImage(contentId))
        .extracting(CollectionEntity::getId)
        .containsExactly(protectedGallery);
  }

  @Test
  void findProtectedCollectionsForImage_findsGalleryWhenTheGallerySortsFirst() {
    // Same image, opposite id order: the answer must not depend on which parent sorts first.
    Long contentId = seedImageContent();
    Long protectedGallery = seedCollection("s1-protected-first", "sunshine");
    Long publicWrapper = seedCollection("s1-public-second", null);
    link(protectedGallery, contentId);
    link(publicWrapper, contentId);

    assertThat(contentService.findProtectedCollectionsForImage(contentId))
        .extracting(CollectionEntity::getId)
        .containsExactly(protectedGallery);
  }

  @Test
  void findProtectedCollectionsForImage_returnsEveryProtectedParent() {
    Long contentId = seedImageContent();
    Long galleryOne = seedCollection("s1-protected-one", "alpha");
    Long galleryTwo = seedCollection("s1-protected-two", "bravo");
    link(galleryOne, contentId);
    link(galleryTwo, contentId);

    assertThat(contentService.findProtectedCollectionsForImage(contentId))
        .extracting(CollectionEntity::getId)
        .containsExactlyInAnyOrder(galleryOne, galleryTwo);
  }

  @Test
  void findProtectedCollectionsForImage_returnsEmptyWhenNoParentIsProtected() {
    Long contentId = seedImageContent();
    link(seedCollection("s1-open-one", null), contentId);
    link(seedCollection("s1-open-two", null), contentId);

    assertThat(contentService.findProtectedCollectionsForImage(contentId)).isEmpty();
  }

  @Test
  void findProtectedCollectionsForImage_returnsEmptyForAnOrphanImage() {
    Long contentId = seedImageContent();

    assertThat(contentService.findProtectedCollectionsForImage(contentId)).isEmpty();
  }

  // --- The ZIP sibling of S1 ---------------------------------------------------

  @Test
  void collectionDownloadGate_seesTheGalleryThatAlsoOwnsThePublicCollectionsImage() {
    // Request arrives for the PUBLIC wrapper, which carries no password of its own. The image it
    // serves also lives in a protected gallery, so the gate must surface that gallery.
    Long imageId = seedImage("s1zip-shared");
    Long publicWrapper = seedCollection("s1zip-public", null);
    Long protectedGallery = seedCollection("s1zip-gallery", "sunshine");
    link(publicWrapper, imageId);
    link(protectedGallery, imageId);

    assertThat(
            contentService.findProtectedCollectionsForCollectionDownload(publicWrapper, List.of()))
        .extracting(CollectionEntity::getId)
        .containsExactly(protectedGallery);
  }

  @Test
  void collectionDownloadGate_subsetNarrowsToTheRequestedImagesGates() {
    // ?imageIds=<open image only> must not drag in the gallery that gates a DIFFERENT image of
    // the same wrapper -- fail-closed, but only on what the request actually asks for.
    Long gatedImage = seedImage("s1zip-subset-gated");
    Long openImage = seedImage("s1zip-subset-open");
    Long publicWrapper = seedCollection("s1zip-subset-public", null);
    Long protectedGallery = seedCollection("s1zip-subset-gallery", "sunshine");
    link(publicWrapper, gatedImage);
    link(publicWrapper, openImage);
    link(protectedGallery, gatedImage);

    assertThat(
            contentService.findProtectedCollectionsForCollectionDownload(
                publicWrapper, List.of(gatedImage)))
        .extracting(CollectionEntity::getId)
        .containsExactly(protectedGallery);
    assertThat(
            contentService.findProtectedCollectionsForCollectionDownload(
                publicWrapper, List.of(openImage)))
        .isEmpty();
  }

  @Test
  void collectionDownloadGate_returnsEmptyWhenNothingServedIsGated() {
    Long imageId = seedImage("s1zip-open");
    Long publicWrapper = seedCollection("s1zip-open-wrapper", null);
    link(publicWrapper, imageId);

    assertThat(contentService.findProtectedCollectionsForCollectionDownload(publicWrapper, null))
        .isEmpty();
  }
}
