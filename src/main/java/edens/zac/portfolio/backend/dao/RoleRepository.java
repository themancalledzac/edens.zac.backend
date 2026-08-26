package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.RoleEntity;
import edens.zac.portfolio.backend.types.AccessLevel;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Role-based access data access. Roles hold per-collection grants; users join roles to inherit
 * them. Resolution unions across a user's roles with the highest rank winning.
 */
@Component
@Slf4j
public class RoleRepository extends BaseDao {

  public RoleRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  private static final RowMapper<RoleEntity> ROLE_ROW_MAPPER =
      (rs, n) ->
          RoleEntity.builder()
              .id(rs.getLong("id"))
              .name(rs.getString("name"))
              .createdAt(getLocalDateTime(rs, "created_at"))
              .createdBy(getLong(rs, "created_by"))
              .build();

  /** A collection this user can reach through a role, at its highest level. */
  public record EffectiveGrant(Long collectionId, AccessLevel level) {}

  /** A collection a role grants, at a level. */
  public record RoleCollectionGrant(Long collectionId, String title, AccessLevel level) {}

  /** A member of a role, joined to their account, for the role-detail view. */
  public record RoleMember(Long userId, String email, String name) {}

  /**
   * One role granting a collection, for the collection-detail (inverse) view. The provenance pair
   * is null for a direct grant; for an inherited copy it carries the origin collection's id and
   * title so the admin UI can badge waterfalled rows.
   */
  public record CollectionRoleGrant(
      Long roleId,
      String name,
      AccessLevel level,
      Long inheritedFromCollectionId,
      String inheritedFromCollectionTitle) {}

  /**
   * A grant held on a collection: the role, the level, and its provenance. A null {@code
   * inheritedFromCollectionId} means the grant is direct; otherwise it is an inherited copy whose
   * origin (the collection holding the direct grant) is that id.
   */
  public record CollectionGrant(Long roleId, AccessLevel level, Long inheritedFromCollectionId) {
    public boolean direct() {
      return inheritedFromCollectionId == null;
    }
  }

  private static final RowMapper<CollectionGrant> COLLECTION_GRANT_ROW_MAPPER =
      (rs, n) ->
          new CollectionGrant(
              rs.getLong("role_id"),
              AccessLevel.valueOf(rs.getString("level")),
              getLong(rs, "inherited_from_collection_id"));

  /**
   * SQL rank expression for a qualified level column (e.g. {@code rank("rc.level")}), mirroring
   * {@link AccessLevel#rank()}. Takes the column name because insertInheritedGrant compares two
   * columns in one statement. ADMIN is deliberately absent: it is a computed sentinel, never
   * stored, so it can never appear in a level column. A future storable level is one edit here.
   */
  private static String rank(String levelColumn) {
    return "CASE " + levelColumn + " WHEN 'COLLABORATOR' THEN 2 WHEN 'CLIENT' THEN 1 ELSE 0 END";
  }

  // ---- Role CRUD ----

  /**
   * Create a role. Every role created through this method is SHARED, so the {@code kind} column is
   * written the constant {@code 'SHARED'} literal. The column's V45 CHECK admits both {@code
   * 'PERSONAL'} and {@code 'SHARED'} -- PERSONAL is used only by V45's own per-user backfill (one
   * role per pre-existing grant-holder), never by this method.
   */
  @Transactional
  public Long createRole(String name, Long createdBy) {
    return insertAndReturnId(
        "INSERT INTO role (name, kind, created_by) VALUES (:name, 'SHARED', :createdBy)",
        "id",
        createParameterSource().addValue("name", name).addValue("createdBy", createdBy));
  }

  @Transactional(readOnly = true)
  public List<RoleEntity> findAll() {
    return query("SELECT * FROM role ORDER BY name ASC", ROLE_ROW_MAPPER);
  }

  @Transactional(readOnly = true)
  public Optional<RoleEntity> findById(Long roleId) {
    return queryForObject(
        "SELECT * FROM role WHERE id = :id",
        ROLE_ROW_MAPPER,
        createParameterSource().addValue("id", roleId));
  }

