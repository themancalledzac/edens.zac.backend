package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.TagEntity;
import edens.zac.portfolio.backend.services.SlugUtil;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import edens.zac.portfolio.backend.types.DisplayMode;
import java.sql.Array;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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
              .convertedCollectionId(getLong(rs, "converted_collection_id"))
              .createdAt(getLocalDateTime(rs, "created_at"))
              .build();

  /** Collection row mapper for {@link #findCollectionsByTagId}. */
  private static final RowMapper<CollectionEntity> COLLECTION_ROW_MAPPER =
      (rs, rowNum) -> {
        CollectionEntity entity = new CollectionEntity();
        entity.setId(rs.getLong("id"));
        entity.setType(CollectionType.valueOf(rs.getString("type")));
        entity.setTitle(rs.getString("title"));
        entity.setSlug(rs.getString("slug"));
        entity.setDescription(rs.getString("description"));
        entity.setCollectionDate(getLocalDate(rs, "collection_date"));
        entity.setVisibility(CollectionVisibility.valueOf(rs.getString("visibility")));

        String displayMode = rs.getString("display_mode");
        if (displayMode != null) {
          try {
            entity.setDisplayMode(DisplayMode.valueOf(displayMode));
          } catch (IllegalArgumentException e) {
            log.warn("Invalid display_mode value: {}", displayMode);
          }
        }

        Long coverImageId = getLong(rs, "cover_image_id");
        if (coverImageId != null) {
          entity.setCoverImageId(coverImageId);
        }

        entity.setContentPerPage(getInteger(rs, "content_per_page"));
        entity.setTotalContent(getInteger(rs, "total_content"));
        entity.setRowsWide(getInteger(rs, "rows_wide"));
        entity.setGalleryPassword(rs.getString("gallery_password"));
        Array emailsArray = rs.getArray("recipient_emails");
        entity.setRecipientEmails(
            emailsArray != null
                ? new ArrayList<>(Arrays.asList((String[]) emailsArray.getArray()))
                : new ArrayList<>());
        entity.setRating(getInteger(rs, "rating"));
        entity.setCreatedAt(getLocalDateTime(rs, "created_at"));
        entity.setUpdatedAt(getLocalDateTime(rs, "updated_at"));

        return entity;
      };

  // ============================================================
  // Tag CRUD Operations
  // ============================================================

  @Transactional(readOnly = true)
  public Optional<TagEntity> findByTagName(String tagName) {
    String sql =
        "SELECT id, tag_name, slug, converted_collection_id, created_at FROM tag WHERE tag_name = :tagName";
    MapSqlParameterSource params = createParameterSource().addValue("tagName", tagName);
    return queryForObject(sql, TAG_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public Optional<TagEntity> findByTagNameIgnoreCase(String tagName) {
    String sql =
        "SELECT id, tag_name, slug, converted_collection_id, created_at FROM tag WHERE LOWER(tag_name) = LOWER(:tagName)";
    MapSqlParameterSource params = createParameterSource().addValue("tagName", tagName);
    return queryForObject(sql, TAG_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<TagEntity> findByTagNameContainingIgnoreCase(String searchTerm) {
    String sql =
        "SELECT id, tag_name, slug, converted_collection_id, created_at FROM tag WHERE LOWER(tag_name) LIKE LOWER(:searchTerm) ORDER BY tag_name ASC";
    MapSqlParameterSource params =
        createParameterSource().addValue("searchTerm", "%" + searchTerm + "%");
    return query(sql, TAG_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<TagEntity> findAllByOrderByTagNameAsc() {
    String sql =
        "SELECT id, tag_name, slug, converted_collection_id, created_at FROM tag ORDER BY tag_name ASC";
    return query(sql, TAG_ROW_MAPPER);
  }

  @Transactional(readOnly = true)
  public Optional<TagEntity> findById(Long id) {
    String sql =
        "SELECT id, tag_name, slug, converted_collection_id, created_at FROM tag WHERE id = :id";
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
    String sql =
        "SELECT id, tag_name, slug, converted_collection_id, created_at FROM tag WHERE slug = :slug";
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
      if (entity.getCreatedAt() == null) {
        entity.setCreatedAt(LocalDateTime.now());
      }
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("tagName", entity.getTagName())
              .addValue("slug", entity.getSlug())
              .addValue("createdAt", entity.getCreatedAt());
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

  /** Stamps the collection a tag was promoted into (or null to clear). */
  @Transactional
  public void updateConvertedCollectionId(Long tagId, Long collectionId) {
    String sql = "UPDATE tag SET converted_collection_id = :collectionId WHERE id = :id";
    MapSqlParameterSource params =
        createParameterSource().addValue("id", tagId).addValue("collectionId", collectionId);
    update(sql, params);
  }

  @Transactional
  public TagEntity findOrCreate(String tagName) {
    if (tagName == null || tagName.trim().isEmpty()) {
      return null;
    }

    String trimmedName = tagName.trim();

    String slug = SlugUtil.generateSlug(trimmedName);
    Optional<TagEntity> existing = findBySlug(slug);
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
        SELECT t.id, t.tag_name, t.slug, t.converted_collection_id, t.created_at
        FROM tag t
        JOIN collection_tags ct ON t.id = ct.tag_id
        WHERE ct.collection_id = :collectionId
        ORDER BY t.tag_name ASC
        """;
    MapSqlParameterSource params = createParameterSource().addValue("collectionId", collectionId);
    return query(sql, TAG_ROW_MAPPER, params);
  }

  /**
   * Batch-load collection tags (full {@link TagEntity}) for a set of collection ids, in a single
   * query. Returns a map collectionId -&gt; ordered list of tags ({@code tag_name ASC});
   * collections with no tags are simply absent from the map. Empty/null input returns an empty map.
   * Mirrors {@link #findTagsByContentIds} for collection-level tags. Used by {@link
   * edens.zac.portfolio.backend.services.SyntheticCollectionResolver} to enrich each {@code
   * COLLECTION} content-ref block with its tags, so synthetic list views (e.g. {@code
   * all-collections}) carry per-collection tags for client-side filtering.
   */
  @Transactional(readOnly = true)
  public Map<Long, List<TagEntity>> findTagsByCollectionIds(List<Long> collectionIds) {
    if (collectionIds == null || collectionIds.isEmpty()) {
      return Map.of();
    }

    String sql =
        """
        SELECT ct.collection_id, t.id, t.tag_name, t.slug, t.converted_collection_id, t.created_at
        FROM collection_tags ct
        JOIN tag t ON ct.tag_id = t.id
        WHERE ct.collection_id IN (:collectionIds)
        ORDER BY t.tag_name ASC
        """;
    MapSqlParameterSource params = createParameterSource().addValue("collectionIds", collectionIds);

    Map<Long, List<TagEntity>> result = new HashMap<>();
    namedParameterJdbcTemplate.query(
        sql,
        params,
        rs -> {
          Long collectionId = rs.getLong("collection_id");
          TagEntity tag =
              TagEntity.builder()
                  .id(rs.getLong("id"))
                  .tagName(rs.getString("tag_name"))
                  .slug(rs.getString("slug"))
                  .convertedCollectionId(getLong(rs, "converted_collection_id"))
                  .createdAt(getLocalDateTime(rs, "created_at"))
                  .build();
          result.computeIfAbsent(collectionId, k -> new ArrayList<>()).add(tag);
        });

    return result;
  }

  /**
   * Collections carrying the tag within the allowed visibilities, ordered rating- then date-desc.
   * The tag-view's primary members; empty when none.
   */
  @Transactional(readOnly = true)
  public List<CollectionEntity> findCollectionsByTagId(
      Long tagId, List<CollectionVisibility> allowed) {
    if (tagId == null || allowed == null || allowed.isEmpty()) {
      return List.of();
    }
    String sql =
        """
        SELECT c.id, c.type, c.title, c.slug, c.description, c.collection_date,
               c.visibility, c.display_mode, c.cover_image_id, c.content_per_page, c.total_content,
               c.rows_wide, c.gallery_password, c.recipient_emails, c.rating, c.created_at, c.updated_at
        FROM collection c
        JOIN collection_tags ct ON ct.collection_id = c.id
        WHERE ct.tag_id = :tagId
          AND c.visibility IN (:visibilities)
        ORDER BY c.rating DESC NULLS LAST, c.collection_date DESC NULLS LAST
        """;
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("tagId", tagId)
            .addValue("visibilities", allowed.stream().map(CollectionVisibility::name).toList());
    return query(sql, COLLECTION_ROW_MAPPER, params);
  }

  /**
   * IDs of tagged IMAGE content, newest first. Content has no visibility of its own, so an image
   * qualifies only via a visible membership ({@code collection_content.visible}) in a collection
   * within {@code allowed}. Distinct; empty when none qualify.
   */
  @Transactional(readOnly = true)
  public List<Long> findImageContentByTagId(Long tagId, List<CollectionVisibility> allowed) {
    if (tagId == null || allowed == null || allowed.isEmpty()) {
      return List.of();
    }
    String sql =
        """
        SELECT DISTINCT c.id, c.created_at
        FROM content c
        JOIN content_tags ctg ON ctg.content_id = c.id
        JOIN collection_content cc ON cc.content_id = c.id
        JOIN collection col ON col.id = cc.collection_id
        WHERE ctg.tag_id = :tagId
          AND c.content_type = 'IMAGE'
          AND cc.visible = true
          AND col.visibility IN (:visibilities)
        ORDER BY c.created_at DESC NULLS LAST, c.id DESC
        """;
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("tagId", tagId)
            .addValue("visibilities", allowed.stream().map(CollectionVisibility::name).toList());
    return query(sql, (rs, rowNum) -> rs.getLong("id"), params);
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
        SELECT ct.content_id, t.id, t.tag_name, t.slug, t.converted_collection_id, t.created_at
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
                  .convertedCollectionId(getLong(rs, "converted_collection_id"))
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
        SELECT t.id, t.tag_name, t.slug, t.converted_collection_id, t.created_at
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
