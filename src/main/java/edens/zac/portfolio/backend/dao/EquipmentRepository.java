package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.ContentCameraEntity;
import edens.zac.portfolio.backend.entity.ContentFilmTypeEntity;
import edens.zac.portfolio.backend.entity.ContentLensEntity;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for camera, lens, and film type entities. Consolidates ContentCameraDao,
 * ContentLensDao, and ContentFilmTypeDao.
 */
@Component
@Slf4j
public class EquipmentRepository extends BaseDao {

  public EquipmentRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  // ============================================================
  // Camera RowMappers
  // ============================================================

  private static final RowMapper<ContentCameraEntity> CAMERA_ROW_MAPPER =
      (rs, rowNum) ->
          ContentCameraEntity.builder()
              .id(rs.getLong("id"))
              .cameraName(rs.getString("camera_name"))
              .createdAt(getLocalDateTime(rs, "created_at"))
              .build();

  private static final RowMapper<ContentCameraEntity> CAMERA_ROW_MAPPER_WITH_SERIAL =
      (rs, rowNum) ->
          ContentCameraEntity.builder()
              .id(rs.getLong("id"))
              .cameraName(rs.getString("camera_name"))
              .bodySerialNumber(getString(rs, "body_serial_number"))
              .createdAt(getLocalDateTime(rs, "created_at"))
              .build();

  // ============================================================
  // Lens RowMappers
  // ============================================================

  private static final RowMapper<ContentLensEntity> LENS_ROW_MAPPER =
      (rs, rowNum) ->
          ContentLensEntity.builder()
              .id(rs.getLong("id"))
              .lensName(rs.getString("lens_name"))
              .createdAt(getLocalDateTime(rs, "created_at"))
              .build();

  private static final RowMapper<ContentLensEntity> LENS_ROW_MAPPER_WITH_SERIAL =
      (rs, rowNum) ->
          ContentLensEntity.builder()
              .id(rs.getLong("id"))
              .lensName(rs.getString("lens_name"))
              .lensSerialNumber(getString(rs, "lens_serial_number"))
              .createdAt(getLocalDateTime(rs, "created_at"))
              .build();

  // ============================================================
  // Film Type RowMapper
  // ============================================================

  private static final RowMapper<ContentFilmTypeEntity> FILM_TYPE_ROW_MAPPER =
      (rs, rowNum) ->
          ContentFilmTypeEntity.builder()
              .id(rs.getLong("id"))
              .filmTypeName(rs.getString("film_type_name"))
              .displayName(rs.getString("display_name"))
              .defaultIso(rs.getInt("default_iso"))
              .createdAt(getLocalDateTime(rs, "created_at"))
              .build();

  // ============================================================
  // Camera Operations
  // ============================================================

  @Transactional(readOnly = true)
  public Optional<ContentCameraEntity> findCameraByBodySerialNumber(String bodySerialNumber) {
    if (bodySerialNumber == null || bodySerialNumber.trim().isEmpty()) {
      return Optional.empty();
    }
    String sql =
        "SELECT id, camera_name, body_serial_number, created_at FROM content_cameras WHERE body_serial_number = :bodySerialNumber";
    MapSqlParameterSource params =
        createParameterSource().addValue("bodySerialNumber", bodySerialNumber.trim());
    return queryForObject(sql, CAMERA_ROW_MAPPER_WITH_SERIAL, params);
  }

