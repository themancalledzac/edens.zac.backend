package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.types.CollectionType;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mutual ("sibling") association between collections (mirror of collection_people). Rows are stored
 * reciprocally — a link between A and B is two rows (A,B) and (B,A) — so "siblings of X" is a
 * single-direction lookup on collection_id. No dedicated entity POJO: the join is manipulated
 * directly via SQL, matching collection_people / collection_locations.
 */
@Component
public class CollectionSiblingRepository extends BaseDao {

  private static final RowMapper<Records.CollectionList> SIBLING_ROW_MAPPER =
      (rs, rowNum) ->
          new Records.CollectionList(
              rs.getLong("id"),
              rs.getString("name"),
              rs.getString("slug"),
              CollectionType.valueOf(rs.getString("type")));

  public CollectionSiblingRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  /**
   * Reciprocal insert of the pair (a,b) and (b,a). Idempotent via {@code ON CONFLICT DO NOTHING}
   * against the composite PK, so re-adding an existing link is a no-op.
   */
  @Transactional
  public void addSibling(Long a, Long b) {
    String sql =
        "INSERT INTO collection_sibling (collection_id, sibling_collection_id) "
            + "VALUES (:a, :b), (:b, :a) "
            + "ON CONFLICT DO NOTHING";
    update(sql, createParameterSource().addValue("a", a).addValue("b", b));
  }

  /** Bidirectional delete: removes both (a,b) and (b,a). */
  @Transactional
  public void removeSibling(Long a, Long b) {
    String sql =
        "DELETE FROM collection_sibling "
            + "WHERE (collection_id = :a AND sibling_collection_id = :b) "
            + "OR (collection_id = :b AND sibling_collection_id = :a)";
    update(sql, createParameterSource().addValue("a", a).addValue("b", b));
  }

  /**
   * Siblings of one collection as {@link Records.CollectionList} rows, ordered by title. When
   * {@code listedOnly} is true, only LISTED siblings are returned (public read path); when false,
   * every sibling regardless of visibility is returned (admin manage payload).
   */
  @Transactional(readOnly = true)
  public List<Records.CollectionList> findSiblings(Long collectionId, boolean listedOnly) {
    String sql =
        "SELECT c.id, c.title AS name, c.slug, c.type "
            + "FROM collection_sibling cs "
            + "JOIN collection c ON c.id = cs.sibling_collection_id "
            + "WHERE cs.collection_id = :id "
            + (listedOnly ? "AND c.visibility = 'LISTED' " : "")
            + "ORDER BY c.title ASC";
    MapSqlParameterSource params = createParameterSource().addValue("id", collectionId);
    return query(sql, SIBLING_ROW_MAPPER, params);
  }
}
