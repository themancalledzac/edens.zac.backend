package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.DisplayMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
 * Repository for CollectionEntity and CollectionContentEntity. Consolidates CollectionDao and
 * CollectionContentDao.
 */
@Component
@Slf4j
public class CollectionRepository extends BaseDao {

  public CollectionRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  // ============================================================
  // Collection RowMapper & SQL
  // ============================================================

  private static final String SELECT_COLLECTION =
      """
      SELECT id, type, title, slug, description, collection_date,
             visible, display_mode, cover_image_id, content_per_page, total_content,
             rows_wide, password_hash, created_at, updated_at
      FROM collection
      """;

  private static final RowMapper<CollectionEntity> COLLECTION_ROW_MAPPER =
      (rs, rowNum) -> {
        CollectionEntity entity = new CollectionEntity();
        entity.setId(rs.getLong("id"));
        entity.setType(CollectionType.valueOf(rs.getString("type")));
        entity.setTitle(rs.getString("title"));
        entity.setSlug(rs.getString("slug"));
        entity.setDescription(rs.getString("description"));
        entity.setCollectionDate(getLocalDate(rs, "collection_date"));
        entity.setVisible(rs.getBoolean("visible"));

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
        entity.setPasswordHash(rs.getString("password_hash"));
        entity.setCreatedAt(getLocalDateTime(rs, "created_at"));
        entity.setUpdatedAt(getLocalDateTime(rs, "updated_at"));

        return entity;
      };

  // ============================================================
  // CollectionContent RowMapper & SQL
  // ============================================================

  private static final String SELECT_COLLECTION_CONTENT =
      """
      SELECT id, collection_id, content_id, order_index, visible, created_at, updated_at
      FROM collection_content
      """;

  private static final RowMapper<CollectionContentEntity> COLLECTION_CONTENT_ROW_MAPPER =
      (rs, rowNum) ->
          CollectionContentEntity.builder()
              .id(rs.getLong("id"))
              .collectionId(rs.getLong("collection_id"))
              .contentId(rs.getLong("content_id"))
              .orderIndex(getInteger(rs, "order_index"))
              .visible(getBoolean(rs, "visible"))
              .createdAt(getLocalDateTime(rs, "created_at"))
              .updatedAt(getLocalDateTime(rs, "updated_at"))
              .build();

  // ============================================================
  // Collection CRUD Operations
  // ============================================================

