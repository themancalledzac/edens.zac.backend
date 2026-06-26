package edens.zac.portfolio.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifies V35 merged content_people into `users` and re-pointed the tag joins. */
class IdentityMergeMigrationIntegrationTest extends AbstractPostgresIntegrationTest {

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
  void usersReplacesAppUserAndContentPeopleIsGone() {
    assertThat(tableExists("users")).isTrue();
    assertThat(tableExists("app_user")).isFalse();
    assertThat(tableExists("content_people")).isFalse();
    assertThat(columnExists("users", "name")).isTrue();
    assertThat(columnExists("users", "slug")).isFalse();
  }

  @Test
  void tagJoinsReferenceUsersAndAPersonResolvesById() {
    // Seed a tagged person via the new shape: a PERSON row + a content_image_people row.
    jdbc.update(
        "INSERT INTO users (name, webauthn_user_handle, status) VALUES ('Tagged Tom', gen_random_uuid(), 'PERSON')");
    Long userId = jdbc.queryForObject("SELECT id FROM users WHERE name='Tagged Tom'", Long.class);
    // Minimal content row (JOINED inheritance: content_image.id == content.id).
    jdbc.update("INSERT INTO content (content_type) VALUES ('IMAGE')");
    Long contentId = jdbc.queryForObject("SELECT max(id) FROM content", Long.class);
    jdbc.update(
        "INSERT INTO content_image (id, title, image_url_web) VALUES (?, 'x', 'http://x')", contentId);
    jdbc.update(
        "INSERT INTO content_image_people (content_id, person_id) VALUES (?, ?)", contentId, userId);

    Long resolved =
        jdbc.queryForObject(
            "SELECT u.id FROM content_image_people cip JOIN users u ON u.id = cip.person_id WHERE cip.content_id=?",
            Long.class,
            contentId);
    assertThat(resolved).isEqualTo(userId);
  }
}
