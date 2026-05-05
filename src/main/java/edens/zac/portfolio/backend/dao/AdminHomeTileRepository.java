package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.model.Records;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class AdminHomeTileRepository extends BaseDao {

  private static final String FIND_ALL_WITH_COVER_SQL =
      """
      SELECT t.tile_key,
             ci.image_url_web AS cover_image_url,
             t.display_order
      FROM admin_home_tile t
      LEFT JOIN content_image ci ON t.cover_image_id = ci.id
      ORDER BY t.display_order ASC
      """;

  private static final RowMapper<Records.AdminHomeTileResponse> ROW_MAPPER =
      (rs, rowNum) ->
          new Records.AdminHomeTileResponse(
              rs.getString("tile_key"),
              rs.getString("cover_image_url"),
              rs.getInt("display_order"));

  public AdminHomeTileRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  @Transactional(readOnly = true)
  public List<Records.AdminHomeTileResponse> findAllWithCover() {
    return query(FIND_ALL_WITH_COVER_SQL, ROW_MAPPER);
  }
}
