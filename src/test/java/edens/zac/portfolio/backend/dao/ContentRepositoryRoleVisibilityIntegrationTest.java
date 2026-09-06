package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.types.AccessLevel;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves {@link ContentRepository#isImageVisibleToUser} resolves its access predicate through the
 * role tables ({@code role_member} JOIN {@code role_collection}), not through the frozen {@code
 * user_collection} table. Access is granted ONLY via a role here — no {@code user_collection} row
 * is ever inserted — so a passing test confirms the seam was fully re-pointed onto roles.
 *
 * <p>Also pins S-36: LISTED-plus-password is a supported state, so the LISTED arm of that query and
 * of {@link ContentRepository#findSavedImagesByUserId} carries a {@code gallery_password IS NULL}
 * term while the role-grant arm does not. The paired granted/ungranted cases below are what keep
 * the password term from being widened onto the grant arm, which would lock a named client out of
 * their own gallery.
 *
 * <p>Slugs are prefixed {@code contentvis-} because the shared Testcontainers Postgres does NOT
 * truncate {@code collection} between test classes; reusing another class's slug would collide.
 */
class ContentRepositoryRoleVisibilityIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private ContentRepository contentRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private Long seedUser(String label) {
    String email = "contentvis-" + label + "-" + UUID.randomUUID() + "@example.com";
    return jdbcTemplate.queryForObject(
        "INSERT INTO users (name, email, webauthn_user_handle, status) "
            + "VALUES (?, ?, gen_random_uuid(), 'ACTIVE') RETURNING id",
        Long.class,
        email,
        email);
  }

  private Long seedImage() {
    Long imageId =
        jdbcTemplate.queryForObject(
            "INSERT INTO content (content_type) VALUES ('IMAGE') RETURNING id", Long.class);
    jdbcTemplate.update(
        "INSERT INTO content_image (id, title, image_url_web) VALUES (?, ?, ?)",
        imageId,
        "img",
        "https://cdn.example.com/contentvis-" + UUID.randomUUID() + ".jpg");
    return imageId;
  }

  /** An UNLISTED collection (invisible without an explicit grant), slug-prefixed for this class. */
  private Long seedUnlistedCollection() {
    String slug = "contentvis-" + UUID.randomUUID();
    return jdbcTemplate.queryForObject(
        "INSERT INTO collection (title, slug, visibility) "
            + "VALUES (?, ?, 'UNLISTED') RETURNING id",
        Long.class,
        slug,
        slug);
  }

  /** A LISTED collection carrying a gallery password — discoverable as a tile, content gated. */
  private Long seedListedProtectedCollection() {
    String slug = "contentvis-" + UUID.randomUUID();
    return jdbcTemplate.queryForObject(
        "INSERT INTO collection (title, slug, visibility, gallery_password) "
            + "VALUES (?, ?, 'LISTED', 'secret') RETURNING id",
        Long.class,
        slug,
        slug);
  }

  /** A LISTED collection with no password — the ordinary public case, used as the control. */
  private Long seedListedOpenCollection() {
    String slug = "contentvis-" + UUID.randomUUID();
    return jdbcTemplate.queryForObject(
        "INSERT INTO collection (title, slug, visibility) VALUES (?, ?, 'LISTED') RETURNING id",
        Long.class,
        slug,
        slug);
  }

  private void addVisibleMembership(Long collectionId, Long imageId) {
    jdbcTemplate.update(
        "INSERT INTO collection_content (collection_id, content_id, visible) VALUES (?, ?, true)",
        collectionId,
        imageId);
  }

  private void grantViaRole(String label, Long userId, Long collectionId) {
    Long roleId = roleRepository.createRole("contentvis " + label + " " + UUID.randomUUID(), null);
    roleRepository.addMember(roleId, userId, null);
    roleRepository.setCollectionGrant(roleId, collectionId, AccessLevel.GENERAL, null);
  }

  private void save(Long userId, Long imageId) {
    jdbcTemplate.update(
        "INSERT INTO user_saved_image (user_id, image_id) VALUES (?, ?)", userId, imageId);
  }

  @Test
  void imageInUnlistedCollectionIsVisibleWhenAccessComesOnlyFromARole() {
    Long userId = seedUser("granted");
    Long imageId = seedImage();
    Long collectionId = seedUnlistedCollection();
    addVisibleMembership(collectionId, imageId);

    grantViaRole("role", userId, collectionId);

    assertThat(contentRepository.isImageVisibleToUser(imageId, userId)).isTrue();
  }

  @Test
  void imageInUnlistedCollectionIsHiddenWhenNoRoleGrantsIt() {
    Long userId = seedUser("ungranted");
    Long imageId = seedImage();
    Long collectionId = seedUnlistedCollection();
    addVisibleMembership(collectionId, imageId);

    Long roleId = roleRepository.createRole("contentvis empty role", null);
    roleRepository.addMember(roleId, userId, null);

    assertThat(contentRepository.isImageVisibleToUser(imageId, userId)).isFalse();
  }

  @Test
  void imageInListedPasswordProtectedCollectionIsHiddenWithoutAGrant() {
    Long userId = seedUser("pw-ungranted");
    Long imageId = seedImage();
    addVisibleMembership(seedListedProtectedCollection(), imageId);

    assertThat(contentRepository.isImageVisibleToUser(imageId, userId)).isFalse();
  }

  @Test
  void imageInListedPasswordProtectedCollectionStaysVisibleToAGrantHolder() {
    Long userId = seedUser("pw-granted");
    Long imageId = seedImage();
    Long collectionId = seedListedProtectedCollection();
    addVisibleMembership(collectionId, imageId);

    grantViaRole("pw role", userId, collectionId);

    assertThat(contentRepository.isImageVisibleToUser(imageId, userId)).isTrue();
  }

  @Test
  void savedImagesDropAnImageHeldOnlyByAListedPasswordProtectedCollection() {
    Long userId = seedUser("saves-pw");
    Long gatedImageId = seedImage();
    Long openImageId = seedImage();
    addVisibleMembership(seedListedProtectedCollection(), gatedImageId);
    addVisibleMembership(seedListedOpenCollection(), openImageId);
    save(userId, gatedImageId);
    save(userId, openImageId);

    assertThat(contentRepository.findSavedImagesByUserId(userId))
        .extracting(e -> e.getId())
        .containsExactly(openImageId);
  }

  @Test
  void savedImagesKeepAPasswordProtectedImageTheUserHoldsAGrantFor() {
    Long userId = seedUser("saves-pw-granted");
    Long imageId = seedImage();
    Long collectionId = seedListedProtectedCollection();
    addVisibleMembership(collectionId, imageId);
    save(userId, imageId);

    grantViaRole("saves pw role", userId, collectionId);

    assertThat(contentRepository.findSavedImagesByUserId(userId))
        .extracting(e -> e.getId())
        .containsExactly(imageId);
  }
}
