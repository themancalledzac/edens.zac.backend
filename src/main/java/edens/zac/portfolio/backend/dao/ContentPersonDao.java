package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class ContentPersonDao extends BaseDao {

    public ContentPersonDao(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    private static final RowMapper<ContentPersonEntity> PERSON_ROW_MAPPER = (rs, rowNum) -> {
        return ContentPersonEntity.builder()
            .id(rs.getLong("id"))
            .personName(rs.getString("person_name"))
            .createdAt(getLocalDateTime(rs, "created_at"))
            .build();
    };

    @Transactional(readOnly = true)
    public Optional<ContentPersonEntity> findByPersonName(String personName) {
        String sql = "SELECT id, person_name, created_at FROM content_people WHERE person_name = :personName";
        MapSqlParameterSource params = createParameterSource().addValue("personName", personName);
        return queryForObject(sql, PERSON_ROW_MAPPER, params);
    }

    @Transactional(readOnly = true)
    public Optional<ContentPersonEntity> findByPersonNameIgnoreCase(String personName) {
        String sql = "SELECT id, person_name, created_at FROM content_people WHERE LOWER(person_name) = LOWER(:personName)";
        MapSqlParameterSource params = createParameterSource().addValue("personName", personName);
        return queryForObject(sql, PERSON_ROW_MAPPER, params);
    }

    @Transactional(readOnly = true)
    public List<ContentPersonEntity> findByPersonNameContainingIgnoreCase(String searchTerm) {
        String sql = "SELECT id, person_name, created_at FROM content_people WHERE LOWER(person_name) LIKE LOWER(:searchTerm) ORDER BY person_name ASC";
        MapSqlParameterSource params = createParameterSource().addValue("searchTerm", "%" + searchTerm + "%");
        return query(sql, PERSON_ROW_MAPPER, params);
    }

    @Transactional(readOnly = true)
    public List<ContentPersonEntity> findAllByOrderByPersonNameAsc() {
        String sql = "SELECT id, person_name, created_at FROM content_people ORDER BY person_name ASC";
        return query(sql, PERSON_ROW_MAPPER);
    }

    @Transactional(readOnly = true)
    public boolean existsByPersonName(String personName) {
        String sql = "SELECT COUNT(*) > 0 FROM content_people WHERE person_name = :personName";
        MapSqlParameterSource params = createParameterSource().addValue("personName", personName);
        Boolean result = namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class);
        return result != null && result;
    }

    @Transactional(readOnly = true)
    public boolean existsByPersonNameIgnoreCase(String personName) {
        String sql = "SELECT COUNT(*) > 0 FROM content_people WHERE LOWER(person_name) = LOWER(:personName)";
        MapSqlParameterSource params = createParameterSource().addValue("personName", personName);
        Boolean result = namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class);
        return result != null && result;
    }

    @Transactional(readOnly = true)
    public List<ContentPersonEntity> findAllOrderByImageCountDesc() {
        String sql = """
            SELECT p.id, p.person_name, p.created_at
            FROM content_people p
            LEFT JOIN content_image_people cip ON p.id = cip.person_id
            GROUP BY p.id, p.person_name, p.created_at
            ORDER BY COUNT(cip.image_id) DESC
            """;
        return query(sql, PERSON_ROW_MAPPER);
    }

    @Transactional
    public ContentPersonEntity save(ContentPersonEntity entity) {
        if (entity.getId() == null) {
            String sql = "INSERT INTO content_people (person_name, created_at) VALUES (:personName, :createdAt)";
            MapSqlParameterSource params = createParameterSource()
                .addValue("personName", entity.getPersonName())
                .addValue("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt() : java.time.LocalDateTime.now());
            Long id = insertAndReturnId(sql, "id", params);
            entity.setId(id);
            return entity;
        } else {
            String sql = "UPDATE content_people SET person_name = :personName WHERE id = :id";
            MapSqlParameterSource params = createParameterSource()
                .addValue("personName", entity.getPersonName())
                .addValue("id", entity.getId());
            update(sql, params);
            return entity;
        }
    }

    @Transactional(readOnly = true)
    public Optional<ContentPersonEntity> findById(Long id) {
        String sql = "SELECT id, person_name, created_at FROM content_people WHERE id = :id";
        MapSqlParameterSource params = createParameterSource().addValue("id", id);
        return queryForObject(sql, PERSON_ROW_MAPPER, params);
    }
}
