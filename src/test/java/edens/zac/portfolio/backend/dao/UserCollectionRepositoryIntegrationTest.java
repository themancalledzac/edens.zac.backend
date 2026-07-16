package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.dao.UserCollectionRepository.AssociatedCollection;
import edens.zac.portfolio.backend.types.AccessLevel;
import java.util.List;
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

    repo.upsertRole(u, c, AccessLevel.GENERAL, null);
    assertThat(repo.hasMembership(u, c)).isTrue();
    assertThat(repo.hasClientMembership(u, c)).isFalse();

    repo.upsertRole(u, c, AccessLevel.CLIENT, null);
    assertThat(repo.hasClientMembership(u, c)).isTrue();
    assertThat(repo.findCollectionIdsByUserId(u)).containsExactly(c);

    repo.delete(u, c);
    assertThat(repo.hasMembership(u, c)).isFalse();
  }

  @Test
  void findAssociatedCollectionsReturnsTaggedOnlyWithNullRoleThenMemberWithRole() {
    long u = seedUser("Dave");
    long c = seedCollection("tagged-only");

    // Tag the user in collection_people (tagged, no membership yet)
    jdbc.update("INSERT INTO collection_people (collection_id, person_id) VALUES (?, ?)", c, u);

    List<AssociatedCollection> rows = repo.findAssociatedCollections(u);
    assertThat(rows).hasSize(1);
    AssociatedCollection row = rows.get(0);
    assertThat(row.collectionId()).isEqualTo(c);
    assertThat(row.title()).isEqualTo("tagged-only");
    assertThat(row.role()).isNull();

    // Grant membership — role should now appear
    repo.upsertRole(u, c, AccessLevel.CLIENT, null);
    List<AssociatedCollection> after = repo.findAssociatedCollections(u);
    assertThat(after).hasSize(1);
    assertThat(after.get(0).role()).isEqualTo("CLIENT");
  }

  @Test
  void findAssociatedCollectionsIncludesMemberWithoutTag() {
    long u = seedUser("Eve");
    long c = seedCollection("member-only");

    // Grant membership with no collection_people row
    repo.upsertRole(u, c, AccessLevel.GENERAL, null);

    List<AssociatedCollection> rows = repo.findAssociatedCollections(u);
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).role()).isEqualTo("GENERAL");
  }
}
