package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.UserFollowedCollectionEntity;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** JDBC access to {@code user_followed_collection}. Mirrors the BaseDao style. */
@Component
@Slf4j
public class UserFollowedCollectionRepository extends BaseDao {

  public UserFollowedCollectionRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  /**
   * Insert a follow; idempotent via {@code ON CONFLICT DO NOTHING} on the (user, collection) PK.
   */
  @Transactional
  public void insert(UserFollowedCollectionEntity entity) {
    String sql =
        """
        INSERT INTO user_followed_collection (user_id, collection_id)
        VALUES (:userId, :collectionId)
        ON CONFLICT (user_id, collection_id) DO NOTHING
        """;
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("userId", entity.getUserId())
            .addValue("collectionId", entity.getCollectionId());
    update(sql, params);
  }

  /** Remove a follow. Returns the number of rows deleted (0 if it was not followed). */
  @Transactional
  public int deleteByUserIdAndCollectionId(Long userId, Long collectionId) {
    String sql =
        "DELETE FROM user_followed_collection "
            + "WHERE user_id = :userId AND collection_id = :collectionId";
    MapSqlParameterSource params =
        createParameterSource().addValue("userId", userId).addValue("collectionId", collectionId);
    return update(sql, params);
  }

  /** The collection ids a user follows, newest-followed first. */
  @Transactional(readOnly = true)
  public List<Long> findCollectionIdsByUserId(Long userId) {
    String sql =
        """
        SELECT collection_id FROM user_followed_collection
        WHERE user_id = :userId
        ORDER BY created_at DESC
        """;
    MapSqlParameterSource params = createParameterSource().addValue("userId", userId);
    return query(sql, (rs, n) -> rs.getLong("collection_id"), params);
  }
}
