package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.LocationEntity;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.services.SlugUtil;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
              .slug(rs.getString("slug"))
              .createdAt(getLocalDateTime(rs, "created_at"))
              .build();

  @Transactional(readOnly = true)
  public Optional<LocationEntity> findByLocationName(String locationName) {
    String sql =
        "SELECT id, location_name, slug, created_at FROM location WHERE location_name = :locationName";
    MapSqlParameterSource params = createParameterSource().addValue("locationName", locationName);
    return queryForObject(sql, LOCATION_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public Optional<LocationEntity> findByLocationNameIgnoreCase(String locationName) {
    String sql =
        "SELECT id, location_name, slug, created_at FROM location WHERE LOWER(location_name) = LOWER(:locationName)";
    MapSqlParameterSource params = createParameterSource().addValue("locationName", locationName);
    return queryForObject(sql, LOCATION_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<LocationEntity> findAllByOrderByLocationNameAsc() {
    String sql =
        "SELECT id, location_name, slug, created_at FROM location ORDER BY location_name ASC";
    return query(sql, LOCATION_ROW_MAPPER);
  }

  @Transactional(readOnly = true)
  public Optional<LocationEntity> findById(Long id) {
    String sql = "SELECT id, location_name, slug, created_at FROM location WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    return queryForObject(sql, LOCATION_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public Map<Long, LocationEntity> findByIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return Map.of();
    }

    String sql = "SELECT id, location_name, slug, created_at FROM location WHERE id IN (:ids)";
    MapSqlParameterSource params = createParameterSource().addValue("ids", ids);
    List<LocationEntity> locations = query(sql, LOCATION_ROW_MAPPER, params);

    return locations.stream().collect(Collectors.toMap(LocationEntity::getId, loc -> loc));
  }

  @Transactional(readOnly = true)
  public Optional<LocationEntity> findBySlug(String slug) {
    String sql = "SELECT id, location_name, slug, created_at FROM location WHERE slug = :slug";
    MapSqlParameterSource params = createParameterSource().addValue("slug", slug);
    return queryForObject(sql, LOCATION_ROW_MAPPER, params);
  }

  @Transactional
  public LocationEntity save(LocationEntity entity) {
    if (entity.getSlug() == null || entity.getSlug().isEmpty()) {
      entity.setSlug(SlugUtil.generateSlug(entity.getLocationName()));
    }
    if (entity.getId() == null) {
      String sql =
          "INSERT INTO location (location_name, slug, created_at) VALUES (:locationName, :slug, :createdAt)";
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("locationName", entity.getLocationName())
              .addValue("slug", entity.getSlug())
              .addValue(
                  "createdAt",
                  entity.getCreatedAt() != null ? entity.getCreatedAt() : LocalDateTime.now());
      Long id = insertAndReturnId(sql, "id", params);
      entity.setId(id);
      return entity;
    } else {
      String sql = "UPDATE location SET location_name = :locationName, slug = :slug WHERE id = :id";
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("locationName", entity.getLocationName())
              .addValue("slug", entity.getSlug())
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

    String slug = SlugUtil.generateSlug(trimmedName);
    Optional<LocationEntity> existing = findBySlug(slug);
    if (existing.isPresent()) {
      return existing.get();
    }

    LocationEntity newLocation =
        LocationEntity.builder().locationName(trimmedName).createdAt(LocalDateTime.now()).build();
    return save(newLocation);
  }

  // ============================================================
  // Image Location Join Table Operations
  // ============================================================

  @Transactional
  public void saveImageLocations(Long imageId, List<Long> locationIds) {
    String deleteSql = "DELETE FROM content_image_locations WHERE image_id = :imageId";
    MapSqlParameterSource deleteParams = createParameterSource().addValue("imageId", imageId);
    update(deleteSql, deleteParams);

    if (locationIds != null && !locationIds.isEmpty()) {
      String insertSql =
          "INSERT INTO content_image_locations (image_id, location_id) VALUES (:imageId, :locationId) ON CONFLICT DO NOTHING";
      MapSqlParameterSource[] batchParams =
          locationIds.stream()
              .map(
                  locationId ->
                      createParameterSource()
                          .addValue("imageId", imageId)
                          .addValue("locationId", locationId))
              .toArray(MapSqlParameterSource[]::new);
      batchUpdate(insertSql, batchParams);
    }
  }

  @Transactional(readOnly = true)
  public List<Long> findImageLocationIds(Long imageId) {
    String sql = "SELECT location_id FROM content_image_locations WHERE image_id = :imageId";
    MapSqlParameterSource params = createParameterSource().addValue("imageId", imageId);
    return namedParameterJdbcTemplate.queryForList(sql, params, Long.class);
  }

  @Transactional(readOnly = true)
  public Map<Long, List<LocationEntity>> findLocationsByContentIds(List<Long> contentIds) {
    if (contentIds == null || contentIds.isEmpty()) {
      return Map.of();
    }

    String sql =
        """
        SELECT cil.image_id, l.id, l.location_name, l.slug, l.created_at
        FROM content_image_locations cil
        JOIN location l ON cil.location_id = l.id
        WHERE cil.image_id IN (:contentIds)
        ORDER BY l.location_name ASC
        """;
    MapSqlParameterSource params = createParameterSource().addValue("contentIds", contentIds);

    Map<Long, List<LocationEntity>> result = new HashMap<>();
    namedParameterJdbcTemplate.query(
        sql,
        params,
        rs -> {
          Long imageId = rs.getLong("image_id");
          LocationEntity entity =
              LocationEntity.builder()
                  .id(rs.getLong("id"))
                  .locationName(rs.getString("location_name"))
                  .slug(rs.getString("slug"))
                  .createdAt(getLocalDateTime(rs, "created_at"))
                  .build();
          result.computeIfAbsent(imageId, k -> new ArrayList<>()).add(entity);
        });

    return result;
  }

  // ============================================================
  // Collection Location Join Table Operations
  // ============================================================

  @Transactional
  public void saveCollectionLocations(Long collectionId, List<Long> locationIds) {
    String deleteSql = "DELETE FROM collection_locations WHERE collection_id = :collectionId";
    MapSqlParameterSource deleteParams =
        createParameterSource().addValue("collectionId", collectionId);
    update(deleteSql, deleteParams);

    if (locationIds != null && !locationIds.isEmpty()) {
      String insertSql =
          "INSERT INTO collection_locations (collection_id, location_id) VALUES (:collectionId, :locationId) ON CONFLICT DO NOTHING";
      MapSqlParameterSource[] batchParams =
          locationIds.stream()
              .map(
                  locationId ->
                      createParameterSource()
                          .addValue("collectionId", collectionId)
                          .addValue("locationId", locationId))
              .toArray(MapSqlParameterSource[]::new);
      batchUpdate(insertSql, batchParams);
    }
  }

  @Transactional(readOnly = true)
  public List<Long> findCollectionLocationIds(Long collectionId) {
    String sql = "SELECT location_id FROM collection_locations WHERE collection_id = :collectionId";
    MapSqlParameterSource params = createParameterSource().addValue("collectionId", collectionId);
    return namedParameterJdbcTemplate.queryForList(sql, params, Long.class);
  }

  @Transactional(readOnly = true)
  public List<LocationEntity> findCollectionLocations(Long collectionId) {
    String sql =
        """
        SELECT l.id, l.location_name, l.slug, l.created_at
        FROM location l
        JOIN collection_locations cl ON l.id = cl.location_id
        WHERE cl.collection_id = :collectionId
        ORDER BY l.location_name ASC
        """;
    MapSqlParameterSource params = createParameterSource().addValue("collectionId", collectionId);
    return query(sql, LOCATION_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public Map<Long, List<LocationEntity>> findLocationsByCollectionIds(List<Long> collectionIds) {
    if (collectionIds == null || collectionIds.isEmpty()) {
      return Map.of();
    }

    String sql =
        """
        SELECT cl.collection_id, l.id, l.location_name, l.slug, l.created_at
        FROM collection_locations cl
        JOIN location l ON cl.location_id = l.id
        WHERE cl.collection_id IN (:collectionIds)
        ORDER BY l.location_name ASC
        """;
    MapSqlParameterSource params = createParameterSource().addValue("collectionIds", collectionIds);

    Map<Long, List<LocationEntity>> result = new HashMap<>();
    namedParameterJdbcTemplate.query(
        sql,
        params,
        rs -> {
          Long collectionId = rs.getLong("collection_id");
          LocationEntity entity =
              LocationEntity.builder()
                  .id(rs.getLong("id"))
                  .locationName(rs.getString("location_name"))
                  .slug(rs.getString("slug"))
                  .createdAt(getLocalDateTime(rs, "created_at"))
                  .build();
          result.computeIfAbsent(collectionId, k -> new ArrayList<>()).add(entity);
        });

    return result;
  }

  // ============================================================
  // Aggregate Queries
  // ============================================================

  /**
   * Find all locations that have at least one visible collection or at least one image, with counts
   * of collections and orphan images (images not in any visible collection at this location).
   */
  @Transactional(readOnly = true)
  public List<Records.LocationWithCounts> findLocationsWithVisibleContent() {
    String sql =
        """
        SELECT l.id, l.location_name, l.slug,
          COUNT(DISTINCT c.id) AS collection_count,
          COUNT(DISTINCT CASE
            WHEN cil.image_id IS NOT NULL
              AND NOT EXISTS (
                SELECT 1 FROM collection_content cc2
                JOIN collection c2 ON cc2.collection_id = c2.id
                JOIN collection_locations cl2 ON c2.id = cl2.collection_id
                WHERE cc2.content_id = cil.image_id
                  AND cl2.location_id = l.id
                  AND c2.visible = true
                  AND cc2.visible = true
              )
            THEN cil.image_id
          END) AS orphan_image_count
        FROM location l
        LEFT JOIN collection_locations cl ON cl.location_id = l.id
        LEFT JOIN collection c ON c.id = cl.collection_id AND c.visible = true
        LEFT JOIN content_image_locations cil ON cil.location_id = l.id
        GROUP BY l.id, l.location_name, l.slug
        HAVING COUNT(DISTINCT c.id) > 0
            OR COUNT(DISTINCT CASE
                 WHEN cil.image_id IS NOT NULL
                   AND NOT EXISTS (
                     SELECT 1 FROM collection_content cc2
                     JOIN collection c2 ON cc2.collection_id = c2.id
                     JOIN collection_locations cl2 ON c2.id = cl2.collection_id
                     WHERE cc2.content_id = cil.image_id
                       AND cl2.location_id = l.id
                       AND c2.visible = true
                       AND cc2.visible = true
                   )
                 THEN cil.image_id
               END) > 0
        ORDER BY l.location_name ASC
        """;
    return query(
        sql,
        (rs, rowNum) ->
            new Records.LocationWithCounts(
                rs.getLong("id"),
                rs.getString("location_name"),
                rs.getString("slug"),
                rs.getInt("collection_count"),
                rs.getInt("orphan_image_count")));
  }

  @Transactional
  public void clearAllAssociationsByLocationId(Long locationId) {
    MapSqlParameterSource params = createParameterSource().addValue("locationId", locationId);
    update("DELETE FROM collection_locations WHERE location_id = :locationId", params);
    update("DELETE FROM content_image_locations WHERE location_id = :locationId", params);
  }

  @Transactional
  public void deleteById(Long id) {
    String sql = "DELETE FROM location WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    update(sql, params);
  }
}
