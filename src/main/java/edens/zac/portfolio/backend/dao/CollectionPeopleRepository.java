package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.model.Records;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * People-on-collection join (mirror of content_image_people). Reads return {@link Records.Person}
 * DTOs directly so callers (CollectionProcessingUtil) can populate {@code CollectionModel.people}
 * without going through {@code ContentPersonEntity}.
 *
 * <p>The actual person table is {@code content_people} with column {@code person_name}; columns are
 * aliased as {@code name} so they map cleanly into {@link Records.Person}.
 */
@Component
public class CollectionPeopleRepository extends BaseDao {

  private static final RowMapper<Records.Person> PERSON_ROW_MAPPER =
      (rs, rowNum) ->
          new Records.Person(rs.getLong("id"), rs.getString("name"), rs.getString("slug"));

  public CollectionPeopleRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  @Transactional(readOnly = true)
  public List<Records.Person> findPeopleForCollection(Long collectionId) {
    String sql =
        """
        SELECT p.id, p.person_name AS name, p.slug
        FROM collection_people cp
        JOIN content_people p ON p.id = cp.person_id
        WHERE cp.collection_id = :collectionId
        ORDER BY p.person_name
        """;
    return query(
        sql, PERSON_ROW_MAPPER, createParameterSource().addValue("collectionId", collectionId));
  }

  /**
   * Batch loader: collectionId -> people list. Used by CollectionProcessingUtil to populate people
   * on N collection models in one query instead of N+1.
   */
  @Transactional(readOnly = true)
  public Map<Long, List<Records.Person>> findPeopleForCollections(List<Long> collectionIds) {
    if (collectionIds == null || collectionIds.isEmpty()) {
      return Map.of();
    }
    String sql =
        """
        SELECT cp.collection_id, p.id, p.person_name AS name, p.slug
        FROM collection_people cp
        JOIN content_people p ON p.id = cp.person_id
        WHERE cp.collection_id IN (:collectionIds)
        ORDER BY cp.collection_id, p.person_name
        """;
    Map<Long, List<Records.Person>> result = new HashMap<>();
    namedParameterJdbcTemplate.query(
        sql,
        createParameterSource().addValue("collectionIds", collectionIds),
        rs -> {
          Long cid = rs.getLong("collection_id");
          Records.Person person =
              new Records.Person(rs.getLong("id"), rs.getString("name"), rs.getString("slug"));
          result.computeIfAbsent(cid, k -> new ArrayList<>()).add(person);
        });
    return result;
  }

  /** Replace the entire people list for a collection (DELETE-then-batch-INSERT). */
  @Transactional
  public void setPeopleForCollection(Long collectionId, List<Long> personIds) {
    String deleteSql = "DELETE FROM collection_people WHERE collection_id = :collectionId";
    update(deleteSql, createParameterSource().addValue("collectionId", collectionId));
    if (personIds == null || personIds.isEmpty()) {
      return;
    }
    String insertSql =
        "INSERT INTO collection_people (collection_id, person_id) VALUES (:collectionId, :personId)";
    SqlParameterSource[] batch =
        personIds.stream()
            .distinct()
            .map(
                pid ->
                    (SqlParameterSource)
                        createParameterSource()
                            .addValue("collectionId", collectionId)
                            .addValue("personId", pid))
            .toArray(SqlParameterSource[]::new);
    batchUpdate(insertSql, batch);
  }
}
