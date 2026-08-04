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
 * Exercises the V53 FOLD against actual data, which no other test does. The shared harness
 * (PersonNameUniqueMigrationIntegrationTest) migrates an empty users table, so steps 1-4 process
 * zero rows there and only the end-state index is asserted -- a fold that destroyed every tag would
 * still pass it. This test owns a dedicated container, migrates to V52, seeds the production shape,
 * then migrates to V53 and asserts no tag was lost.
 *
 * <p>The seed deliberately includes the collision shape that a 2-row group cannot produce: TWO
 * tag-only PERSON losers in the same name group holding the SAME content_id, where neither collides
 * with the winner. V53's original single collision DELETE only removed rows colliding with the
 * WINNER, so both losers survived it and the repoint UPDATE then violated
 * content_image_people_pkey. The owner's dev database held only 2-row groups (87+106, 90+107), so
 * this was invisible there; 3+ row groups are plausible in prod because V35 minted one PERSON row
 * per unlinked content_people row and content_people.person_name was never unique.
 *
 * <p>The INVITED account is inserted LAST, after both PERSON rows, so it carries the HIGHEST id.
 * That makes {@code survivorIsTheAccountNotTheTagRow} discriminating: a survivor rule that ordered
 * by id alone, rather than by {@code (status = 'PERSON'), id}, would pick a tag row and fail.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V53FoldMigrationIntegrationTest {

  private static final String DUPED = "Tara Edens";
  private static final String UNIQUE = "Logan Radde";

  private PostgreSQLContainer<?> postgres;
  private JdbcTemplate jdbc;

  private Long loserA;
  private Long loserB;
  private Long winner;
  private Long control;
  private Long sharedContent;
  private Long loserOnlyContent;
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

    // Stop just short of V53 so the seed rows exist as pre-V53 data would.
    migrateTo(dataSource, "52");

    // Order matters: both PERSON rows first, the account last, so the account has the highest id.
    loserA = insertUser(DUPED, "PERSON");
    loserB = insertUser(DUPED, "PERSON");
    winner = insertUser(DUPED, "INVITED");
    control = insertUser(UNIQUE, "PERSON");

    sharedContent = insertContent();
    loserOnlyContent = insertContent();
    winnerAlsoContent = insertContent();

    // Shape (b): two losers, same content, neither colliding with the winner.
    tagImage(sharedContent, loserA);
    tagImage(sharedContent, loserB);
    // Only one loser holds this one -- a plain move.
    tagImage(loserOnlyContent, loserA);
    // Shape (a): the winner already holds it, so the loser's row must be dropped, not moved.
    tagImage(winnerAlsoContent, winner);
    tagImage(winnerAlsoContent, loserA);
    // The control person must come through completely untouched.
    tagImage(loserOnlyContent, control);

    collectionId =
        jdbc.queryForObject(
            "INSERT INTO collection (title, slug, visibility) "
                + "VALUES ('Rome 2023', 'rome-2023', 'LISTED') RETURNING id",
            Long.class);
    // Shape (b) again, on collection_people.
    tagCollection(collectionId, loserA);
    tagCollection(collectionId, loserB);

    roleId =
        jdbc.queryForObject(
            "INSERT INTO role (name) VALUES ('Test Role') RETURNING id", Long.class);
    // Shape (b) again, on role_member. A tag-only PERSON should not hold memberships, but
    // UserMergeService moves them, so V53 stays consistent with it -- and must not choke.
    addRoleMember(roleId, loserA);
    addRoleMember(roleId, loserB);

    migrateTo(dataSource, "53");
  }

  private Long insertUser(String name, String status) {
    return jdbc.queryForObject(
        "INSERT INTO users (name, webauthn_user_handle, status) "
            + "VALUES (?, gen_random_uuid(), ?) RETURNING id",
        Long.class,
        name,
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

  private void tagCollection(Long collection, Long personId) {
    jdbc.update(
        "INSERT INTO collection_people (collection_id, person_id) VALUES (?, ?)",
        collection,
        personId);
  }

  private void addRoleMember(Long role, Long userId) {
    jdbc.update("INSERT INTO role_member (role_id, user_id) VALUES (?, ?)", role, userId);
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
  void migrationCompletesWithoutAPrimaryKeyViolation() {
    // The headline regression: with only the winner-collision DELETE, migrating this seed failed
    // with "duplicate key value violates unique constraint content_image_people_pkey" and rolled
    // the whole migration back, blocking application boot. Reaching @BeforeAll's end proves it
    // applied, and the index below proves it ran to completion rather than stopping early.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE tablename = 'users' "
                    + "AND indexname = 'idx_users_name_lower'",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void survivorIsTheAccountNotTheTagRow() {
    List<Long> remaining =
        jdbc.queryForList(
            "SELECT id FROM users WHERE LOWER(name) = LOWER(?) ORDER BY id", Long.class, DUPED);
    assertThat(remaining).containsExactly(winner);
    assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, winner))
        .isEqualTo("INVITED");
  }

  @Test
  void everyImageTagLandsOnTheSurvivorExactlyOnce() {
    // Three distinct images were tagged across the group; all three must survive on the winner,
    // each exactly once. Losing sharedContent would be the silent data loss this migration exists
    // to prevent, and a duplicate would mean the collision deletes over-deleted.
    assertThat(imageTagsFor(winner))
        .containsExactlyInAnyOrder(sharedContent, loserOnlyContent, winnerAlsoContent);
    assertThat(imageTagsFor(loserA)).isEmpty();
    assertThat(imageTagsFor(loserB)).isEmpty();
  }

  @Test
  void collectionAndRoleJoinsFoldOntoTheSurvivorExactlyOnce() {
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

  @Test
  void aPersonWithAUniqueNameIsUntouched() {
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, control))
        .isEqualTo(1);
    assertThat(imageTagsFor(control)).containsExactly(loserOnlyContent);
  }

  @Test
  void noOrphanedJoinRowsPointAtADeletedUser() {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM content_image_people cip "
                    + "WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = cip.person_id)",
                Integer.class))
        .isZero();
  }

  private List<Long> imageTagsFor(Long personId) {
    return jdbc.queryForList(
        "SELECT content_id FROM content_image_people WHERE person_id = ? ORDER BY content_id",
        Long.class,
        personId);
  }
}
