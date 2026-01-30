package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.model.CollectionSummary;
import edens.zac.portfolio.backend.types.CollectionType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** DAO for CollectionEntity using raw SQL queries. Replaces CollectionRepository. */
@Component
@Slf4j
public class CollectionDao extends BaseDao {

  public CollectionDao(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  private static final String SELECT_COLLECTION =
      """
            SELECT id, type, title, slug, description, location_id, collection_date,
                   visible, display_mode, cover_image_id, content_per_page, total_content,
                   created_at, updated_at
            FROM collection
            """;

  /**
   * RowMapper for CollectionEntity. Note: Relationships (collectionContent, tags, people) are
   * loaded separately.
   */
  private static final RowMapper<CollectionEntity> COLLECTION_ROW_MAPPER =
      (rs, rowNum) -> {
        CollectionEntity entity = new CollectionEntity();
        entity.setId(rs.getLong("id"));
        entity.setType(CollectionType.valueOf(rs.getString("type")));
        entity.setTitle(rs.getString("title"));
        entity.setSlug(rs.getString("slug"));
        entity.setDescription(rs.getString("description"));
        entity.setLocationId(getLong(rs, "location_id"));

        entity.setCollectionDate(getLocalDate(rs, "collection_date"));

        entity.setVisible(rs.getBoolean("visible"));

        String displayMode = rs.getString("display_mode");
        if (displayMode != null) {
          try {
            entity.setDisplayMode(
                edens.zac.portfolio.backend.model.CollectionBaseModel.DisplayMode.valueOf(
                    displayMode));
          } catch (IllegalArgumentException e) {
            log.warn("Invalid display_mode value: {}", displayMode);
          }
        }

        Long coverImageId = getLong(rs, "cover_image_id");
        if (coverImageId != null) {
          // Store the ID reference directly (entity uses coverImageId, not coverImage
          // entity)
          entity.setCoverImageId(coverImageId);
        }

        entity.setContentPerPage(getInteger(rs, "content_per_page"));

        entity.setTotalContent(getInteger(rs, "total_content"));

        entity.setCreatedAt(getLocalDateTime(rs, "created_at"));
        entity.setUpdatedAt(getLocalDateTime(rs, "updated_at"));

        return entity;
      };

  /** Find collection by slug. */
  @Transactional(readOnly = true)
  public Optional<CollectionEntity> findBySlug(String slug) {
    String sql = SELECT_COLLECTION + " WHERE slug = :slug";
    MapSqlParameterSource params = createParameterSource().addValue("slug", slug);
    return queryForObject(sql, COLLECTION_ROW_MAPPER, params);
  }

  /** Check if collection exists by slug. */
  @Transactional(readOnly = true)
  public boolean existsBySlug(String slug) {
    String sql = "SELECT COUNT(*) > 0 FROM collection WHERE slug = :slug";
    MapSqlParameterSource params = createParameterSource().addValue("slug", slug);
    Boolean result = namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class);
    return result != null && result;
  }

  /** Find top 50 collections by type and visible=true, ordered by collection_date DESC. */
  @Transactional(readOnly = true)
  public List<CollectionEntity> findTop50ByTypeAndVisibleTrueOrderByCollectionDateDesc(
      CollectionType type) {
    String sql =
        SELECT_COLLECTION
            + " WHERE type = :type AND visible = true "
            + "ORDER BY collection_date DESC NULLS LAST "
            + "LIMIT 50";
    MapSqlParameterSource params = createParameterSource().addValue("type", type.name());
    return query(sql, COLLECTION_ROW_MAPPER, params);
  }

  /** Find all visible collections by type, ordered by collection_date DESC. */
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

  /** Find top 50 collections by type (visibility irrelevant), ordered by collection_date DESC. */
  @Transactional(readOnly = true)
  public List<CollectionEntity> findTop50ByTypeOrderByCollectionDateDesc(CollectionType type) {
    String sql =
        SELECT_COLLECTION
            + " WHERE type = :type "
            + "ORDER BY collection_date DESC NULLS LAST "
            + "LIMIT 50";
    MapSqlParameterSource params = createParameterSource().addValue("type", type.name());
    return query(sql, COLLECTION_ROW_MAPPER, params);
  }

  /** Count collections by type. */
  @Transactional(readOnly = true)
  public long countByType(CollectionType type) {
    String sql = "SELECT COUNT(*) FROM collection WHERE type = :type";
    MapSqlParameterSource params = createParameterSource().addValue("type", type.name());
    Long count = namedParameterJdbcTemplate.queryForObject(sql, params, Long.class);
    return count != null ? count : 0L;
  }

  /** Count visible collections by type. */
  @Transactional(readOnly = true)
  public long countByTypeAndVisibleTrue(CollectionType type) {
    String sql = "SELECT COUNT(*) FROM collection WHERE type = :type AND visible = true";
    MapSqlParameterSource params = createParameterSource().addValue("type", type.name());
    Long count = namedParameterJdbcTemplate.queryForObject(sql, params, Long.class);
    return count != null ? count : 0L;
  }

  /** Find all collections ordered by collection_date DESC. */
  @Transactional(readOnly = true)
  public List<CollectionEntity> findAllByOrderByCollectionDateDesc() {
    String sql = SELECT_COLLECTION + " ORDER BY collection_date DESC NULLS LAST";
    return query(sql, COLLECTION_ROW_MAPPER);
  }

