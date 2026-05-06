package edens.zac.portfolio.backend.dao;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class AdminHomeTileRepository extends BaseDao {

  /** Catalog row for an admin home tile slot. Cover URL is resolved by AdminHomeService. */
  public record TileRow(String tileKey, int displayOrder) {}

  private static final String FIND_ALL_ORDERED_SQL =
      """
      SELECT tile_key, display_order
      FROM admin_home_tile
      ORDER BY display_order ASC
      """;

  private static final RowMapper<TileRow> ROW_MAPPER =
      (rs, rowNum) -> new TileRow(rs.getString("tile_key"), rs.getInt("display_order"));

  public AdminHomeTileRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  public List<TileRow> findAllOrderedByDisplay() {
    return query(FIND_ALL_ORDERED_SQL, ROW_MAPPER);
  }
}
