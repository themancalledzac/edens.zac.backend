package edens.zac.portfolio.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies the V38 Flyway migration applied: the {@code users} table has a nullable {@code
 * description} column capped at 500 characters. Extends the Testcontainers base so the full V1..V38
 * chain runs on context start.
 */
class UserDescriptionMigrationIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private JdbcTemplate jdbc;

  @Test
  void usersTableHasDescriptionColumn() {
    Integer columnCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_name = 'users' AND column_name = 'description'",
            Integer.class);
    assertThat(columnCount).isEqualTo(1);
  }

  @Test
  void descriptionColumnIsNullableWithCharacterLimit() {
    // Check data_type and character_maximum_length
    String dataType =
        jdbc.queryForObject(
            "SELECT data_type FROM information_schema.columns "
                + "WHERE table_name = 'users' AND column_name = 'description'",
            String.class);
    assertThat(dataType).isEqualTo("character varying");

    Integer charMaxLength =
        jdbc.queryForObject(
            "SELECT character_maximum_length FROM information_schema.columns "
                + "WHERE table_name = 'users' AND column_name = 'description'",
            Integer.class);
    assertThat(charMaxLength).isEqualTo(500);

    // Confirm nullable (is_nullable = 'YES')
    String isNullable =
        jdbc.queryForObject(
            "SELECT is_nullable FROM information_schema.columns "
                + "WHERE table_name = 'users' AND column_name = 'description'",
            String.class);
    assertThat(isNullable).isEqualTo("YES");
  }

  @Test
  void descriptionRoundTripsForNewUser() {
    Long userId =
        jdbc.queryForObject(
            "INSERT INTO users (name, email, webauthn_user_handle, status) "
                + "VALUES (?, ?, gen_random_uuid(), 'ACTIVE') RETURNING id",
            Long.class,
            "Desc Migration Test",
            "desc-migration@example.com");

    // On insert description is NULL by default.
    String initial =
        jdbc.queryForObject("SELECT description FROM users WHERE id = ?", String.class, userId);
    assertThat(initial).isNull();

    jdbc.update("UPDATE users SET description = ? WHERE id = ?", "Hello world.", userId);
    String updated =
        jdbc.queryForObject("SELECT description FROM users WHERE id = ?", String.class, userId);
    assertThat(updated).isEqualTo("Hello world.");
  }
}
