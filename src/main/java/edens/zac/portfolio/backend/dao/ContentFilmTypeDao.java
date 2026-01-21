package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.ContentFilmTypeEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * DAO for ContentFilmTypeEntity using raw SQL queries.
 * Replaces ContentFilmTypeRepository.
 */
@Component
@Slf4j
public class ContentFilmTypeDao extends BaseDao {

    ContentFilmTypeDao(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    private static final RowMapper<ContentFilmTypeEntity> FILM_TYPE_ROW_MAPPER = (rs, rowNum) -> {
        ContentFilmTypeEntity entity = ContentFilmTypeEntity.builder()
            .id(rs.getLong("id"))
            .filmTypeName(rs.getString("film_type_name"))
            .displayName(rs.getString("display_name"))
            .defaultIso(rs.getInt("default_iso"))
            .createdAt(getLocalDateTime(rs, "created_at"))
            .build();
        return entity;
    };

    @Transactional(readOnly = true)
    public Optional<ContentFilmTypeEntity> findByFilmTypeName(String filmTypeName) {
        String sql = "SELECT id, film_type_name, display_name, default_iso, created_at FROM content_film_types WHERE film_type_name = :filmTypeName";
        MapSqlParameterSource params = createParameterSource().addValue("filmTypeName", filmTypeName);
        return queryForObject(sql, FILM_TYPE_ROW_MAPPER, params);
    }

    @Transactional(readOnly = true)
    public Optional<ContentFilmTypeEntity> findByFilmTypeNameIgnoreCase(String filmTypeName) {
        String sql = "SELECT id, film_type_name, display_name, default_iso, created_at FROM content_film_types WHERE LOWER(film_type_name) = LOWER(:filmTypeName)";
        MapSqlParameterSource params = createParameterSource().addValue("filmTypeName", filmTypeName);
        return queryForObject(sql, FILM_TYPE_ROW_MAPPER, params);
    }

    @Transactional(readOnly = true)
    public Optional<ContentFilmTypeEntity> findByDisplayName(String displayName) {
        String sql = "SELECT id, film_type_name, display_name, default_iso, created_at FROM content_film_types WHERE display_name = :displayName";
        MapSqlParameterSource params = createParameterSource().addValue("displayName", displayName);
        return queryForObject(sql, FILM_TYPE_ROW_MAPPER, params);
    }

    @Transactional(readOnly = true)
    public List<ContentFilmTypeEntity> findBySearchTerm(String searchTerm) {
        String sql = """
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
    public List<ContentFilmTypeEntity> findAllByOrderByDisplayNameAsc() {
        String sql = "SELECT id, film_type_name, display_name, default_iso, created_at FROM content_film_types ORDER BY display_name ASC";
        return query(sql, FILM_TYPE_ROW_MAPPER);
    }

    @Transactional(readOnly = true)
    public List<ContentFilmTypeEntity> findAllByOrderByDefaultIsoAsc() {
        String sql = "SELECT id, film_type_name, display_name, default_iso, created_at FROM content_film_types ORDER BY default_iso ASC";
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
        String sql = "SELECT COUNT(*) > 0 FROM content_film_types WHERE LOWER(film_type_name) = LOWER(:filmTypeName)";
        MapSqlParameterSource params = createParameterSource().addValue("filmTypeName", filmTypeName);
        Boolean result = namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class);
        return result != null && result;
    }

    @Transactional(readOnly = true)
    public boolean existsByDisplayName(String displayName) {
        String sql = "SELECT COUNT(*) > 0 FROM content_film_types WHERE display_name = :displayName";
        MapSqlParameterSource params = createParameterSource().addValue("displayName", displayName);
        Boolean result = namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class);
        return result != null && result;
    }

    @Transactional(readOnly = true)
    public List<ContentFilmTypeEntity> findAllOrderByUsageCountDesc() {
        String sql = """
            SELECT f.id, f.film_type_name, f.display_name, f.default_iso, f.created_at
            FROM content_film_types f
            LEFT JOIN content_image ci ON f.id = ci.film_type_id
            GROUP BY f.id, f.film_type_name, f.display_name, f.default_iso, f.created_at
            ORDER BY COUNT(ci.id) DESC
            """;
        return query(sql, FILM_TYPE_ROW_MAPPER);
    }

    @Transactional
    public ContentFilmTypeEntity save(ContentFilmTypeEntity entity) {
        if (entity.getId() == null) {
            String sql = "INSERT INTO content_film_types (film_type_name, display_name, default_iso, created_at) VALUES (:filmTypeName, :displayName, :defaultIso, :createdAt)";
            MapSqlParameterSource params = createParameterSource()
                .addValue("filmTypeName", entity.getFilmTypeName())
                .addValue("displayName", entity.getDisplayName())
                .addValue("defaultIso", entity.getDefaultIso())
                .addValue("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt() : java.time.LocalDateTime.now());
            Long id = insertAndReturnId(sql, "id", params);
            entity.setId(id);
            return entity;
        } else {
            String sql = "UPDATE content_film_types SET film_type_name = :filmTypeName, display_name = :displayName, default_iso = :defaultIso WHERE id = :id";
            MapSqlParameterSource params = createParameterSource()
                .addValue("filmTypeName", entity.getFilmTypeName())
                .addValue("displayName", entity.getDisplayName())
                .addValue("defaultIso", entity.getDefaultIso())
                .addValue("id", entity.getId());
            update(sql, params);
            return entity;
        }
    }

    @Transactional(readOnly = true)
    public Optional<ContentFilmTypeEntity> findById(Long id) {
        String sql = "SELECT id, film_type_name, display_name, default_iso, created_at FROM content_film_types WHERE id = :id";
        MapSqlParameterSource params = createParameterSource().addValue("id", id);
        return queryForObject(sql, FILM_TYPE_ROW_MAPPER, params);
    }
}
