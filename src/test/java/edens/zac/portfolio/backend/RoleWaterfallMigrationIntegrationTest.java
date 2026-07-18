package edens.zac.portfolio.backend;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.dao.RoleRepository;
import edens.zac.portfolio.backend.services.CollectionService;
import edens.zac.portfolio.backend.types.AccessLevel;
import edens.zac.portfolio.backend.types.RoleKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies the V47 backfill: existing direct grants are materialized down the collection tree as
 * inherited copies carrying their origin. Flyway already applied V47 to the (then empty) container
 * schema, so this seeds a pre-waterfall state -- a tree plus direct-only {@code role_collection}
 * rows written straight through the repository -- and re-runs the V47 backfill statement, mirroring
 * how {@link RoleMigrationIntegrationTest} re-runs the V45 backfill.
 */
class RoleWaterfallMigrationIntegrationTest extends AbstractPostgresIntegrationTest {

  /** The WITH RECURSIVE backfill from V47__role_collection_waterfall.sql, verbatim. */
  private static final String V47_BACKFILL =
      """
      WITH RECURSIVE descendant AS (
        SELECT rc.role_id,
               rc.collection_id AS origin_id,
               rc.level,
               cct.referenced_collection_id AS descendant_id
          FROM role_collection rc
          JOIN collection_content cc ON cc.collection_id = rc.collection_id AND cc.visible = true
          JOIN content_collection cct ON cct.id = cc.content_id
         WHERE rc.inherited_from_collection_id IS NULL
           AND cct.referenced_collection_id IS NOT NULL
        UNION
        SELECT d.role_id, d.origin_id, d.level, cct.referenced_collection_id
          FROM descendant d
          JOIN collection_content cc ON cc.collection_id = d.descendant_id AND cc.visible = true
          JOIN content_collection cct ON cct.id = cc.content_id
         WHERE cct.referenced_collection_id IS NOT NULL
      )
      INSERT INTO role_collection (role_id, collection_id, level, inherited_from_collection_id)
      SELECT DISTINCT ON (d.role_id, d.descendant_id)
             d.role_id, d.descendant_id, d.level, d.origin_id
        FROM descendant d
       WHERE NOT EXISTS (SELECT 1 FROM role_collection rc
                          WHERE rc.role_id = d.role_id AND rc.collection_id = d.descendant_id)
       ORDER BY d.role_id, d.descendant_id, (d.level = 'CLIENT') DESC
      ON CONFLICT (role_id, collection_id) DO NOTHING
      """;

  @Autowired private JdbcTemplate jdbc;
  @Autowired private RoleRepository roleRepository;
  @Autowired private CollectionService collectionService;

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

  private Long inheritedFrom(long roleId, long collectionId) {
    return jdbc.queryForObject(
        "SELECT inherited_from_collection_id FROM role_collection"
            + " WHERE role_id=? AND collection_id=?",
        Long.class,
        roleId,
        collectionId);
  }

  @Test
  void backfillMaterializesDirectGrantsDownVisibleTree() {
    long user = seedUser("Backfill");
    long parent = seedCollection("bf-pnwer");
    long child = seedCollection("bf-pnwer-2025");
    long grandchild = seedCollection("bf-pnwer-2025-day1");
    long hidden = seedCollection("bf-pnwer-hidden");
    long selfGranted = seedCollection("bf-pnwer-2026");
    collectionService.linkCollectionToParent(parent, child);
    collectionService.linkCollectionToParent(child, grandchild);
    collectionService.linkCollectionToParent(parent, hidden);
    collectionService.linkCollectionToParent(parent, selfGranted);
    jdbc.update(
        "UPDATE collection_content cc SET visible = false FROM content_collection cct"
            + " WHERE cc.content_id = cct.id AND cc.collection_id = ?"
            + " AND cct.referenced_collection_id = ?",
        parent,
        hidden);

    // Pre-waterfall state: direct rows only, written straight through the repository (which does
    // not propagate) -- the shape the table had the moment V47's ALTER ran in prod.
    long roleId = roleRepository.createRole("role:bf-pnwer", RoleKind.SHARED, null);
    roleRepository.addMember(roleId, user, null);
    roleRepository.setCollectionGrant(roleId, parent, AccessLevel.CLIENT, null);
    roleRepository.setCollectionGrant(roleId, selfGranted, AccessLevel.GENERAL, null);
    assertThat(roleRepository.canView(user, child)).isFalse();

    jdbc.execute(V47_BACKFILL);

    // Visible descendants inherit CLIENT with the origin recorded.
    assertThat(roleRepository.isClient(user, child)).isTrue();
    assertThat(roleRepository.isClient(user, grandchild)).isTrue();
    assertThat(inheritedFrom(roleId, child)).isEqualTo(parent);
    assertThat(inheritedFrom(roleId, grandchild)).isEqualTo(parent);
    // The hidden link blocks inheritance entirely.
    assertThat(roleRepository.canView(user, hidden)).isFalse();
    // A descendant with its own direct grant is left untouched (direct wins).
    assertThat(roleRepository.canView(user, selfGranted)).isTrue();
    assertThat(roleRepository.isClient(user, selfGranted)).isFalse();
    assertThat(inheritedFrom(roleId, selfGranted)).isNull();
  }
}
