package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies V52 against a real Postgres: {@code collection.type} is gone and the two surviving
 * discriminators are intact. Schema introspection only -- this class deliberately inserts no rows,
 * because the shared container truncates auth tables only and a stray collection row would leak
 * into every other fixture's counts.
 */
class V52DropCollectionTypeMigrationIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private JdbcTemplate jdbc;

  private Integer countCollectionColumn(String column) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM information_schema.columns "
            + "WHERE table_schema = 'public' AND table_name = 'collection' AND column_name = ?",
        Integer.class,
        column);
  }

  private String nullabilityOf(String column) {
    return jdbc.queryForObject(
        "SELECT is_nullable FROM information_schema.columns "
            + "WHERE table_schema = 'public' AND table_name = 'collection' AND column_name = ?",
        String.class,
        column);
  }

  @Test
  @DisplayName("V52 removes the collection.type column entirely")
  void collectionTypeColumnIsGone() {
    assertThat(countCollectionColumn("type")).isZero();
  }

  @Test
  @DisplayName("is_client and is_blog survive V52 and stay NOT NULL")
  void discriminatorFlagsSurviveAsNotNull() {
    assertThat(countCollectionColumn("is_client")).isEqualTo(1);
    assertThat(countCollectionColumn("is_blog")).isEqualTo(1);
    assertThat(nullabilityOf("is_client")).isEqualTo("NO");
    assertThat(nullabilityOf("is_blog")).isEqualTo("NO");
  }

  @Test
  @DisplayName("the V50 mutual-exclusion CHECK still guards the two flags")
  void mutualExclusionCheckConstraintSurvives() {
    Integer constraints =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_constraint WHERE conname = 'chk_collection_client_blog_excl'",
            Integer.class);
    assertThat(constraints).isEqualTo(1);
  }

  @Test
  @DisplayName("DROP COLUMN took idx_collection_type with it -- no DROP INDEX was needed")
  void typeIndexWasAutoDroppedByTheColumnDrop() {
    Integer indexes =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_indexes "
                + "WHERE schemaname = 'public' AND indexname = 'idx_collection_type'",
            Integer.class);
    assertThat(indexes).isZero();
  }
}
