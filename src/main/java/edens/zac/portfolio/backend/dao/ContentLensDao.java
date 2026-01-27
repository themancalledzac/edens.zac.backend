package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.ContentLensEntity;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class ContentLensDao extends BaseDao {

  public ContentLensDao(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  private static final RowMapper<ContentLensEntity> LENS_ROW_MAPPER =
      (rs, rowNum) -> {
        return ContentLensEntity.builder()
            .id(rs.getLong("id"))
            .lensName(rs.getString("lens_name"))
            .lensSerialNumber(getString(rs, "lens_serial_number"))
            .createdAt(getLocalDateTime(rs, "created_at"))
            .build();
      };

  @Transactional(readOnly = true)
  public Optional<ContentLensEntity> findByLensName(String lensName) {
    String sql = "SELECT id, lens_name, lens_serial_number, created_at FROM content_lenses WHERE lens_name = :lensName";
    MapSqlParameterSource params = createParameterSource().addValue("lensName", lensName);
    return queryForObject(sql, LENS_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public Optional<ContentLensEntity> findByLensNameIgnoreCase(String lensName) {
    String sql =
        "SELECT id, lens_name, lens_serial_number, created_at FROM content_lenses WHERE LOWER(lens_name) = LOWER(:lensName)";
    MapSqlParameterSource params = createParameterSource().addValue("lensName", lensName);
    return queryForObject(sql, LENS_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<ContentLensEntity> findByLensNameContainingIgnoreCase(String searchTerm) {
    String sql =
        "SELECT id, lens_name, lens_serial_number, created_at FROM content_lenses WHERE LOWER(lens_name) LIKE LOWER(:searchTerm) ORDER BY lens_name ASC";
    MapSqlParameterSource params =
        createParameterSource().addValue("searchTerm", "%" + searchTerm + "%");
    return query(sql, LENS_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<ContentLensEntity> findAllByOrderByLensNameAsc() {
    String sql = "SELECT id, lens_name, lens_serial_number, created_at FROM content_lenses ORDER BY lens_name ASC";
    return query(sql, LENS_ROW_MAPPER);
  }

  @Transactional(readOnly = true)
  public boolean existsByLensName(String lensName) {
    String sql = "SELECT COUNT(*) > 0 FROM content_lenses WHERE lens_name = :lensName";
    MapSqlParameterSource params = createParameterSource().addValue("lensName", lensName);
    Boolean result = namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class);
    return result != null && result;
  }

  @Transactional(readOnly = true)
  public boolean existsByLensNameIgnoreCase(String lensName) {
    String sql =
        "SELECT COUNT(*) > 0 FROM content_lenses WHERE LOWER(lens_name) = LOWER(:lensName)";
    MapSqlParameterSource params = createParameterSource().addValue("lensName", lensName);
    Boolean result = namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class);
    return result != null && result;
  }

  @Transactional(readOnly = true)
  public List<ContentLensEntity> findAllOrderByUsageCountDesc() {
    String sql =
        """
            SELECT l.id, l.lens_name, l.lens_serial_number, l.created_at
            FROM content_lenses l
            LEFT JOIN content_image ci ON l.id = ci.lens_id
            GROUP BY l.id, l.lens_name, l.lens_serial_number, l.created_at
            ORDER BY COUNT(ci.id) DESC
            """;
    return query(sql, LENS_ROW_MAPPER);
  }

  @Transactional
  public ContentLensEntity save(ContentLensEntity entity) {
    if (entity.getId() == null) {
      String sql =
          "INSERT INTO content_lenses (lens_name, lens_serial_number, created_at) VALUES (:lensName, :lensSerialNumber, :createdAt)";
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("lensName", entity.getLensName())
              .addValue("lensSerialNumber", entity.getLensSerialNumber())
              .addValue(
                  "createdAt",
                  entity.getCreatedAt() != null
                      ? entity.getCreatedAt()
                      : java.time.LocalDateTime.now());
      Long id = insertAndReturnId(sql, "id", params);
      entity.setId(id);
      return entity;
    } else {
      String sql = "UPDATE content_lenses SET lens_name = :lensName, lens_serial_number = :lensSerialNumber WHERE id = :id";
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("lensName", entity.getLensName())
              .addValue("lensSerialNumber", entity.getLensSerialNumber())
              .addValue("id", entity.getId());
      update(sql, params);
      return entity;
    }
  }

  @Transactional(readOnly = true)
  public Optional<ContentLensEntity> findById(Long id) {
    String sql = "SELECT id, lens_name, lens_serial_number, created_at FROM content_lenses WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    return queryForObject(sql, LENS_ROW_MAPPER, params);
  }
}
