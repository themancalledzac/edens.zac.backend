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
        "INSERT INTO collection (title, slug, type, visibility) "
            + "VALUES (?, ?, 'CLIENT_GALLERY', 'UNLISTED') RETURNING id",
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

  @Test
  void imageInUnlistedCollectionIsVisibleWhenAccessComesOnlyFromARole() {
    Long userId = seedUser("granted");
    Long imageId = seedImage();
    Long collectionId = seedUnlistedCollection();
    addVisibleMembership(collectionId, imageId);

    // Access is granted ONLY through a role -> role_member + role_collection. No user_collection
    // row.
    Long roleId = roleRepository.createRole("contentvis role", null);
    roleRepository.addMember(roleId, userId, null);
    roleRepository.setCollectionGrant(roleId, collectionId, AccessLevel.GENERAL, null);

    assertThat(contentRepository.isImageVisibleToUser(imageId, userId)).isTrue();
  }

  @Test
  void imageInUnlistedCollectionIsHiddenWhenNoRoleGrantsIt() {
    Long userId = seedUser("ungranted");
    Long imageId = seedImage();
    Long collectionId = seedUnlistedCollection();
    addVisibleMembership(collectionId, imageId);

    // A role exists but does NOT grant this collection, and the user is not otherwise granted.
    Long roleId = roleRepository.createRole("contentvis empty role", null);
    roleRepository.addMember(roleId, userId, null);

    assertThat(contentRepository.isImageVisibleToUser(imageId, userId)).isFalse();
  }
}
