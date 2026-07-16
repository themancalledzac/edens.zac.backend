package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.RoleEntity;
import edens.zac.portfolio.backend.types.AccessLevel;
import edens.zac.portfolio.backend.types.RoleKind;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Role-based access data access. Roles hold per-collection grants; users join roles to inherit
 * them. Resolution unions across a user's roles with CLIENT beating GENERAL.
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
              .kind(RoleKind.valueOf(rs.getString("kind")))
              .createdAt(getLocalDateTime(rs, "created_at"))
              .createdBy(getLong(rs, "created_by"))
              .build();

  /** A collection this user can reach through a role, at its highest level. */
  public record EffectiveGrant(Long collectionId, AccessLevel level) {}

  /** A collection a role grants, at a level. */
  public record RoleCollectionGrant(Long collectionId, String title, AccessLevel level) {}

  // ---- Role CRUD ----

  @Transactional
  public Long createRole(String name, RoleKind kind, Long createdBy) {
    return insertAndReturnId(
        "INSERT INTO role (name, kind, created_by) VALUES (:name, :kind, :createdBy)",
        "id",
        createParameterSource()
            .addValue("name", name)
            .addValue("kind", kind.name())
            .addValue("createdBy", createdBy));
  }

  @Transactional(readOnly = true)
  public List<RoleEntity> findAll() {
    return query("SELECT * FROM role ORDER BY kind DESC, name ASC", ROLE_ROW_MAPPER);
  }

  @Transactional(readOnly = true)
  public Optional<RoleEntity> findById(Long roleId) {
    return queryForObject(
        "SELECT * FROM role WHERE id = :id",
        ROLE_ROW_MAPPER,
        createParameterSource().addValue("id", roleId));
  }

  @Transactional
  public void deleteRole(Long roleId) {
    update("DELETE FROM role WHERE id = :id", createParameterSource().addValue("id", roleId));
  }

  // ---- Membership ----

  @Transactional
  public void addMember(Long roleId, Long userId, Long addedBy) {
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
         ORDER BY r.kind DESC, r.name ASC
        """,
        ROLE_ROW_MAPPER,
        createParameterSource().addValue("userId", userId));
  }

  @Transactional(readOnly = true)
  public List<Long> memberUserIds(Long roleId) {
    return query(
        "SELECT user_id FROM role_member WHERE role_id = :roleId ORDER BY added_at ASC",
        (rs, n) -> rs.getLong("user_id"),
        createParameterSource().addValue("roleId", roleId));
  }

  // ---- Collection grants on a role ----

  @Transactional
  public void setCollectionGrant(
      Long roleId, Long collectionId, AccessLevel level, Long grantedBy) {
    update(
        """
        INSERT INTO role_collection (role_id, collection_id, level, granted_by)
        VALUES (:roleId, :collectionId, :level, :grantedBy)
        ON CONFLICT (role_id, collection_id) DO UPDATE SET level = EXCLUDED.level
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

  @Transactional(readOnly = true)
  public boolean isClient(Long userId, Long collectionId) {
    Integer count =
        queryForObject(
                """
                SELECT count(*) FROM role_member rm
                  JOIN role_collection rc ON rc.role_id = rm.role_id
                 WHERE rm.user_id = :userId AND rc.collection_id = :collectionId
                   AND rc.level = 'CLIENT'
                """,
                (rs, n) -> rs.getInt(1),
                createParameterSource()
                    .addValue("userId", userId)
                    .addValue("collectionId", collectionId))
            .orElse(0);
    return count > 0;
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

  @Transactional(readOnly = true)
  public List<EffectiveGrant> effectiveGrants(Long userId) {
    return query(
        """
        SELECT rc.collection_id,
               CASE WHEN bool_or(rc.level = 'CLIENT') THEN 'CLIENT' ELSE 'GENERAL' END AS level
          FROM role_member rm
          JOIN role_collection rc ON rc.role_id = rm.role_id
         WHERE rm.user_id = :userId
         GROUP BY rc.collection_id
        """,
        (rs, n) ->
            new EffectiveGrant(
                rs.getLong("collection_id"), AccessLevel.valueOf(rs.getString("level"))),
        createParameterSource().addValue("userId", userId));
  }

  /** Move the source user's role memberships onto the target, de-duped (identity merge). */
  @Transactional
  public void repointMemberships(Long sourceId, Long targetId) {
    var p = createParameterSource().addValue("src", sourceId).addValue("tgt", targetId);
    update(
        "DELETE FROM role_member WHERE user_id = :src "
            + "AND role_id IN (SELECT role_id FROM role_member WHERE user_id = :tgt)",
        p);
    update("UPDATE role_member SET user_id = :tgt WHERE user_id = :src", p);
  }
}
