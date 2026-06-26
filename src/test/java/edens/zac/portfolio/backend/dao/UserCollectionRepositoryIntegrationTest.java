package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.types.CollectionRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class UserCollectionRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private UserCollectionRepository repo;
  @Autowired private JdbcTemplate jdbc;

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
  void upsertPromotesGeneralToClientThenView() {
    long u = seedUser("Mary");
    long c = seedCollection("g1");

    repo.upsertRole(u, c, CollectionRole.GENERAL, null);
    assertThat(repo.hasMembership(u, c)).isTrue();
    assertThat(repo.hasClientMembership(u, c)).isFalse();

    repo.upsertRole(u, c, CollectionRole.CLIENT, null);
    assertThat(repo.hasClientMembership(u, c)).isTrue();
    assertThat(repo.findCollectionIdsByUserId(u)).containsExactly(c);

    repo.delete(u, c);
    assertThat(repo.hasMembership(u, c)).isFalse();
  }
}
