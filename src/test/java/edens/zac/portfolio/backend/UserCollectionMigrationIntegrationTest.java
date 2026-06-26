package edens.zac.portfolio.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifies V36 created user_collection + dropped gallery_access. */
class UserCollectionMigrationIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private JdbcTemplate jdbc;

  private boolean tableExists(String table) {
    Integer c =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name=?",
            Integer.class,
            table);
    return c != null && c > 0;
  }

  private boolean columnExists(String table, String column) {
    Integer c =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_name=? AND column_name=?",
            Integer.class,
            table,
            column);
    return c != null && c > 0;
  }

  @Test
  void userCollectionReplacesGalleryAccess() {
    assertThat(tableExists("user_collection")).isTrue();
    assertThat(tableExists("gallery_access")).isFalse();
  }

  @Test
  void membershipRoundTripsAndRejectsBadRole() {
    jdbc.update(
        "INSERT INTO users (name, webauthn_user_handle, status) VALUES ('Member Mary', gen_random_uuid(), 'ACTIVE')");
    Long userId = jdbc.queryForObject("SELECT id FROM users WHERE name='Member Mary'", Long.class);
    jdbc.update(
        "INSERT INTO collection (title, slug, type) VALUES ('C', 'c-slug', 'CLIENT_GALLERY')");
    Long collectionId =
        jdbc.queryForObject("SELECT id FROM collection WHERE slug='c-slug'", Long.class);

    jdbc.update(
        "INSERT INTO user_collection (user_id, collection_id, role) VALUES (?, ?, 'CLIENT')",
        userId,
        collectionId);
    String role =
        jdbc.queryForObject(
            "SELECT role FROM user_collection WHERE user_id=? AND collection_id=?",
            String.class,
            userId,
            collectionId);
    assertThat(role).isEqualTo("CLIENT");

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO user_collection (user_id, collection_id, role) VALUES (?, ?, 'admin')",
                    userId,
                    collectionId))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }
}