  @Transactional
  public int deleteRole(Long roleId) {
    return update(
        "DELETE FROM role WHERE id = :id", createParameterSource().addValue("id", roleId));
  }

  // ---- Membership ----

  /**
   * Add a user to a role. Idempotent.
   *
   * <p>Rejects a tag-only {@code PERSON} row. Since V35 a person tagged in photos and an account
   * share one {@code users} table, so a person id and an account id are indistinguishable at the
   * two admin endpoints that reach here -- both pass a path variable straight through. A PERSON row
   * cannot log in today, so admitting one grants nobody anything now; the risk is the grant lying
   * dormant until that person is upgraded to an account, which then inherits it silently.
   *
   * @throws IllegalArgumentException (400) when the id is not an account
   */
  @Transactional
  public void addMember(Long roleId, Long userId, Long addedBy) {
    Boolean isAccount =
        namedParameterJdbcTemplate.queryForObject(
            "SELECT COUNT(*) > 0 FROM users WHERE id = :userId AND status <> 'PERSON'",
            createParameterSource().addValue("userId", userId),
            Boolean.class);
    if (!Boolean.TRUE.equals(isAccount)) {
      throw new IllegalArgumentException("Role membership requires an account user: " + userId);
    }
    update(
        """
        INSERT INTO role_member (role_id, user_id, added_by)
        VALUES (:roleId, :userId, :addedBy)
        ON CONFLICT (role_id, user_id) DO NOTHING
        """,
        createParameterSource()
            .addValue("roleId", roleId)
            .addValue("userId", userId)
            .addValue("addedBy", addedBy));
  }

  /**
   * Drop every {@code role_member} row the user holds, but only while that user is still a tag-only
   * {@code PERSON}. Enforces the invariant {@link #addMember} states -- no membership row may point
   * at a PERSON -- at the moment such a row would stop being harmless.
   *
   * <p>Such a row is not dormant at the authorization layer: {@link #canView} joins {@code
   * role_member} to {@code role_collection} and tests no status, so it already answers true for the
   * PERSON. What makes it harmless today is only that a PERSON has no way to authenticate. Turning
   * the row into an account supplies exactly that, and the row id does not change across the flip,
   * so the new account inherits whatever was pointing at the person the moment it can log in.
   * {@code addMember} has refused to create such rows since the S-2 fix and {@code
   * repointMemberships} drops rather than moves them, but no migration ever purged the rows that
   * predate the guard -- so the precondition is existing data rather than something an attacker has
   * to arrange.
   *
   * <p>The PERSON test lives inside the statement rather than in a caller's precondition (working
   * rule 17), so this is safe to call unconditionally from any path that may be about to change a
   * status: it deletes nothing at all for a real account, whose memberships are legitimate.
   *
   * <p>Dropping rather than preserving matches {@link #repointMemberships}: these are rows {@code
   * addMember} would refuse to create, they grant nothing in their current state, and they exist
   * only because the rule went unenforced. The count is logged at WARN so the disposition is
   * visible rather than silent.
   *
   * @param userId the {@code users.id} whose dormant grants should be dropped
   * @return the number of membership rows dropped; 0 for any user that is not a PERSON
   */
  @Transactional
  public int dropMembershipsIfPerson(Long userId) {
    int dropped =
        update(
            "DELETE FROM role_member WHERE user_id = :userId "
                + "AND EXISTS (SELECT 1 FROM users WHERE id = :userId AND status = 'PERSON')",
            createParameterSource().addValue("userId", userId));
    if (dropped > 0) {
      log.warn(
          "Dropped {} dormant role grant(s) on a PERSON row before a status change (userId={})",
          dropped,
          userId);
    }
    return dropped;
  }

  @Transactional
  public void removeMember(Long roleId, Long userId) {
    update(
        "DELETE FROM role_member WHERE role_id = :roleId AND user_id = :userId",
        createParameterSource().addValue("roleId", roleId).addValue("userId", userId));
  }

  @Transactional(readOnly = true)
  public List<RoleEntity> rolesForUser(Long userId) {
    return query(
        """
        SELECT r.* FROM role r
          JOIN role_member rm ON rm.role_id = r.id
         WHERE rm.user_id = :userId
         ORDER BY r.name ASC
        """,
        ROLE_ROW_MAPPER,
        createParameterSource().addValue("userId", userId));
  }

