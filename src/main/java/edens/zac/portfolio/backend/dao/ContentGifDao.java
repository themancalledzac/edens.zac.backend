package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.ContentGifEntity;
import edens.zac.portfolio.backend.types.ContentType;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DAO for ContentGifEntity using raw SQL queries. Handles JOINED inheritance pattern (content +
 * content_gif).
 */
@Component
@Slf4j
public class ContentGifDao extends BaseDao {

  public ContentGifDao(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  private static final String SELECT_CONTENT_GIF =
      """
            SELECT c.id, c.content_type, c.created_at, c.updated_at,
                   cg.title, cg.gif_url, cg.thumbnail_url, cg.width, cg.height,
                   cg.author, cg.create_date
            FROM content c
            JOIN content_gif cg ON c.id = cg.id
            """;

  private static final RowMapper<ContentGifEntity> CONTENT_GIF_ROW_MAPPER =
      (rs, rowNum) -> {
        return ContentGifEntity.builder()
            .id(rs.getLong("id"))
            .contentType(ContentType.GIF)
            .title(getString(rs, "title"))
            .gifUrl(rs.getString("gif_url"))
            .thumbnailUrl(getString(rs, "thumbnail_url"))
            .width(getInteger(rs, "width"))
            .height(getInteger(rs, "height"))
            .author(getString(rs, "author"))
            .createDate(getString(rs, "create_date"))
            .createdAt(getLocalDateTime(rs, "created_at"))
            .updatedAt(getLocalDateTime(rs, "updated_at"))
            .tags(new HashSet<>())
            .build();
      };

  /** Find ContentGifEntity by ID. */
  @Transactional(readOnly = true)
  public Optional<ContentGifEntity> findById(Long id) {
    String sql = SELECT_CONTENT_GIF + " WHERE c.id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    return queryForObject(sql, CONTENT_GIF_ROW_MAPPER, params);
  }

  /** Find all gifs ordered by createDate DESC. */
  @Transactional(readOnly = true)
  public List<ContentGifEntity> findAllOrderByCreateDateDesc() {
    String sql = SELECT_CONTENT_GIF + " ORDER BY cg.create_date DESC NULLS LAST, c.created_at DESC";
    return query(sql, CONTENT_GIF_ROW_MAPPER);
  }

  /** Save ContentGifEntity. Inserts into content first, then content_gif using the same ID. */
  @Transactional
  public ContentGifEntity save(ContentGifEntity entity) {
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
              .addValue("contentType", ContentType.GIF.name())
              .addValue("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt() : now)
              .addValue("updatedAt", entity.getUpdatedAt() != null ? entity.getUpdatedAt() : now);

      Long contentId = insertAndReturnId(contentSql, "id", contentParams);

      // Step 2: Insert into content_gif
      String gifSql =
          """
                    INSERT INTO content_gif (id, title, gif_url, thumbnail_url, width, height, author, create_date)
                    VALUES (:id, :title, :gifUrl, :thumbnailUrl, :width, :height, :author, :createDate)
                    """;

      MapSqlParameterSource gifParams =
          createParameterSource()
              .addValue("id", contentId)
              .addValue("title", entity.getTitle())
              .addValue("gifUrl", entity.getGifUrl())
              .addValue("thumbnailUrl", entity.getThumbnailUrl())
              .addValue("width", entity.getWidth())
              .addValue("height", entity.getHeight())
              .addValue("author", entity.getAuthor())
              .addValue("createDate", entity.getCreateDate());

      update(gifSql, gifParams);

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

      String gifSql =
          """
                    UPDATE content_gif
                    SET title = :title, gif_url = :gifUrl, thumbnail_url = :thumbnailUrl,
                        width = :width, height = :height, author = :author, create_date = :createDate
                    WHERE id = :id
                    """;

      MapSqlParameterSource gifParams =
          createParameterSource()
              .addValue("id", entity.getId())
              .addValue("title", entity.getTitle())
              .addValue("gifUrl", entity.getGifUrl())
              .addValue("thumbnailUrl", entity.getThumbnailUrl())
              .addValue("width", entity.getWidth())
              .addValue("height", entity.getHeight())
              .addValue("author", entity.getAuthor())
              .addValue("createDate", entity.getCreateDate());

      update(gifSql, gifParams);

      entity.setUpdatedAt(now);
      return entity;
    }
  }

  /**
   * Delete gif by ID. Deletes from content_gif first (child table), then content (parent table).
   * Note: Tags are deleted via content_tags table (handled by TagDao or cascade).
   */
  @Transactional
  public void deleteById(Long id) {
    MapSqlParameterSource params = createParameterSource().addValue("id", id);

    // Delete from many-to-many join tables first
    // Tags are deleted via content_tags (content.id = gif.id for GIFs)
    String deleteTagsSql = "DELETE FROM content_tags WHERE content_id = :id";
    update(deleteTagsSql, params);

    // Delete from content_gif (child table)
    String gifSql = "DELETE FROM content_gif WHERE id = :id";
    update(gifSql, params);

    // Delete from content (parent table)
    String contentSql = "DELETE FROM content WHERE id = :id";
    update(contentSql, params);
  }
}