  @Transactional(readOnly = true)
  public Optional<CollectionEntity> findBySlug(String slug) {
    String sql = SELECT_COLLECTION + " WHERE slug = :slug";
    MapSqlParameterSource params = createParameterSource().addValue("slug", slug);
    return queryForObject(sql, COLLECTION_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<CollectionEntity> findByTypeAndVisibleTrueOrderByCollectionDateDesc(
      CollectionType type) {
    String sql =
        SELECT_COLLECTION
            + " WHERE type = :type AND visible = true "
            + "ORDER BY collection_date DESC NULLS LAST";
    MapSqlParameterSource params = createParameterSource().addValue("type", type.name());
    return query(sql, COLLECTION_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<CollectionEntity> findByTypeOrderByCollectionDateDesc(
      CollectionType type, int limit, int offset) {
    String sql =
        SELECT_COLLECTION
            + " WHERE type = :type AND visible = true "
            + "ORDER BY collection_date DESC NULLS LAST "
            + "LIMIT :limit OFFSET :offset";
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("type", type.name())
            .addValue("limit", limit)
            .addValue("offset", offset);
    return query(sql, COLLECTION_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public long countByType(CollectionType type) {
    String sql = "SELECT COUNT(*) FROM collection WHERE type = :type AND visible = true";
    MapSqlParameterSource params = createParameterSource().addValue("type", type.name());
    Long count = namedParameterJdbcTemplate.queryForObject(sql, params, Long.class);
    return count != null ? count : 0L;
  }

  @Transactional(readOnly = true)
  public List<CollectionEntity> findVisibleByLocationName(
      String locationName, int limit, int offset) {
    String sql =
        """
        SELECT c.id, c.type, c.title, c.slug, c.description, c.collection_date,
               c.visible, c.display_mode, c.cover_image_id, c.content_per_page, c.total_content,
               c.rows_wide, c.password_hash, c.created_at, c.updated_at
        FROM collection c
        JOIN collection_locations cl ON c.id = cl.collection_id
        JOIN location l ON cl.location_id = l.id
        WHERE l.location_name = :locationName AND c.visible = true
        ORDER BY c.collection_date DESC NULLS LAST
        LIMIT :limit OFFSET :offset
        """;
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("locationName", locationName)
            .addValue("limit", limit)
            .addValue("offset", offset);
    return query(sql, COLLECTION_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<Long> findVisibleIdsByLocationName(String locationName) {
    String sql =
        "SELECT c.id FROM collection c "
            + "JOIN collection_locations cl ON c.id = cl.collection_id "
            + "JOIN location l ON cl.location_id = l.id "
            + "WHERE l.location_name = :locationName AND c.visible = true";
    MapSqlParameterSource params = createParameterSource().addValue("locationName", locationName);
    return namedParameterJdbcTemplate.queryForList(sql, params, Long.class);
  }

  @Transactional(readOnly = true)
  public long countVisibleByLocationName(String locationName) {
    String sql =
        """
        SELECT COUNT(*) FROM collection c
        JOIN collection_locations cl ON c.id = cl.collection_id
        JOIN location l ON cl.location_id = l.id
        WHERE l.location_name = :locationName AND c.visible = true
        """;
    MapSqlParameterSource params = createParameterSource().addValue("locationName", locationName);
    Long count = namedParameterJdbcTemplate.queryForObject(sql, params, Long.class);
    return count != null ? count : 0L;
  }

  @Transactional(readOnly = true)
  public List<CollectionEntity> findAllByOrderByCollectionDateDesc() {
    String sql = SELECT_COLLECTION + " ORDER BY collection_date DESC NULLS LAST";
    return query(sql, COLLECTION_ROW_MAPPER);
  }

  @Transactional(readOnly = true)
  public List<CollectionEntity> findAllByOrderByCollectionDateDesc(int limit, int offset) {
    String sql =
        SELECT_COLLECTION + " ORDER BY collection_date DESC NULLS LAST LIMIT :limit OFFSET :offset";
    MapSqlParameterSource params =
        createParameterSource().addValue("limit", limit).addValue("offset", offset);
    return query(sql, COLLECTION_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<CollectionEntity> findVisibleByOrderByCollectionDateDesc(int limit, int offset) {
    String sql =
        SELECT_COLLECTION
            + " WHERE visible = true ORDER BY collection_date DESC NULLS LAST LIMIT :limit OFFSET :offset";
    MapSqlParameterSource params =
        createParameterSource().addValue("limit", limit).addValue("offset", offset);
    return query(sql, COLLECTION_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public long countAllCollections() {
    String sql = "SELECT COUNT(*) FROM collection";
    Long count = jdbcTemplate.queryForObject(sql, Long.class);
    return count != null ? count : 0L;
  }

  @Transactional(readOnly = true)
  public long countVisibleCollections() {
    String sql = "SELECT COUNT(*) FROM collection WHERE visible = true";
    Long count = jdbcTemplate.queryForObject(sql, Long.class);
    return count != null ? count : 0L;
  }

  @Transactional(readOnly = true)
  public List<Records.CollectionList> findIdTitleSlugAndType() {
    String sql = "SELECT id, title, slug, type FROM collection ORDER BY title ASC";
    return jdbcTemplate.query(
        sql,
        (rs, rowNum) ->
            new Records.CollectionList(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("slug"),
                CollectionType.valueOf(rs.getString("type"))));
  }

  @Transactional
  public CollectionEntity save(CollectionEntity entity) {
    if (entity.getId() == null) {
      String sql =
          """
          INSERT INTO collection (type, title, slug, description, collection_date,
                                 visible, display_mode, cover_image_id, content_per_page, total_content,
                                 rows_wide, password_hash, created_at, updated_at)
          VALUES (:type, :title, :slug, :description, :collectionDate,
                  :visible, :displayMode, :coverImageId, :contentPerPage, :totalContent,
                  :rowsWide, :passwordHash, :createdAt, :updatedAt)
          """;

      MapSqlParameterSource params =
          createParameterSource()
              .addValue("type", entity.getType().name())
              .addValue("title", entity.getTitle())
              .addValue("slug", entity.getSlug())
              .addValue("description", entity.getDescription())
              .addValue("collectionDate", entity.getCollectionDate())
              .addValue("visible", entity.getVisible())
              .addValue(
                  "displayMode",
                  entity.getDisplayMode() != null ? entity.getDisplayMode().name() : null)
              .addValue("coverImageId", entity.getCoverImageId())
              .addValue("contentPerPage", entity.getContentPerPage())
              .addValue("totalContent", entity.getTotalContent())
              .addValue("rowsWide", entity.getRowsWide())
              .addValue("passwordHash", entity.getPasswordHash())
              .addValue(
                  "createdAt",
                  entity.getCreatedAt() != null ? entity.getCreatedAt() : LocalDateTime.now())
              .addValue(
                  "updatedAt",
                  entity.getUpdatedAt() != null ? entity.getUpdatedAt() : LocalDateTime.now());

      Long id = insertAndReturnId(sql, "id", params);
      entity.setId(id);
      return entity;
    } else {
      String sql =
          """
          UPDATE collection
          SET type = :type, title = :title, slug = :slug, description = :description,
              collection_date = :collectionDate, visible = :visible, display_mode = :displayMode,
              cover_image_id = :coverImageId, content_per_page = :contentPerPage, total_content = :totalContent,
              rows_wide = :rowsWide, password_hash = :passwordHash, updated_at = :updatedAt
          WHERE id = :id
          """;

      MapSqlParameterSource params =
          createParameterSource()
              .addValue("id", entity.getId())
              .addValue("type", entity.getType().name())
              .addValue("title", entity.getTitle())
              .addValue("slug", entity.getSlug())
              .addValue("description", entity.getDescription())
              .addValue("collectionDate", entity.getCollectionDate())
              .addValue("visible", entity.getVisible())
              .addValue(
                  "displayMode",
                  entity.getDisplayMode() != null ? entity.getDisplayMode().name() : null)
              .addValue("coverImageId", entity.getCoverImageId())
              .addValue("contentPerPage", entity.getContentPerPage())
              .addValue("totalContent", entity.getTotalContent())
              .addValue("rowsWide", entity.getRowsWide())
              .addValue("passwordHash", entity.getPasswordHash())
              .addValue("updatedAt", LocalDateTime.now());

      update(sql, params);
      return entity;
    }
  }

  @Transactional(readOnly = true)
  public Optional<CollectionEntity> findById(Long id) {
    String sql = SELECT_COLLECTION + " WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    return queryForObject(sql, COLLECTION_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<CollectionEntity> findByIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    String sql = SELECT_COLLECTION + " WHERE id IN (:ids)";
    MapSqlParameterSource params = createParameterSource().addValue("ids", ids);
    return query(sql, COLLECTION_ROW_MAPPER, params);
  }

  @Transactional
  public void deleteById(Long id) {
    String sql = "DELETE FROM collection WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    update(sql, params);
  }

  @Transactional
  public void saveCollectionPeople(Long collectionId, List<Long> personIds) {
    String deleteSql = "DELETE FROM collection_people WHERE collection_id = :collectionId";
    MapSqlParameterSource deleteParams =
        createParameterSource().addValue("collectionId", collectionId);
    update(deleteSql, deleteParams);

    if (personIds != null && !personIds.isEmpty()) {
      String insertSql =
          "INSERT INTO collection_people (collection_id, person_id) VALUES (:collectionId, :personId)";
      MapSqlParameterSource[] batchParams =
          personIds.stream()
              .map(
                  personId ->
                      createParameterSource()
                          .addValue("collectionId", collectionId)
                          .addValue("personId", personId))
              .toArray(MapSqlParameterSource[]::new);
      batchUpdate(insertSql, batchParams);
    }
  }

  @Transactional(readOnly = true)
  public List<Long> findCollectionPersonIds(Long collectionId) {
    String sql = "SELECT person_id FROM collection_people WHERE collection_id = :collectionId";
    MapSqlParameterSource params = createParameterSource().addValue("collectionId", collectionId);
    return namedParameterJdbcTemplate.queryForList(sql, params, Long.class);
  }

  // ============================================================
  // CollectionContent Operations
  // ============================================================

  @Transactional(readOnly = true)
  public List<CollectionContentEntity> findContentByCollectionIdOrderByOrderIndex(
      Long collectionId) {
    String sql =
        SELECT_COLLECTION_CONTENT
            + " WHERE collection_id = :collectionId "
            + "ORDER BY order_index ASC";
    MapSqlParameterSource params = createParameterSource().addValue("collectionId", collectionId);
    return query(sql, COLLECTION_CONTENT_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<CollectionContentEntity> findContentByCollectionId(
      Long collectionId, int limit, int offset) {
    String sql =
        SELECT_COLLECTION_CONTENT
            + " WHERE collection_id = :collectionId "
            + "ORDER BY order_index ASC "
            + "LIMIT :limit OFFSET :offset";
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("collectionId", collectionId)
            .addValue("limit", limit)
            .addValue("offset", offset);
    return query(sql, COLLECTION_CONTENT_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public long countContentByCollectionId(Long collectionId) {
    String sql = "SELECT COUNT(*) FROM collection_content WHERE collection_id = :collectionId";
    MapSqlParameterSource params = createParameterSource().addValue("collectionId", collectionId);
    Long count = namedParameterJdbcTemplate.queryForObject(sql, params, Long.class);
    return count != null ? count : 0L;
  }

  @Transactional(readOnly = true)
  public List<CollectionContentEntity> findContentByCollectionIdAndContentType(
      Long collectionId, String contentType) {
    String sql =
        """
        SELECT cc.id, cc.collection_id, cc.content_id, cc.order_index, cc.visible,
               cc.created_at, cc.updated_at
        FROM collection_content cc
        JOIN content c ON cc.content_id = c.id
        WHERE cc.collection_id = :collectionId AND c.content_type = :contentType
        ORDER BY cc.order_index ASC
        """;
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("collectionId", collectionId)
            .addValue("contentType", contentType);
    return query(sql, COLLECTION_CONTENT_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<CollectionContentEntity> findImageContentByCollectionIds(List<Long> collectionIds) {
    if (collectionIds == null || collectionIds.isEmpty()) {
      return List.of();
    }
    String sql =
        """
        SELECT cc.id, cc.collection_id, cc.content_id, cc.order_index, cc.visible,
               cc.created_at, cc.updated_at
        FROM collection_content cc
        JOIN content c ON cc.content_id = c.id
        WHERE cc.collection_id IN (:collectionIds) AND c.content_type = 'IMAGE'
        ORDER BY cc.collection_id, cc.order_index ASC
        """;
    MapSqlParameterSource params = createParameterSource().addValue("collectionIds", collectionIds);
    return query(sql, COLLECTION_CONTENT_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public Integer getMaxOrderIndexForCollection(Long collectionId) {
    String sql =
        "SELECT MAX(order_index) FROM collection_content WHERE collection_id = :collectionId";
    MapSqlParameterSource params = createParameterSource().addValue("collectionId", collectionId);
    return namedParameterJdbcTemplate.queryForObject(sql, params, Integer.class);
  }

  @Transactional
  public void updateContentOrderIndex(Long id, Integer orderIndex) {
    String sql = "UPDATE collection_content SET order_index = :orderIndex WHERE id = :id";
    MapSqlParameterSource params =
        createParameterSource().addValue("orderIndex", orderIndex).addValue("id", id);
    update(sql, params);
  }

  @Transactional
  public void updateContentVisible(Long id, Boolean visible) {
    String sql = "UPDATE collection_content SET visible = :visible WHERE id = :id";
    MapSqlParameterSource params =
        createParameterSource().addValue("visible", visible).addValue("id", id);
    update(sql, params);
  }

  @Transactional
  public int shiftContentOrderIndices(
      Long collectionId, Integer startIndex, Integer endIndex, Integer shiftAmount) {
    String sql =
        """
        UPDATE collection_content
        SET order_index = order_index + :shiftAmount
        WHERE collection_id = :collectionId AND order_index >= :startIndex AND order_index <= :endIndex
        """;
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("shiftAmount", shiftAmount)
            .addValue("collectionId", collectionId)
            .addValue("startIndex", startIndex)
            .addValue("endIndex", endIndex);
    return update(sql, params);
  }

  @Transactional
  public void deleteContentByCollectionId(Long collectionId) {
    String sql = "DELETE FROM collection_content WHERE collection_id = :collectionId";
    MapSqlParameterSource params = createParameterSource().addValue("collectionId", collectionId);
    update(sql, params);
  }

  @Transactional(readOnly = true)
  public Optional<CollectionContentEntity> findContentByCollectionIdAndOrderIndex(
      Long collectionId, Integer orderIndex) {
    String sql =
        SELECT_COLLECTION_CONTENT
            + " WHERE collection_id = :collectionId AND order_index = :orderIndex";
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("collectionId", collectionId)
            .addValue("orderIndex", orderIndex);
    return queryForObject(sql, COLLECTION_CONTENT_ROW_MAPPER, params);
  }

  @Transactional
  public void removeContentFromCollection(Long collectionId, List<Long> contentIds) {
    if (contentIds == null || contentIds.isEmpty()) {
      return;
    }
    String sql =
        "DELETE FROM collection_content WHERE collection_id = :collectionId AND content_id IN (:contentIds)";
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("collectionId", collectionId)
            .addValue("contentIds", contentIds);
    update(sql, params);
  }

  @Transactional(readOnly = true)
  public Optional<CollectionContentEntity> findContentByCollectionIdAndContentId(
      Long collectionId, Long contentId) {
    String sql =
        SELECT_COLLECTION_CONTENT
            + " WHERE collection_id = :collectionId AND content_id = :contentId";
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("collectionId", collectionId)
            .addValue("contentId", contentId);
    return queryForObject(sql, COLLECTION_CONTENT_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public List<CollectionContentEntity> findContentByContentIdsIn(List<Long> contentIds) {
    if (contentIds == null || contentIds.isEmpty()) {
      return List.of();
    }
    String sql = SELECT_COLLECTION_CONTENT + " WHERE content_id IN (:contentIds)";
    MapSqlParameterSource params = createParameterSource().addValue("contentIds", contentIds);
    return query(sql, COLLECTION_CONTENT_ROW_MAPPER, params);
  }

  @Transactional
  public int updateContentOrderIndexForContent(
      Long collectionId, Long contentId, Integer orderIndex) {
    String sql =
        """
        UPDATE collection_content
        SET order_index = :orderIndex
        WHERE collection_id = :collectionId AND content_id = :contentId
        """;
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("orderIndex", orderIndex)
            .addValue("collectionId", collectionId)
            .addValue("contentId", contentId);
    return update(sql, params);
  }

  @Transactional
  public int batchUpdateContentOrderIndexes(
      Long collectionId, Map<Long, Integer> contentIdToOrderIndex) {
    if (contentIdToOrderIndex == null || contentIdToOrderIndex.isEmpty()) {
      return 0;
    }

    StringBuilder sql =
        new StringBuilder("UPDATE collection_content SET order_index = CASE content_id");
    MapSqlParameterSource params = createParameterSource();
    params.addValue("collectionId", collectionId);

    List<Long> contentIds = new ArrayList<>();
    int index = 0;
    for (Map.Entry<Long, Integer> entry : contentIdToOrderIndex.entrySet()) {
      Long contentId = entry.getKey();
      Integer orderIndex = entry.getValue();
      String contentIdParam = "contentId" + index;
      String orderIndexParam = "orderIndex" + index;

      sql.append(" WHEN :").append(contentIdParam).append(" THEN :").append(orderIndexParam);
      params.addValue(contentIdParam, contentId);
      params.addValue(orderIndexParam, orderIndex);
      contentIds.add(contentId);
      index++;
    }

    sql.append(" END WHERE collection_id = :collectionId AND content_id IN (:contentIds)");
    params.addValue("contentIds", contentIds);

    return update(sql.toString(), params);
  }

  @Transactional
  public CollectionContentEntity saveContent(CollectionContentEntity entity) {
    if (entity.getId() == null) {
      String sql =
          """
          INSERT INTO collection_content (collection_id, content_id, order_index, visible, created_at, updated_at)
          VALUES (:collectionId, :contentId, :orderIndex, :visible, :createdAt, :updatedAt)
          """;
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("collectionId", entity.getCollectionId())
              .addValue("contentId", entity.getContentId())
              .addValue("orderIndex", entity.getOrderIndex())
              .addValue("visible", entity.getVisible())
              .addValue(
                  "createdAt",
                  entity.getCreatedAt() != null ? entity.getCreatedAt() : LocalDateTime.now())
              .addValue(
                  "updatedAt",
                  entity.getUpdatedAt() != null ? entity.getUpdatedAt() : LocalDateTime.now());
      Long id = insertAndReturnId(sql, "id", params);
      entity.setId(id);
      return entity;
    } else {
      String sql =
          """
          UPDATE collection_content
          SET collection_id = :collectionId, content_id = :contentId, order_index = :orderIndex, visible = :visible, updated_at = :updatedAt
          WHERE id = :id
          """;
      MapSqlParameterSource params =
          createParameterSource()
              .addValue("collectionId", entity.getCollectionId())
              .addValue("contentId", entity.getContentId())
              .addValue("orderIndex", entity.getOrderIndex())
              .addValue("visible", entity.getVisible())
              .addValue("updatedAt", LocalDateTime.now())
              .addValue("id", entity.getId());
      update(sql, params);
      return entity;
    }
  }

  @Transactional
  public void deleteContentById(Long id) {
    String sql = "DELETE FROM collection_content WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    update(sql, params);
  }
}
