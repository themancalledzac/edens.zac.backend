package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.TagEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * DAO for TagEntity using raw SQL queries.
 * Manages tags and their associations via collection_tags and content_tags join
 * tables.
 */
@Component
@Slf4j
public class TagDao extends BaseDao {

    public TagDao(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    private static final RowMapper<TagEntity> TAG_ROW_MAPPER = (rs, rowNum) -> TagEntity.builder()
            .id(rs.getLong("id"))
            .tagName(rs.getString("tag_name"))
            .createdAt(getLocalDateTime(rs, "created_at"))
            .build();

    // ============================================================
    // Tag CRUD Operations
    // ============================================================

    /**
     * Find tag by exact name.
     */
    @Transactional(readOnly = true)
    public Optional<TagEntity> findByTagName(String tagName) {
        String sql = "SELECT id, tag_name, created_at FROM tag WHERE tag_name = :tagName";
        MapSqlParameterSource params = createParameterSource().addValue("tagName", tagName);
        return queryForObject(sql, TAG_ROW_MAPPER, params);
    }

    /**
     * Find tag by name (case-insensitive).
     */
    @Transactional(readOnly = true)
    public Optional<TagEntity> findByTagNameIgnoreCase(String tagName) {
        String sql = "SELECT id, tag_name, created_at FROM tag WHERE LOWER(tag_name) = LOWER(:tagName)";
        MapSqlParameterSource params = createParameterSource().addValue("tagName", tagName);
        return queryForObject(sql, TAG_ROW_MAPPER, params);
    }

    /**
     * Find tags by name containing (case-insensitive, for autocomplete).
     */
    @Transactional(readOnly = true)
    public List<TagEntity> findByTagNameContainingIgnoreCase(String searchTerm) {
        String sql = "SELECT id, tag_name, created_at FROM tag WHERE LOWER(tag_name) LIKE LOWER(:searchTerm) ORDER BY tag_name ASC";
        MapSqlParameterSource params = createParameterSource().addValue("searchTerm", "%" + searchTerm + "%");
        return query(sql, TAG_ROW_MAPPER, params);
    }

    /**
     * Find all tags ordered by name.
     */
    @Transactional(readOnly = true)
    public List<TagEntity> findAllByOrderByTagNameAsc() {
        String sql = "SELECT id, tag_name, created_at FROM tag ORDER BY tag_name ASC";
        return query(sql, TAG_ROW_MAPPER);
    }

    /**
     * Find tag by ID.
     */
    @Transactional(readOnly = true)
    public Optional<TagEntity> findById(Long id) {
        String sql = "SELECT id, tag_name, created_at FROM tag WHERE id = :id";
        MapSqlParameterSource params = createParameterSource().addValue("id", id);
        return queryForObject(sql, TAG_ROW_MAPPER, params);
    }

    /**
     * Check if tag exists by name.
     */
    @Transactional(readOnly = true)
    public boolean existsByTagName(String tagName) {
        String sql = "SELECT COUNT(*) > 0 FROM tag WHERE tag_name = :tagName";
        MapSqlParameterSource params = createParameterSource().addValue("tagName", tagName);
        Boolean result = namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class);
        return result != null && result;
    }

