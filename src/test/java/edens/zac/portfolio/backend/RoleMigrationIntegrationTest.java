package edens.zac.portfolio.backend;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.dao.RoleRepository;
import edens.zac.portfolio.backend.types.AccessLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies the V45 backfill: a legacy {@code user_collection} grant becomes a PERSONAL role that
 * preserves the original grant level. {@code user_collection} still exists (dropped only in the
 * future V46 contract release), so this seeds a legacy row and re-runs the backfill statements
 * against a fresh personal role.
 */
class RoleMigrationIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private JdbcTemplate jdbc;
  @Autowired private RoleRepository roleRepository;

  private long seedUser(String name) {
    jdbc.update(
        "INSERT INTO users (name, webauthn_user_handle, status) VALUES (?, gen_random_uuid(), 'ACTIVE')",
        name);
    return jdbc.queryForObject("SELECT id FROM users WHERE name=?", Long.class, name);
  }

  private long seedCollection(String slug) {
    jdbc.update(
        "INSERT INTO collection (title, slug, type, visibility) VALUES (?, ?, 'CLIENT_GALLERY', 'UNLISTED')",
        slug,
        slug);
    return jdbc.queryForObject("SELECT id FROM collection WHERE slug=?", Long.class, slug);
  }

  @Test
  void backfillCreatesPersonalRolePreservingLevel() {
    long user = seedUser("Legacy");
    long coll = seedCollection("legacy-client");
    // Simulate a pre-cutover CLIENT grant in the still-present user_collection table.
    jdbc.update(
        "INSERT INTO user_collection (user_id, collection_id, role) VALUES (?, ?, 'CLIENT')",
        user,
        coll);

    // Re-run the V45 backfill statements (idempotent-safe for this fresh user).
    jdbc.update("INSERT INTO role (name, kind) SELECT 'user:' || ?, 'PERSONAL'", user);
    Long roleId =
        jdbc.queryForObject("SELECT id FROM role WHERE name = 'user:' || ?", Long.class, user);
    jdbc.update("INSERT INTO role_member (role_id, user_id) VALUES (?, ?)", roleId, user);
    jdbc.update(
        "INSERT INTO role_collection (role_id, collection_id, level) "
            + "SELECT ?, collection_id, role FROM user_collection WHERE user_id = ?",
        roleId,
        user);

    assertThat(roleRepository.canView(user, coll)).isTrue();
    assertThat(roleRepository.isClient(user, coll)).isTrue();
    assertThat(roleRepository.effectiveGrants(user))
        .singleElement()
        .satisfies(g -> assertThat(g.level()).isEqualTo(AccessLevel.CLIENT));
  }
}
