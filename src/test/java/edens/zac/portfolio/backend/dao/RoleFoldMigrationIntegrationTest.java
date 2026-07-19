package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Exercises the V48 fold migration's fold body against real seeded PERSONAL roles.
 *
 * <p>Flyway applied V1..V48 at container start, so the LIVE schema already forbids {@code
 * kind='PERSONAL'} (V48 step 11 tightened the CHECK) and V48's archive tables already exist. Each
 * test therefore runs inside a SINGLE self-managed JDBC transaction that is ROLLED BACK at the end:
 * it relaxes the kind CHECK so PERSONAL can be seeded, seeds shared + personal roles, snapshots
 * temp archive tables that shadow the permanent ones, populates a {@code fold_mapping} temp table,
 * and replays the EXACT fold statements sliced out of {@code V48__fold_personal_roles.sql}.
 * Postgres DDL is transactional, so the rollback leaves the container schema + data pristine for
 * the next test -- nothing leaks past the base class {@code @AfterEach} truncate.
 *
 * <p>The replayed SQL is loaded from the migration file (never a hand-copied duplicate), so these
 * assertions validate the statements that actually ship, not a divergent copy. The fold body is
 * bracketed in the migration by {@code V48_REPLAY_FOLD_STEPS_*} (steps 3-8: fail-closed guard,
 * create targets, fold members, MAX-level direct grants, waterfall re-materialize, delete personal)
 * and {@code V48_REPLAY_LOSS_GATE_*} (step 9: the invariant-4 access-loss gate).
 */
class RoleFoldMigrationIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final String MIGRATION = "/db/migration/V48__fold_personal_roles.sql";

  /** Steps 3-8, sliced verbatim from the migration. */
  private static String foldSteps;

  /** Step 9 (the invariant-4 gate), sliced verbatim from the migration. */
  private static String lossGate;

  @Autowired private DataSource dataSource;

  @BeforeAll
  static void loadFoldBodyFromMigration() throws IOException {
    String sql = readClasspath(MIGRATION);
    foldSteps = slice(sql, "V48_REPLAY_FOLD_STEPS_START", "V48_REPLAY_FOLD_STEPS_END");
    lossGate = slice(sql, "V48_REPLAY_LOSS_GATE_START", "V48_REPLAY_LOSS_GATE_END");
  }

  // ---------------------------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------------------------

  @Test // adversarial finding B1: MAX-level union, never a downgrade.
  void fold_unionsLevels_neverDowngrades() throws SQLException {
    // Forward: a personal CLIENT grant upgrades the target's existing GENERAL grant.
    inRolledBackTx(
        c -> {
          long u1 = seedUser(c, "fu-u1");
          long c1 = seedCollection(c, "fu-c1");
          long tyler = seedRole(c, "tylerabby", "SHARED");
          setDirectGrant(c, tyler, c1, "GENERAL");
          long personal = seedRole(c, "user:900", "PERSONAL");
          addMember(c, personal, u1);
          setDirectGrant(c, personal, c1, "CLIENT");

          snapshotArchive(c);
          createFoldMapping(c, Map.of(personal, "tylerabby"));
          exec(c, foldSteps);
          exec(c, lossGate); // must not raise

          assertThat(directLevel(c, tyler, c1)).isEqualTo("CLIENT");
          assertThat(isMember(c, tyler, u1)).isTrue();
          assertThat(roleExists(c, personal)).isFalse();
        });

    // Reverse: a personal GENERAL grant must NOT downgrade the target's existing CLIENT grant.
    inRolledBackTx(
        c -> {
          long u1 = seedUser(c, "fr-u1");
          long c1 = seedCollection(c, "fr-c1");
          long tyler = seedRole(c, "tylerabby", "SHARED");
          setDirectGrant(c, tyler, c1, "CLIENT");
          long personal = seedRole(c, "user:901", "PERSONAL");
          addMember(c, personal, u1);
          setDirectGrant(c, personal, c1, "GENERAL");

          snapshotArchive(c);
          createFoldMapping(c, Map.of(personal, "tylerabby"));
          exec(c, foldSteps);
          exec(c, lossGate);

          assertThat(directLevel(c, tyler, c1)).isEqualTo("CLIENT");
        });
  }

  @Test // invariant 4: the gate raises (rolling back the whole tx) when the fold drops access.
  void fold_gateRaises_whenMappingDropsAccess() throws SQLException {
    inRolledBackTx(
        c -> {
          long u2 = seedUser(c, "gr-u2");
          long c2 = seedCollection(c, "gr-c2");
          seedRole(c, "weigel", "SHARED");
          long personal = seedRole(c, "user:902", "PERSONAL");
          addMember(c, personal, u2);
          setDirectGrant(c, personal, c2, "CLIENT");

          snapshotArchive(c);
          createFoldMapping(c, Map.of(personal, "weigel"));
          exec(c, foldSteps); // folds cleanly: weigel now grants u2 CLIENT on c2

          // Simulate the fold dropping access: remove the grant it produced. The archive still
          // records u2's pre-fold CLIENT on c2, so the gate must detect the loss and RAISE.
          long weigel = roleId(c, "weigel");
          exec(
              c,
              "DELETE FROM role_collection WHERE role_id=" + weigel + " AND collection_id=" + c2);

          assertThatThrownBy(() -> exec(c, lossGate))
              .isInstanceOf(SQLException.class)
              .hasMessageContaining("ACCESS LOSS");
        });
  }

  @Test // invariant 7: an unmapped PERSONAL role fails the fold loudly (keyed on kind, not name).
  void fold_raises_onUnmappedPersonalRole() throws SQLException {
    inRolledBackTx(
        c -> {
          long u3 = seedUser(c, "un-u3");
          long c3 = seedCollection(c, "un-c3");
          long mapped = seedRole(c, "user:903", "PERSONAL");
          addMember(c, mapped, u3);
          setDirectGrant(c, mapped, c3, "CLIENT");
          seedRole(c, "user:904", "PERSONAL"); // deliberately absent from the mapping
          seedRole(c, "pnwer", "SHARED");

          snapshotArchive(c);
          createFoldMapping(c, Map.of(mapped, "pnwer"));

          assertThatThrownBy(() -> exec(c, foldSteps))
              .isInstanceOf(SQLException.class)
              .hasMessageContaining("unmapped PERSONAL role");
        });
  }

  @Test // waterfall: a folded direct grant re-materializes onto visible descendants.
  void fold_rematerializesInheritedGrants_forTargetRole() throws SQLException {
    inRolledBackTx(
        c -> {
          long u4 = seedUser(c, "wf-u4");
          long parent = seedCollection(c, "wf-parent");
          long child = seedCollection(c, "wf-child");
          linkParentChild(c, parent, child);
          long personal = seedRole(c, "user:905", "PERSONAL");
          addMember(c, personal, u4);
          setDirectGrant(c, personal, parent, "CLIENT");
          seedRole(c, "edens", "SHARED");

          snapshotArchive(c);
          createFoldMapping(c, Map.of(personal, "edens"));
          exec(c, foldSteps);
          exec(c, lossGate); // no loss: u4 keeps parent and gains the inherited child

          long edens = roleId(c, "edens");
          assertThat(directLevel(c, edens, parent)).isEqualTo("CLIENT");
          // child is an INHERITED row whose origin is the parent that held the direct grant.
          assertThat(inheritedFrom(c, edens, child)).isEqualTo(parent);
          assertThat(anyLevel(c, edens, child)).isEqualTo("CLIENT");
        });
  }

  @Test // deleting personal roles is CASCADE-clean and touches only their own rows.
  void deletingPersonalRoles_removesTheirMembershipsAndGrantsOnly() throws SQLException {
    inRolledBackTx(
        c -> {
          long u5 = seedUser(c, "del-u5");
          long c5 = seedCollection(c, "del-c5");
          long other = seedCollection(c, "del-other");
          long target = seedRole(c, "the bois", "SHARED");
          setDirectGrant(c, target, other, "GENERAL"); // target's own grant -- must survive
          long personal = seedRole(c, "user:906", "PERSONAL");
          addMember(c, personal, u5);
          setDirectGrant(c, personal, c5, "CLIENT");

          snapshotArchive(c);
          createFoldMapping(c, Map.of(personal, "the bois"));
          exec(c, foldSteps);
          exec(c, lossGate);

          // The personal role and ALL of its own rows are gone (CASCADE).
          assertThat(roleExists(c, personal)).isFalse();
          assertThat(count(c, "role_member WHERE role_id=" + personal)).isZero();
          assertThat(count(c, "role_collection WHERE role_id=" + personal)).isZero();
          // The target keeps its pre-existing grant and gains the folded grant + member.
          assertThat(directLevel(c, target, other)).isEqualTo("GENERAL");
          assertThat(directLevel(c, target, c5)).isEqualTo("CLIENT");
          assertThat(isMember(c, target, u5)).isTrue();
        });
  }

  @Test // collision regression: folding a DIRECT grant onto a row the target already INHERITS must
  // convert that row to direct (origin nulled), so a later parent-grant removal cannot sweep it
  // away.
  void fold_convertsInheritedRowToDirect_onDirectFoldCollision() throws SQLException {
    inRolledBackTx(
        c -> {
          long u = seedUser(c, "col-u");
          long parent = seedCollection(c, "col-parent");
          long child = seedCollection(c, "col-child");
          linkParentChild(c, parent, child);
          long target = seedRole(c, "pnwer", "SHARED");
          setDirectGrant(c, target, parent, "CLIENT"); // target holds a direct grant on the parent
          // Pre-fold: the V47 waterfall already materialized (target, child) inherited from parent.
          exec(
              c,
              "INSERT INTO role_collection"
                  + " (role_id, collection_id, level, inherited_from_collection_id)"
                  + " VALUES ("
                  + target
                  + ","
                  + child
                  + ",'CLIENT',"
                  + parent
                  + ")");
          long personal = seedRole(c, "user:907", "PERSONAL");
          addMember(c, personal, u);
          setDirectGrant(
              c, personal, child, "CLIENT"); // personal holds a DIRECT grant on the child

          snapshotArchive(c);
          createFoldMapping(c, Map.of(personal, "pnwer"));
          exec(c, foldSteps);
          exec(c, lossGate);

          // The collided row is now DIRECT (origin cleared) at the MAX level -- WITHOUT Fix 1 the
          // old
          // upgrade-only guard left it tagged inherited_from=parent and this assertion fails.
          assertThat(inheritedFrom(c, target, child)).isNull();
          assertThat(anyLevel(c, target, child)).isEqualTo("CLIENT");
          // Removing the parent grant's inherited copies
          // (RoleRepository.removeInheritedGrantsByOrigin)
          // must NOT delete the now-direct folded grant.
          exec(
              c,
              "DELETE FROM role_collection WHERE role_id="
                  + target
                  + " AND inherited_from_collection_id="
                  + parent);
          assertThat(anyLevel(c, target, child)).isEqualTo("CLIENT");
        });
  }

  @Test // step-6 bool_or union: two personal roles folding into one target, CLIENT wins either
  // order.
  void fold_unionsAcrossPersonalRoles_clientWins() throws SQLException {
    // Seed order 1: CLIENT personal first, then GENERAL personal.
    inRolledBackTx(
        c -> {
          long u1 = seedUser(c, "cpu-u1a");
          long u2 = seedUser(c, "cpu-u2a");
          long c1 = seedCollection(c, "cpu-c1a");
          seedRole(c, "edens", "SHARED");
          long p1 = seedRole(c, "user:908", "PERSONAL");
          addMember(c, p1, u1);
          setDirectGrant(c, p1, c1, "CLIENT");
          long p2 = seedRole(c, "user:909", "PERSONAL");
          addMember(c, p2, u2);
          setDirectGrant(c, p2, c1, "GENERAL");

          snapshotArchive(c);
          createFoldMapping(c, Map.of(p1, "edens", p2, "edens"));
          exec(c, foldSteps);
          exec(c, lossGate);

          assertThat(directLevel(c, roleId(c, "edens"), c1)).isEqualTo("CLIENT");
        });

    // Seed order 2: GENERAL personal first, then CLIENT personal (proves order-independence).
    inRolledBackTx(
        c -> {
          long u1 = seedUser(c, "cpu-u1b");
          long u2 = seedUser(c, "cpu-u2b");
          long c1 = seedCollection(c, "cpu-c1b");
          seedRole(c, "edens", "SHARED");
          long p2 = seedRole(c, "user:909", "PERSONAL");
          addMember(c, p2, u2);
          setDirectGrant(c, p2, c1, "GENERAL");
          long p1 = seedRole(c, "user:908", "PERSONAL");
          addMember(c, p1, u1);
          setDirectGrant(c, p1, c1, "CLIENT");

          snapshotArchive(c);
          createFoldMapping(c, Map.of(p1, "edens", p2, "edens"));
          exec(c, foldSteps);
          exec(c, lossGate);

          assertThat(directLevel(c, roleId(c, "edens"), c1)).isEqualTo("CLIENT");
        });
  }

  @Test // invariant 4, downgrade branch: CLIENT-before / GENERAL-after must RAISE (not just
  // absent).
  void fold_gateRaises_onClientToGeneralDowngrade() throws SQLException {
    inRolledBackTx(
        c -> {
          long u = seedUser(c, "dg-u");
          long c1 = seedCollection(c, "dg-c1");
          seedRole(c, "weigel", "SHARED");
          long personal = seedRole(c, "user:910", "PERSONAL");
          addMember(c, personal, u);
          setDirectGrant(c, personal, c1, "CLIENT");

          snapshotArchive(c);
          createFoldMapping(c, Map.of(personal, "weigel"));
          exec(c, foldSteps); // weigel now grants u CLIENT on c1

          // Simulate a downgrade (u keeps the collection, but at a lower level) -- the gate's
          // was_client-AND-NOT-is_client branch must fire, distinct from the absent-after branch.
          long weigel = roleId(c, "weigel");
          exec(
              c,
              "UPDATE role_collection SET level='GENERAL' WHERE role_id="
                  + weigel
                  + " AND collection_id="
                  + c1);

          assertThatThrownBy(() -> exec(c, lossGate))
              .isInstanceOf(SQLException.class)
              .hasMessageContaining("ACCESS LOSS");
        });
  }

  @Test // standing-rule runtime verification: V48 tightened the CHECK and named it role_kind_check.
  void v48_tightenedKindCheck_toSharedOnly_named_roleKindCheck() throws SQLException {
    try (Connection c = dataSource.getConnection()) {
      c.setAutoCommit(false);
      try {
        assertThat(
                count(
                    c,
                    "pg_constraint WHERE conrelid='role'::regclass AND conname='role_kind_check'"))
            .isEqualTo(1L);
        assertThatThrownBy(
                () -> exec(c, "INSERT INTO role (name, kind) VALUES ('v48-guard','PERSONAL')"))
            .isInstanceOf(SQLException.class);
      } finally {
        c.rollback();
        c.setAutoCommit(true);
      }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Transaction harness -- relax the CHECK, run the scenario, always roll back.
  // ---------------------------------------------------------------------------------------------

  @FunctionalInterface
  private interface Scenario {
    void run(Connection c) throws SQLException;
  }

  private void inRolledBackTx(Scenario scenario) throws SQLException {
    try (Connection c = dataSource.getConnection()) {
      c.setAutoCommit(false);
      try {
        relaxKindCheck(c);
        scenario.run(c);
      } finally {
        c.rollback();
        c.setAutoCommit(true);
      }
    }
  }

  /** Drop the (post-V48, SHARED-only) kind CHECK name-agnostically so PERSONAL can be seeded. */
  private void relaxKindCheck(Connection c) throws SQLException {
    exec(
        c,
        "DO $$ DECLARE cn text; BEGIN"
            + " SELECT conname INTO cn FROM pg_constraint"
            + " WHERE conrelid='role'::regclass AND contype='c'"
            + " AND pg_get_constraintdef(oid) ILIKE '%kind%';"
            + " IF cn IS NOT NULL THEN EXECUTE format('ALTER TABLE role DROP CONSTRAINT %I', cn);"
            + " END IF; END $$");
  }

  /** Snapshot the pre-fold state into temp tables that shadow V48's permanent archive tables. */
  private void snapshotArchive(Connection c) throws SQLException {
    exec(c, "CREATE TEMP TABLE role_fold_archive_role_member AS TABLE role_member");
    exec(c, "CREATE TEMP TABLE role_fold_archive_role_collection AS TABLE role_collection");
  }

  private void createFoldMapping(Connection c, Map<Long, String> mapping) throws SQLException {
    exec(
        c,
        "CREATE TEMP TABLE fold_mapping"
            + " (personal_role_id BIGINT PRIMARY KEY, target_role_name TEXT NOT NULL)");
    for (Map.Entry<Long, String> e : mapping.entrySet()) {
      exec(c, "INSERT INTO fold_mapping VALUES (" + e.getKey() + ",'" + e.getValue() + "')");
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Seed + query helpers (all on the caller's uncommitted connection).
  // ---------------------------------------------------------------------------------------------

  private long seedUser(Connection c, String name) throws SQLException {
    exec(
        c,
        "INSERT INTO users (name, webauthn_user_handle, status)"
            + " VALUES ('"
            + name
            + "', gen_random_uuid(), 'ACTIVE')");
    return queryLong(c, "SELECT id FROM users WHERE name='" + name + "'");
  }

  private long seedCollection(Connection c, String slug) throws SQLException {
    exec(
        c,
        "INSERT INTO collection (title, slug, type, visibility)"
            + " VALUES ('"
            + slug
            + "','"
            + slug
            + "','CLIENT_GALLERY','UNLISTED')");
    return queryLong(c, "SELECT id FROM collection WHERE slug='" + slug + "'");
  }

  private long seedRole(Connection c, String name, String kind) throws SQLException {
    exec(c, "INSERT INTO role (name, kind) VALUES ('" + name + "','" + kind + "')");
    return roleId(c, name);
  }

  private void addMember(Connection c, long roleId, long userId) throws SQLException {
    exec(c, "INSERT INTO role_member (role_id, user_id) VALUES (" + roleId + "," + userId + ")");
  }

  private void setDirectGrant(Connection c, long roleId, long collId, String level)
      throws SQLException {
    exec(
        c,
        "INSERT INTO role_collection (role_id, collection_id, level) VALUES ("
            + roleId
            + ","
            + collId
            + ",'"
            + level
            + "')");
  }

  /** Replicate CollectionService.linkCollectionToParent's persisted rows via raw SQL. */
  private void linkParentChild(Connection c, long parentId, long childId) throws SQLException {
    long contentId =
        queryLong(c, "INSERT INTO content (content_type) VALUES ('COLLECTION') RETURNING id");
    exec(
        c,
        "INSERT INTO content_collection (id, referenced_collection_id) VALUES ("
            + contentId
            + ","
            + childId
            + ")");
    exec(
        c,
        "INSERT INTO collection_content (collection_id, content_id, order_index, visible)"
            + " VALUES ("
            + parentId
            + ","
            + contentId
            + ",0,true)");
  }

  private long roleId(Connection c, String name) throws SQLException {
    return queryLong(c, "SELECT id FROM role WHERE name='" + name + "'");
  }

  private boolean roleExists(Connection c, long roleId) throws SQLException {
    return count(c, "role WHERE id=" + roleId) > 0;
  }

  private boolean isMember(Connection c, long roleId, long userId) throws SQLException {
    return count(c, "role_member WHERE role_id=" + roleId + " AND user_id=" + userId) > 0;
  }

  private String directLevel(Connection c, long roleId, long collId) throws SQLException {
    return queryString(
        c,
        "SELECT level FROM role_collection WHERE role_id="
            + roleId
            + " AND collection_id="
            + collId
            + " AND inherited_from_collection_id IS NULL");
  }

  private String anyLevel(Connection c, long roleId, long collId) throws SQLException {
    return queryString(
        c,
        "SELECT level FROM role_collection WHERE role_id="
            + roleId
            + " AND collection_id="
            + collId);
  }

  private Long inheritedFrom(Connection c, long roleId, long collId) throws SQLException {
    return queryNullableLong(
        c,
        "SELECT inherited_from_collection_id FROM role_collection WHERE role_id="
            + roleId
            + " AND collection_id="
            + collId);
  }

  private long count(Connection c, String fromWhere) throws SQLException {
    return queryLong(c, "SELECT count(*) FROM " + fromWhere);
  }

  // ---------------------------------------------------------------------------------------------
  // Low-level JDBC + classpath helpers.
  // ---------------------------------------------------------------------------------------------

  private void exec(Connection c, String sql) throws SQLException {
    try (Statement st = c.createStatement()) {
      st.execute(sql);
    }
  }

  private long queryLong(Connection c, String sql) throws SQLException {
    try (Statement st = c.createStatement();
        ResultSet rs = st.executeQuery(sql)) {
      rs.next();
      return rs.getLong(1);
    }
  }

  private Long queryNullableLong(Connection c, String sql) throws SQLException {
    try (Statement st = c.createStatement();
        ResultSet rs = st.executeQuery(sql)) {
      rs.next();
      long v = rs.getLong(1);
      return rs.wasNull() ? null : v;
    }
  }

  private String queryString(Connection c, String sql) throws SQLException {
    try (Statement st = c.createStatement();
        ResultSet rs = st.executeQuery(sql)) {
      rs.next();
      return rs.getString(1);
    }
  }

  private static String readClasspath(String path) throws IOException {
    try (InputStream in = RoleFoldMigrationIntegrationTest.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IOException("migration not found on classpath: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  /** Return the SQL between the line after {@code startTag} and the line before {@code endTag}. */
  private static String slice(String sql, String startTag, String endTag) {
    int s = sql.indexOf(startTag);
    int e = sql.indexOf(endTag);
    if (s < 0 || e < 0) {
      throw new IllegalStateException("replay marker not found: " + startTag + " / " + endTag);
    }
    s = sql.indexOf('\n', s) + 1;
    e = sql.lastIndexOf('\n', e);
    String body = sql.substring(s, e).trim();
    if (body.isEmpty()) {
      throw new IllegalStateException("empty replay region for " + startTag);
    }
    return body;
  }
}