  @Transactional(readOnly = true)
  public List<RoleMember> membersForRole(Long roleId) {
    return query(
        """
        SELECT u.id AS user_id, u.email, u.name
          FROM role_member rm
          JOIN users u ON u.id = rm.user_id
         WHERE rm.role_id = :roleId
         ORDER BY rm.added_at ASC
        """,
        (rs, n) ->
            new RoleMember(rs.getLong("user_id"), rs.getString("email"), rs.getString("name")),
        createParameterSource().addValue("roleId", roleId));
  }

  // ---- Collection grants on a role ----

  /**
   * Upsert a DIRECT grant. Clearing {@code inherited_from_collection_id} on conflict converts an
   * inherited copy into a direct grant, so an explicit admin grant is never mistaken for (or later
   * swept away with) waterfalled rows. The audit pair is refreshed on conflict too: the row records
   * who set the CURRENT level and when (an inherited copy carries a null actor, which must not
   * survive its promotion to direct).
   */
  @Transactional
  public void setCollectionGrant(
      Long roleId, Long collectionId, AccessLevel level, Long grantedBy) {
    update(
        """
        INSERT INTO role_collection (role_id, collection_id, level, granted_by)
        VALUES (:roleId, :collectionId, :level, :grantedBy)
        ON CONFLICT (role_id, collection_id)
          DO UPDATE SET level = EXCLUDED.level,
                        granted_by = EXCLUDED.granted_by,
                        granted_at = EXCLUDED.granted_at,
                        inherited_from_collection_id = NULL
        """,
        createParameterSource()
            .addValue("roleId", roleId)
            .addValue("collectionId", collectionId)
            .addValue("level", level.name())
            .addValue("grantedBy", grantedBy));
  }

  @Transactional
  public void removeCollectionGrant(Long roleId, Long collectionId) {
    update(
        "DELETE FROM role_collection WHERE role_id = :roleId AND collection_id = :collectionId",
        createParameterSource().addValue("roleId", roleId).addValue("collectionId", collectionId));
  }

  @Transactional(readOnly = true)
  public List<RoleCollectionGrant> grantsForRole(Long roleId) {
    return query(
        """
        SELECT rc.collection_id, c.title, rc.level
          FROM role_collection rc
          JOIN collection c ON c.id = rc.collection_id
         WHERE rc.role_id = :roleId
         ORDER BY c.title ASC
        """,
        (rs, n) ->
            new RoleCollectionGrant(
                rs.getLong("collection_id"),
                rs.getString("title"),
                AccessLevel.valueOf(rs.getString("level"))),
        createParameterSource().addValue("roleId", roleId));
  }

  /**
   * The inverse of {@link #grantsForRole}: every role granting a collection, with its level and
   * waterfall provenance (origin collection id + title, null for direct grants). Ordered by name
   * (matches {@link #findAll}).
   */
  @Transactional(readOnly = true)
  public List<CollectionRoleGrant> rolesGrantingCollection(Long collectionId) {
    return query(
        """
        SELECT rc.role_id, r.name, rc.level,
               rc.inherited_from_collection_id,
               origin.title AS inherited_from_collection_title
          FROM role_collection rc
          JOIN role r ON r.id = rc.role_id
          LEFT JOIN collection origin ON origin.id = rc.inherited_from_collection_id
         WHERE rc.collection_id = :collectionId
         ORDER BY r.name ASC
        """,
        (rs, n) ->
            new CollectionRoleGrant(
                rs.getLong("role_id"),
                rs.getString("name"),
                AccessLevel.valueOf(rs.getString("level")),
                getLong(rs, "inherited_from_collection_id"),
                rs.getString("inherited_from_collection_title")),
        createParameterSource().addValue("collectionId", collectionId));
  }

  // ---- Waterfall provenance (inherited copies of direct grants) ----

