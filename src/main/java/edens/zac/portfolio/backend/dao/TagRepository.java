package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.TagEntity;
import edens.zac.portfolio.backend.services.SlugUtil;
import java.time.LocalDateTime;
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
 * Repository for TagEntity. Manages tags and their associations via collection_tags and
 * content_tags join tables.
 */
@Component
@Slf4j
public class TagRepository extends BaseDao {

  public TagRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  private static final RowMapper<TagEntity> TAG_ROW_MAPPER =
      (rs, rowNum) ->
          TagEntity.builder()
              .id(rs.getLong("id"))
              .tagName(rs.getString("tag_name"))
              .slug(rs.getString("slug"))
              .createdAt(getLocalDateTime(rs, "created_at"))
              .build();

  // ============================================================
  // Tag CRUD Operations
  // ============================================================

  @Transactional(readOnly = true)
  public Optional<TagEntity> findByTagName(String tagName) {
    String sql = "SELECT id, tag_name, slug, created_at FROM tag WHERE tag_name = :tagName";
    MapSqlParameterSource params = createParameterSource().addValue("tagName", tagName);
    return queryForObject(sql, TAG_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public Optional<TagEntity> findByTagNameIgnoreCase(String tagName) {
    String sql =
        "SELECT id, tag_name, slug, created_at FROM tag WHERE LOWER(tag_name) = LOWER(:tagName)";
    MapSqlParameterSource params = createParameterSource().addValue("tagName", tagName);
    return queryForObject(sql, TAG_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<TagEntity> findByTagNameContainingIgnoreCase(String searchTerm) {
    String sql =
        "SELECT id, tag_name, slug, created_at FROM tag WHERE LOWER(tag_name) LIKE LOWER(:searchTerm) ORDER BY tag_name ASC";
    MapSqlParameterSource params =
        createParameterSource().addValue("searchTerm", "%" + searchTerm + "%");
    return query(sql, TAG_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<TagEntity> findAllByOrderByTagNameAsc() {
    String sql = "SELECT id, tag_name, slug, created_at FROM tag ORDER BY tag_name ASC";
    return query(sql, TAG_ROW_MAPPER);
  }

  @Transactional(readOnly = true)
  public Optional<TagEntity> findById(Long id) {
    String sql = "SELECT id, tag_name, slug, created_at FROM tag WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    return queryForObject(sql, TAG_ROW_MAPPER, params);
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

  @Transactional(readOnly = true)
  public Optional<TagEntity> findBySlug(String slug) {
    String sql = "SELECT id, tag_name, slug, created_at FROM tag WHERE slug = :slug";
    MapSqlParameterSource params = createParameterSource().addValue("slug", slug);
    return queryForObject(sql, TAG_ROW_MAPPER, params);
  }

  @Transactional
  public TagEntity save(TagEntity entity) {
    if (entity.getSlug() == null || entity.getSlug().isEmpty()) {
      entity.setSlug(SlugUtil.generateSlug(entity.getTagName()));
    }
    if (entity.getId() == null) {
      String sql =
          "INSERT INTO tag (tag_name, slug, created_at) VALUES (:tagName, :slug, :createdAt)";
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("tagName", entity.getTagName())
              .addValue("slug", entity.getSlug())
              .addValue(
                  "createdAt",
                  entity.getCreatedAt() != null ? entity.getCreatedAt() : LocalDateTime.now());
      Long id = insertAndReturnId(sql, "id", params);
      entity.setId(id);
      return entity;
    } else {
      String sql = "UPDATE tag SET tag_name = :tagName, slug = :slug WHERE id = :id";
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("tagName", entity.getTagName())
              .addValue("slug", entity.getSlug())
              .addValue("id", entity.getId());
      update(sql, params);
      return entity;
    }
  }

  @Transactional
  public TagEntity findOrCreate(String tagName) {
    if (tagName == null || tagName.trim().isEmpty()) {
      return null;
    }

    String trimmedName = tagName.trim();

    Optional<TagEntity> existing = findByTagNameIgnoreCase(trimmedName);
    if (existing.isPresent()) {
      return existing.get();
    }

    TagEntity newTag =
        TagEntity.builder().tagName(trimmedName).createdAt(LocalDateTime.now()).build();
    return save(newTag);
  }

  @Transactional
  public void deleteById(Long id) {
    String sql = "DELETE FROM tag WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    update(sql, params);
  }

  @Transactional
  public void deleteAllAssociationsByTagId(Long tagId) {
    MapSqlParameterSource params = createParameterSource().addValue("tagId", tagId);
    update("DELETE FROM content_tags WHERE tag_id = :tagId", params);
    update("DELETE FROM collection_tags WHERE tag_id = :tagId", params);
  }

  // ============================================================
  // Collection Tags Operations
  // ============================================================

  @Transactional
  public void saveCollectionTags(Long collectionId, List<Long> tagIds) {
    String deleteSql = "DELETE FROM collection_tags WHERE collection_id = :collectionId";
    MapSqlParameterSource deleteParams =
        createParameterSource().addValue("collectionId", collectionId);
    update(deleteSql, deleteParams);

    if (tagIds != null && !tagIds.isEmpty()) {
      String insertSql =
          "INSERT INTO collection_tags (collection_id, tag_id) VALUES (:collectionId, :tagId)";
      MapSqlParameterSource[] batchParams =
          tagIds.stream()
              .map(
                  tagId ->
                      createParameterSource()
                          .addValue("collectionId", collectionId)
                          .addValue("tagId", tagId))
              .toArray(MapSqlParameterSource[]::new);
      batchUpdate(insertSql, batchParams);
    }
  }

  @Transactional(readOnly = true)
  public List<Long> findCollectionTagIds(Long collectionId) {
    String sql = "SELECT tag_id FROM collection_tags WHERE collection_id = :collectionId";
    MapSqlParameterSource params = createParameterSource().addValue("collectionId", collectionId);
    return namedParameterJdbcTemplate.queryForList(sql, params, Long.class);
  }

  @Transactional(readOnly = true)
  public List<TagEntity> findCollectionTags(Long collectionId) {
    String sql =
        """
        SELECT t.id, t.tag_name, t.slug, t.created_at
        FROM tag t
        JOIN collection_tags ct ON t.id = ct.tag_id
        WHERE ct.collection_id = :collectionId
        ORDER BY t.tag_name ASC
        """;
    MapSqlParameterSource params = createParameterSource().addValue("collectionId", collectionId);
    return query(sql, TAG_ROW_MAPPER, params);
  }

  @Transactional
  public void deleteCollectionTags(Long collectionId) {
    String sql = "DELETE FROM collection_tags WHERE collection_id = :collectionId";
    MapSqlParameterSource params = createParameterSource().addValue("collectionId", collectionId);
    update(sql, params);
  }

  @Transactional
  public void addCollectionTag(Long collectionId, Long tagId) {
    String sql =
        "INSERT INTO collection_tags (collection_id, tag_id) VALUES (:collectionId, :tagId) ON CONFLICT DO NOTHING";
    MapSqlParameterSource params =
        createParameterSource().addValue("collectionId", collectionId).addValue("tagId", tagId);
    update(sql, params);
  }

  @Transactional
  public void removeCollectionTag(Long collectionId, Long tagId) {
    String sql =
        "DELETE FROM collection_tags WHERE collection_id = :collectionId AND tag_id = :tagId";
    MapSqlParameterSource params =
        createParameterSource().addValue("collectionId", collectionId).addValue("tagId", tagId);
    update(sql, params);
  }

  // ============================================================
  // Content Tags Operations
  // ============================================================

  @Transactional
  public void saveContentTags(Long contentId, List<Long> tagIds) {
    String deleteSql = "DELETE FROM content_tags WHERE content_id = :contentId";
    MapSqlParameterSource deleteParams = createParameterSource().addValue("contentId", contentId);
    update(deleteSql, deleteParams);

    if (tagIds != null && !tagIds.isEmpty()) {
      String insertSql =
          "INSERT INTO content_tags (content_id, tag_id) VALUES (:contentId, :tagId) ON CONFLICT DO NOTHING";
      MapSqlParameterSource[] batchParams =
          tagIds.stream()
              .map(
                  tagId ->
                      createParameterSource()
                          .addValue("contentId", contentId)
                          .addValue("tagId", tagId))
              .toArray(MapSqlParameterSource[]::new);
      batchUpdate(insertSql, batchParams);
    }
  }

  @Transactional(readOnly = true)
  public Map<Long, List<Long>> findTagIdsByContentIds(List<Long> contentIds) {
    if (contentIds == null || contentIds.isEmpty()) {
      return Map.of();
    }

    String sql = "SELECT content_id, tag_id FROM content_tags WHERE content_id IN (:contentIds)";
    MapSqlParameterSource params = createParameterSource().addValue("contentIds", contentIds);

    Map<Long, List<Long>> result = new HashMap<>();
    namedParameterJdbcTemplate.query(
        sql,
        params,
        rs -> {
          Long contentId = rs.getLong("content_id");
          Long tagId = rs.getLong("tag_id");
          result.computeIfAbsent(contentId, k -> new ArrayList<>()).add(tagId);
        });

    return result;
  }

  @Transactional(readOnly = true)
  public Map<Long, List<TagEntity>> findTagsByContentIds(List<Long> contentIds) {
    if (contentIds == null || contentIds.isEmpty()) {
      return Map.of();
    }

    String sql =
        """
        SELECT ct.content_id, t.id, t.tag_name, t.slug, t.created_at
        FROM content_tags ct
        JOIN tag t ON ct.tag_id = t.id
        WHERE ct.content_id IN (:contentIds)
        ORDER BY t.tag_name ASC
        """;
    MapSqlParameterSource params = createParameterSource().addValue("contentIds", contentIds);

    Map<Long, List<TagEntity>> result = new HashMap<>();
    namedParameterJdbcTemplate.query(
        sql,
        params,
        rs -> {
          Long contentId = rs.getLong("content_id");
          TagEntity tag =
              TagEntity.builder()
                  .id(rs.getLong("id"))
                  .tagName(rs.getString("tag_name"))
                  .slug(rs.getString("slug"))
                  .createdAt(getLocalDateTime(rs, "created_at"))
                  .build();
          result.computeIfAbsent(contentId, k -> new ArrayList<>()).add(tag);
        });
    return result;
  }

  @Transactional(readOnly = true)
  public List<TagEntity> findContentTags(Long contentId) {
    String sql =
        """
        SELECT t.id, t.tag_name, t.slug, t.created_at
        FROM tag t
        JOIN content_tags ct ON t.id = ct.tag_id
        WHERE ct.content_id = :contentId
        ORDER BY t.tag_name ASC
        """;
    MapSqlParameterSource params = createParameterSource().addValue("contentId", contentId);
    return query(sql, TAG_ROW_MAPPER, params);
  }

  @Transactional
  public void deleteContentTags(Long contentId) {
    String sql = "DELETE FROM content_tags WHERE content_id = :contentId";
    MapSqlParameterSource params = createParameterSource().addValue("contentId", contentId);
    update(sql, params);
  }

  @Transactional
  public void addContentTag(Long contentId, Long tagId) {
    String sql =
        "INSERT INTO content_tags (content_id, tag_id) VALUES (:contentId, :tagId) ON CONFLICT DO NOTHING";
    MapSqlParameterSource params =
        createParameterSource().addValue("contentId", contentId).addValue("tagId", tagId);
    update(sql, params);
  }

  @Transactional
  public void removeContentTag(Long contentId, Long tagId) {
    String sql = "DELETE FROM content_tags WHERE content_id = :contentId AND tag_id = :tagId";
    MapSqlParameterSource params =
        createParameterSource().addValue("contentId", contentId).addValue("tagId", tagId);
    update(sql, params);
  }
}
