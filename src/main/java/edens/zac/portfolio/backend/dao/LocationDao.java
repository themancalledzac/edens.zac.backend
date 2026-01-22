package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.LocationEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * DAO for LocationEntity using raw SQL queries.
 */
@Component
@Slf4j
public class LocationDao extends BaseDao {

    public LocationDao(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    private static final RowMapper<LocationEntity> LOCATION_ROW_MAPPER = (rs, rowNum) ->
        LocationEntity.builder()
            .id(rs.getLong("id"))
            .locationName(rs.getString("location_name"))
            .createdAt(getLocalDateTime(rs, "created_at"))
            .build();

    /**
     * Find location by exact name.
     */
    @Transactional(readOnly = true)
    public Optional<LocationEntity> findByLocationName(String locationName) {
        String sql = "SELECT id, location_name, created_at FROM location WHERE location_name = :locationName";
        MapSqlParameterSource params = createParameterSource().addValue("locationName", locationName);
        return queryForObject(sql, LOCATION_ROW_MAPPER, params);
    }

    /**
     * Find location by name (case-insensitive).
     */
    @Transactional(readOnly = true)
    public Optional<LocationEntity> findByLocationNameIgnoreCase(String locationName) {
        String sql = "SELECT id, location_name, created_at FROM location WHERE LOWER(location_name) = LOWER(:locationName)";
        MapSqlParameterSource params = createParameterSource().addValue("locationName", locationName);
        return queryForObject(sql, LOCATION_ROW_MAPPER, params);
    }

    /**
     * Find locations by name containing (case-insensitive, for autocomplete).
     */
    @Transactional(readOnly = true)
    public List<LocationEntity> findByLocationNameContainingIgnoreCase(String searchTerm) {
        String sql = "SELECT id, location_name, created_at FROM location WHERE LOWER(location_name) LIKE LOWER(:searchTerm) ORDER BY location_name ASC";
        MapSqlParameterSource params = createParameterSource().addValue("searchTerm", "%" + searchTerm + "%");
        return query(sql, LOCATION_ROW_MAPPER, params);
    }

    /**
     * Find all locations ordered by name.
     */
    @Transactional(readOnly = true)
    public List<LocationEntity> findAllByOrderByLocationNameAsc() {
        String sql = "SELECT id, location_name, created_at FROM location ORDER BY location_name ASC";
        return query(sql, LOCATION_ROW_MAPPER);
    }

    /**
     * Find location by ID.
     */
    @Transactional(readOnly = true)
    public Optional<LocationEntity> findById(Long id) {
        String sql = "SELECT id, location_name, created_at FROM location WHERE id = :id";
        MapSqlParameterSource params = createParameterSource().addValue("id", id);
        return queryForObject(sql, LOCATION_ROW_MAPPER, params);
    }

    /**
     * Check if location exists by name.
     */
    @Transactional(readOnly = true)
    public boolean existsByLocationName(String locationName) {
        String sql = "SELECT COUNT(*) > 0 FROM location WHERE location_name = :locationName";
        MapSqlParameterSource params = createParameterSource().addValue("locationName", locationName);
        Boolean result = namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class);
        return result != null && result;
    }

    /**
     * Check if location exists by name (case-insensitive).
     */
    @Transactional(readOnly = true)
    public boolean existsByLocationNameIgnoreCase(String locationName) {
        String sql = "SELECT COUNT(*) > 0 FROM location WHERE LOWER(location_name) = LOWER(:locationName)";
        MapSqlParameterSource params = createParameterSource().addValue("locationName", locationName);
        Boolean result = namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class);
        return result != null && result;
    }

    /**
     * Save a location. Returns entity with generated ID.
     */
    @Transactional
    public LocationEntity save(LocationEntity entity) {
        if (entity.getId() == null) {
            String sql = "INSERT INTO location (location_name, created_at) VALUES (:locationName, :createdAt)";
            MapSqlParameterSource params = createParameterSource()
                .addValue("locationName", entity.getLocationName())
                .addValue("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt() : LocalDateTime.now());
            Long id = insertAndReturnId(sql, "id", params);
            entity.setId(id);
            return entity;
        } else {
            String sql = "UPDATE location SET location_name = :locationName WHERE id = :id";
            MapSqlParameterSource params = createParameterSource()
                .addValue("locationName", entity.getLocationName())
                .addValue("id", entity.getId());
            update(sql, params);
            return entity;
        }
    }

    /**
     * Find or create a location by name.
     * If the location exists (case-insensitive), returns the existing one.
     * Otherwise, creates a new location and returns it.
     */
    @Transactional
    public LocationEntity findOrCreate(String locationName) {
        if (locationName == null || locationName.trim().isEmpty()) {
            return null;
        }

        String trimmedName = locationName.trim();

        // Try to find existing (case-insensitive)
        Optional<LocationEntity> existing = findByLocationNameIgnoreCase(trimmedName);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Create new
        LocationEntity newLocation = LocationEntity.builder()
            .locationName(trimmedName)
            .createdAt(LocalDateTime.now())
            .build();
        return save(newLocation);
    }

    /**
     * Delete location by ID.
     */
    @Transactional
    public void deleteById(Long id) {
        String sql = "DELETE FROM location WHERE id = :id";
        MapSqlParameterSource params = createParameterSource().addValue("id", id);
        update(sql, params);
    }
}
