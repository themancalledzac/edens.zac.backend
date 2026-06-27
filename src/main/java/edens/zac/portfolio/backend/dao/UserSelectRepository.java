package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.UserSelectEntity;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** JDBC access to {@code user_selects}. Mirrors the BaseDao style. */
@Component
@Slf4j
public class UserSelectRepository extends BaseDao {

  public UserSelectRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  private static final RowMapper<UserSelectEntity> USER_SELECT_ROW_MAPPER =
      (rs, rowNum) ->
          UserSelectEntity.builder()
              .userId(rs.getLong("user_id"))
              .contentId(rs.getLong("content_id"))
              .collectionId(rs.getLong("collection_id"))
              .createdAt(getLocalDateTime(rs, "created_at"))
              .build();

  /** Insert a select; idempotent via {@code ON CONFLICT DO NOTHING} on the (user, content) PK. */
  @Transactional
  public void insert(UserSelectEntity entity) {
    String sql =
        """
        INSERT INTO user_selects (user_id, content_id, collection_id)
        VALUES (:userId, :contentId, :collectionId)
        ON CONFLICT (user_id, content_id) DO NOTHING
        """;
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("userId", entity.getUserId())
            .addValue("contentId", entity.getContentId())
            .addValue("collectionId", entity.getCollectionId());
    update(sql, params);
  }

  /** Remove a select. Returns the number of rows deleted (0 if it was not selected). */
  @Transactional
  public int deleteByUserIdAndContentId(Long userId, Long contentId) {
    String sql = "DELETE FROM user_selects WHERE user_id = :userId AND content_id = :contentId";
    MapSqlParameterSource params =
        createParameterSource().addValue("userId", userId).addValue("contentId", contentId);
    return update(sql, params);
  }

  /** The selected image ids for a user within one collection, newest-selected first. */
  @Transactional(readOnly = true)
  public List<Long> findContentIdsByUserIdAndCollectionId(Long userId, Long collectionId) {
    String sql =
        """
        SELECT content_id FROM user_selects
        WHERE user_id = :userId AND collection_id = :collectionId
        ORDER BY created_at DESC
        """;
    MapSqlParameterSource params =
        createParameterSource().addValue("userId", userId).addValue("collectionId", collectionId);
    return query(sql, (rs, n) -> rs.getLong("content_id"), params);
  }

  /** Every select a user holds, across all collections, newest-selected first. */
  @Transactional(readOnly = true)
  public List<UserSelectEntity> findByUserId(Long userId) {
    String sql =
        """
        SELECT user_id, content_id, collection_id, created_at FROM user_selects
        WHERE user_id = :userId
        ORDER BY created_at DESC
        """;
    MapSqlParameterSource params = createParameterSource().addValue("userId", userId);
    return query(sql, USER_SELECT_ROW_MAPPER, params);
  }
}