  /**
   * Upsert an INHERITED copy of a direct grant held on {@code originCollectionId}. Never clobbers a
   * direct row, and never downgrades: an existing inherited row is only rewritten when the incoming
   * level outranks it, in which case the origin moves with it. Everything else is a no-op, keeping
   * direct grants sticky.
   */
  @Transactional
  public void insertInheritedGrant(
      Long roleId, Long collectionId, AccessLevel level, Long originCollectionId) {
    update(
        """
        INSERT INTO role_collection (role_id, collection_id, level, inherited_from_collection_id)
        VALUES (:roleId, :collectionId, :level, :originId)
        ON CONFLICT (role_id, collection_id) DO UPDATE
           SET level = EXCLUDED.level,
               inherited_from_collection_id = EXCLUDED.inherited_from_collection_id
         WHERE role_collection.inherited_from_collection_id IS NOT NULL
           AND\s"""
            // The \s above is load-bearing: text blocks strip trailing whitespace from every
            // line (JEP 378), so a plain space before the closing """ is silently dropped and
            // this concatenation becomes invalid SQL ("ANDCASE ..."). Do not replace it with " ".
            + rank("role_collection.level")
            + " < "
            + rank("EXCLUDED.level"),
        createParameterSource()
            .addValue("roleId", roleId)
            .addValue("collectionId", collectionId)
            .addValue("level", level.name())
            .addValue("originId", originCollectionId));
  }

  /** Delete every inherited copy of the role's direct grant on the origin, tree-wide. */
  @Transactional
  public void removeInheritedGrantsByOrigin(Long roleId, Long originCollectionId) {
    update(
        """
        DELETE FROM role_collection
         WHERE role_id = :roleId AND inherited_from_collection_id = :originId
        """,
        createParameterSource()
            .addValue("roleId", roleId)
            .addValue("originId", originCollectionId));
  }

  /** Delete one collection's inherited copy for a specific origin (used by the unlink hook). */
  @Transactional
  public void removeInheritedGrantsForCollectionByOrigin(
      Long roleId, Long collectionId, Long originCollectionId) {
    update(
        """
        DELETE FROM role_collection
         WHERE role_id = :roleId AND collection_id = :collectionId
           AND inherited_from_collection_id = :originId
        """,
        createParameterSource()
            .addValue("roleId", roleId)
            .addValue("collectionId", collectionId)
            .addValue("originId", originCollectionId));
  }

  /** The DIRECT grants held on a collection (provenance null), across all roles. */
  @Transactional(readOnly = true)
  public List<CollectionGrant> directGrantsForCollection(Long collectionId) {
    return query(
        """
        SELECT role_id, level, inherited_from_collection_id
          FROM role_collection
         WHERE collection_id = :collectionId AND inherited_from_collection_id IS NULL
         ORDER BY role_id ASC
        """,
        COLLECTION_GRANT_ROW_MAPPER,
        createParameterSource().addValue("collectionId", collectionId));
  }

  /** Every grant held on a collection -- direct and inherited -- across all roles. */
  @Transactional(readOnly = true)
  public List<CollectionGrant> allGrantsForCollection(Long collectionId) {
    return query(
        """
        SELECT role_id, level, inherited_from_collection_id
          FROM role_collection
         WHERE collection_id = :collectionId
         ORDER BY role_id ASC
        """,
        COLLECTION_GRANT_ROW_MAPPER,
        createParameterSource().addValue("collectionId", collectionId));
  }

  // ---- Resolution (the seam) ----

  @Transactional(readOnly = true)
  public boolean canView(Long userId, Long collectionId) {
    Integer count =
        queryForObject(
                """
                SELECT count(*) FROM role_member rm
                  JOIN role_collection rc ON rc.role_id = rm.role_id
                 WHERE rm.user_id = :userId AND rc.collection_id = :collectionId
                """,
                (rs, n) -> rs.getInt(1),
                createParameterSource()
                    .addValue("userId", userId)
                    .addValue("collectionId", collectionId))
            .orElse(0);
    return count > 0;
  }

