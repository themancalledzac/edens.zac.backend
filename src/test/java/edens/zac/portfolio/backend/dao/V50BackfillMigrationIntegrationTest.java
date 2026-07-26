package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Exercises V50 against actual data, which no other test does: the shared harness migrates an empty
 * collection table, so the backfill UPDATEs and the tag-attachment joins are never executed against
 * a single row. This test owns a dedicated container, migrates to V49, seeds one collection per
 * legacy type plus a colliding tag, then migrates to latest and asserts the outcome.
 *
 * <p>The V50 DML is deliberately NOT re-run inside the shared singleton container: its global
 * UPDATE would rewrite the divergent-flag rows other fixtures depend on, producing order-dependent
 * failures.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V50BackfillMigrationIntegrationTest {

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

    for (String type :
        List.of("BLOG", "CLIENT_GALLERY", "PORTFOLIO", "ART_GALLERY", "HOME", "PARENT", "MISC")) {
      jdbc.update(
          "INSERT INTO collection (title, slug, type, visibility, display_mode) "
              + "VALUES (?, ?, ?, 'LISTED', NULL)",
          type,
          type.toLowerCase().replace('_', '-'),
          type);
    }
    // A pre-existing label tag that already holds the target slug: V50 must reuse it, not
    // duplicate it.
    jdbc.update(
        "INSERT INTO tag (tag_name, slug, created_at) VALUES (?, ?, NOW())",
        "Portfolio",
        "portfolio");
    // A pre-existing tag holding the target NAME but a drifted slug: the slug guard alone would
    // let the INSERT through, the tag_name unique constraint would reject it, and the slug join
    // would then attach zero rows. V50 repairs the slug instead.
    jdbc.update(
        "INSERT INTO tag (tag_name, slug, created_at) VALUES (?, ?, NOW())",
        "Art Gallery",
        "art-gallery-old");

    migrateTo(dataSource, "latest");
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

  private Map<String, Object> row(String slug) {
    return jdbc.queryForMap(
        "SELECT type, is_client, is_blog, display_mode FROM collection WHERE slug = ?", slug);
  }

  @Test
  void backfillDerivesFlagsFromEveryLegacyType() {
    assertThat(row("client-gallery"))
        .containsEntry("is_client", true)
        .containsEntry("is_blog", false);
    assertThat(row("blog")).containsEntry("is_client", false).containsEntry("is_blog", true);
    for (String slug : List.of("portfolio", "art-gallery", "home", "parent", "misc")) {
      assertThat(row(slug)).containsEntry("is_client", false).containsEntry("is_blog", false);
    }
  }

  @Test
  void displayModeBackfillPreservesOrderedForEveryNonBlogRow() {
    // The application fallback for a NULL display_mode became unconditionally CHRONOLOGICAL, so
    // without this backfill every un-set non-blog collection would silently reorder on next read.
    for (String slug :
        List.of("client-gallery", "portfolio", "art-gallery", "home", "parent", "misc")) {
      assertThat(row(slug)).containsEntry("display_mode", "ORDERED");
    }
    assertThat(row("blog")).containsEntry("display_mode", null);
  }

  @Test
  void labelTagsAreSeededOnceAndAttachedOnlyToTheirType() {
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM tag WHERE slug = 'portfolio'", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tag WHERE slug = 'art-gallery'", Integer.class))
        .isEqualTo(1);
    // The name-colliding tag was repaired in place rather than duplicated under a second name.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tag WHERE tag_name = 'Art Gallery'", Integer.class))
        .isEqualTo(1);

    assertThat(taggedSlugs("art-gallery")).containsExactly("art-gallery");
    assertThat(taggedSlugs("portfolio")).containsExactly("portfolio");
  }

  @Test
  void rerunningTheJoinInsertsAddsNoDuplicateRows() {
    // The join-table inserts are guarded by NOT EXISTS rather than a constraint-dependent
    // ON CONFLICT target, so they must be safely repeatable.
    int before = jdbc.queryForObject("SELECT count(*) FROM collection_tags", Integer.class);
    jdbc.update(
        "INSERT INTO collection_tags (collection_id, tag_id) "
            + "SELECT c.id, t.id FROM collection c JOIN tag t ON t.slug = 'portfolio' "
            + "WHERE c.type = 'PORTFOLIO' AND NOT EXISTS (SELECT 1 FROM collection_tags ct "
            + "WHERE ct.collection_id = c.id AND ct.tag_id = t.id)");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM collection_tags", Integer.class))
        .isEqualTo(before);
  }

  @Test
  void mutualExclusionCheckConstraintIsEnforced() {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname ="
                    + " 'chk_collection_client_blog_excl'",
                Integer.class))
        .isEqualTo(1);
  }

  private List<String> taggedSlugs(String tagSlug) {
    return jdbc.queryForList(
        "SELECT c.slug FROM collection c JOIN collection_tags ct ON ct.collection_id = c.id "
            + "JOIN tag t ON t.id = ct.tag_id WHERE t.slug = ? ORDER BY c.slug",
        String.class,
        tagSlug);
  }
}
