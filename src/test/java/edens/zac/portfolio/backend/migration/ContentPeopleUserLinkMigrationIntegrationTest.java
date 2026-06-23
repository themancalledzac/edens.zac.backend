package edens.zac.portfolio.backend.migration;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies V31 adds the nullable {@code content_people.user_id} FK and its partial unique index.
 */
class ContentPeopleUserLinkMigrationIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private JdbcTemplate jdbc;

  @Test
  void userIdColumnExistsAndIsNullable() {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns "
                + "WHERE table_name = 'content_people' AND column_name = 'user_id' "
                + "AND is_nullable = 'YES'",
            Integer.class);
    assertThat(count).isEqualTo(1);
  }

  @Test
  void partialUniqueIndexOnUserIdExists() {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_indexes "
                + "WHERE tablename = 'content_people' AND indexname = 'uq_content_people_user_id'",
            Integer.class);
    assertThat(count).isEqualTo(1);
  }
}
