package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class CollectionContentDao extends BaseDao {

    public CollectionContentDao(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    private static final String SELECT_COLLECTION_CONTENT = """
        SELECT id, collection_id, content_id, order_index, visible, created_at, updated_at
        FROM collection_content
        """;

    private static final RowMapper<CollectionContentEntity> COLLECTION_CONTENT_ROW_MAPPER = (rs, rowNum) -> {
        return CollectionContentEntity.builder()
            .id(rs.getLong("id"))
            .collectionId(rs.getLong("collection_id"))
            .contentId(rs.getLong("content_id"))
            .orderIndex(getInteger(rs, "order_index"))
            .visible(getBoolean(rs, "visible"))
            .createdAt(getLocalDateTime(rs, "created_at"))
            .updatedAt(getLocalDateTime(rs, "updated_at"))
            .build();
    };

    @Transactional(readOnly = true)
    public List<CollectionContentEntity> findByCollectionIdOrderByOrderIndex(Long collectionId) {
        String sql = SELECT_COLLECTION_CONTENT +
            " WHERE collection_id = :collectionId " +
            "ORDER BY order_index ASC";
        MapSqlParameterSource params = createParameterSource().addValue("collectionId", collectionId);
        return query(sql, COLLECTION_CONTENT_ROW_MAPPER, params);
    }

    @Transactional(readOnly = true)
    public List<CollectionContentEntity> findByCollectionId(Long collectionId, int limit, int offset) {
        String sql = SELECT_COLLECTION_CONTENT +
            " WHERE collection_id = :collectionId " +
            "ORDER BY order_index ASC " +
            "LIMIT :limit OFFSET :offset";
        MapSqlParameterSource params = createParameterSource()
            .addValue("collectionId", collectionId)
            .addValue("limit", limit)
            .addValue("offset", offset);
        return query(sql, COLLECTION_CONTENT_ROW_MAPPER, params);
    }

    @Transactional(readOnly = true)
    public long countByCollectionId(Long collectionId) {
        String sql = "SELECT COUNT(*) FROM collection_content WHERE collection_id = :collectionId";
        MapSqlParameterSource params = createParameterSource().addValue("collectionId", collectionId);
        Long count = namedParameterJdbcTemplate.queryForObject(sql, params, Long.class);
        return count != null ? count : 0L;
    }

    @Transactional(readOnly = true)
    public List<CollectionContentEntity> findByCollectionIdAndContentType(Long collectionId, String contentType) {
        String sql = """
            SELECT cc.id, cc.collection_id, cc.content_id, cc.order_index, cc.visible,
                   cc.created_at, cc.updated_at
            FROM collection_content cc
            JOIN content c ON cc.content_id = c.id
            WHERE cc.collection_id = :collectionId AND c.content_type = :contentType
            ORDER BY cc.order_index ASC
            """;
        MapSqlParameterSource params = createParameterSource()
            .addValue("collectionId", collectionId)
            .addValue("contentType", contentType);
        return query(sql, COLLECTION_CONTENT_ROW_MAPPER, params);
    }

    @Transactional(readOnly = true)
    public Integer getMaxOrderIndexForCollection(Long collectionId) {
        String sql = "SELECT MAX(order_index) FROM collection_content WHERE collection_id = :collectionId";
        MapSqlParameterSource params = createParameterSource().addValue("collectionId", collectionId);
        return namedParameterJdbcTemplate.queryForObject(sql, params, Integer.class);
    }

    @Transactional
    public void updateOrderIndex(Long id, Integer orderIndex) {
        String sql = "UPDATE collection_content SET order_index = :orderIndex WHERE id = :id";
        MapSqlParameterSource params = createParameterSource()
            .addValue("orderIndex", orderIndex)
            .addValue("id", id);
        update(sql, params);
    }

    @Transactional
    public void updateVisible(Long id, Boolean visible) {
        String sql = "UPDATE collection_content SET visible = :visible WHERE id = :id";
        MapSqlParameterSource params = createParameterSource()
            .addValue("visible", visible)
            .addValue("id", id);
        update(sql, params);
    }

    @Transactional
    public int shiftOrderIndices(Long collectionId, Integer startIndex, Integer endIndex, Integer shiftAmount) {
        String sql = """
            UPDATE collection_content
            SET order_index = order_index + :shiftAmount
            WHERE collection_id = :collectionId AND order_index >= :startIndex AND order_index <= :endIndex
            """;
        MapSqlParameterSource params = createParameterSource()
            .addValue("shiftAmount", shiftAmount)
            .addValue("collectionId", collectionId)
            .addValue("startIndex", startIndex)
            .addValue("endIndex", endIndex);
        return update(sql, params);
    }

    @Transactional
    public void deleteByCollectionId(Long collectionId) {
        String sql = "DELETE FROM collection_content WHERE collection_id = :collectionId";
        MapSqlParameterSource params = createParameterSource().addValue("collectionId", collectionId);
        update(sql, params);
    }

    @Transactional(readOnly = true)
    public Optional<CollectionContentEntity> findByCollectionIdAndOrderIndex(Long collectionId, Integer orderIndex) {
        String sql = SELECT_COLLECTION_CONTENT +
            " WHERE collection_id = :collectionId AND order_index = :orderIndex";
        MapSqlParameterSource params = createParameterSource()
            .addValue("collectionId", collectionId)
            .addValue("orderIndex", orderIndex);
        return queryForObject(sql, COLLECTION_CONTENT_ROW_MAPPER, params);
    }

    @Transactional
    public void removeContentFromCollection(Long collectionId, List<Long> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) {
            return;
        }
        String sql = "DELETE FROM collection_content WHERE collection_id = :collectionId AND content_id IN (:contentIds)";
        MapSqlParameterSource params = createParameterSource()
            .addValue("collectionId", collectionId)
            .addValue("contentIds", contentIds);
        update(sql, params);
    }

    @Transactional(readOnly = true)
    public Optional<CollectionContentEntity> findByCollectionIdAndContentId(Long collectionId, Long contentId) {
        String sql = SELECT_COLLECTION_CONTENT +
            " WHERE collection_id = :collectionId AND content_id = :contentId";
        MapSqlParameterSource params = createParameterSource()
            .addValue("collectionId", collectionId)
            .addValue("contentId", contentId);
        return queryForObject(sql, COLLECTION_CONTENT_ROW_MAPPER, params);
    }

    @Transactional(readOnly = true)
    public List<CollectionContentEntity> findByContentIdsIn(List<Long> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) {
            return List.of();
        }
        String sql = SELECT_COLLECTION_CONTENT +
            " WHERE content_id IN (:contentIds)";
        MapSqlParameterSource params = createParameterSource().addValue("contentIds", contentIds);
        return query(sql, COLLECTION_CONTENT_ROW_MAPPER, params);
    }

    @Transactional
    public int updateOrderIndexForContent(Long collectionId, Long contentId, Integer orderIndex) {
        String sql = """
            UPDATE collection_content
            SET order_index = :orderIndex
            WHERE collection_id = :collectionId AND content_id = :contentId
            """;
        MapSqlParameterSource params = createParameterSource()
            .addValue("orderIndex", orderIndex)
            .addValue("collectionId", collectionId)
            .addValue("contentId", contentId);
        return update(sql, params);
    }

    @Transactional
    public CollectionContentEntity save(CollectionContentEntity entity) {
        if (entity.getId() == null) {
            String sql = """
                INSERT INTO collection_content (collection_id, content_id, order_index, visible, created_at, updated_at)
                VALUES (:collectionId, :contentId, :orderIndex, :visible, :createdAt, :updatedAt)
                """;
            MapSqlParameterSource params = createParameterSource()
                .addValue("collectionId", entity.getCollectionId())
                .addValue("contentId", entity.getContentId())
                .addValue("orderIndex", entity.getOrderIndex())
                .addValue("visible", entity.getVisible())
                .addValue("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt() : java.time.LocalDateTime.now())
                .addValue("updatedAt", entity.getUpdatedAt() != null ? entity.getUpdatedAt() : java.time.LocalDateTime.now());
            Long id = insertAndReturnId(sql, "id", params);
            entity.setId(id);
            return entity;
        } else {
            String sql = """
                UPDATE collection_content
                SET collection_id = :collectionId, content_id = :contentId, order_index = :orderIndex, visible = :visible, updated_at = :updatedAt
                WHERE id = :id
                """;
            MapSqlParameterSource params = createParameterSource()
                .addValue("collectionId", entity.getCollectionId())
                .addValue("contentId", entity.getContentId())
                .addValue("orderIndex", entity.getOrderIndex())
                .addValue("visible", entity.getVisible())
                .addValue("updatedAt", java.time.LocalDateTime.now())
                .addValue("id", entity.getId());
            update(sql, params);
            return entity;
        }
    }

    @Transactional
    public void deleteById(Long id) {
        String sql = "DELETE FROM collection_content WHERE id = :id";
        MapSqlParameterSource params = createParameterSource().addValue("id", id);
        update(sql, params);
    }
}
