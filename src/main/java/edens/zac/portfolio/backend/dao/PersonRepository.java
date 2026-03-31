package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import edens.zac.portfolio.backend.services.SlugUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Repository for ContentPersonEntity. Manages people and their content associations. */
@Component
@Slf4j
public class PersonRepository extends BaseDao {

  public PersonRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  private static final RowMapper<ContentPersonEntity> PERSON_ROW_MAPPER =
      (rs, rowNum) ->
          ContentPersonEntity.builder()
              .id(rs.getLong("id"))
              .personName(rs.getString("person_name"))
              .slug(rs.getString("slug"))
              .createdAt(getLocalDateTime(rs, "created_at"))
              .build();

  @Transactional(readOnly = true)
  public Optional<ContentPersonEntity> findByPersonName(String personName) {
    String sql =
        "SELECT id, person_name, slug, created_at FROM content_people WHERE person_name = :personName";
    MapSqlParameterSource params = createParameterSource().addValue("personName", personName);
    return queryForObject(sql, PERSON_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public Optional<ContentPersonEntity> findByPersonNameIgnoreCase(String personName) {
    String sql =
        "SELECT id, person_name, slug, created_at FROM content_people WHERE LOWER(person_name) = LOWER(:personName)";
    MapSqlParameterSource params = createParameterSource().addValue("personName", personName);
    return queryForObject(sql, PERSON_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<ContentPersonEntity> findByPersonNameContainingIgnoreCase(String searchTerm) {
    String sql =
        "SELECT id, person_name, slug, created_at FROM content_people WHERE LOWER(person_name) LIKE LOWER(:searchTerm) ORDER BY person_name ASC";
    MapSqlParameterSource params =
        createParameterSource().addValue("searchTerm", "%" + searchTerm + "%");
    return query(sql, PERSON_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<ContentPersonEntity> findAllByOrderByPersonNameAsc() {
    String sql =
        "SELECT id, person_name, slug, created_at FROM content_people ORDER BY person_name ASC";
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
    String sql =
        "SELECT COUNT(*) > 0 FROM content_people WHERE LOWER(person_name) = LOWER(:personName)";
    MapSqlParameterSource params = createParameterSource().addValue("personName", personName);
    Boolean result = namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class);
    return result != null && result;
  }

  @Transactional(readOnly = true)
  public List<ContentPersonEntity> findAllOrderByImageCountDesc() {
    String sql =
        """
        SELECT p.id, p.person_name, p.slug, p.created_at
        FROM content_people p
        LEFT JOIN content_image_people cip ON p.id = cip.person_id
        GROUP BY p.id, p.person_name, p.slug, p.created_at
        ORDER BY COUNT(cip.image_id) DESC
        """;
    return query(sql, PERSON_ROW_MAPPER);
  }

  @Transactional(readOnly = true)
  public Optional<ContentPersonEntity> findBySlug(String slug) {
    String sql = "SELECT id, person_name, slug, created_at FROM content_people WHERE slug = :slug";
    MapSqlParameterSource params = createParameterSource().addValue("slug", slug);
    return queryForObject(sql, PERSON_ROW_MAPPER, params);
  }

  @Transactional
  public ContentPersonEntity save(ContentPersonEntity entity) {
    if (entity.getSlug() == null || entity.getSlug().isEmpty()) {
      entity.setSlug(SlugUtil.generateSlug(entity.getPersonName()));
    }
    if (entity.getId() == null) {
      String sql =
          "INSERT INTO content_people (person_name, slug, created_at) VALUES (:personName, :slug, :createdAt)";
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("personName", entity.getPersonName())
              .addValue("slug", entity.getSlug())
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
          "UPDATE content_people SET person_name = :personName, slug = :slug WHERE id = :id";
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("personName", entity.getPersonName())
              .addValue("slug", entity.getSlug())
              .addValue("id", entity.getId());
      update(sql, params);
      return entity;
    }
  }

  @Transactional(readOnly = true)
  public Optional<ContentPersonEntity> findById(Long id) {
    String sql = "SELECT id, person_name, slug, created_at FROM content_people WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    return queryForObject(sql, PERSON_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public Map<Long, List<ContentPersonEntity>> findPeopleByContentIds(List<Long> contentIds) {
    if (contentIds == null || contentIds.isEmpty()) {
      return Map.of();
    }

    String sql =
        """
        SELECT cip.image_id AS content_id, p.id, p.person_name, p.slug, p.created_at
        FROM content_image_people cip
        JOIN content_people p ON cip.person_id = p.id
        WHERE cip.image_id IN (:contentIds)
        ORDER BY p.person_name ASC
        """;
    MapSqlParameterSource params = createParameterSource().addValue("contentIds", contentIds);

    Map<Long, List<ContentPersonEntity>> result = new HashMap<>();
    namedParameterJdbcTemplate.query(
        sql,
        params,
        rs -> {
          Long contentId = rs.getLong("content_id");
          ContentPersonEntity person =
              ContentPersonEntity.builder()
                  .id(rs.getLong("id"))
                  .personName(rs.getString("person_name"))
                  .slug(rs.getString("slug"))
                  .createdAt(getLocalDateTime(rs, "created_at"))
                  .build();
          result.computeIfAbsent(contentId, k -> new ArrayList<>()).add(person);
        });
    return result;
  }

  @Transactional
  public void deleteById(Long id) {
    String sql = "DELETE FROM content_people WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    update(sql, params);
  }

  @Transactional
  public void deleteAllAssociationsByPersonId(Long personId) {
    MapSqlParameterSource params = createParameterSource().addValue("personId", personId);
    update("DELETE FROM content_image_people WHERE person_id = :personId", params);
    update("DELETE FROM collection_people WHERE person_id = :personId", params);
  }

  @Transactional(readOnly = true)
  public List<ContentPersonEntity> findContentPeople(Long contentId) {
    String sql =
        """
        SELECT p.id, p.person_name, p.slug, p.created_at
        FROM content_people p
        JOIN content_image_people cip ON p.id = cip.person_id
        WHERE cip.image_id = :contentId
        ORDER BY p.person_name ASC
        """;
    MapSqlParameterSource params = createParameterSource().addValue("contentId", contentId);
    return query(sql, PERSON_ROW_MAPPER, params);
  }
}
