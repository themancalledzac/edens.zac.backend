package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.dao.RoleRepository.CollectionRoleGrant;
import edens.zac.portfolio.backend.dao.RoleRepository.EffectiveGrant;
import edens.zac.portfolio.backend.dao.RoleRepository.RoleMember;
import edens.zac.portfolio.backend.entity.RoleEntity;
import edens.zac.portfolio.backend.types.AccessLevel;
import edens.zac.portfolio.backend.types.RoleKind;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class RoleRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private RoleRepository repo;
  @Autowired private JdbcTemplate jdbc;

  private long seedUser(String name) {
    jdbc.update(
        "INSERT INTO users (name, webauthn_user_handle, status) VALUES (?, gen_random_uuid(), 'ACTIVE')",
        name);
    return jdbc.queryForObject("SELECT id FROM users WHERE name=?", Long.class, name);
  }

  private long seedUserWithEmail(String name, String email) {
    jdbc.update(
        "INSERT INTO users (name, email, webauthn_user_handle, status)"
            + " VALUES (?, ?, gen_random_uuid(), 'ACTIVE')",
        name,
        email);
    return jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
  }

  private long seedCollection(String slug) {
    jdbc.update(
        "INSERT INTO collection (title, slug, type, visibility) VALUES (?, ?, 'CLIENT_GALLERY', 'UNLISTED')",
        slug,
        slug);
    return jdbc.queryForObject("SELECT id FROM collection WHERE slug=?", Long.class, slug);
  }

  @Test
  void membershipAndGrantResolveThroughRole() {
    long user = seedUser("Mara");
    long coll = seedCollection("role-g1");
    long roleId = repo.createRole("edens family", RoleKind.SHARED, null);

    repo.addMember(roleId, user, null);
    repo.setCollectionGrant(roleId, coll, AccessLevel.GENERAL, null);

    assertThat(repo.canView(user, coll)).isTrue();
    assertThat(repo.isClient(user, coll)).isFalse();
    assertThat(repo.memberCollectionIdsForUser(user)).containsExactly(coll);

    repo.setCollectionGrant(roleId, coll, AccessLevel.CLIENT, null);
    assertThat(repo.isClient(user, coll)).isTrue();
  }

  @Test
  void unionAcrossRolesClientBeatsGeneral() {
    long user = seedUser("Bo");
    long coll = seedCollection("role-shared");
    long general = repo.createRole("the bois", RoleKind.SHARED, null);
    long client = repo.createRole("tyler abby", RoleKind.SHARED, null);
    repo.addMember(general, user, null);
    repo.addMember(client, user, null);
    repo.setCollectionGrant(general, coll, AccessLevel.GENERAL, null);
    repo.setCollectionGrant(client, coll, AccessLevel.CLIENT, null);

    List<EffectiveGrant> grants = repo.effectiveGrants(user);
    assertThat(grants).hasSize(1);
    assertThat(grants.get(0).collectionId()).isEqualTo(coll);
    assertThat(grants.get(0).level()).isEqualTo(AccessLevel.CLIENT);
  }

  @Test
  void rolesForUserListsMemberships() {
    long user = seedUser("Pat");
    long r1 = repo.createRole("pnwer staff", RoleKind.SHARED, null);
    repo.addMember(r1, user, null);

    List<RoleEntity> roles = repo.rolesForUser(user);
    assertThat(roles).extracting(RoleEntity::getName).containsExactly("pnwer staff");
  }

  @Test
  void membersForRoleJoinsAccountsInAddOrder() {
    long first = seedUserWithEmail("Nia", "nia-mfr@example.com");
    long second = seedUserWithEmail("Owen", "owen-mfr@example.com");
    long roleId = repo.createRole("membersForRole crew", RoleKind.SHARED, null);
    repo.addMember(roleId, first, null);
    repo.addMember(roleId, second, null);

    List<RoleMember> members = repo.membersForRole(roleId);

    assertThat(members)
        .extracting(RoleMember::userId, RoleMember::email, RoleMember::name)
        .containsExactly(
            tuple(first, "nia-mfr@example.com", "Nia"),
            tuple(second, "owen-mfr@example.com", "Owen"));
  }

  @Test
  void rolesGrantingCollectionOrdersSharedFirstThenName() {
    long coll = seedCollection("role-inverse-order");
    long personal = repo.createRole("aaa personal", RoleKind.PERSONAL, null);
    long sharedBravo = repo.createRole("bravo crew", RoleKind.SHARED, null);
    long sharedAlpha = repo.createRole("alpha crew", RoleKind.SHARED, null);
    repo.setCollectionGrant(personal, coll, AccessLevel.CLIENT, null);
    repo.setCollectionGrant(sharedBravo, coll, AccessLevel.GENERAL, null);
    repo.setCollectionGrant(sharedAlpha, coll, AccessLevel.CLIENT, null);

    List<CollectionRoleGrant> grants = repo.rolesGrantingCollection(coll);

    assertThat(grants)
        .extracting(
            CollectionRoleGrant::roleId,
            CollectionRoleGrant::name,
            CollectionRoleGrant::kind,
            CollectionRoleGrant::level)
        .containsExactly(
            tuple(sharedAlpha, "alpha crew", RoleKind.SHARED, AccessLevel.CLIENT),
            tuple(sharedBravo, "bravo crew", RoleKind.SHARED, AccessLevel.GENERAL),
            tuple(personal, "aaa personal", RoleKind.PERSONAL, AccessLevel.CLIENT));
  }

  @Test
  void rolesGrantingCollectionEmptyWhenNoGrants() {
    long coll = seedCollection("role-inverse-empty");
    repo.createRole("ungranted role", RoleKind.SHARED, null);

    assertThat(repo.rolesGrantingCollection(coll)).isEmpty();
  }

  @Test
  void rolesGrantingCollectionReturnsBothRolesAndOnlyThatCollection() {
    long coll = seedCollection("role-inverse-two");
    long other = seedCollection("role-inverse-other");
    long r1 = repo.createRole("family gallery", RoleKind.SHARED, null);
    long r2 = repo.createRole("wedding party", RoleKind.SHARED, null);
    long unrelated = repo.createRole("unrelated role", RoleKind.SHARED, null);
    repo.setCollectionGrant(r1, coll, AccessLevel.GENERAL, null);
    repo.setCollectionGrant(r2, coll, AccessLevel.CLIENT, null);
    repo.setCollectionGrant(unrelated, other, AccessLevel.CLIENT, null);

    List<CollectionRoleGrant> grants = repo.rolesGrantingCollection(coll);

    assertThat(grants)
        .extracting(CollectionRoleGrant::roleId, CollectionRoleGrant::level)
        .containsExactly(tuple(r1, AccessLevel.GENERAL), tuple(r2, AccessLevel.CLIENT));
  }

  @Test
  void removeMemberRevokesAccess() {
    long user = seedUser("Sam");
    long coll = seedCollection("role-revoke");
    long roleId = repo.createRole("weigel family", RoleKind.SHARED, null);
    repo.addMember(roleId, user, null);
    repo.setCollectionGrant(roleId, coll, AccessLevel.GENERAL, null);
    assertThat(repo.canView(user, coll)).isTrue();

    repo.removeMember(roleId, user);
    assertThat(repo.canView(user, coll)).isFalse();
  }
}