  /**
   * True when the user holds a CLIENT-or-higher grant (COLLABORATOR included) on the collection.
   */
  @Transactional(readOnly = true)
  public boolean isClient(Long userId, Long collectionId) {
    Integer count =
        queryForObject(
                """
                SELECT count(*) FROM role_member rm
                  JOIN role_collection rc ON rc.role_id = rm.role_id
                 WHERE rm.user_id = :userId AND rc.collection_id = :collectionId
                   AND\s"""
                    // \s is load-bearing here too -- see the comment in insertInheritedGrant.
                    + rank("rc.level")
                    + " >= "
                    + AccessLevel.CLIENT.rank(),
                (rs, n) -> rs.getInt(1),
                createParameterSource()
                    .addValue("userId", userId)
                    .addValue("collectionId", collectionId))
            .orElse(0);
    return count > 0;
  }

  /** The user's highest stored grant level on one collection, or empty when no role grants it. */
  @Transactional(readOnly = true)
  public Optional<AccessLevel> highestLevel(Long userId, Long collectionId) {
    return queryForObject(
        """
        SELECT rc.level FROM role_member rm
          JOIN role_collection rc ON rc.role_id = rm.role_id
         WHERE rm.user_id = :userId AND rc.collection_id = :collectionId
         ORDER BY\s"""
            // \s is load-bearing here too -- see the comment in insertInheritedGrant.
            + rank("rc.level")
            + " DESC LIMIT 1",
        (rs, n) -> AccessLevel.valueOf(rs.getString("level")),
        createParameterSource().addValue("userId", userId).addValue("collectionId", collectionId));
  }

  @Transactional(readOnly = true)
  public List<Long> memberCollectionIdsForUser(Long userId) {
    return query(
        """
        SELECT DISTINCT rc.collection_id
          FROM role_member rm
          JOIN role_collection rc ON rc.role_id = rm.role_id
         WHERE rm.user_id = :userId
         ORDER BY rc.collection_id ASC
        """,
        (rs, n) -> rs.getLong("collection_id"),
        createParameterSource().addValue("userId", userId));
  }

  /** Deduped (collectionId, level) the user can reach, the highest rank winning on conflict. */
  @Transactional(readOnly = true)
  public List<EffectiveGrant> effectiveGrants(Long userId) {
    return query(
        """
        SELECT DISTINCT ON (rc.collection_id) rc.collection_id, rc.level
          FROM role_member rm
          JOIN role_collection rc ON rc.role_id = rm.role_id
         WHERE rm.user_id = :userId
         ORDER BY rc.collection_id, """
            + rank("rc.level")
            + " DESC",
        (rs, n) ->
            new EffectiveGrant(
                rs.getLong("collection_id"), AccessLevel.valueOf(rs.getString("level"))),
        createParameterSource().addValue("userId", userId));
  }

  /**
   * Move the source user's role memberships onto the target, de-duped (identity merge).
   *
   * <p>Upholds the same rule {@link #addMember} enforces: no {@code role_member} row may point at a
   * tag-only {@code PERSON}. A merge target is not required to be an account -- de-duplicating two
   * tag-only people is a normal operation -- so when the target is a PERSON the source's membership
   * rows are dropped rather than moved. They are rows {@code addMember} would refuse to create,
   * they grant nothing while they point at a PERSON, and moving them would carry an illegal state
   * across the merge instead of ending it. Dropping matches what the schema does unaided: {@code
   * role_member.user_id} is {@code ON DELETE CASCADE}, so anything left on the source vanishes when
   * the caller deletes it.
   *
   * <p>The trailing delete clears whatever the guard refused to move. It is a no-op on every merge
   * into an account, and it means this method leaves the table consistent on its own rather than
   * depending on the caller to delete the source row.
   *
   * @return the number of membership rows dropped because the target cannot hold them; 0 on every
   *     merge into an account
   */
  @Transactional
  public int repointMemberships(Long sourceId, Long targetId) {
    var p = createParameterSource().addValue("src", sourceId).addValue("tgt", targetId);
    update(
        "DELETE FROM role_member WHERE user_id = :src "
            + "AND role_id IN (SELECT role_id FROM role_member WHERE user_id = :tgt)",
        p);
    update(
        "UPDATE role_member SET user_id = :tgt WHERE user_id = :src "
            + "AND EXISTS (SELECT 1 FROM users WHERE id = :tgt AND status <> 'PERSON')",
        p);
    return update("DELETE FROM role_member WHERE user_id = :src", p);
  }
}
