package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.ContentTextEntity;
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
 * DAO for ContentTextEntity using raw SQL queries. Handles JOINED inheritance pattern (content +
 * content_text).
 */
@Component
@Slf4j
public class ContentTextDao extends BaseDao {

  public ContentTextDao(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  private static final String SELECT_CONTENT_TEXT =
      """
        SELECT c.id, c.content_type, c.created_at, c.updated_at,
               ct.text_content, ct.format_type
        FROM content c
        JOIN content_text ct ON c.id = ct.id
        """;

  private static final RowMapper<ContentTextEntity> CONTENT_TEXT_ROW_MAPPER =
      (rs, rowNum) -> {
        return ContentTextEntity.builder()
            .id(rs.getLong("id"))
            .contentType(ContentType.TEXT)
            .textContent(rs.getString("text_content"))
            .formatType(getString(rs, "format_type"))
            .createdAt(getLocalDateTime(rs, "created_at"))
            .updatedAt(getLocalDateTime(rs, "updated_at"))
            .build();
      };

  /** Find ContentTextEntity by ID. */
  @Transactional(readOnly = true)
  public Optional<ContentTextEntity> findById(Long id) {
    String sql = SELECT_CONTENT_TEXT + " WHERE c.id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    return queryForObject(sql, CONTENT_TEXT_ROW_MAPPER, params);
  }

  /** Find all text content ordered by created_at DESC. */
  @Transactional(readOnly = true)
  public List<ContentTextEntity> findAllOrderByCreatedAtDesc() {
    String sql = SELECT_CONTENT_TEXT + " ORDER BY c.created_at DESC";
    return query(sql, CONTENT_TEXT_ROW_MAPPER);
  }

  /** Save ContentTextEntity. Inserts into content first, then content_text using the same ID. */
  @Transactional
  public ContentTextEntity save(ContentTextEntity entity) {
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
              .addValue("contentType", ContentType.TEXT.name())
              .addValue("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt() : now)
              .addValue("updatedAt", entity.getUpdatedAt() != null ? entity.getUpdatedAt() : now);

      Long contentId = insertAndReturnId(contentSql, "id", contentParams);

      // Step 2: Insert into content_text
      String textSql =
          """
                INSERT INTO content_text (id, text_content, format_type)
                VALUES (:id, :textContent, :formatType)
                """;

      MapSqlParameterSource textParams =
          createParameterSource()
              .addValue("id", contentId)
              .addValue("textContent", entity.getTextContent())
              .addValue("formatType", entity.getFormatType());

      update(textSql, textParams);

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

      String textSql =
          """
                UPDATE content_text
                SET text_content = :textContent, format_type = :formatType
                WHERE id = :id
                """;

      MapSqlParameterSource textParams =
          createParameterSource()
              .addValue("id", entity.getId())
              .addValue("textContent", entity.getTextContent())
              .addValue("formatType", entity.getFormatType());

      update(textSql, textParams);

      entity.setUpdatedAt(now);
      return entity;
    }
  }

  /** Delete text content by ID. */
  @Transactional
  public void deleteById(Long id) {
    // Delete from content_text first (child table)
    String textSql = "DELETE FROM content_text WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    update(textSql, params);

    // Delete from content (parent table)
    String contentSql = "DELETE FROM content WHERE id = :id";
    update(contentSql, params);
  }
}
