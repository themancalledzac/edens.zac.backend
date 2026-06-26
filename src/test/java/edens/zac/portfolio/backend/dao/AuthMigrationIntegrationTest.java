package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AuthMigrationIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  private boolean tableExists(String table) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_name = ?",
            Integer.class,
            table);
    return count != null && count > 0;
  }

  private boolean columnExists(String table, String column) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
            Integer.class,
            table,
            column);
    return count != null && count > 0;
  }

  // V29 created `app_user`; V35 renamed it to `users` (the content_people identity merge). Flyway
  // runs through the latest migration here, so these assert the post-merge `users` shape.
  @Test
  void v29CreatesThreeAuthTables() {
    assertThat(tableExists("users")).isTrue();
    assertThat(tableExists("user_session")).isTrue();
    assertThat(tableExists("gallery_access")).isTrue();
  }

  @Test
  void appUserHasWebauthnHandleAndUniqueEmail() {
    assertThat(columnExists("users", "webauthn_user_handle")).isTrue();
    assertThat(columnExists("users", "password_hash")).isTrue();

    // email UNIQUE is enforced: a duplicate insert must throw.
    jdbcTemplate.update(
        "INSERT INTO users (name, email, role, webauthn_user_handle, status) "
            + "VALUES ('Dup', 'dup@example.com', 'ADMIN', gen_random_uuid(), 'ACTIVE')");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO users (name, email, role, webauthn_user_handle, status) "
                        + "VALUES ('Dup2', 'dup@example.com', 'CLIENT', gen_random_uuid(), 'ACTIVE')"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void userSessionForeignKeyCascadesOnUserDelete() {
    Long userId =
        jdbcTemplate.queryForObject(
            "INSERT INTO users (name, email, role, webauthn_user_handle, status) "
                + "VALUES ('Cascade', 'cascade@example.com', 'ADMIN', gen_random_uuid(), 'ACTIVE') RETURNING id",
            Long.class);
    jdbcTemplate.update(
        "INSERT INTO user_session (user_id, token_hash, expires_at) "
            + "VALUES (?, 'hash-1', now() + interval '1 day')",
        userId);

    jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);

    Integer remaining =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_session WHERE user_id = ?", Integer.class, userId);
    assertThat(remaining).isZero();
  }
}
