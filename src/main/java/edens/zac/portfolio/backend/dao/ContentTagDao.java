package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.ContentTagEntity;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class ContentTagDao extends BaseDao {

  public ContentTagDao(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  private static final RowMapper<ContentTagEntity> TAG_ROW_MAPPER =
      (rs, rowNum) -> {
        return ContentTagEntity.builder()
            .id(rs.getLong("id"))
            .tagName(rs.getString("tag_name"))
            .createdAt(getLocalDateTime(rs, "created_at"))
            .build();
      };

  @Transactional(readOnly = true)
  public Optional<ContentTagEntity> findByTagName(String tagName) {
    String sql = "SELECT id, tag_name, created_at FROM tag WHERE tag_name = :tagName";
    MapSqlParameterSource params = createParameterSource().addValue("tagName", tagName);
    return queryForObject(sql, TAG_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public Optional<ContentTagEntity> findByTagNameIgnoreCase(String tagName) {
    String sql = "SELECT id, tag_name, created_at FROM tag WHERE LOWER(tag_name) = LOWER(:tagName)";
    MapSqlParameterSource params = createParameterSource().addValue("tagName", tagName);
    return queryForObject(sql, TAG_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<ContentTagEntity> findByTagNameContainingIgnoreCase(String searchTerm) {
    String sql =
        "SELECT id, tag_name, created_at FROM tag WHERE LOWER(tag_name) LIKE LOWER(:searchTerm) ORDER BY tag_name ASC";
    MapSqlParameterSource params =
        createParameterSource().addValue("searchTerm", "%" + searchTerm + "%");
    return query(sql, TAG_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<ContentTagEntity> findAllByOrderByTagNameAsc() {
    String sql = "SELECT id, tag_name, created_at FROM tag ORDER BY tag_name ASC";
    return query(sql, TAG_ROW_MAPPER);
  }

  @Transactional(readOnly = true)
  public boolean existsByTagName(String tagName) {
    String sql = "SELECT COUNT(*) > 0 FROM tag WHERE tag_name = :tagName";
    MapSqlParameterSource params = createParameterSource().addValue("tagName", tagName);
    Boolean result = namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class);
    return result != null && result;
  }

  @Transactional(readOnly = true)
  public boolean existsByTagNameIgnoreCase(String tagName) {
    String sql = "SELECT COUNT(*) > 0 FROM tag WHERE LOWER(tag_name) = LOWER(:tagName)";
    MapSqlParameterSource params = createParameterSource().addValue("tagName", tagName);
    Boolean result = namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class);
    return result != null && result;
  }

  @Transactional
  public ContentTagEntity save(ContentTagEntity entity) {
    if (entity.getId() == null) {
      String sql = "INSERT INTO tag (tag_name, created_at) VALUES (:tagName, :createdAt)";
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("tagName", entity.getTagName())
              .addValue(
                  "createdAt",
                  entity.getCreatedAt() != null
                      ? entity.getCreatedAt()
                      : java.time.LocalDateTime.now());
      Long id = insertAndReturnId(sql, "id", params);
      entity.setId(id);
      return entity;
    } else {
      String sql = "UPDATE tag SET tag_name = :tagName WHERE id = :id";
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("tagName", entity.getTagName())
              .addValue("id", entity.getId());
      update(sql, params);
      return entity;
    }
  }

  @Transactional(readOnly = true)
  public Optional<ContentTagEntity> findById(Long id) {
    String sql = "SELECT id, tag_name, created_at FROM tag WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    return queryForObject(sql, TAG_ROW_MAPPER, params);
  }
}
