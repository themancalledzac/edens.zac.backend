package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.ContentCameraEntity;
import edens.zac.portfolio.backend.entity.ContentFilmTypeEntity;
import edens.zac.portfolio.backend.entity.ContentLensEntity;
import edens.zac.portfolio.backend.types.FilmFormat;
import java.time.LocalDateTime;
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

  private static final String SELECT_CAMERA =
      """
      SELECT id, camera_name, is_film, default_film_format, created_at
      FROM content_cameras
      """;

  private static final RowMapper<ContentCameraEntity> CAMERA_ROW_MAPPER =
      (rs, rowNum) ->
          ContentCameraEntity.builder()
              .id(rs.getLong("id"))
              .cameraName(rs.getString("camera_name"))
              .isFilm(rs.getBoolean("is_film"))
              .defaultFilmFormat(
                  getString(rs, "default_film_format") != null
                      ? FilmFormat.valueOf(rs.getString("default_film_format"))
                      : null)
              .createdAt(getLocalDateTime(rs, "created_at"))
              .build();

  private static final RowMapper<ContentCameraEntity> CAMERA_ROW_MAPPER_WITH_SERIAL =
      (rs, rowNum) ->
          ContentCameraEntity.builder()
              .id(rs.getLong("id"))
              .cameraName(rs.getString("camera_name"))
              .bodySerialNumber(getString(rs, "body_serial_number"))
              .isFilm(rs.getBoolean("is_film"))
              .defaultFilmFormat(
                  getString(rs, "default_film_format") != null
                      ? FilmFormat.valueOf(rs.getString("default_film_format"))
                      : null)
              .createdAt(getLocalDateTime(rs, "created_at"))
              .build();

  private static final String SELECT_LENS =
      """
      SELECT id, lens_name, created_at
      FROM content_lenses
      """;

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

  private static final String SELECT_FILM_TYPE =
      """
      SELECT id, film_type_name, display_name, default_iso, created_at
      FROM content_film_types
      """;

  private static final RowMapper<ContentFilmTypeEntity> FILM_TYPE_ROW_MAPPER =
      (rs, rowNum) ->
          ContentFilmTypeEntity.builder()
              .id(rs.getLong("id"))
              .filmTypeName(rs.getString("film_type_name"))
              .displayName(rs.getString("display_name"))
              .defaultIso(rs.getInt("default_iso"))
              .createdAt(getLocalDateTime(rs, "created_at"))
              .build();

  @Transactional(readOnly = true)
  public Optional<ContentCameraEntity> findCameraByBodySerialNumber(String bodySerialNumber) {
    if (bodySerialNumber == null || bodySerialNumber.trim().isEmpty()) {
      return Optional.empty();
    }
    String sql =
        "SELECT id, camera_name, body_serial_number, is_film, default_film_format, created_at FROM content_cameras WHERE body_serial_number = :bodySerialNumber";
    MapSqlParameterSource params =
        createParameterSource().addValue("bodySerialNumber", bodySerialNumber.trim());
    return queryForObject(sql, CAMERA_ROW_MAPPER_WITH_SERIAL, params);
  }

  @Transactional(readOnly = true)
  public Optional<ContentCameraEntity> findCameraByNameIgnoreCase(String cameraName) {
    String sql = SELECT_CAMERA + " WHERE LOWER(camera_name) = LOWER(:cameraName)";
    MapSqlParameterSource params = createParameterSource().addValue("cameraName", cameraName);
    return queryForObject(sql, CAMERA_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<ContentCameraEntity> findAllCamerasOrderByName() {
    String sql = SELECT_CAMERA + " ORDER BY camera_name ASC";
    return query(sql, CAMERA_ROW_MAPPER);
  }

  @Transactional
  public ContentCameraEntity saveCamera(ContentCameraEntity entity) {
    if (entity.getId() == null) {
      String sql =
          "INSERT INTO content_cameras (camera_name, body_serial_number, is_film, default_film_format, created_at) VALUES (:cameraName, :bodySerialNumber, :isFilm, :defaultFilmFormat, :createdAt)";
      if (entity.getCreatedAt() == null) {
        entity.setCreatedAt(LocalDateTime.now());
      }
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("cameraName", entity.getCameraName())
              .addValue("bodySerialNumber", entity.getBodySerialNumber())
              .addValue("isFilm", entity.getIsFilm() != null ? entity.getIsFilm() : Boolean.FALSE)
              .addValue(
                  "defaultFilmFormat",
                  entity.getDefaultFilmFormat() != null
                      ? entity.getDefaultFilmFormat().name()
                      : null)
              .addValue("createdAt", entity.getCreatedAt());
      Long id = insertAndReturnId(sql, "id", params);
      entity.setId(id);
      return entity;
    } else {
      String sql =
          "UPDATE content_cameras SET camera_name = :cameraName, body_serial_number = :bodySerialNumber, is_film = :isFilm, default_film_format = :defaultFilmFormat WHERE id = :id";
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("cameraName", entity.getCameraName())
              .addValue("bodySerialNumber", entity.getBodySerialNumber())
              .addValue("isFilm", entity.getIsFilm() != null ? entity.getIsFilm() : Boolean.FALSE)
              .addValue(
                  "defaultFilmFormat",
                  entity.getDefaultFilmFormat() != null
                      ? entity.getDefaultFilmFormat().name()
                      : null)
              .addValue("id", entity.getId());
      update(sql, params);
      return entity;
    }
  }

  /** Update only the film metadata (is_film, default_film_format) for a camera by id. */
  @Transactional
  public void updateCameraFilmMetadata(Long id, Boolean isFilm, FilmFormat defaultFilmFormat) {
    String sql =
        "UPDATE content_cameras SET is_film = :isFilm, default_film_format = :defaultFilmFormat WHERE id = :id";
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("isFilm", isFilm != null ? isFilm : Boolean.FALSE)
            .addValue(
                "defaultFilmFormat", defaultFilmFormat != null ? defaultFilmFormat.name() : null)
            .addValue("id", id);
    update(sql, params);
  }

  @Transactional(readOnly = true)
  public Optional<ContentCameraEntity> findCameraById(Long id) {
    String sql = SELECT_CAMERA + " WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    return queryForObject(sql, CAMERA_ROW_MAPPER, params);
  }

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
  public Optional<ContentLensEntity> findLensByNameIgnoreCase(String lensName) {
    String sql = SELECT_LENS + " WHERE LOWER(lens_name) = LOWER(:lensName)";
    MapSqlParameterSource params = createParameterSource().addValue("lensName", lensName);
    return queryForObject(sql, LENS_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<ContentLensEntity> findAllLensesOrderByName() {
    String sql = SELECT_LENS + " ORDER BY lens_name ASC";
    return query(sql, LENS_ROW_MAPPER);
  }

  @Transactional(readOnly = true)
  public boolean existsByLensNameIgnoreCase(String lensName) {
    String sql =
        "SELECT COUNT(*) > 0 FROM content_lenses WHERE LOWER(lens_name) = LOWER(:lensName)";
    MapSqlParameterSource params = createParameterSource().addValue("lensName", lensName);
    Boolean result = namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class);
    return result != null && result;
  }

  @Transactional
  public ContentLensEntity saveLens(ContentLensEntity entity) {
    if (entity.getId() == null) {
      String sql =
          "INSERT INTO content_lenses (lens_name, lens_serial_number, created_at) VALUES (:lensName, :lensSerialNumber, :createdAt)";
      if (entity.getCreatedAt() == null) {
        entity.setCreatedAt(LocalDateTime.now());
      }
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("lensName", entity.getLensName())
              .addValue("lensSerialNumber", entity.getLensSerialNumber())
              .addValue("createdAt", entity.getCreatedAt());
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
    String sql = SELECT_LENS + " WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    return queryForObject(sql, LENS_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public Optional<ContentFilmTypeEntity> findFilmTypeByNameIgnoreCase(String filmTypeName) {
    String sql = SELECT_FILM_TYPE + " WHERE LOWER(film_type_name) = LOWER(:filmTypeName)";
    MapSqlParameterSource params = createParameterSource().addValue("filmTypeName", filmTypeName);
    return queryForObject(sql, FILM_TYPE_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<ContentFilmTypeEntity> findAllFilmTypesOrderByDisplayName() {
    String sql = SELECT_FILM_TYPE + " ORDER BY display_name ASC";
    return query(sql, FILM_TYPE_ROW_MAPPER);
  }

  @Transactional(readOnly = true)
  public boolean existsByFilmTypeNameIgnoreCase(String filmTypeName) {
    String sql =
        "SELECT COUNT(*) > 0 FROM content_film_types WHERE LOWER(film_type_name) = LOWER(:filmTypeName)";
    MapSqlParameterSource params = createParameterSource().addValue("filmTypeName", filmTypeName);
    Boolean result = namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class);
    return result != null && result;
  }

  @Transactional
  public ContentFilmTypeEntity saveFilmType(ContentFilmTypeEntity entity) {
    if (entity.getId() == null) {
      String sql =
          "INSERT INTO content_film_types (film_type_name, display_name, default_iso, created_at) VALUES (:filmTypeName, :displayName, :defaultIso, :createdAt)";
      if (entity.getCreatedAt() == null) {
        entity.setCreatedAt(LocalDateTime.now());
      }
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("filmTypeName", entity.getFilmTypeName())
              .addValue("displayName", entity.getDisplayName())
              .addValue("defaultIso", entity.getDefaultIso())
              .addValue("createdAt", entity.getCreatedAt());
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
    String sql = SELECT_FILM_TYPE + " WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    return queryForObject(sql, FILM_TYPE_ROW_MAPPER, params);
  }
}