    /**
     * Check if tag exists by name (case-insensitive).
     */
    @Transactional(readOnly = true)
    public boolean existsByTagNameIgnoreCase(String tagName) {
        String sql = "SELECT COUNT(*) > 0 FROM tag WHERE LOWER(tag_name) = LOWER(:tagName)";
        MapSqlParameterSource params = createParameterSource().addValue("tagName", tagName);
        Boolean result = namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class);
        return result != null && result;
    }

    /**
     * Save a tag. Returns entity with generated ID.
     */
    @Transactional
    public TagEntity save(TagEntity entity) {
        if (entity.getId() == null) {
            String sql = "INSERT INTO tag (tag_name, created_at) VALUES (:tagName, :createdAt)";
            MapSqlParameterSource params = createParameterSource()
                    .addValue("tagName", entity.getTagName())
                    .addValue("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt() : LocalDateTime.now());
            Long id = insertAndReturnId(sql, "id", params);
            entity.setId(id);
            return entity;
        } else {
            String sql = "UPDATE tag SET tag_name = :tagName WHERE id = :id";
            MapSqlParameterSource params = createParameterSource()
                    .addValue("tagName", entity.getTagName())
                    .addValue("id", entity.getId());
            update(sql, params);
            return entity;
        }
    }

    /**
     * Find or create a tag by name.
     * If the tag exists (case-insensitive), returns the existing one.
     * Otherwise, creates a new tag and returns it.
     */
    @Transactional
    public TagEntity findOrCreate(String tagName) {
        if (tagName == null || tagName.trim().isEmpty()) {
            return null;
        }

        String trimmedName = tagName.trim();

        // Try to find existing (case-insensitive)
        Optional<TagEntity> existing = findByTagNameIgnoreCase(trimmedName);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Create new
        TagEntity newTag = TagEntity.builder()
                .tagName(trimmedName)
                .createdAt(LocalDateTime.now())
                .build();
        return save(newTag);
    }

    /**
     * Delete tag by ID.
     * Note: collection_tags and content_tags entries will be cascade-deleted due to
     * FK constraints.
     */
    @Transactional
    public void deleteById(Long id) {
        String sql = "DELETE FROM tag WHERE id = :id";
        MapSqlParameterSource params = createParameterSource().addValue("id", id);
        update(sql, params);
    }

    // ============================================================
    // Collection Tags Operations
    // ============================================================

    /**
     * Save collection tags (many-to-many relationship via collection_tags).
     * Deletes existing tags for the collection and inserts new ones.
     *
     * @param collectionId The collection's ID
     * @param tagIds       List of tag IDs to associate
     */
    @Transactional
    public void saveCollectionTags(Long collectionId, List<Long> tagIds) {
        // Delete existing tags for this collection
        String deleteSql = "DELETE FROM collection_tags WHERE collection_id = :collectionId";
        MapSqlParameterSource deleteParams = createParameterSource()
                .addValue("collectionId", collectionId);
        update(deleteSql, deleteParams);

        // Insert new tags
        if (tagIds != null && !tagIds.isEmpty()) {
            String insertSql = "INSERT INTO collection_tags (collection_id, tag_id) VALUES (:collectionId, :tagId)";
            MapSqlParameterSource[] batchParams = tagIds.stream()
                    .map(tagId -> createParameterSource()
                            .addValue("collectionId", collectionId)
                            .addValue("tagId", tagId))
                    .toArray(MapSqlParameterSource[]::new);
            batchUpdate(insertSql, batchParams);
        }
    }

    /**
     * Find tag IDs for a collection.
     *
     * @param collectionId The collection's ID
     * @return List of tag IDs
     */
    @Transactional(readOnly = true)
    public List<Long> findCollectionTagIds(Long collectionId) {
        String sql = "SELECT tag_id FROM collection_tags WHERE collection_id = :collectionId";
        MapSqlParameterSource params = createParameterSource()
                .addValue("collectionId", collectionId);
        return namedParameterJdbcTemplate.queryForList(sql, params, Long.class);
    }

    /**
     * Find tags for a collection.
     *
     * @param collectionId The collection's ID
     * @return List of TagEntity objects
     */
    @Transactional(readOnly = true)
    public List<TagEntity> findCollectionTags(Long collectionId) {
        String sql = """
                SELECT t.id, t.tag_name, t.created_at
                FROM tag t
                JOIN collection_tags ct ON t.id = ct.tag_id
                WHERE ct.collection_id = :collectionId
                ORDER BY t.tag_name ASC
                """;
        MapSqlParameterSource params = createParameterSource()
                .addValue("collectionId", collectionId);
        return query(sql, TAG_ROW_MAPPER, params);
    }

    /**
     * Delete all tags for a collection.
     *
     * @param collectionId The collection's ID
     */
    @Transactional
    public void deleteCollectionTags(Long collectionId) {
        String sql = "DELETE FROM collection_tags WHERE collection_id = :collectionId";
        MapSqlParameterSource params = createParameterSource()
                .addValue("collectionId", collectionId);
        update(sql, params);
    }

    /**
     * Add a single tag to a collection.
     *
     * @param collectionId The collection's ID
     * @param tagId        The tag ID to add
     */
    @Transactional
    public void addCollectionTag(Long collectionId, Long tagId) {
        String sql = "INSERT INTO collection_tags (collection_id, tag_id) VALUES (:collectionId, :tagId) ON CONFLICT DO NOTHING";
        MapSqlParameterSource params = createParameterSource()
                .addValue("collectionId", collectionId)
                .addValue("tagId", tagId);
        update(sql, params);
    }

    /**
     * Remove a single tag from a collection.
     *
     * @param collectionId The collection's ID
     * @param tagId        The tag ID to remove
     */
    @Transactional
    public void removeCollectionTag(Long collectionId, Long tagId) {
        String sql = "DELETE FROM collection_tags WHERE collection_id = :collectionId AND tag_id = :tagId";
        MapSqlParameterSource params = createParameterSource()
                .addValue("collectionId", collectionId)
                .addValue("tagId", tagId);
        update(sql, params);
    }

    // ============================================================
    // Content Tags Operations
    // ============================================================

    /**
     * Save content tags (many-to-many relationship via content_tags).
     * Deletes existing tags for the content and inserts new ones.
     * Works for all content types (IMAGE, GIF, TEXT, COLLECTION) since they share
     * content.id.
     *
     * @param contentId The content's ID (from base content table)
     * @param tagIds    List of tag IDs to associate
     */
    @Transactional
    public void saveContentTags(Long contentId, List<Long> tagIds) {
        // Delete existing tags for this content
        String deleteSql = "DELETE FROM content_tags WHERE content_id = :contentId";
        MapSqlParameterSource deleteParams = createParameterSource()
                .addValue("contentId", contentId);
        update(deleteSql, deleteParams);

        // Insert new tags
        if (tagIds != null && !tagIds.isEmpty()) {
            String insertSql = "INSERT INTO content_tags (content_id, tag_id) VALUES (:contentId, :tagId)";
            MapSqlParameterSource[] batchParams = tagIds.stream()
                    .map(tagId -> createParameterSource()
                            .addValue("contentId", contentId)
                            .addValue("tagId", tagId))
                    .toArray(MapSqlParameterSource[]::new);
            batchUpdate(insertSql, batchParams);
        }
    }

    /**
     * Find tag IDs for content.
     *
     * @param contentId The content's ID (from base content table)
     * @return List of tag IDs
     */
    @Transactional(readOnly = true)
    public List<Long> findContentTagIds(Long contentId) {
        String sql = "SELECT tag_id FROM content_tags WHERE content_id = :contentId";
        MapSqlParameterSource params = createParameterSource()
                .addValue("contentId", contentId);
        return namedParameterJdbcTemplate.queryForList(sql, params, Long.class);
    }

    /**
     * Find tags for content.
     *
     * @param contentId The content's ID (from base content table)
     * @return List of TagEntity objects
     */
    @Transactional(readOnly = true)
    public List<TagEntity> findContentTags(Long contentId) {
        String sql = """
                SELECT t.id, t.tag_name, t.created_at
                FROM tag t
                JOIN content_tags ct ON t.id = ct.tag_id
                WHERE ct.content_id = :contentId
                ORDER BY t.tag_name ASC
                """;
        MapSqlParameterSource params = createParameterSource()
                .addValue("contentId", contentId);
        return query(sql, TAG_ROW_MAPPER, params);
    }

    /**
     * Delete all tags for content.
     *
     * @param contentId The content's ID (from base content table)
     */
    @Transactional
    public void deleteContentTags(Long contentId) {
        String sql = "DELETE FROM content_tags WHERE content_id = :contentId";
        MapSqlParameterSource params = createParameterSource()
                .addValue("contentId", contentId);
        update(sql, params);
    }

    /**
     * Add a single tag to content.
     *
     * @param contentId The content's ID (from base content table)
     * @param tagId     The tag ID to add
     */
    @Transactional
    public void addContentTag(Long contentId, Long tagId) {
        String sql = "INSERT INTO content_tags (content_id, tag_id) VALUES (:contentId, :tagId) ON CONFLICT DO NOTHING";
        MapSqlParameterSource params = createParameterSource()
                .addValue("contentId", contentId)
                .addValue("tagId", tagId);
        update(sql, params);
    }

    /**
     * Remove a single tag from content.
     *
     * @param contentId The content's ID (from base content table)
     * @param tagId     The tag ID to remove
     */
    @Transactional
    public void removeContentTag(Long contentId, Long tagId) {
        String sql = "DELETE FROM content_tags WHERE content_id = :contentId AND tag_id = :tagId";
        MapSqlParameterSource params = createParameterSource()
                .addValue("contentId", contentId)
                .addValue("tagId", tagId);
        update(sql, params);
    }
}
