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
 * Fifth waterfall hook: toggling an existing parent-to-child link's {@code visible} flag through
 * the collection-update flow re-syncs the child subtree's inherited grants. The V47 backfill and
 * every other propagation path gate on {@code cc.visible = true}, so a toggle that skipped this
 * hook would leave the subtree either silently over-granted (stale inherited rows after hiding) or
 * under-granted (nothing materialized on reveal until an unrelated write). Covers both directions,
 * both wiring sites (the {@code newValue} existing-entry branch and the {@code prev} branch),
 * subtree scope, sibling isolation, and direct-grant stickiness -- mirroring {@link
 * RoleGrantPropagationServiceIntegrationTest}.
 */
class RoleGrantVisibilityToggleIntegrationTest extends AbstractPostgresIntegrationTest {

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

  /** Hide the parent-to-child membership link ({@code cc.visible = false}) out-of-band. */
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

  private static Records.ChildCollection child(long collectionId, boolean visible) {
    return new Records.ChildCollection(collectionId, null, null, null, visible, null);
  }

  /** Wrap a {@link CollectionRequests.CollectionUpdate} into a bare collection-update request. */
  private static CollectionRequests.Update collectionsUpdate(
      long parentId, CollectionRequests.CollectionUpdate collections) {
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
        collections,
        null);
  }

  /** Toggle an EXISTING link's visibility through the {@code newValue} branch (site 1). */
  private CollectionRequests.Update newValueVisibility(
      long parentId, long childId, boolean visible) {
    return collectionsUpdate(
        parentId,
        new CollectionRequests.CollectionUpdate(null, List.of(child(childId, visible)), null));
  }

  /** Toggle an EXISTING link's visibility through the {@code prev} branch (site 2). */
  private CollectionRequests.Update prevVisibility(long parentId, long childId, boolean visible) {
    return collectionsUpdate(
        parentId,
        new CollectionRequests.CollectionUpdate(List.of(child(childId, visible)), null, null));
  }

  @Test
  void hidingLinkViaNewValueStripsChildSubtree() {
    long user = seedUser("Tg-NvHide");
    long parent = seedCollection("tg-nvh-parent");
    long child = seedCollection("tg-nvh-child");
    long grandchild = seedCollection("tg-nvh-grandchild");
    collectionService.linkCollectionToParent(parent, child);
    collectionService.linkCollectionToParent(child, grandchild);
    long roleId = seedMemberRole("role:tg-nvh", user);
    // Grant on the parent waterfalls to the visible subtree.
    roleGrantSetClient(roleId, parent);
    assertThat(roleRepository.isClient(user, child)).isTrue();
    assertThat(roleRepository.isClient(user, grandchild)).isTrue();

    collectionService.updateContent(parent, newValueVisibility(parent, child, false));

    assertThat(roleRepository.canView(user, child)).isFalse();
    assertThat(roleRepository.canView(user, grandchild)).isFalse();
    assertThat(grantRowCount(roleId, child)).isZero();
    assertThat(grantRowCount(roleId, grandchild)).isZero();
    // Parent's own direct grant is untouched.
    assertThat(roleRepository.isClient(user, parent)).isTrue();
    assertThat(inheritedFrom(roleId, parent)).isNull();
  }

  @Test
  void revealingLinkViaNewValueMaterializesChildSubtree() {
    long user = seedUser("Tg-NvShow");
    long parent = seedCollection("tg-nvs-parent");
    long child = seedCollection("tg-nvs-child");
    long grandchild = seedCollection("tg-nvs-grandchild");
    collectionService.linkCollectionToParent(parent, child);
    collectionService.linkCollectionToParent(child, grandchild);
    hideLink(parent, child);
    long roleId = seedMemberRole("role:tg-nvs", user);
    roleGrantSetClient(roleId, parent);
    // Hidden link blocks inheritance until revealed.
    assertThat(roleRepository.canView(user, child)).isFalse();

    collectionService.updateContent(parent, newValueVisibility(parent, child, true));

    assertThat(roleRepository.isClient(user, child)).isTrue();
    assertThat(roleRepository.isClient(user, grandchild)).isTrue();
    assertThat(inheritedFrom(roleId, child)).isEqualTo(parent);
    assertThat(inheritedFrom(roleId, grandchild)).isEqualTo(parent);
  }

  @Test
  void hidingLinkViaPrevStripsChildSubtree() {
    long user = seedUser("Tg-PvHide");
    long parent = seedCollection("tg-pvh-parent");
    long child = seedCollection("tg-pvh-child");
    long grandchild = seedCollection("tg-pvh-grandchild");
    collectionService.linkCollectionToParent(parent, child);
    collectionService.linkCollectionToParent(child, grandchild);
    long roleId = seedMemberRole("role:tg-pvh", user);
    roleGrantSetClient(roleId, parent);
    assertThat(roleRepository.isClient(user, grandchild)).isTrue();

    collectionService.updateContent(parent, prevVisibility(parent, child, false));

    assertThat(roleRepository.canView(user, child)).isFalse();
    assertThat(roleRepository.canView(user, grandchild)).isFalse();
    assertThat(roleRepository.isClient(user, parent)).isTrue();
  }

  @Test
  void revealingLinkViaPrevMaterializesChildSubtree() {
    long user = seedUser("Tg-PvShow");
    long parent = seedCollection("tg-pvs-parent");
    long child = seedCollection("tg-pvs-child");
    long grandchild = seedCollection("tg-pvs-grandchild");
    collectionService.linkCollectionToParent(parent, child);
    collectionService.linkCollectionToParent(child, grandchild);
    hideLink(parent, child);
    long roleId = seedMemberRole("role:tg-pvs", user);
    roleGrantSetClient(roleId, parent);
    assertThat(roleRepository.canView(user, child)).isFalse();

    collectionService.updateContent(parent, prevVisibility(parent, child, true));

    assertThat(roleRepository.isClient(user, child)).isTrue();
    assertThat(roleRepository.isClient(user, grandchild)).isTrue();
    assertThat(inheritedFrom(roleId, child)).isEqualTo(parent);
    assertThat(inheritedFrom(roleId, grandchild)).isEqualTo(parent);
  }

  @Test
  void togglingOneChildLeavesSiblingSubtreeUntouched() {
    long user = seedUser("Tg-Sibling");
    long parent = seedCollection("tg-sib-parent");
    long hiddenChild = seedCollection("tg-sib-hidden");
    long sibling = seedCollection("tg-sib-keep");
    collectionService.linkCollectionToParent(parent, hiddenChild);
    collectionService.linkCollectionToParent(parent, sibling);
    long roleId = seedMemberRole("role:tg-sib", user);
    roleGrantSetClient(roleId, parent);
    assertThat(roleRepository.isClient(user, hiddenChild)).isTrue();
    assertThat(roleRepository.isClient(user, sibling)).isTrue();

    collectionService.updateContent(parent, newValueVisibility(parent, hiddenChild, false));

    assertThat(roleRepository.canView(user, hiddenChild)).isFalse();
    // Sibling keeps its inherited grant; hiding one link does not touch the other subtree.
    assertThat(roleRepository.isClient(user, sibling)).isTrue();
    assertThat(inheritedFrom(roleId, sibling)).isEqualTo(parent);
  }

  @Test
  void directChildGrantSurvivesLinkHidden() {
    long user = seedUser("Tg-Direct");
    long parent = seedCollection("tg-dir-parent");
    long child = seedCollection("tg-dir-child");
    collectionService.linkCollectionToParent(parent, child);
    long roleId = seedMemberRole("role:tg-dir", user);
    roleGrantSetGeneral(roleId, parent);
    // Child gets its own direct CLIENT grant, converting the inherited copy into a sticky direct
    // row.
    roleGrantSetClient(roleId, child);
    assertThat(roleRepository.isClient(user, child)).isTrue();
    assertThat(inheritedFrom(roleId, child)).isNull();

    collectionService.updateContent(parent, newValueVisibility(parent, child, false));

    // Hiding the link strips only inherited copies; the child's own direct grant is sticky.
    assertThat(roleRepository.isClient(user, child)).isTrue();
    assertThat(inheritedFrom(roleId, child)).isNull();
  }

  // Direct grant helpers routed through the propagation service (mirrors setGrant usage).
  @Autowired private RoleGrantPropagationService propagation;

  private void roleGrantSetClient(long roleId, long collectionId) {
    propagation.setGrant(roleId, collectionId, AccessLevel.CLIENT, null);
  }

  private void roleGrantSetGeneral(long roleId, long collectionId) {
    propagation.setGrant(roleId, collectionId, AccessLevel.GENERAL, null);
  }
}
