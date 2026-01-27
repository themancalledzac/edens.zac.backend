package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.ContentCameraEntity;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class ContentCameraDao extends BaseDao {

  public ContentCameraDao(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  private static final RowMapper<ContentCameraEntity> CAMERA_ROW_MAPPER =
      (rs, rowNum) -> {
        return ContentCameraEntity.builder()
            .id(rs.getLong("id"))
            .cameraName(rs.getString("camera_name"))
            .bodySerialNumber(getString(rs, "body_serial_number"))
            .createdAt(getLocalDateTime(rs, "created_at"))
            .build();
      };

  @Transactional(readOnly = true)
  public Optional<ContentCameraEntity> findByCameraName(String cameraName) {
    String sql =
        "SELECT id, camera_name, body_serial_number, created_at FROM content_cameras WHERE camera_name = :cameraName";
    MapSqlParameterSource params = createParameterSource().addValue("cameraName", cameraName);
    return queryForObject(sql, CAMERA_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public Optional<ContentCameraEntity> findByCameraNameIgnoreCase(String cameraName) {
    String sql =
        "SELECT id, camera_name, body_serial_number, created_at FROM content_cameras WHERE LOWER(camera_name) = LOWER(:cameraName)";
    MapSqlParameterSource params = createParameterSource().addValue("cameraName", cameraName);
    return queryForObject(sql, CAMERA_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<ContentCameraEntity> findByCameraNameContainingIgnoreCase(String searchTerm) {
    String sql =
        "SELECT id, camera_name, body_serial_number, created_at FROM content_cameras WHERE LOWER(camera_name) LIKE LOWER(:searchTerm) ORDER BY camera_name ASC";
    MapSqlParameterSource params =
        createParameterSource().addValue("searchTerm", "%" + searchTerm + "%");
    return query(sql, CAMERA_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<ContentCameraEntity> findAllByOrderByCameraNameAsc() {
    String sql = "SELECT id, camera_name, body_serial_number, created_at FROM content_cameras ORDER BY camera_name ASC";
    return query(sql, CAMERA_ROW_MAPPER);
  }

  @Transactional(readOnly = true)
  public boolean existsByCameraName(String cameraName) {
    String sql = "SELECT COUNT(*) > 0 FROM content_cameras WHERE camera_name = :cameraName";
    MapSqlParameterSource params = createParameterSource().addValue("cameraName", cameraName);
    Boolean result = namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class);
    return result != null && result;
  }

  @Transactional(readOnly = true)
  public boolean existsByCameraNameIgnoreCase(String cameraName) {
    String sql =
        "SELECT COUNT(*) > 0 FROM content_cameras WHERE LOWER(camera_name) = LOWER(:cameraName)";
    MapSqlParameterSource params = createParameterSource().addValue("cameraName", cameraName);
    Boolean result = namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class);
    return result != null && result;
  }

  @Transactional(readOnly = true)
  public List<ContentCameraEntity> findAllOrderByUsageCountDesc() {
    String sql =
        """
            SELECT c.id, c.camera_name, c.body_serial_number, c.created_at
            FROM content_cameras c
            LEFT JOIN content_image ci ON c.id = ci.camera_id
            GROUP BY c.id, c.camera_name, c.body_serial_number, c.created_at
            ORDER BY COUNT(ci.id) DESC
            """;
    return query(sql, CAMERA_ROW_MAPPER);
  }

  @Transactional
  public ContentCameraEntity save(ContentCameraEntity entity) {
    if (entity.getId() == null) {
      String sql =
          "INSERT INTO content_cameras (camera_name, body_serial_number, created_at) VALUES (:cameraName, :bodySerialNumber, :createdAt)";
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("cameraName", entity.getCameraName())
              .addValue("bodySerialNumber", entity.getBodySerialNumber())
              .addValue(
                  "createdAt",
                  entity.getCreatedAt() != null
                      ? entity.getCreatedAt()
                      : java.time.LocalDateTime.now());
      Long id = insertAndReturnId(sql, "id", params);
      entity.setId(id);
      return entity;
    } else {
      String sql = "UPDATE content_cameras SET camera_name = :cameraName, body_serial_number = :bodySerialNumber WHERE id = :id";
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("cameraName", entity.getCameraName())
              .addValue("bodySerialNumber", entity.getBodySerialNumber())
              .addValue("id", entity.getId());
      update(sql, params);
      return entity;
    }
  }

  @Transactional(readOnly = true)
  public Optional<ContentCameraEntity> findById(Long id) {
    String sql = "SELECT id, camera_name, body_serial_number, created_at FROM content_cameras WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    return queryForObject(sql, CAMERA_ROW_MAPPER, params);
  }
}
