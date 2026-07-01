package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.UserSavedImageEntity;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** JDBC access to {@code user_saved_image}. Mirrors the BaseDao style. */
@Component
@Slf4j
public class UserSavedImageRepository extends BaseDao {

  public UserSavedImageRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  /** Insert a save; idempotent via {@code ON CONFLICT DO NOTHING} on the (user, image) PK. */
  @Transactional
  public void insert(UserSavedImageEntity entity) {
    String sql =
        """
        INSERT INTO user_saved_image (user_id, image_id)
        VALUES (:userId, :imageId)
        ON CONFLICT (user_id, image_id) DO NOTHING
        """;
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("userId", entity.getUserId())
            .addValue("imageId", entity.getImageId());
    update(sql, params);
  }

  /** Remove a save. Returns the number of rows deleted (0 if it was not saved). */
  @Transactional
  public int deleteByUserIdAndImageId(Long userId, Long imageId) {
    String sql = "DELETE FROM user_saved_image WHERE user_id = :userId AND image_id = :imageId";
    MapSqlParameterSource params =
        createParameterSource().addValue("userId", userId).addValue("imageId", imageId);
    return update(sql, params);
  }

  /** The image ids a user has saved, newest-saved first. */
  @Transactional(readOnly = true)
  public List<Long> findImageIdsByUserId(Long userId) {
    String sql =
        """
        SELECT image_id FROM user_saved_image
        WHERE user_id = :userId
        ORDER BY created_at DESC
        """;
    MapSqlParameterSource params = createParameterSource().addValue("userId", userId);
    return query(sql, (rs, n) -> rs.getLong("image_id"), params);
  }
}
