package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.model.Records;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Directional association between collections (mirror of collection_people). A row (collection_id,
 * sibling_collection_id) means the first collection links to the second; a mutual link is the pair
 * (A,B) and (B,A), and a one-way link is a lone row. "Siblings of X" is therefore a
 * single-direction lookup on collection_id. No dedicated entity POJO: the join is manipulated
 * directly via SQL, matching collection_people / collection_locations.
 */
@Component
public class CollectionSiblingRepository extends BaseDao {

  private static final RowMapper<Records.SiblingRow> SIBLING_ROW_MAPPER =
      (rs, rowNum) -> {
        long coverImageId = rs.getLong("cover_image_id");
        Long coverImageIdOrNull = rs.wasNull() ? null : coverImageId;
        return new Records.SiblingRow(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("slug"),
            coverImageIdOrNull,
            rs.getBoolean("is_client"),
            rs.getBoolean("is_blog"),
            rs.getBoolean("mutual"));
      };

  public CollectionSiblingRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  /**
   * Set the direction of the link from {@code a} to {@code b}. The forward row is always upserted;
   * when {@code mutual} the reverse row is upserted too, and when not it is deleted. The delete
   * branch is what downgrades an existing mutual link to one-way. Idempotent in both modes via
   * {@code ON CONFLICT DO NOTHING} against the composite PK.
   */
  @Transactional
  public void setSibling(Long a, Long b, boolean mutual) {
    String forwardSql =
        "INSERT INTO collection_sibling (collection_id, sibling_collection_id) "
            + "VALUES (:a, :b) "
            + "ON CONFLICT DO NOTHING";
    update(forwardSql, createParameterSource().addValue("a", a).addValue("b", b));

    String reverseSql =
        mutual
            ? "INSERT INTO collection_sibling (collection_id, sibling_collection_id) "
                + "VALUES (:b, :a) "
                + "ON CONFLICT DO NOTHING"
            : "DELETE FROM collection_sibling "
                + "WHERE collection_id = :b AND sibling_collection_id = :a";
    update(reverseSql, createParameterSource().addValue("a", a).addValue("b", b));
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
   * Siblings of one collection as {@link Records.SiblingRow} projections (including the raw {@code
   * cover_image_id}), ordered by title. When {@code listedOnly} is true, only LISTED siblings are
   * returned (public read path); when false, every sibling regardless of visibility is returned
   * (admin manage payload). Cover image URLs are resolved separately in a batch by the caller to
   * avoid N+1.
   *
   * <p>Each row carries {@code mutual}, true when the sibling links back and false for a one-way
   * link.
   */
  @Transactional(readOnly = true)
  public List<Records.SiblingRow> findSiblings(Long collectionId, boolean listedOnly) {
    String sql =
        "SELECT c.id, c.title AS name, c.slug, c.cover_image_id, c.is_client, c.is_blog, "
            + "EXISTS (SELECT 1 FROM collection_sibling r "
            + "WHERE r.collection_id = cs.sibling_collection_id "
            + "AND r.sibling_collection_id = cs.collection_id) AS mutual "
            + "FROM collection_sibling cs "
            + "JOIN collection c ON c.id = cs.sibling_collection_id "
            + "WHERE cs.collection_id = :id "
            + (listedOnly ? "AND c.visibility = 'LISTED' " : "")
            + "ORDER BY c.title ASC";
    MapSqlParameterSource params = createParameterSource().addValue("id", collectionId);
    return query(sql, SIBLING_ROW_MAPPER, params);
  }
}
