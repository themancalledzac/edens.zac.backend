package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.LocationEntity;
import edens.zac.portfolio.backend.model.Records;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Repository for LocationEntity using raw SQL queries. */
@Component
@Slf4j
public class LocationRepository extends BaseDao {

  public LocationRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  private static final RowMapper<LocationEntity> LOCATION_ROW_MAPPER =
      (rs, rowNum) ->
          LocationEntity.builder()
              .id(rs.getLong("id"))
              .locationName(rs.getString("location_name"))
              .createdAt(getLocalDateTime(rs, "created_at"))
              .build();

  @Transactional(readOnly = true)
  public Optional<LocationEntity> findByLocationName(String locationName) {
    String sql =
        "SELECT id, location_name, created_at FROM location WHERE location_name = :locationName";
    MapSqlParameterSource params = createParameterSource().addValue("locationName", locationName);
    return queryForObject(sql, LOCATION_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public Optional<LocationEntity> findByLocationNameIgnoreCase(String locationName) {
    String sql =
        "SELECT id, location_name, created_at FROM location WHERE LOWER(location_name) = LOWER(:locationName)";
    MapSqlParameterSource params = createParameterSource().addValue("locationName", locationName);
    return queryForObject(sql, LOCATION_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<LocationEntity> findAllByOrderByLocationNameAsc() {
    String sql = "SELECT id, location_name, created_at FROM location ORDER BY location_name ASC";
    return query(sql, LOCATION_ROW_MAPPER);
  }

  @Transactional(readOnly = true)
  public Optional<LocationEntity> findById(Long id) {
    String sql = "SELECT id, location_name, created_at FROM location WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    return queryForObject(sql, LOCATION_ROW_MAPPER, params);
  }

  @Transactional
  public LocationEntity save(LocationEntity entity) {
    if (entity.getId() == null) {
      String sql =
          "INSERT INTO location (location_name, created_at) VALUES (:locationName, :createdAt)";
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("locationName", entity.getLocationName())
              .addValue(
                  "createdAt",
                  entity.getCreatedAt() != null ? entity.getCreatedAt() : LocalDateTime.now());
      Long id = insertAndReturnId(sql, "id", params);
      entity.setId(id);
      return entity;
    } else {
      String sql = "UPDATE location SET location_name = :locationName WHERE id = :id";
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("locationName", entity.getLocationName())
              .addValue("id", entity.getId());
      update(sql, params);
      return entity;
    }
  }

  @Transactional
  public LocationEntity findOrCreate(String locationName) {
    if (locationName == null || locationName.trim().isEmpty()) {
      return null;
    }

    String trimmedName = locationName.trim();

    Optional<LocationEntity> existing = findByLocationNameIgnoreCase(trimmedName);
    if (existing.isPresent()) {
      return existing.get();
    }

    LocationEntity newLocation =
        LocationEntity.builder().locationName(trimmedName).createdAt(LocalDateTime.now()).build();
    return save(newLocation);
  }

  /**
   * Find all locations that have at least one visible collection or at least one image, with counts
   * of collections and orphan images (images not in any visible collection at this location).
   */
  @Transactional(readOnly = true)
  public List<Records.LocationWithCounts> findLocationsWithVisibleContent() {
    String sql =
        """
        SELECT l.id, l.location_name,
          COUNT(DISTINCT c.id) AS collection_count,
          COUNT(DISTINCT CASE
            WHEN ci.id IS NOT NULL
              AND NOT EXISTS (
                SELECT 1 FROM collection_content cc2
                JOIN collection c2 ON cc2.collection_id = c2.id
                WHERE cc2.content_id = ci.id
                  AND c2.location_id = l.id
                  AND c2.visible = true
                  AND cc2.visible = true
              )
            THEN ci.id
          END) AS orphan_image_count
        FROM location l
        LEFT JOIN collection c ON c.location_id = l.id AND c.visible = true
        LEFT JOIN content_image ci ON ci.location_id = l.id
        GROUP BY l.id, l.location_name
        HAVING COUNT(DISTINCT c.id) > 0
            OR COUNT(DISTINCT CASE
                 WHEN ci.id IS NOT NULL
                   AND NOT EXISTS (
                     SELECT 1 FROM collection_content cc2
                     JOIN collection c2 ON cc2.collection_id = c2.id
                     WHERE cc2.content_id = ci.id
                       AND c2.location_id = l.id
                       AND c2.visible = true
                       AND cc2.visible = true
                   )
                 THEN ci.id
               END) > 0
        ORDER BY l.location_name ASC
        """;
    return query(
        sql,
        (rs, rowNum) ->
            new Records.LocationWithCounts(
                rs.getLong("id"),
                rs.getString("location_name"),
                rs.getInt("collection_count"),
                rs.getInt("orphan_image_count")));
  }

  @Transactional
  public void deleteById(Long id) {
    String sql = "DELETE FROM location WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    update(sql, params);
  }
}
