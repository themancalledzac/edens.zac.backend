package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.dao.RoleRepository;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.types.AccessLevel;
import edens.zac.portfolio.backend.types.RoleKind;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Waterfall role grants (V47): a grant on a parent collection is materialized onto every visible
 * descendant as an inherited {@code role_collection} row, and the untouched resolution queries
 * ({@code canView}/{@code isClient}/{@code memberCollectionIdsForUser}) already see them. Covers
 * the four sync hooks (grant, remove, link, unlink), the direct-wins-and-is-sticky conflict rule,
 * CLIENT-beats-GENERAL upgrades, re-materialization from surviving ancestors, and the {@code
 * cc.visible = false} gate.
 */
class RoleGrantPropagationServiceIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private RoleGrantPropagationService propagation;
  @Autowired private RoleRepository roleRepository;
  @Autowired private CollectionService collectionService;
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

  private long seedMemberRole(String roleName, long userId) {
    long roleId = roleRepository.createRole(roleName, RoleKind.SHARED, null);
    roleRepository.addMember(roleId, userId, null);
    return roleId;
  }

  /** Hide the parent-to-child membership link ({@code cc.visible = false}). */
  private void hideLink(long parentId, long childId) {
    jdbc.update(
        "UPDATE collection_content cc SET visible = false FROM content_collection cct"
            + " WHERE cc.content_id = cct.id AND cc.collection_id = ?"
            + " AND cct.referenced_collection_id = ?",
        parentId,
        childId);
  }

  private int grantRowCount(long roleId, long collectionId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM role_collection WHERE role_id=? AND collection_id=?",
        Integer.class,
        roleId,
        collectionId);
  }

  private Long inheritedFrom(long roleId, long collectionId) {
    return jdbc.queryForObject(
        "SELECT inherited_from_collection_id FROM role_collection"
            + " WHERE role_id=? AND collection_id=?",
        Long.class,
        roleId,
        collectionId);
  }

  /** An update DTO that only removes the given child collections from the parent. */
  private static CollectionRequests.Update removeChildrenUpdate(
      long parentId, List<Long> childIds) {
    return new CollectionRequests.Update(
        parentId,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        new CollectionRequests.CollectionUpdate(null, null, childIds),
        null);
  }

  /** An update DTO that only adds the given child collection to the parent. */
  private static CollectionRequests.Update addChildUpdate(
      long parentId, long childId, Boolean visible) {
    Records.ChildCollection child =
        new Records.ChildCollection(childId, null, null, null, visible, null);
    return new CollectionRequests.Update(
        parentId,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        new CollectionRequests.CollectionUpdate(null, List.of(child), null),
        null);
  }

  @Test
  void grantOnParentWaterfallsToEveryDescendant() {
    long user = seedUser("Wf-Parent");
    long parent = seedCollection("wf-pnwer");
    long child1 = seedCollection("wf-pnwer-2025");
    long child2 = seedCollection("wf-pnwer-2026");
    long grandchild = seedCollection("wf-pnwer-2025-day1");
    collectionService.linkCollectionToParent(parent, child1);
    collectionService.linkCollectionToParent(parent, child2);
    collectionService.linkCollectionToParent(child1, grandchild);
    long roleId = seedMemberRole("role:wf-pnwer", user);

    propagation.setGrant(roleId, parent, AccessLevel.CLIENT, null);

    for (long coll : new long[] {parent, child1, child2, grandchild}) {
      assertThat(roleRepository.canView(user, coll)).isTrue();
      assertThat(roleRepository.isClient(user, coll)).isTrue();
    }
    assertThat(roleRepository.memberCollectionIdsForUser(user))
        .containsExactlyInAnyOrder(parent, child1, child2, grandchild);
    // Provenance: descendants carry the ORIGIN (the parent), the parent row is direct.
    assertThat(inheritedFrom(roleId, parent)).isNull();
    assertThat(inheritedFrom(roleId, child1)).isEqualTo(parent);
    assertThat(inheritedFrom(roleId, child2)).isEqualTo(parent);
    assertThat(inheritedFrom(roleId, grandchild)).isEqualTo(parent);
  }

  @Test
  void childLinkedAfterGrantInheritsWithItsSubtree() {
    long user = seedUser("Wf-Link");
    long parent = seedCollection("wf-link-parent");
    long child = seedCollection("wf-link-child");
    long grandchild = seedCollection("wf-link-grandchild");
    collectionService.linkCollectionToParent(child, grandchild);
    long roleId = seedMemberRole("role:wf-link", user);
    propagation.setGrant(roleId, parent, AccessLevel.CLIENT, null);
    assertThat(roleRepository.canView(user, child)).isFalse();

    collectionService.linkCollectionToParent(parent, child);

    assertThat(roleRepository.isClient(user, child)).isTrue();
    assertThat(roleRepository.isClient(user, grandchild)).isTrue();
    assertThat(inheritedFrom(roleId, child)).isEqualTo(parent);
    assertThat(inheritedFrom(roleId, grandchild)).isEqualTo(parent);
  }

  @Test
  void childAddedThroughCollectionUpdateFlowInherits() {
    long user = seedUser("Wf-UpdateAdd");
    long parent = seedCollection("wf-upd-parent");
    long child = seedCollection("wf-upd-child");
    long roleId = seedMemberRole("role:wf-upd", user);
    propagation.setGrant(roleId, parent, AccessLevel.CLIENT, null);

    collectionService.updateContent(parent, addChildUpdate(parent, child, null));

    assertThat(roleRepository.isClient(user, child)).isTrue();
    assertThat(inheritedFrom(roleId, child)).isEqualTo(parent);
  }

  @Test
  void childAddedHiddenThroughCollectionUpdateFlowDoesNotInherit() {
    long user = seedUser("Wf-UpdateHidden");
    long parent = seedCollection("wf-updh-parent");
    long child = seedCollection("wf-updh-child");
    long roleId = seedMemberRole("role:wf-updh", user);
    propagation.setGrant(roleId, parent, AccessLevel.CLIENT, null);

    collectionService.updateContent(parent, addChildUpdate(parent, child, false));

    assertThat(roleRepository.canView(user, child)).isFalse();
    assertThat(grantRowCount(roleId, child)).isZero();
  }

  @Test
  void removingParentGrantStripsInheritedButDirectChildGrantSurvives() {
    long user = seedUser("Wf-Remove");
    long parent = seedCollection("wf-rm-parent");
    long keeper = seedCollection("wf-rm-keeper");
    long loser = seedCollection("wf-rm-loser");
    collectionService.linkCollectionToParent(parent, keeper);
    collectionService.linkCollectionToParent(parent, loser);
    long roleId = seedMemberRole("role:wf-rm", user);
    propagation.setGrant(roleId, parent, AccessLevel.GENERAL, null);
    // Direct grant on one child: converts its inherited copy into a sticky direct row.
    propagation.setGrant(roleId, keeper, AccessLevel.CLIENT, null);

    propagation.removeGrant(roleId, parent);

    assertThat(roleRepository.canView(user, parent)).isFalse();
    assertThat(roleRepository.canView(user, loser)).isFalse();
    assertThat(roleRepository.isClient(user, keeper)).isTrue();
    assertThat(inheritedFrom(roleId, keeper)).isNull();
  }

  @Test
  void unlinkingChildStripsOnlyItsSubtree() {
    long user = seedUser("Wf-Unlink");
    long parent = seedCollection("wf-ul-parent");
    long unlinked = seedCollection("wf-ul-child");
    long grandchild = seedCollection("wf-ul-grandchild");
    long sibling = seedCollection("wf-ul-sibling");
    collectionService.linkCollectionToParent(parent, unlinked);
    collectionService.linkCollectionToParent(unlinked, grandchild);
    collectionService.linkCollectionToParent(parent, sibling);
    long roleId = seedMemberRole("role:wf-ul", user);
    propagation.setGrant(roleId, parent, AccessLevel.CLIENT, null);
    assertThat(roleRepository.isClient(user, grandchild)).isTrue();

    collectionService.updateContent(parent, removeChildrenUpdate(parent, List.of(unlinked)));

    assertThat(roleRepository.canView(user, unlinked)).isFalse();
    assertThat(roleRepository.canView(user, grandchild)).isFalse();
    assertThat(roleRepository.isClient(user, sibling)).isTrue();
    assertThat(roleRepository.isClient(user, parent)).isTrue();
  }

  @Test
  void clientBeatsGeneralAcrossOriginsAndRemovalRematerializesFromAncestor() {
    long user = seedUser("Wf-Level");
    long grandparent = seedCollection("wf-lvl-grandparent");
    long parent = seedCollection("wf-lvl-parent");
    long child = seedCollection("wf-lvl-child");
    collectionService.linkCollectionToParent(grandparent, parent);
    collectionService.linkCollectionToParent(parent, child);
    long roleId = seedMemberRole("role:wf-lvl", user);

    propagation.setGrant(roleId, grandparent, AccessLevel.GENERAL, null);
    assertThat(roleRepository.canView(user, child)).isTrue();
    assertThat(roleRepository.isClient(user, child)).isFalse();

    // CLIENT on the middle collection upgrades the child's inherited copy (new origin: parent).
    propagation.setGrant(roleId, parent, AccessLevel.CLIENT, null);
    assertThat(roleRepository.isClient(user, child)).isTrue();
    assertThat(inheritedFrom(roleId, child)).isEqualTo(parent);

    // Removing the CLIENT grant re-materializes GENERAL from the surviving grandparent grant.
    propagation.removeGrant(roleId, parent);
    assertThat(roleRepository.canView(user, parent)).isTrue();
    assertThat(roleRepository.isClient(user, parent)).isFalse();
    assertThat(roleRepository.canView(user, child)).isTrue();
    assertThat(roleRepository.isClient(user, child)).isFalse();
    assertThat(inheritedFrom(roleId, child)).isEqualTo(grandparent);
  }

  @Test
  void removingDirectChildGrantRematerializesInheritedCopy() {
    long user = seedUser("Wf-Sticky");
    long parent = seedCollection("wf-st-parent");
    long child = seedCollection("wf-st-child");
    collectionService.linkCollectionToParent(parent, child);
    long roleId = seedMemberRole("role:wf-st", user);
    propagation.setGrant(roleId, parent, AccessLevel.GENERAL, null);
    propagation.setGrant(roleId, child, AccessLevel.CLIENT, null);
    assertThat(roleRepository.isClient(user, child)).isTrue();

    propagation.removeGrant(roleId, child);

    assertThat(roleRepository.canView(user, child)).isTrue();
    assertThat(roleRepository.isClient(user, child)).isFalse();
    assertThat(inheritedFrom(roleId, child)).isEqualTo(parent);
  }

  @Test
  void hiddenLinkBlocksInheritanceForChildAndItsSubtree() {
    long user = seedUser("Wf-Hidden");
    long parent = seedCollection("wf-hd-parent");
    long visible = seedCollection("wf-hd-visible");
    long hidden = seedCollection("wf-hd-hidden");
    long hiddenChild = seedCollection("wf-hd-hidden-child");
    collectionService.linkCollectionToParent(parent, visible);
    collectionService.linkCollectionToParent(parent, hidden);
    collectionService.linkCollectionToParent(hidden, hiddenChild);
    hideLink(parent, hidden);
    long roleId = seedMemberRole("role:wf-hd", user);

    propagation.setGrant(roleId, parent, AccessLevel.CLIENT, null);

    assertThat(roleRepository.isClient(user, visible)).isTrue();
    assertThat(roleRepository.canView(user, hidden)).isFalse();
    assertThat(roleRepository.canView(user, hiddenChild)).isFalse();
    assertThat(grantRowCount(roleId, hidden)).isZero();
    assertThat(grantRowCount(roleId, hiddenChild)).isZero();
  }
}
