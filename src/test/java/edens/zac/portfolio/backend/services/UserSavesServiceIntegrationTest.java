package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.RoleRepository;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.types.AccessLevel;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Exercises {@link UserSavesService} end-to-end against the real schema: seeds a user + saved
 * images and asserts full {@link ContentModels.Image} models come back newest-saved first with real
 * fields populated, and that the B4 visibility gate blocks saving (and reading) images the caller
 * cannot see.
 */
class UserSavesServiceIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private UserSavesService userSavesService;
  @Autowired private RoleRepository roleRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private Long seedUser(String email) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO users (name, email, webauthn_user_handle, status) "
            + "VALUES (?, ?, gen_random_uuid(), 'ACTIVE') RETURNING id",
        Long.class,
        email,
        email);
  }

  private Long seedImage(String title, String webUrl) {
    Long imageId =
        jdbcTemplate.queryForObject(
            "INSERT INTO content (content_type) VALUES ('IMAGE') RETURNING id", Long.class);
    jdbcTemplate.update(
        "INSERT INTO content_image (id, title, image_url_web) VALUES (?, ?, ?)",
        imageId,
        title,
        webUrl);
    return imageId;
  }

  /** Seed a collection with the given visibility and return its id. */
  private Long seedCollection(CollectionVisibility visibility) {
    String slug = "coll-" + UUID.randomUUID();
    return jdbcTemplate.queryForObject(
        "INSERT INTO collection (title, slug, type, visibility) "
            + "VALUES (?, ?, 'BLOG', ?) RETURNING id",
        Long.class,
        slug,
        slug,
        visibility.name());
  }

  /** Add {@code imageId} to {@code collectionId} with the given per-membership visibility flag. */
  private void addMembership(Long collectionId, Long imageId, boolean visible) {
    jdbcTemplate.update(
        "INSERT INTO collection_content (collection_id, content_id, visible) VALUES (?, ?, ?)",
        collectionId,
        imageId,
        visible);
  }

  /**
   * Grant {@code userId} GENERAL view access on {@code collectionId} through a role (the current
   * mechanism): a fresh role the user joins, carrying a GENERAL grant on the collection. Access no
   * longer flows through {@code user_collection}, which V45 froze.
   */
  private void grantMembership(Long userId, Long collectionId) {
    Long roleId = roleRepository.createRole("saves-grant-" + UUID.randomUUID(), null);
    roleRepository.addMember(roleId, userId, null);
    roleRepository.setCollectionGrant(roleId, collectionId, AccessLevel.GENERAL, null);
  }

  /** Seed an image that is publicly visible (in a LISTED collection, membership visible). */
  private Long seedVisibleImage(String title, String webUrl) {
    Long imageId = seedImage(title, webUrl);
    Long listed = seedCollection(CollectionVisibility.LISTED);
    addMembership(listed, imageId, true);
    return imageId;
  }

  private void save(Long userId, Long imageId, Instant createdAt) {
    jdbcTemplate.update(
        "INSERT INTO user_saved_image (user_id, image_id, created_at) VALUES (?, ?, ?)",
        userId,
        imageId,
        Timestamp.from(createdAt));
  }

  @Test
  void listSavedImagesReturnsFullModelsNewestFirst() {
    Long userId = seedUser("saves-img-" + UUID.randomUUID() + "@example.com");
    String olderUrl = "https://cdn.example.com/" + UUID.randomUUID() + ".jpg";
    String newerUrl = "https://cdn.example.com/" + UUID.randomUUID() + ".jpg";
    Long olderImage = seedVisibleImage("Older", olderUrl);
    Long newerImage = seedVisibleImage("Newer", newerUrl);

    Instant now = Instant.now();
    save(userId, olderImage, now.minusSeconds(60));
    save(userId, newerImage, now);

    List<ContentModels.Image> images = userSavesService.listSavedImages(userId);

    assertThat(images).hasSize(2);
    assertThat(images.get(0).id()).isEqualTo(newerImage);
    assertThat(images.get(0).title()).isEqualTo("Newer");
    assertThat(images.get(0).imageUrl()).isEqualTo(newerUrl);
    assertThat(images.get(1).id()).isEqualTo(olderImage);
    assertThat(images.get(1).title()).isEqualTo("Older");
    assertThat(images.get(1).imageUrl()).isEqualTo(olderUrl);
  }

  @Test
  void listSavedImagesIsEmptyWhenNoneSaved() {
    Long userId = seedUser("saves-img-empty-" + UUID.randomUUID() + "@example.com");

    assertThat(userSavesService.listSavedImages(userId)).isEmpty();
  }

  @Test
  void savesAreIsolatedPerUser_userAneverSeesUserBsaves() {
    Long userA = seedUser("saves-a-" + UUID.randomUUID() + "@example.com");
    Long userB = seedUser("saves-b-" + UUID.randomUUID() + "@example.com");
    Long imageA =
        seedVisibleImage("A's image", "https://cdn.example.com/" + UUID.randomUUID() + ".jpg");
    Long imageB =
        seedVisibleImage("B's image", "https://cdn.example.com/" + UUID.randomUUID() + ".jpg");

    userSavesService.add(userA, imageA);
    userSavesService.add(userB, imageB);

    // User A sees only their own save; user B's save never leaks into A's list.
    assertThat(userSavesService.listSavedImageIds(userA)).containsExactly(imageA);
    assertThat(userSavesService.listSavedImageIds(userA)).doesNotContain(imageB);
    assertThat(userSavesService.listSavedImages(userA))
        .extracting(ContentModels.Image::id)
        .containsExactly(imageA);

    // And symmetrically for user B.
    assertThat(userSavesService.listSavedImageIds(userB)).containsExactly(imageB);
    assertThat(userSavesService.listSavedImageIds(userB)).doesNotContain(imageA);
  }

  // ---- B4 visibility gate ----------------------------------------------------

  @Test
  void addAllowsImageInListedCollection() {
    Long userId = seedUser("saves-listed-" + UUID.randomUUID() + "@example.com");
    Long imageId =
        seedVisibleImage("Listed", "https://cdn.example.com/" + UUID.randomUUID() + ".jpg");

    userSavesService.add(userId, imageId);

    assertThat(userSavesService.listSavedImageIds(userId)).containsExactly(imageId);
  }

  @Test
  void addBlocksImageOnlyInHiddenCollection() {
    Long userId = seedUser("saves-hidden-" + UUID.randomUUID() + "@example.com");
    Long imageId = seedImage("Hidden", "https://cdn.example.com/" + UUID.randomUUID() + ".jpg");
    Long hidden = seedCollection(CollectionVisibility.HIDDEN);
    addMembership(hidden, imageId, true);

    assertThatThrownBy(() -> userSavesService.add(userId, imageId))
        .isInstanceOf(ResourceNotFoundException.class);

    assertThat(userSavesService.listSavedImageIds(userId)).isEmpty();
  }

  @Test
  void addBlocksImageOnlyInUnlistedCollection() {
    // Policy default: UNLISTED-only images are NOT saveable (LISTED or explicit membership only).
    Long userId = seedUser("saves-unlisted-" + UUID.randomUUID() + "@example.com");
    Long imageId = seedImage("Unlisted", "https://cdn.example.com/" + UUID.randomUUID() + ".jpg");
    Long unlisted = seedCollection(CollectionVisibility.UNLISTED);
    addMembership(unlisted, imageId, true);

    assertThatThrownBy(() -> userSavesService.add(userId, imageId))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void addAllowsImageInCollectionUserHasExplicitAccessTo() {
    Long userId = seedUser("saves-grant-" + UUID.randomUUID() + "@example.com");
    Long imageId = seedImage("Gated", "https://cdn.example.com/" + UUID.randomUUID() + ".jpg");
    Long gated = seedCollection(CollectionVisibility.UNLISTED);
    addMembership(gated, imageId, true);
    grantMembership(userId, gated);

    userSavesService.add(userId, imageId);

    assertThat(userSavesService.listSavedImageIds(userId)).containsExactly(imageId);
  }

  @Test
  void addBlocksImageWhoseOnlyMembershipIsSoftRemoved() {
    // cc.visible = false must not count as a visible membership even in a LISTED collection.
    Long userId = seedUser("saves-softremoved-" + UUID.randomUUID() + "@example.com");
    Long imageId =
        seedImage("SoftRemoved", "https://cdn.example.com/" + UUID.randomUUID() + ".jpg");
    Long listed = seedCollection(CollectionVisibility.LISTED);
    addMembership(listed, imageId, false);

    assertThatThrownBy(() -> userSavesService.add(userId, imageId))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void addBlocksNonexistentImage() {
    Long userId = seedUser("saves-missing-" + UUID.randomUUID() + "@example.com");

    assertThatThrownBy(() -> userSavesService.add(userId, 999_999_999L))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void savedImageDropsFromListWhenLaterHidden() {
    // Defense-in-depth: an image saved while visible must fall out of the list once its only
    // membership is soft-removed (owner hides it).
    Long userId = seedUser("saves-hide-later-" + UUID.randomUUID() + "@example.com");
    Long imageId =
        seedImage("HiddenLater", "https://cdn.example.com/" + UUID.randomUUID() + ".jpg");
    Long listed = seedCollection(CollectionVisibility.LISTED);
    addMembership(listed, imageId, true);

    userSavesService.add(userId, imageId);
    assertThat(userSavesService.listSavedImageIds(userId)).containsExactly(imageId);
    assertThat(userSavesService.listSavedImages(userId))
        .extracting(ContentModels.Image::id)
        .containsExactly(imageId);

    // Owner hides the image (soft-remove the membership).
    jdbcTemplate.update(
        "UPDATE collection_content SET visible = false WHERE collection_id = ? AND content_id = ?",
        listed,
        imageId);

    // The raw save row still exists, but the full-model read filters it out.
    assertThat(userSavesService.listSavedImageIds(userId)).containsExactly(imageId);
    assertThat(userSavesService.listSavedImages(userId)).isEmpty();
  }
}
