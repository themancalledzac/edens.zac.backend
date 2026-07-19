package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.dao.RoleRepository.CollectionRoleGrant;
import edens.zac.portfolio.backend.dao.RoleRepository.EffectiveGrant;
import edens.zac.portfolio.backend.dao.RoleRepository.RoleMember;
import edens.zac.portfolio.backend.entity.RoleEntity;
import edens.zac.portfolio.backend.types.AccessLevel;
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
    long roleId = repo.createRole("edens family", null);

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
    long general = repo.createRole("the bois", null);
    long client = repo.createRole("tyler abby", null);
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
    long r1 = repo.createRole("pnwer staff", null);
    repo.addMember(r1, user, null);

    List<RoleEntity> roles = repo.rolesForUser(user);
    assertThat(roles).extracting(RoleEntity::getName).containsExactly("pnwer staff");
  }

  @Test
  void membersForRoleJoinsAccountsInAddOrder() {
    long first = seedUserWithEmail("Nia", "nia-mfr@example.com");
    long second = seedUserWithEmail("Owen", "owen-mfr@example.com");
    long roleId = repo.createRole("membersForRole crew", null);
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
  void rolesGrantingCollectionOrdersByName() {
    long coll = seedCollection("role-inverse-order");
    long aaaCrew = repo.createRole("aaa crew", null);
    long bravoCrew = repo.createRole("bravo crew", null);
    long alphaCrew = repo.createRole("alpha crew", null);
    repo.setCollectionGrant(aaaCrew, coll, AccessLevel.CLIENT, null);
    repo.setCollectionGrant(bravoCrew, coll, AccessLevel.GENERAL, null);
    repo.setCollectionGrant(alphaCrew, coll, AccessLevel.CLIENT, null);

    List<CollectionRoleGrant> grants = repo.rolesGrantingCollection(coll);

    assertThat(grants)
        .extracting(
            CollectionRoleGrant::roleId, CollectionRoleGrant::name, CollectionRoleGrant::level)
        .containsExactly(
            tuple(aaaCrew, "aaa crew", AccessLevel.CLIENT),
            tuple(alphaCrew, "alpha crew", AccessLevel.CLIENT),
            tuple(bravoCrew, "bravo crew", AccessLevel.GENERAL));
  }

  @Test
  void rolesGrantingCollectionEmptyWhenNoGrants() {
    long coll = seedCollection("role-inverse-empty");
    repo.createRole("ungranted role", null);

    assertThat(repo.rolesGrantingCollection(coll)).isEmpty();
  }

  @Test
  void rolesGrantingCollectionReturnsBothRolesAndOnlyThatCollection() {
    long coll = seedCollection("role-inverse-two");
    long other = seedCollection("role-inverse-other");
    long r1 = repo.createRole("family gallery", null);
    long r2 = repo.createRole("wedding party", null);
    long unrelated = repo.createRole("unrelated role", null);
    repo.setCollectionGrant(r1, coll, AccessLevel.GENERAL, null);
    repo.setCollectionGrant(r2, coll, AccessLevel.CLIENT, null);
    repo.setCollectionGrant(unrelated, other, AccessLevel.CLIENT, null);

    List<CollectionRoleGrant> grants = repo.rolesGrantingCollection(coll);

    assertThat(grants)
        .extracting(CollectionRoleGrant::roleId, CollectionRoleGrant::level)
        .containsExactly(tuple(r1, AccessLevel.GENERAL), tuple(r2, AccessLevel.CLIENT));
    // Direct grants carry no waterfall provenance.
    assertThat(grants)
        .allSatisfy(
            g -> {
              assertThat(g.inheritedFromCollectionId()).isNull();
              assertThat(g.inheritedFromCollectionTitle()).isNull();
            });
  }

  @Test
  void rolesGrantingCollectionExposesInheritanceProvenance() {
    long origin = seedCollection("role-inverse-origin");
    long child = seedCollection("role-inverse-inherited");
    long roleId = repo.createRole("waterfall crew", null);
    repo.setCollectionGrant(roleId, origin, AccessLevel.CLIENT, null);
    repo.insertInheritedGrant(roleId, child, AccessLevel.CLIENT, origin);

    List<CollectionRoleGrant> grants = repo.rolesGrantingCollection(child);

    assertThat(grants)
        .singleElement()
        .satisfies(
            g -> {
              assertThat(g.roleId()).isEqualTo(roleId);
              assertThat(g.level()).isEqualTo(AccessLevel.CLIENT);
              assertThat(g.inheritedFromCollectionId()).isEqualTo(origin);
              assertThat(g.inheritedFromCollectionTitle()).isEqualTo("role-inverse-origin");
            });

    // Converting the inherited copy to a direct grant records the acting admin, not the
    // inherited row's null actor.
    long actor = seedUser("Provenance Admin");
    repo.setCollectionGrant(roleId, child, AccessLevel.CLIENT, actor);
    assertThat(
            jdbc.queryForObject(
                "SELECT granted_by FROM role_collection WHERE role_id=? AND collection_id=?",
                Long.class,
                roleId,
                child))
        .isEqualTo(actor);
    assertThat(repo.rolesGrantingCollection(child))
        .singleElement()
        .satisfies(g -> assertThat(g.inheritedFromCollectionId()).isNull());
  }

  @Test
  void removeMemberRevokesAccess() {
    long user = seedUser("Sam");
    long coll = seedCollection("role-revoke");
    long roleId = repo.createRole("weigel family", null);
    repo.addMember(roleId, user, null);
    repo.setCollectionGrant(roleId, coll, AccessLevel.GENERAL, null);
    assertThat(repo.canView(user, coll)).isTrue();

    repo.removeMember(roleId, user);
    assertThat(repo.canView(user, coll)).isFalse();
  }
}
