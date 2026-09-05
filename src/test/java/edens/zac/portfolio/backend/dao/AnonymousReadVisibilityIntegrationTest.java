package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.entity.ContentEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.model.ImageSearchRequest;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Covers the three anonymous read routes that returned private client-gallery images: the image
 * search (S-29), the location page's orphan strip (S-32) and the tag view (S-34). All three are
 * anonymous, all three are shared-cacheable, and in every case the leak lived entirely in the SQL.
 *
 * <p>This runs against real Postgres because a mocked repository cannot see a missing predicate.
 * Every existing search test stubs {@code ContentRepository}, so deleting the fix would leave the
 * whole suite green.
 *
 * <p>Content carries no visibility of its own, so each case turns on the collection it is a member
 * of. Both leaking states are seeded: UNLISTED-with-password, and LISTED-with-password, which is
 * the one a visibility-only filter misses. Slugs are prefixed {@code anonvis-} because the shared
 * container does not truncate {@code collection} or {@code content} between test classes.
 */
class AnonymousReadVisibilityIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private ContentRepository contentRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private JdbcTemplate jdbc;

  private Long publicImageId;
  private Long unlistedPasswordImageId;
  private Long listedPasswordImageId;
  private Long unheldImageId;
  private Long tagId;
  private String locationName;

  private Long seedCollection(String visibility, String galleryPassword) {
    String slug = "anonvis-" + UUID.randomUUID();
    return jdbc.queryForObject(
        "INSERT INTO collection (title, slug, visibility, gallery_password) VALUES (?, ?, ?, ?)"
            + " RETURNING id",
        Long.class,
        slug,
        slug,
        visibility,
        galleryPassword);
  }

  private Long seedImage() {
    Long contentId =
        jdbc.queryForObject(
            "INSERT INTO content (content_type) VALUES ('IMAGE') RETURNING id", Long.class);
    jdbc.update(
        "INSERT INTO content_image (id, title, image_url_web, rating) VALUES (?, ?, ?, 5)",
        contentId,
        "anonvis image",
        "https://cdn.example.com/anonvis-" + UUID.randomUUID() + ".jpg");
    return contentId;
  }

  private void addMembership(Long collectionId, Long contentId) {
    jdbc.update(
        "INSERT INTO collection_content (collection_id, content_id, order_index, visible)"
            + " VALUES (?, ?, 0, true)",
        collectionId,
        contentId);
  }

  private Long seedImageIn(String visibility, String galleryPassword) {
    Long contentId = seedImage();
    addMembership(seedCollection(visibility, galleryPassword), contentId);
    return contentId;
  }

  private void tagAt(Long contentId) {
    jdbc.update("INSERT INTO content_tags (content_id, tag_id) VALUES (?, ?)", contentId, tagId);
    Long locationId =
        jdbc.queryForObject(
            "SELECT id FROM location WHERE location_name = ?", Long.class, locationName);
    jdbc.update(
        "INSERT INTO content_image_locations (content_id, location_id) VALUES (?, ?)",
        contentId,
        locationId);
  }

  /**
   * One image per membership state, each tagged and located so a single seed drives all three
   * routes. {@code unheldImageId} belongs to no collection at all and is therefore reachable from
   * no public surface.
   */
  @BeforeEach
  void seed() {
    String slug = "anonvis-" + UUID.randomUUID();
    jdbc.update("INSERT INTO tag (tag_name, slug) VALUES (?, ?)", slug, slug);
    tagId = jdbc.queryForObject("SELECT id FROM tag WHERE slug = ?", Long.class, slug);

    locationName = "anonvis-loc-" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO location (location_name, slug) VALUES (?, ?)", locationName, locationName);

    publicImageId = seedImageIn("LISTED", null);
    unlistedPasswordImageId = seedImageIn("UNLISTED", "hunter2");
    listedPasswordImageId = seedImageIn("LISTED", "hunter2");
    unheldImageId = seedImage();

    tagAt(publicImageId);
    tagAt(unlistedPasswordImageId);
    tagAt(listedPasswordImageId);
    tagAt(unheldImageId);
  }

  private ImageSearchRequest request(boolean publicOnly) {
    return new ImageSearchRequest(
        null, List.of(tagId), null, null, null, null, null, null, null, null, 0, 50, publicOnly);
  }

  private List<Long> idsOf(List<ContentImageEntity> images) {
    return images.stream().map(ContentImageEntity::getId).toList();
  }

  @Test
  void theAnonymousImageSearchOmitsPrivateGalleryImages() {
    assertThat(idsOf(contentRepository.searchImages(request(true), 50, 0)))
        .containsExactly(publicImageId)
        .doesNotContain(unlistedPasswordImageId, listedPasswordImageId, unheldImageId);
  }

  @Test
  void theAnonymousImageSearchCountMatchesThePage() {
    assertThat(contentRepository.countSearchImages(request(true))).isEqualTo(1L);
  }

  @Test
  void theAdminImageSearchStillReturnsEveryImage() {
    assertThat(idsOf(contentRepository.searchImages(request(false), 50, 0)))
        .containsExactlyInAnyOrder(
            publicImageId, unlistedPasswordImageId, listedPasswordImageId, unheldImageId);
    assertThat(contentRepository.countSearchImages(request(false))).isEqualTo(4L);
  }

  @Test
  void theLocationOrphanStripOmitsPrivateGalleryImages() {
    List<ContentEntity> orphans =
        contentRepository.findOrphanContentByLocationName(locationName, List.of(), 50, 0);

    assertThat(orphans)
        .extracting(ContentEntity::getId)
        .containsExactly(publicImageId)
        .doesNotContain(unlistedPasswordImageId, listedPasswordImageId, unheldImageId);
    assertThat(contentRepository.countOrphanContentByLocationName(locationName, List.of()))
        .isEqualTo(1L);
  }

  @Test
  void theTagViewOmitsAListedGalleryThatHasAPassword() {
    assertThat(tagRepository.findImageContentByTagId(tagId, List.of(CollectionVisibility.LISTED)))
        .containsExactly(publicImageId)
        .doesNotContain(listedPasswordImageId, unlistedPasswordImageId, unheldImageId);
  }

  /**
   * The local scope widens the allowed visibilities to every state, which is exactly the case a
   * visibility-only filter would let through. The password term is unconditional, so it does not.
   */
  @Test
  void theTagViewsWiderLocalScopeStillOmitsPasswordGalleries() {
    assertThat(
            tagRepository.findImageContentByTagId(
                tagId,
                List.of(
                    CollectionVisibility.LISTED,
                    CollectionVisibility.UNLISTED,
                    CollectionVisibility.HIDDEN)))
        .containsExactly(publicImageId);
  }
}
