package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.ContentPersonEntity;
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

/**
 * Repository for ContentPersonEntity. Manages people and their content associations.
 *
 * <p>Since the V35 identity merge, a "person tag" and a "login account" are one row in {@code
 * users}. A tagged-only person is a {@code users} row with {@code status='PERSON'} (no account
 * secrets). Person-tag reads project only {@code id, name} from {@code users} and must never select
 * the account columns ({@code email}, {@code password_hash}, {@code webauthn_user_handle}, {@code
 * role}, {@code status}).
 */
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
              .personName(rs.getString("name"))
              .createdAt(getLocalDateTime(rs, "created_at"))
              .build();

  @Transactional(readOnly = true)
  public Optional<ContentPersonEntity> findByPersonName(String personName) {
    String sql = "SELECT id, name, created_at FROM users WHERE name = :personName";
    MapSqlParameterSource params = createParameterSource().addValue("personName", personName);
    return queryForObject(sql, PERSON_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public Optional<ContentPersonEntity> findByPersonNameIgnoreCase(String personName) {
    String sql = "SELECT id, name, created_at FROM users WHERE LOWER(name) = LOWER(:personName)";
    MapSqlParameterSource params = createParameterSource().addValue("personName", personName);
    return queryForObject(sql, PERSON_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<ContentPersonEntity> findByPersonNameContainingIgnoreCase(String searchTerm) {
    String sql =
        "SELECT id, name, created_at FROM users WHERE LOWER(name) LIKE LOWER(:searchTerm) ORDER BY name ASC";
    MapSqlParameterSource params =
        createParameterSource().addValue("searchTerm", "%" + searchTerm + "%");
    return query(sql, PERSON_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<ContentPersonEntity> findAllByOrderByPersonNameAsc() {
    String sql = "SELECT id, name, created_at FROM users ORDER BY name ASC";
    return query(sql, PERSON_ROW_MAPPER);
  }

  @Transactional(readOnly = true)
  public boolean existsByPersonName(String personName) {
    String sql = "SELECT COUNT(*) > 0 FROM users WHERE name = :personName";
    MapSqlParameterSource params = createParameterSource().addValue("personName", personName);
    Boolean result = namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class);
    return result != null && result;
  }

  @Transactional(readOnly = true)
  public boolean existsByPersonNameIgnoreCase(String personName) {
    String sql = "SELECT COUNT(*) > 0 FROM users WHERE LOWER(name) = LOWER(:personName)";
    MapSqlParameterSource params = createParameterSource().addValue("personName", personName);
    Boolean result = namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class);
    return result != null && result;
  }

  @Transactional(readOnly = true)
  public List<ContentPersonEntity> findAllOrderByImageCountDesc() {
    String sql =
        """
        SELECT p.id, p.name, p.created_at
        FROM users p
        LEFT JOIN content_image_people cip ON p.id = cip.person_id
        GROUP BY p.id, p.name, p.created_at
        ORDER BY COUNT(cip.content_id) DESC
        """;
    return query(sql, PERSON_ROW_MAPPER);
  }

  /**
   * Of the given identity ids, those that are real accounts (status {@code <> 'PERSON'}) — drops
   * tag-only PERSON rows. Replaces the pre-merge {@code findLinkedUserIdsByPersonIds}: it preserves
   * the "only account-backed persons receive a user_collection membership" rule now that a person
   * tag and an account share one {@code users} row.
   */
  @Transactional(readOnly = true)
  public List<Long> findAccountUserIdsByIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    return query(
        "SELECT id FROM users WHERE id IN (:ids) AND status <> 'PERSON'",
        (rs, n) -> rs.getLong("id"),
        createParameterSource().addValue("ids", ids));
  }

  /**
   * Find-or-create a person identity by name. A brand-new person is inserted into {@code users} as
   * a tag-only {@code PERSON} row (no account: {@code email}/{@code password_hash} null). Account
   * provisioning is a separate flow.
   */
  @Transactional
  public ContentPersonEntity save(ContentPersonEntity entity) {
    if (entity.getId() == null) {
      String sql =
          """
          INSERT INTO users (name, webauthn_user_handle, status, created_at)
          VALUES (:name, gen_random_uuid(), 'PERSON', :createdAt)
          """;
      if (entity.getCreatedAt() == null) {
        entity.setCreatedAt(java.time.LocalDateTime.now());
      }
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("name", entity.getPersonName())
              .addValue("createdAt", entity.getCreatedAt());
      Long id = insertAndReturnId(sql, "id", params);
      entity.setId(id);
      return entity;
    } else {
      String sql = "UPDATE users SET name = :name WHERE id = :id";
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("name", entity.getPersonName())
              .addValue("id", entity.getId());
      update(sql, params);
      return entity;
    }
  }

  @Transactional(readOnly = true)
  public Optional<ContentPersonEntity> findById(Long id) {
    String sql = "SELECT id, name, created_at FROM users WHERE id = :id";
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
        SELECT cip.content_id, p.id, p.name, p.created_at
        FROM content_image_people cip
        JOIN users p ON cip.person_id = p.id
        WHERE cip.content_id IN (:contentIds)
        ORDER BY p.name ASC
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
                  .personName(rs.getString("name"))
                  .createdAt(getLocalDateTime(rs, "created_at"))
                  .build();
          result.computeIfAbsent(contentId, k -> new ArrayList<>()).add(person);
        });
    return result;
  }

  @Transactional
  public void deleteById(Long id) {
    String sql = "DELETE FROM users WHERE id = :id";
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
        SELECT p.id, p.name, p.created_at
        FROM users p
        JOIN content_image_people cip ON p.id = cip.person_id
        WHERE cip.content_id = :contentId
        ORDER BY p.name ASC
        """;
    MapSqlParameterSource params = createParameterSource().addValue("contentId", contentId);
    return query(sql, PERSON_ROW_MAPPER, params);
  }
}
