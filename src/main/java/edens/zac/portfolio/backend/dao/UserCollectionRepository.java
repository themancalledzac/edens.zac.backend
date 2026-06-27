package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.UserCollectionEntity;
import edens.zac.portfolio.backend.types.CollectionRole;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class UserCollectionRepository extends BaseDao {

  public UserCollectionRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  private static final String SELECT_USER_COLLECTION =
      """
      SELECT user_id, collection_id, role, granted_at, granted_by
      FROM user_collection
      """;

  private static final RowMapper<UserCollectionEntity> ROW_MAPPER =
      (rs, rowNum) ->
          UserCollectionEntity.builder()
              .userId(rs.getLong("user_id"))
              .collectionId(rs.getLong("collection_id"))
              .role(CollectionRole.valueOf(rs.getString("role")))
              .grantedAt(getLocalDateTime(rs, "granted_at"))
              .grantedBy(getLong(rs, "granted_by"))
              .build();

  /** True when the user holds ANY membership (GENERAL or CLIENT) for the collection. */
  @Transactional(readOnly = true)
  public boolean hasMembership(Long userId, Long collectionId) {
    Integer count =
        queryForObject(
                "SELECT count(*) FROM user_collection WHERE user_id = :userId AND collection_id = :collectionId",
                (rs, n) -> rs.getInt(1),
                createParameterSource()
                    .addValue("userId", userId)
                    .addValue("collectionId", collectionId))
            .orElse(0);
    return count > 0;
  }

  /** True when the user holds a CLIENT membership for the collection (download/tag/star powers). */
  @Transactional(readOnly = true)
  public boolean hasClientMembership(Long userId, Long collectionId) {
    Integer count =
        queryForObject(
                "SELECT count(*) FROM user_collection "
                    + "WHERE user_id = :userId AND collection_id = :collectionId AND role = 'CLIENT'",
                (rs, n) -> rs.getInt(1),
                createParameterSource()
                    .addValue("userId", userId)
                    .addValue("collectionId", collectionId))
            .orElse(0);
    return count > 0;
  }

  /** All collection ids the user is a member of (any role). */
  @Transactional(readOnly = true)
  public List<Long> findCollectionIdsByUserId(Long userId) {
    return query(
        "SELECT collection_id FROM user_collection WHERE user_id = :userId ORDER BY granted_at ASC",
        (rs, n) -> rs.getLong("collection_id"),
        createParameterSource().addValue("userId", userId));
  }

  /** Every membership row for the user (used to build MeResponse.galleries). */
  @Transactional(readOnly = true)
  public List<UserCollectionEntity> findByUserId(Long userId) {
    return query(
        SELECT_USER_COLLECTION + " WHERE user_id = :userId ORDER BY granted_at ASC",
        ROW_MAPPER,
        createParameterSource().addValue("userId", userId));
  }

  /** Insert or update the membership role for (user, collection). */
  @Transactional
  public void upsertRole(Long userId, Long collectionId, CollectionRole role, Long grantedBy) {
    String sql =
        """
        INSERT INTO user_collection (user_id, collection_id, role, granted_by)
        VALUES (:userId, :collectionId, :role, :grantedBy)
        ON CONFLICT (user_id, collection_id) DO UPDATE SET role = EXCLUDED.role
        """;
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("userId", userId)
            .addValue("collectionId", collectionId)
            .addValue("role", role.name())
            .addValue("grantedBy", grantedBy);
    update(sql, params);
  }

  /** Remove the membership entirely (no access). */
  @Transactional
  public void delete(Long userId, Long collectionId) {
    update(
        "DELETE FROM user_collection WHERE user_id = :userId AND collection_id = :collectionId",
        createParameterSource().addValue("userId", userId).addValue("collectionId", collectionId));
  }

  /**
   * Re-point a source identity's collection memberships onto target (de-duped). No-op for a PERSON.
   */
  @Transactional
  public void repointMemberships(Long sourceId, Long targetId) {
    MapSqlParameterSource p =
        createParameterSource().addValue("src", sourceId).addValue("tgt", targetId);
    update(
        "DELETE FROM user_collection WHERE user_id = :src "
            + "AND collection_id IN (SELECT collection_id FROM user_collection WHERE user_id = :tgt)",
        p);
    update("UPDATE user_collection SET user_id = :tgt WHERE user_id = :src", p);
  }

  /** A row for the admin toggle UI: a collection the user is tagged in or a member of, + role. */
  public record AssociatedCollection(Long collectionId, String title, String role) {}

  @Transactional(readOnly = true)
  public List<AssociatedCollection> findAssociatedCollections(Long userId) {
    String sql =
        """
        SELECT c.id AS collection_id, c.title AS title, uc.role AS role
          FROM collection c
          JOIN (
                SELECT collection_id FROM collection_people WHERE person_id = :userId
                UNION
                SELECT collection_id FROM user_collection   WHERE user_id   = :userId
               ) assoc ON assoc.collection_id = c.id
          LEFT JOIN user_collection uc ON uc.collection_id = c.id AND uc.user_id = :userId
         ORDER BY c.title ASC
        """;
    return query(
        sql,
        (rs, n) ->
            new AssociatedCollection(
                rs.getLong("collection_id"), rs.getString("title"), rs.getString("role")),
        createParameterSource().addValue("userId", userId));
  }
}
