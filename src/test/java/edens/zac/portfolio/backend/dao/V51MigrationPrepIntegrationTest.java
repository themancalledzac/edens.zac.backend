package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Exercises V51 against actual data, which no other test does: the shared harness migrates an empty
 * collection table, so V51's backfill UPDATE and its tag DELETEs would never touch a single row.
 * This test owns a dedicated container, migrates to V49, seeds one collection per legacy type plus
 * the operator tag V50 must reuse, migrates to V50 so the real label-tag backfill runs, seeds a
 * converted tag squatting the canonical slug, then migrates to latest and asserts V51's outcome.
 *
 * <p>V51's DML is deliberately NOT re-run inside the shared singleton container: its global
 * content_per_page UPDATE would rewrite the rows sibling fixtures depend on, producing
 * order-dependent failures.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V51MigrationPrepIntegrationTest {

  private static final List<String> LEGACY_TYPES =
      List.of("CLIENT_GALLERY", "PORTFOLIO", "ART_GALLERY", "HOME", "PARENT", "MISC");

  private PostgreSQLContainer<?> postgres;
  private JdbcTemplate jdbc;

  @BeforeAll
  void migrateSeededDatabase() {
    postgres =
        new PostgreSQLContainer<>("postgres:16-alpine").withInitScript("db/test-base-schema.sql");
    postgres.start();

    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setUrl(postgres.getJdbcUrl());
    dataSource.setUsername(postgres.getUsername());
    dataSource.setPassword(postgres.getPassword());
    jdbc = new JdbcTemplate(dataSource);

    // Stop just short of V50 so the seed rows exist as pre-V50 data would.
    migrateTo(dataSource, "49");

    // content_per_page is omitted, so it lands NULL -- exactly what the parent-type create path
    // writes (CollectionProcessingUtil.java:584-587).
    for (String type : LEGACY_TYPES) {
      jdbc.update(
          "INSERT INTO collection (title, slug, type, visibility, display_mode) "
              + "VALUES (?, ?, ?, 'LISTED', NULL)",
          type,
          slugOf(type),
          type);
    }
    // One row that already has a content_per_page, to prove the backfill does not overwrite it.
    jdbc.update(
        "INSERT INTO collection (title, slug, type, visibility, display_mode, content_per_page) "
            + "VALUES ('BLOG', 'blog', 'BLOG', 'LISTED', NULL, 50)");

    // A pre-existing tag holding a label NAME under an operator-chosen slug -- the tag V50
    // REUSED rather than created. V51 must detach its collections and leave the tag itself.
    jdbc.update(
        "INSERT INTO tag (tag_name, slug, created_at) VALUES (?, ?, NOW())",
        "Portfolio",
        "portfolio-work");
    // 'Art Gallery' is deliberately NOT seeded, so V50 creates it itself under the canonical
    // slug -- that is the shape V51 must delete outright.

    migrateTo(dataSource, "50");

    // A tag the operator has since CONVERTED into a collection, squatting the canonical
    // 'portfolio' slug. V39's converted_collection_id must exempt it from both V51 DELETEs.
    Long miscId = jdbc.queryForObject("SELECT id FROM collection WHERE slug = 'misc'", Long.class);
    jdbc.update(
        "INSERT INTO tag (tag_name, slug, created_at, converted_collection_id) "
            + "VALUES ('Portfolio Prints', 'portfolio', NOW(), ?)",
        miscId);
    Long convertedTagId =
        jdbc.queryForObject("SELECT id FROM tag WHERE slug = 'portfolio'", Long.class);
    jdbc.update(
        "INSERT INTO collection_tags (collection_id, tag_id) VALUES (?, ?)",
        miscId,
        convertedTagId);

    migrateTo(dataSource, "latest");
  }

  private static String slugOf(String type) {
    return type.toLowerCase().replace('_', '-');
  }

  private void migrateTo(DataSource dataSource, String target) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .baselineVersion("0")
        .locations("classpath:db/migration")
        .target(target)
        .load()
        .migrate();
  }

  @AfterAll
  void stopContainer() {
    if (postgres != null) {
      postgres.stop();
    }
  }

  @Test
  @DisplayName("V51 is recorded as applied -- proves it contains no aborting DROP INDEX")
  void v51IsRecordedAsSuccessfulInFlywayHistory() {
    // A bare `DROP INDEX idx_collection_type_visible_date` would abort here: V20 dropped the
    // `visible` column, which auto-dropped V9's partial index of that name.
    assertThat(
            jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '51'", Boolean.class))
        .isTrue();
  }

  @Test
  @DisplayName("collection.type becomes nullable with a MISC default")
  void typeColumnIsNullableWithMiscDefault() {
    Map<String, Object> column =
        jdbc.queryForMap(
            "SELECT is_nullable, column_default FROM information_schema.columns "
                + "WHERE table_name = 'collection' AND column_name = 'type'");
    assertThat(column).containsEntry("is_nullable", "YES");
    assertThat((String) column.get("column_default")).startsWith("'MISC'");
  }

  @Test
  @DisplayName("an INSERT omitting type lands on MISC; an explicit NULL is accepted")
  void insertsWithoutTypeAreAccepted() {
    jdbc.update(
        "INSERT INTO collection (title, slug, visibility) "
            + "VALUES ('Omitted', 'omitted-type-probe', 'LISTED')");
    assertThat(
            jdbc.queryForObject(
                "SELECT type FROM collection WHERE slug = 'omitted-type-probe'", String.class))
        .isEqualTo("MISC");

    jdbc.update(
        "INSERT INTO collection (title, slug, type, visibility) "
            + "VALUES ('Explicit', 'null-type-probe', NULL, 'LISTED')");
    assertThat(
            jdbc.queryForObject(
                "SELECT type FROM collection WHERE slug = 'null-type-probe'", String.class))
        .isNull();
  }

  @Test
  @DisplayName("collection_type_archive snapshots one row per collection, types and flags intact")
  void archiveTableCapturesEveryLegacyType() {
    // A literal 7, not a live count of `collection`: sibling tests in this class insert probe
    // rows after the migration, and the archive is a snapshot taken at migration time.
    assertThat(jdbc.queryForObject("SELECT count(*) FROM collection_type_archive", Integer.class))
        .isEqualTo(7);
    assertThat(
            jdbc.queryForList(
                "SELECT slug FROM collection_type_archive ORDER BY slug", String.class))
        .containsExactly(
            "art-gallery", "blog", "client-gallery", "home", "misc", "parent", "portfolio");

    assertThat(archived("parent")).containsEntry("type", "PARENT");
    assertThat(archived("home")).containsEntry("type", "HOME");
    assertThat(archived("client-gallery"))
        .containsEntry("type", "CLIENT_GALLERY")
        .containsEntry("is_client", true)
        .containsEntry("is_blog", false);
    assertThat(archived("blog")).containsEntry("type", "BLOG").containsEntry("is_blog", true);
    assertThat(archived("portfolio").get("archived_at")).isNotNull();

    // The whole reason the archive has to exist: these five are mutually indistinguishable from
    // the flags alone, so U5's DROP COLUMN destroys them unless they are copied out first.
    for (String slug : List.of("portfolio", "art-gallery", "home", "parent", "misc")) {
      assertThat(archived(slug))
          .as(slug)
          .containsEntry("is_client", false)
          .containsEntry("is_blog", false);
    }
  }

  private Map<String, Object> archived(String slug) {
    return jdbc.queryForMap(
        "SELECT type, is_client, is_blog, archived_at FROM collection_type_archive WHERE slug = ?",
        slug);
  }
}
