package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
 * Exercises the V54 fold against actual data. The shared harness migrates an EMPTY users table, so
 * the fold processes zero rows there and only the end-state index is asserted -- a fold that
 * destroyed every tag would still pass it.
 *
 * <p>Seeds a THREE-row name group, which is the shape V53 could not handle: two tag-only PERSON
 * losers holding the same content_id, neither colliding with the winner. The index is dropped after
 * V53 because V53 creates it and it is exactly what makes duplicates unrepresentable.
 *
 * <p>The account is inserted last, so it has the highest id -- a survivor rule ordering by id alone
 * would pick a tag row and fail {@code survivorIsTheAccountNotTheTagRow}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V54FoldMigrationIntegrationTest {

  private static final String DUPED = "Tara Edens";

  private PostgreSQLContainer<?> postgres;
  private JdbcTemplate jdbc;

  private Long loserA;
  private Long loserB;
  private Long winner;
  private Long sharedContent;
  private Long winnerAlsoContent;
  private Long collectionId;
  private Long roleId;

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

    // Stop at V53, then reproduce a database that still holds duplicates when V54 runs.
    migrateTo(dataSource, "53");
    jdbc.execute("DROP INDEX idx_users_name_lower");

    loserA = insertUser("PERSON");
    loserB = insertUser("PERSON");
    winner = insertUser("INVITED");

    sharedContent = insertContent();
    winnerAlsoContent = insertContent();

    // Both losers on the same content -- the collision V53 missed.
    tagImage(sharedContent, loserA);
    tagImage(sharedContent, loserB);
    // Winner already holds this one, so the loser's row must collapse into it, not duplicate it.
    tagImage(winnerAlsoContent, winner);
    tagImage(winnerAlsoContent, loserA);

    collectionId =
        jdbc.queryForObject(
            "INSERT INTO collection (title, slug, visibility) "
                + "VALUES ('Rome 2023', 'rome-2023', 'LISTED') RETURNING id",
            Long.class);
    jdbc.update(
        "INSERT INTO collection_people (collection_id, person_id) VALUES (?, ?)",
        collectionId,
        loserA);
    jdbc.update(
        "INSERT INTO collection_people (collection_id, person_id) VALUES (?, ?)",
        collectionId,
        loserB);

    roleId =
        jdbc.queryForObject(
            "INSERT INTO role (name) VALUES ('Test Role') RETURNING id", Long.class);
    jdbc.update("INSERT INTO role_member (role_id, user_id) VALUES (?, ?)", roleId, loserA);
    jdbc.update("INSERT INTO role_member (role_id, user_id) VALUES (?, ?)", roleId, loserB);

    migrateTo(dataSource, "54");
  }

  private Long insertUser(String status) {
    return jdbc.queryForObject(
        "INSERT INTO users (name, webauthn_user_handle, status) "
            + "VALUES (?, gen_random_uuid(), ?) RETURNING id",
        Long.class,
        DUPED,
        status);
  }

  private Long insertContent() {
    return jdbc.queryForObject(
        "INSERT INTO content (content_type) VALUES ('IMAGE') RETURNING id", Long.class);
  }

  private void tagImage(Long contentId, Long personId) {
    jdbc.update(
        "INSERT INTO content_image_people (content_id, person_id) VALUES (?, ?)",
        contentId,
        personId);
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
  void foldCompletesAndRestoresTheUniqueIndex() {
    // V53's approach failed this seed with "duplicate key value violates unique constraint
    // content_image_people_pkey" and rolled back, blocking boot. Reaching here proves V54 applied.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE tablename = 'users' "
                    + "AND indexname = 'idx_users_name_lower'",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void survivorIsTheAccountNotTheTagRow() {
    assertThat(
            jdbc.queryForList(
                "SELECT id FROM users WHERE LOWER(name) = LOWER(?) ORDER BY id", Long.class, DUPED))
        .containsExactly(winner);
  }

  @Test
  void everyTagLandsOnTheSurvivorExactlyOnce() {
    // Losing sharedContent would be the silent data loss this exists to prevent; a duplicate would
    // mean the conflict handling failed.
    assertThat(imageTagsFor(winner)).containsExactlyInAnyOrder(sharedContent, winnerAlsoContent);
    assertThat(imageTagsFor(loserA)).isEmpty();
    assertThat(imageTagsFor(loserB)).isEmpty();
    assertThat(
            jdbc.queryForList(
                "SELECT collection_id FROM collection_people WHERE person_id = ?",
                Long.class,
                winner))
        .containsExactly(collectionId);
    assertThat(
            jdbc.queryForList(
                "SELECT role_id FROM role_member WHERE user_id = ?", Long.class, winner))
        .containsExactly(roleId);
  }

  private List<Long> imageTagsFor(Long personId) {
    return jdbc.queryForList(
        "SELECT content_id FROM content_image_people WHERE person_id = ? ORDER BY content_id",
        Long.class,
        personId);
  }
}
