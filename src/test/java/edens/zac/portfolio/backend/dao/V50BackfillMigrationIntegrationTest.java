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
 * legacy type plus a colliding tag, then migrates to V50 (NOT latest -- see the target below) and
 * asserts the outcome.
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
    // A pre-existing tag holding the label NAME under an operator-chosen slug. This is the
    // shape that broke the old slug-keyed seed: the slug guard passed, the tag_name unique
    // constraint rejected the INSERT, and the slug join then attached zero rows while the
    // migration reported success. V50 must attach to this tag and leave its slug alone.
    jdbc.update(
        "INSERT INTO tag (tag_name, slug, created_at) VALUES (?, ?, NOW())",
        "Portfolio",
        "portfolio-work");
    // 'Art Gallery' is deliberately NOT seeded, so the other branch is covered too: V50
    // creates it itself and it gets the canonical slug.

    // Deliberately "50", not "latest": V51 deletes the 'Art Gallery' tag outright and detaches
    // every collection_tags row this class pins, so running it here would break three tests
    // that exist to pin V50 in isolation. V51's own behaviour is pinned, against the same three
    // tag shapes plus a converted tag, by V51MigrationPrepIntegrationTest.
    migrateTo(dataSource, "50");
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
  void existingLabelTagKeepsItsSlugAndStillReceivesItsCollections() {
    // The operator's slug is untouched -- rewriting it would break whatever public /{slug}
    // tag-view URL they chose.
    assertThat(
            jdbc.queryForObject("SELECT slug FROM tag WHERE tag_name = 'Portfolio'", String.class))
        .isEqualTo("portfolio-work");
    // And no duplicate was created under the canonical slug.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tag WHERE tag_name = 'Portfolio'", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM tag WHERE slug = 'portfolio'", Integer.class))
        .isZero();
    // The grouping the design depends on is attached anyway -- this is the B8 defect, and it is
    // what the old slug-keyed join silently failed to do.
    assertThat(taggedCollectionSlugs("Portfolio")).containsExactly("portfolio");
  }

  @Test
  void absentLabelTagIsCreatedWithTheCanonicalSlugAndAttached() {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tag WHERE tag_name = 'Art Gallery'", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT slug FROM tag WHERE tag_name = 'Art Gallery'", String.class))
        .isEqualTo("art-gallery");
    assertThat(taggedCollectionSlugs("Art Gallery")).containsExactly("art-gallery");
  }

  @Test
  void rerunningTheJoinInsertsAddsNoDuplicateRows() {
    // The join-table inserts are guarded by NOT EXISTS rather than a constraint-dependent
    // ON CONFLICT target, so they must be safely repeatable.
    int before = jdbc.queryForObject("SELECT count(*) FROM collection_tags", Integer.class);
    jdbc.update(
        "INSERT INTO collection_tags (collection_id, tag_id) "
            + "SELECT c.id, t.id FROM collection c JOIN tag t ON t.id = COALESCE("
            + "(SELECT id FROM tag WHERE tag_name = 'Portfolio'), "
            + "(SELECT id FROM tag WHERE slug = 'portfolio')) "
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

  /** Slugs of the collections attached to the label tag with this name, whatever its slug is. */
  private List<String> taggedCollectionSlugs(String tagName) {
    return jdbc.queryForList(
        "SELECT c.slug FROM collection c JOIN collection_tags ct ON ct.collection_id = c.id "
            + "JOIN tag t ON t.id = ct.tag_id WHERE t.tag_name = ? ORDER BY c.slug",
        String.class,
        tagName);
  }
}
