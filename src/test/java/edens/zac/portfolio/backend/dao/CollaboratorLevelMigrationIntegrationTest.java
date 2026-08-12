package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** V55: the role_collection.level CHECK admits COLLABORATOR and still refuses ADMIN. */
class CollaboratorLevelMigrationIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private JdbcTemplate jdbc;

  private long seedUser(String name) {
    jdbc.update(
        "INSERT INTO users (name, webauthn_user_handle, status) VALUES (?, gen_random_uuid(), 'ACTIVE')",
        name);
    return jdbc.queryForObject("SELECT id FROM users WHERE name=?", Long.class, name);
  }

  private long seedCollection(String slug) {
    jdbc.update(
        "INSERT INTO collection (title, slug, visibility) VALUES (?, ?, 'UNLISTED')", slug, slug);
    return jdbc.queryForObject("SELECT id FROM collection WHERE slug=?", Long.class, slug);
  }

  private long seedRole(String name) {
    jdbc.update("INSERT INTO role (name, kind) VALUES (?, 'SHARED')", name);
    return jdbc.queryForObject("SELECT id FROM role WHERE name=?", Long.class, name);
  }

  @Test
  void checkAcceptsCollaboratorAndRefusesAdmin() {
    seedUser("V55-Check");
    long coll = seedCollection("v55-check");
    long role = seedRole("role:v55-check");

    jdbc.update(
        "INSERT INTO role_collection (role_id, collection_id, level) VALUES (?, ?, 'COLLABORATOR')",
        role,
        coll);
    assertThat(
            jdbc.queryForObject(
                "SELECT level FROM role_collection WHERE role_id=? AND collection_id=?",
                String.class,
                role,
                coll))
        .isEqualTo("COLLABORATOR");

    long coll2 = seedCollection("v55-check-admin");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO role_collection (role_id, collection_id, level) VALUES (?, ?, 'ADMIN')",
                    role,
                    coll2))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