  @Transactional(readOnly = true)
  public Optional<ContentCameraEntity> findCameraByName(String cameraName) {
    String sql =
        "SELECT id, camera_name, created_at FROM content_cameras WHERE camera_name = :cameraName";
    MapSqlParameterSource params = createParameterSource().addValue("cameraName", cameraName);
    return queryForObject(sql, CAMERA_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public Optional<ContentCameraEntity> findCameraByNameIgnoreCase(String cameraName) {
    String sql =
        "SELECT id, camera_name, created_at FROM content_cameras WHERE LOWER(camera_name) = LOWER(:cameraName)";
    MapSqlParameterSource params = createParameterSource().addValue("cameraName", cameraName);
    return queryForObject(sql, CAMERA_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<ContentCameraEntity> findCamerasByNameContainingIgnoreCase(String searchTerm) {
    String sql =
        "SELECT id, camera_name, created_at FROM content_cameras WHERE LOWER(camera_name) LIKE LOWER(:searchTerm) ORDER BY camera_name ASC";
    MapSqlParameterSource params =
        createParameterSource().addValue("searchTerm", "%" + searchTerm + "%");
    return query(sql, CAMERA_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<ContentCameraEntity> findAllCamerasOrderByName() {
    String sql = "SELECT id, camera_name, created_at FROM content_cameras ORDER BY camera_name ASC";
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
  public List<ContentCameraEntity> findCamerasOrderByUsageCount() {
    String sql =
        """
        SELECT c.id, c.camera_name, c.created_at
        FROM content_cameras c
        LEFT JOIN content_image ci ON c.id = ci.camera_id
        GROUP BY c.id, c.camera_name, c.created_at
        ORDER BY COUNT(ci.id) DESC
        """;
    return query(sql, CAMERA_ROW_MAPPER);
  }

  @Transactional
  public ContentCameraEntity saveCamera(ContentCameraEntity entity) {
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
      String sql =
          "UPDATE content_cameras SET camera_name = :cameraName, body_serial_number = :bodySerialNumber WHERE id = :id";
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
  public Optional<ContentCameraEntity> findCameraById(Long id) {
    String sql = "SELECT id, camera_name, created_at FROM content_cameras WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    return queryForObject(sql, CAMERA_ROW_MAPPER, params);
  }

  // ============================================================
  // Lens Operations
  // ============================================================

  @Transactional(readOnly = true)
  public Optional<ContentLensEntity> findLensBySerialNumber(String lensSerialNumber) {
    if (lensSerialNumber == null || lensSerialNumber.trim().isEmpty()) {
      return Optional.empty();
    }
    String sql =
        "SELECT id, lens_name, lens_serial_number, created_at FROM content_lenses WHERE lens_serial_number = :lensSerialNumber";
    MapSqlParameterSource params =
        createParameterSource().addValue("lensSerialNumber", lensSerialNumber.trim());
    return queryForObject(sql, LENS_ROW_MAPPER_WITH_SERIAL, params);
  }

  @Transactional(readOnly = true)
  public Optional<ContentLensEntity> findLensByName(String lensName) {
    String sql = "SELECT id, lens_name, created_at FROM content_lenses WHERE lens_name = :lensName";
    MapSqlParameterSource params = createParameterSource().addValue("lensName", lensName);
    return queryForObject(sql, LENS_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public Optional<ContentLensEntity> findLensByNameIgnoreCase(String lensName) {
    String sql =
        "SELECT id, lens_name, created_at FROM content_lenses WHERE LOWER(lens_name) = LOWER(:lensName)";
    MapSqlParameterSource params = createParameterSource().addValue("lensName", lensName);
    return queryForObject(sql, LENS_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<ContentLensEntity> findLensesByNameContainingIgnoreCase(String searchTerm) {
    String sql =
        "SELECT id, lens_name, created_at FROM content_lenses WHERE LOWER(lens_name) LIKE LOWER(:searchTerm) ORDER BY lens_name ASC";
    MapSqlParameterSource params =
        createParameterSource().addValue("searchTerm", "%" + searchTerm + "%");
    return query(sql, LENS_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<ContentLensEntity> findAllLensesOrderByName() {
    String sql = "SELECT id, lens_name, created_at FROM content_lenses ORDER BY lens_name ASC";
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
  public List<ContentLensEntity> findLensesOrderByUsageCount() {
    String sql =
        """
        SELECT l.id, l.lens_name, l.created_at
        FROM content_lenses l
        LEFT JOIN content_image ci ON l.id = ci.lens_id
        GROUP BY l.id, l.lens_name, l.created_at
        ORDER BY COUNT(ci.id) DESC
        """;
    return query(sql, LENS_ROW_MAPPER);
  }

  @Transactional
  public ContentLensEntity saveLens(ContentLensEntity entity) {
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
      String sql =
          "UPDATE content_lenses SET lens_name = :lensName, lens_serial_number = :lensSerialNumber WHERE id = :id";
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
  public Optional<ContentLensEntity> findLensById(Long id) {
    String sql = "SELECT id, lens_name, created_at FROM content_lenses WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    return queryForObject(sql, LENS_ROW_MAPPER, params);
  }

  // ============================================================
  // Film Type Operations
  // ============================================================

  @Transactional(readOnly = true)
  public Optional<ContentFilmTypeEntity> findFilmTypeByName(String filmTypeName) {
    String sql =
        "SELECT id, film_type_name, display_name, default_iso, created_at FROM content_film_types WHERE film_type_name = :filmTypeName";
    MapSqlParameterSource params = createParameterSource().addValue("filmTypeName", filmTypeName);
    return queryForObject(sql, FILM_TYPE_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public Optional<ContentFilmTypeEntity> findFilmTypeByNameIgnoreCase(String filmTypeName) {
    String sql =
        "SELECT id, film_type_name, display_name, default_iso, created_at FROM content_film_types WHERE LOWER(film_type_name) = LOWER(:filmTypeName)";
    MapSqlParameterSource params = createParameterSource().addValue("filmTypeName", filmTypeName);
    return queryForObject(sql, FILM_TYPE_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public Optional<ContentFilmTypeEntity> findFilmTypeByDisplayName(String displayName) {
    String sql =
        "SELECT id, film_type_name, display_name, default_iso, created_at FROM content_film_types WHERE display_name = :displayName";
    MapSqlParameterSource params = createParameterSource().addValue("displayName", displayName);
    return queryForObject(sql, FILM_TYPE_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<ContentFilmTypeEntity> findFilmTypesBySearchTerm(String searchTerm) {
    String sql =
        """
        SELECT id, film_type_name, display_name, default_iso, created_at
        FROM content_film_types
        WHERE LOWER(film_type_name) LIKE LOWER(:pattern) OR LOWER(display_name) LIKE LOWER(:pattern)
        ORDER BY display_name ASC
        """;
    String pattern = "%" + searchTerm + "%";
    MapSqlParameterSource params = createParameterSource().addValue("pattern", pattern);
    return query(sql, FILM_TYPE_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<ContentFilmTypeEntity> findAllFilmTypesOrderByDisplayName() {
    String sql =
        "SELECT id, film_type_name, display_name, default_iso, created_at FROM content_film_types ORDER BY display_name ASC";
    return query(sql, FILM_TYPE_ROW_MAPPER);
  }

  @Transactional(readOnly = true)
  public List<ContentFilmTypeEntity> findAllFilmTypesOrderByDefaultIso() {
    String sql =
        "SELECT id, film_type_name, display_name, default_iso, created_at FROM content_film_types ORDER BY default_iso ASC";
    return query(sql, FILM_TYPE_ROW_MAPPER);
  }

  @Transactional(readOnly = true)
  public boolean existsByFilmTypeName(String filmTypeName) {
    String sql = "SELECT COUNT(*) > 0 FROM content_film_types WHERE film_type_name = :filmTypeName";
    MapSqlParameterSource params = createParameterSource().addValue("filmTypeName", filmTypeName);
    Boolean result = namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class);
    return result != null && result;
  }

  @Transactional(readOnly = true)
  public boolean existsByFilmTypeNameIgnoreCase(String filmTypeName) {
    String sql =
        "SELECT COUNT(*) > 0 FROM content_film_types WHERE LOWER(film_type_name) = LOWER(:filmTypeName)";
    MapSqlParameterSource params = createParameterSource().addValue("filmTypeName", filmTypeName);
    Boolean result = namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class);
    return result != null && result;
  }

  @Transactional(readOnly = true)
  public boolean existsByFilmTypeDisplayName(String displayName) {
    String sql = "SELECT COUNT(*) > 0 FROM content_film_types WHERE display_name = :displayName";
    MapSqlParameterSource params = createParameterSource().addValue("displayName", displayName);
    Boolean result = namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class);
    return result != null && result;
  }

  @Transactional(readOnly = true)
  public List<ContentFilmTypeEntity> findFilmTypesOrderByUsageCount() {
    String sql =
        """
        SELECT f.id, f.film_type_name, f.display_name, f.default_iso, f.created_at
        FROM content_film_types f
        LEFT JOIN content_image ci ON f.id = ci.film_type_id
        GROUP BY f.id, f.film_type_name, f.display_name, f.default_iso, f.created_at
        ORDER BY COUNT(ci.id) DESC
        """;
    return query(sql, FILM_TYPE_ROW_MAPPER);
  }

  @Transactional
  public ContentFilmTypeEntity saveFilmType(ContentFilmTypeEntity entity) {
    if (entity.getId() == null) {
      String sql =
          "INSERT INTO content_film_types (film_type_name, display_name, default_iso, created_at) VALUES (:filmTypeName, :displayName, :defaultIso, :createdAt)";
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("filmTypeName", entity.getFilmTypeName())
              .addValue("displayName", entity.getDisplayName())
              .addValue("defaultIso", entity.getDefaultIso())
              .addValue(
                  "createdAt",
                  entity.getCreatedAt() != null
                      ? entity.getCreatedAt()
                      : java.time.LocalDateTime.now());
      Long id = insertAndReturnId(sql, "id", params);
      entity.setId(id);
      return entity;
    } else {
      String sql =
          "UPDATE content_film_types SET film_type_name = :filmTypeName, display_name = :displayName, default_iso = :defaultIso WHERE id = :id";
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("filmTypeName", entity.getFilmTypeName())
              .addValue("displayName", entity.getDisplayName())
              .addValue("defaultIso", entity.getDefaultIso())
              .addValue("id", entity.getId());
      update(sql, params);
      return entity;
    }
  }

  @Transactional(readOnly = true)
  public Optional<ContentFilmTypeEntity> findFilmTypeById(Long id) {
    String sql =
        "SELECT id, film_type_name, display_name, default_iso, created_at FROM content_film_types WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    return queryForObject(sql, FILM_TYPE_ROW_MAPPER, params);
  }
}
