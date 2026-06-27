package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.UserRatingOverrideEntity;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC access for {@code user_rating_override}. Upsert is keyed on the {@code (user_id,
 * content_id)} primary key so a user has at most one override per image. Reads are scoped by {@code
 * (user_id, collection_id)} (the secondary index) so a gallery view loads only its rows.
 */
@Component
@Slf4j
public class UserRatingOverrideRepository extends BaseDao {

  public UserRatingOverrideRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  private static final RowMapper<UserRatingOverrideEntity> ROW_MAPPER =
      (rs, rowNum) ->
          UserRatingOverrideEntity.builder()
              .userId(rs.getLong("user_id"))
              .contentId(rs.getLong("content_id"))
              .collectionId(rs.getLong("collection_id"))
              .rating(rs.getInt("rating"))
              .updatedAt(getLocalDateTime(rs, "updated_at"))
              .build();

  /**
   * Insert the override, or update its rating + collection scope + timestamp if a row already
   * exists for the (user, content) pair. Idempotent by primary key.
   */
  @Transactional
  public void upsert(UserRatingOverrideEntity entity) {
    String sql =
        """
        INSERT INTO user_rating_override (user_id, content_id, collection_id, rating, updated_at)
        VALUES (:userId, :contentId, :collectionId, :rating, now())
        ON CONFLICT (user_id, content_id)
        DO UPDATE SET rating = EXCLUDED.rating,
                      collection_id = EXCLUDED.collection_id,
                      updated_at = now()
        """;
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("userId", entity.getUserId())
            .addValue("contentId", entity.getContentId())
            .addValue("collectionId", entity.getCollectionId())
            .addValue("rating", entity.getRating());
    update(sql, params);
  }

  /** Every override this user holds within the given collection's view. */
  @Transactional(readOnly = true)
  public List<UserRatingOverrideEntity> findByUserIdAndCollectionId(
      Long userId, Long collectionId) {
    String sql =
        """
        SELECT user_id, content_id, collection_id, rating, updated_at
        FROM user_rating_override
        WHERE user_id = :userId AND collection_id = :collectionId
        ORDER BY content_id ASC
        """;
    MapSqlParameterSource params =
        createParameterSource().addValue("userId", userId).addValue("collectionId", collectionId);
    return query(sql, ROW_MAPPER, params);
  }

  /** Remove a single user's override for one image. Returns rows affected (0 or 1). */
  @Transactional
  public int deleteByUserIdAndContentId(Long userId, Long contentId) {
    String sql =
        "DELETE FROM user_rating_override WHERE user_id = :userId AND content_id = :contentId";
    MapSqlParameterSource params =
        createParameterSource().addValue("userId", userId).addValue("contentId", contentId);
    return update(sql, params);
  }
}