  /** Find all collections ordered by collection_date DESC with pagination. */
  @Transactional(readOnly = true)
  public List<CollectionEntity> findAllByOrderByCollectionDateDesc(int limit, int offset) {
    String sql =
        SELECT_COLLECTION + " ORDER BY collection_date DESC NULLS LAST LIMIT :limit OFFSET :offset";
    MapSqlParameterSource params =
        createParameterSource().addValue("limit", limit).addValue("offset", offset);
    return query(sql, COLLECTION_ROW_MAPPER, params);
  }

  /** Count all collections. */
  @Transactional(readOnly = true)
  public long countAllCollections() {
    String sql = "SELECT COUNT(*) FROM collection";
    Long count = jdbcTemplate.queryForObject(sql, Long.class);
    return count != null ? count : 0L;
  }

  /** Find collection ID and title only (for metadata). */
  @Transactional(readOnly = true)
  public List<CollectionSummary> findIdAndTitleOnly() {
    String sql = "SELECT id, title FROM collection ORDER BY title ASC";
    return jdbcTemplate.query(
        sql, (rs, rowNum) -> new CollectionSummary(rs.getLong("id"), rs.getString("title")));
  }

  /** Save a new collection. Returns the entity with generated ID. */
  @Transactional
  public CollectionEntity save(CollectionEntity entity) {
    if (entity.getId() == null) {
      // Insert
      String sql =
          """
                    INSERT INTO collection (type, title, slug, description, location_id, collection_date,
                                           visible, display_mode, cover_image_id, content_per_page, total_content,
                                           created_at, updated_at)
                    VALUES (:type, :title, :slug, :description, :locationId, :collectionDate,
                            :visible, :displayMode, :coverImageId, :contentPerPage, :totalContent,
                            :createdAt, :updatedAt)
                    """;

      MapSqlParameterSource params =
          createParameterSource()
              .addValue("type", entity.getType().name())
              .addValue("title", entity.getTitle())
              .addValue("slug", entity.getSlug())
              .addValue("description", entity.getDescription())
              .addValue("locationId", entity.getLocationId())
              .addValue("collectionDate", entity.getCollectionDate())
              .addValue("visible", entity.getVisible())
              .addValue(
                  "displayMode",
                  entity.getDisplayMode() != null ? entity.getDisplayMode().name() : null)
              .addValue("coverImageId", entity.getCoverImageId())
              .addValue("contentPerPage", entity.getContentPerPage())
              .addValue("totalContent", entity.getTotalContent())
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
      // Update
      String sql =
          """
                    UPDATE collection
                    SET type = :type, title = :title, slug = :slug, description = :description,
                        location_id = :locationId,
                        collection_date = :collectionDate, visible = :visible, display_mode = :displayMode,
                        cover_image_id = :coverImageId, content_per_page = :contentPerPage, total_content = :totalContent,
                        updated_at = :updatedAt
                    WHERE id = :id
                    """;

      MapSqlParameterSource params =
          createParameterSource()
              .addValue("id", entity.getId())
              .addValue("type", entity.getType().name())
              .addValue("title", entity.getTitle())
              .addValue("slug", entity.getSlug())
              .addValue("description", entity.getDescription())
              .addValue("locationId", entity.getLocationId())
              .addValue("collectionDate", entity.getCollectionDate())
              .addValue("visible", entity.getVisible())
              .addValue(
                  "displayMode",
                  entity.getDisplayMode() != null ? entity.getDisplayMode().name() : null)
              .addValue("coverImageId", entity.getCoverImageId())
              .addValue("contentPerPage", entity.getContentPerPage())
              .addValue("totalContent", entity.getTotalContent())
              .addValue("updatedAt", LocalDateTime.now());

      update(sql, params);
      return entity;
    }
  }

  /** Find collection by ID. */
  @Transactional(readOnly = true)
  public Optional<CollectionEntity> findById(Long id) {
    String sql = SELECT_COLLECTION + " WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    return queryForObject(sql, COLLECTION_ROW_MAPPER, params);
  }

  /**
   * Batch fetch multiple collections by IDs in a single query.
   * More efficient than calling findById in a loop (avoids N+1).
   */
  @Transactional(readOnly = true)
  public List<CollectionEntity> findByIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    String sql = SELECT_COLLECTION + " WHERE id IN (:ids)";
    MapSqlParameterSource params = createParameterSource().addValue("ids", ids);
    return query(sql, COLLECTION_ROW_MAPPER, params);
  }

  /** Delete collection by ID. */
  @Transactional
  public void deleteById(Long id) {
    String sql = "DELETE FROM collection WHERE id = :id";
    MapSqlParameterSource params = createParameterSource().addValue("id", id);
    update(sql, params);
  }

  /**
   * Save collection people (many-to-many relationship). Deletes existing people and inserts new
   * ones.
   */
  @Transactional
  public void saveCollectionPeople(Long collectionId, List<Long> personIds) {
    // Delete existing people
    String deleteSql = "DELETE FROM collection_people WHERE collection_id = :collectionId";
    MapSqlParameterSource deleteParams =
        createParameterSource().addValue("collectionId", collectionId);
    update(deleteSql, deleteParams);

    // Insert new people
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

  /** Load people for a collection. */
  @Transactional(readOnly = true)
  public List<Long> findCollectionPersonIds(Long collectionId) {
    String sql = "SELECT person_id FROM collection_people WHERE collection_id = :collectionId";
    MapSqlParameterSource params = createParameterSource().addValue("collectionId", collectionId);
    return namedParameterJdbcTemplate.queryForList(sql, params, Long.class);
  }
}
