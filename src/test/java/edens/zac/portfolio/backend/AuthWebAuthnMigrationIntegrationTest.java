package edens.zac.portfolio.backend;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies the V30 Flyway migration applied: the webauthn_credential table exists with the expected
 * columns, the credential_id unique constraint, and the FK to app_user. Extends the F1
 * Testcontainers base so the full V1..V30 chain runs on context start.
 */
class AuthWebAuthnMigrationIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private DataSource dataSource;

  @Test
  void webauthnCredentialTableExistsWithExpectedColumns() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    Integer tableCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_name = 'webauthn_credential'",
            Integer.class);
    assertThat(tableCount).isEqualTo(1);

    Integer columnCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_name = 'webauthn_credential' "
                + "AND column_name IN "
                + "('id','user_id','credential_id','public_key','sign_count','transports',"
                + "'label','created_at','last_used_at')",
            Integer.class);
    assertThat(columnCount).isEqualTo(9);
  }

  @Test
  void credentialIdHasUniqueConstraint() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    Integer uniqueCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.table_constraints "
                + "WHERE table_name = 'webauthn_credential' "
                + "AND constraint_type = 'UNIQUE'",
            Integer.class);
    assertThat(uniqueCount).isGreaterThanOrEqualTo(1);
  }

  @Test
  void deletingUserCascadesToCredentials() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    Long userId =
        jdbc.queryForObject(
            "INSERT INTO users "
                + "(name, email, role, webauthn_user_handle, status) "
                + "VALUES (?, ?, 'ADMIN', gen_random_uuid(), 'ACTIVE') RETURNING id",
            Long.class,
            "Cascade WebAuthn",
            "cascade-webauthn@example.com");
    jdbc.update(
        "INSERT INTO webauthn_credential (user_id, credential_id, public_key) "
            + "VALUES (?, ?, ?)",
        userId,
        new byte[] {1, 2, 3},
        new byte[] {4, 5, 6});

    jdbc.update("DELETE FROM users WHERE id = ?", userId);

    Integer remaining =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM webauthn_credential WHERE user_id = ?", Integer.class, userId);
    assertThat(remaining).isZero();
  }
}
