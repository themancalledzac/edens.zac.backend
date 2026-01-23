package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import edens.zac.portfolio.backend.types.ContentType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DAO for ContentCollectionEntity using raw SQL queries. Handles JOINED inheritance pattern
 * (content + content_collection).
 */
@Component
@Slf4j
public class ContentCollectionDao extends BaseDao {

  public ContentCollectionDao(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  private static final String SELECT_CONTENT_COLLECTION =
      """
        SELECT c.id, c.content_type, c.created_at, c.updated_at,
               cc.referenced_collection_id
        FROM content c
        JOIN content_collection cc ON c.id = cc.id
        """;

  private static final RowMapper<ContentCollectionEntity> CONTENT_COLLECTION_ROW_MAPPER =
      (rs, rowNum) -> {
        Long referencedCollectionId = rs.getLong("referenced_collection_id");

        // Create a minimal CollectionEntity with just the ID
        edens.zac.portfolio.backend.entity.CollectionEntity referencedCollection =
            new edens.zac.portfolio.backend.entity.CollectionEntity();
        referencedCollection.setId(referencedCollectionId);

        return ContentCollectionEntity.builder()
            .id(rs.getLong("id"))
            .contentType(ContentType.COLLECTION)
            .referencedCollection(referencedCollection)
            .createdAt(getLocalDateTime(rs, "created_at"))
            .updatedAt(getLocalDateTime(rs, "updated_at"))
            .build();
      };

  /** Find ContentCollectionEntity by ID. */
  @Transactional(readOnly = true)
  public Optional<ContentCollectionEntity> findById(Long id) {
    String sql = SELECT_CONTENT_COLLECTION + " WHERE c.id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    return queryForObject(sql, CONTENT_COLLECTION_ROW_MAPPER, params);
  }

  /** Find ContentCollectionEntity by referenced collection ID. */
  @Transactional(readOnly = true)
  public Optional<ContentCollectionEntity> findByReferencedCollectionId(
      Long referencedCollectionId) {
    String sql =
        SELECT_CONTENT_COLLECTION + " WHERE cc.referenced_collection_id = :referencedCollectionId";
    MapSqlParameterSource params =
        createParameterSource().addValue("referencedCollectionId", referencedCollectionId);
    return queryForObject(sql, CONTENT_COLLECTION_ROW_MAPPER, params);
  }

  /** Find all content collections ordered by created_at DESC. */
  @Transactional(readOnly = true)
  public List<ContentCollectionEntity> findAllOrderByCreatedAtDesc() {
    String sql = SELECT_CONTENT_COLLECTION + " ORDER BY c.created_at DESC";
    return query(sql, CONTENT_COLLECTION_ROW_MAPPER);
  }

  /**
   * Save ContentCollectionEntity. Inserts into content first, then content_collection using the
   * same ID.
   */
  @Transactional
  public ContentCollectionEntity save(ContentCollectionEntity entity) {
    LocalDateTime now = LocalDateTime.now();

    if (entity.getId() == null) {
      // Step 1: Insert into content table
      String contentSql =
          """
                INSERT INTO content (content_type, created_at, updated_at)
                VALUES (:contentType, :createdAt, :updatedAt)
                """;

      MapSqlParameterSource contentParams =
          createParameterSource()
              .addValue("contentType", ContentType.COLLECTION.name())
              .addValue("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt() : now)
              .addValue("updatedAt", entity.getUpdatedAt() != null ? entity.getUpdatedAt() : now);

      Long contentId = insertAndReturnId(contentSql, "id", contentParams);

      // Step 2: Insert into content_collection
      String collectionSql =
          """
                INSERT INTO content_collection (id, referenced_collection_id)
                VALUES (:id, :referencedCollectionId)
                """;

      MapSqlParameterSource collectionParams =
          createParameterSource()
              .addValue("id", contentId)
              .addValue("referencedCollectionId", entity.getReferencedCollection().getId());

      update(collectionSql, collectionParams);

      entity.setId(contentId);
      if (entity.getCreatedAt() == null) {
        entity.setCreatedAt(now);
      }
      if (entity.getUpdatedAt() == null) {
        entity.setUpdatedAt(now);
      }

      return entity;
    } else {
      // Update existing
      String contentSql =
          """
                UPDATE content
                SET updated_at = :updatedAt
                WHERE id = :id
                """;
      MapSqlParameterSource contentParams =
          createParameterSource().addValue("updatedAt", now).addValue("id", entity.getId());
      update(contentSql, contentParams);

      String collectionSql =
          """
                UPDATE content_collection
                SET referenced_collection_id = :referencedCollectionId
                WHERE id = :id
                """;

      MapSqlParameterSource collectionParams =
          createParameterSource()
              .addValue("id", entity.getId())
              .addValue("referencedCollectionId", entity.getReferencedCollection().getId());

      update(collectionSql, collectionParams);

      entity.setUpdatedAt(now);
      return entity;
    }
  }

  /** Delete content collection by ID. */
  @Transactional
  public void deleteById(Long id) {
    // Delete from content_collection first (child table)
    String collectionSql = "DELETE FROM content_collection WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    update(collectionSql, params);

    // Delete from content (parent table)
    String contentSql = "DELETE FROM content WHERE id = :id";
    update(contentSql, params);
  }
}
