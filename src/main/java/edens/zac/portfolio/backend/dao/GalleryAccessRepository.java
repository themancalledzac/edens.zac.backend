package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.GalleryAccessEntity;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class GalleryAccessRepository extends BaseDao {

  public GalleryAccessRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  private static final String SELECT_GALLERY_ACCESS =
      """
      SELECT id, user_id, collection_id, can_download, can_tag, granted_by, granted_at, expires_at
      FROM gallery_access
      """;

  private static final RowMapper<GalleryAccessEntity> GALLERY_ACCESS_ROW_MAPPER =
      (rs, rowNum) ->
          GalleryAccessEntity.builder()
              .id(rs.getLong("id"))
              .userId(rs.getLong("user_id"))
              .collectionId(rs.getLong("collection_id"))
              .canDownload(rs.getBoolean("can_download"))
              .canTag(rs.getBoolean("can_tag"))
              .grantedBy(getLong(rs, "granted_by"))
              .grantedAt(getLocalDateTime(rs, "granted_at"))
              .expiresAt(getLocalDateTime(rs, "expires_at"))
              .build();

  @Transactional(readOnly = true)
  public List<GalleryAccessEntity> findByUserId(Long userId) {
    String sql = SELECT_GALLERY_ACCESS + " WHERE user_id = :userId ORDER BY granted_at ASC";
    MapSqlParameterSource params = createParameterSource().addValue("userId", userId);
    return query(sql, GALLERY_ACCESS_ROW_MAPPER, params);
  }

  /** True if a grant already exists for the (user, collection) pair; backs idempotent upserts. */
  @Transactional(readOnly = true)
  public boolean existsByUserIdAndCollectionId(Long userId, Long collectionId) {
    Integer count =
        queryForObject(
                "SELECT count(*) FROM gallery_access "
                    + "WHERE user_id = :userId AND collection_id = :collectionId",
                (rs, n) -> rs.getInt(1),
                createParameterSource()
                    .addValue("userId", userId)
                    .addValue("collectionId", collectionId))
            .orElse(0);
    return count > 0;
  }

  @Transactional
  public Long insert(GalleryAccessEntity entity) {
    String sql =
        """
        INSERT INTO gallery_access (user_id, collection_id, can_download, can_tag, granted_by, expires_at)
        VALUES (:userId, :collectionId, :canDownload, :canTag, :grantedBy, :expiresAt)
        """;
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("userId", entity.getUserId())
            .addValue("collectionId", entity.getCollectionId())
            .addValue("canDownload", entity.isCanDownload())
            .addValue("canTag", entity.isCanTag())
            .addValue("grantedBy", entity.getGrantedBy())
            .addValue("expiresAt", entity.getExpiresAt());
    return insertAndReturnId(sql, "id", params);
  }
}
