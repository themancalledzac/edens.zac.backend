package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.MessageEntity;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class MessageRepository extends BaseDao {

  public MessageRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  private static final RowMapper<MessageEntity> MESSAGE_ROW_MAPPER =
      (rs, rowNum) -> {
        MessageEntity entity = new MessageEntity();
        entity.setId(rs.getLong("id"));
        entity.setEmail(rs.getString("email"));
        entity.setMessage(rs.getString("message"));
        entity.setCreatedAt(getLocalDateTime(rs, "created_at"));
        return entity;
      };

  @Transactional
  public MessageEntity insert(String email, String message) {
    String insertSql =
        """
        INSERT INTO messages (email, message)
        VALUES (:email, :message)
        """;
    var params = createParameterSource().addValue("email", email).addValue("message", message);
    Long id = insertAndReturnId(insertSql, "id", params);

    String selectSql = "SELECT id, email, message, created_at FROM messages WHERE id = :id";
    var selectParams = createParameterSource().addValue("id", id);
    return queryForObject(selectSql, MESSAGE_ROW_MAPPER, selectParams)
        .orElseThrow(() -> new IllegalStateException("Inserted message not found: " + id));
  }

  @Transactional(readOnly = true)
  public List<MessageEntity> findAll(int limit, int offset) {
    String sql =
        """
        SELECT id, email, message, created_at
        FROM messages
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
        """;
    MapSqlParameterSource params =
        createParameterSource().addValue("limit", limit).addValue("offset", offset);
    return query(sql, MESSAGE_ROW_MAPPER, params);
  }

  @Transactional(readOnly = true)
  public long count() {
    String sql = "SELECT COUNT(*) FROM messages";
    Long count =
        namedParameterJdbcTemplate.queryForObject(sql, createParameterSource(), Long.class);
    return count != null ? count : 0L;
  }

  @Transactional
  public int deleteById(long id) {
    String sql = "DELETE FROM messages WHERE id = :id";
    var params = createParameterSource().addValue("id", id);
    return update(sql, params);
  }

  /**
   * How many messages are older than {@code cutoff}. Exists so the retention job can report what a
   * purge would remove without removing it -- see {@code MessageRetentionService}, which runs in
   * that reporting mode by default.
   */
  @Transactional(readOnly = true)
  public long countCreatedBefore(LocalDateTime cutoff) {
    String sql = "SELECT COUNT(*) FROM messages WHERE created_at < :cutoff";
    var params = createParameterSource().addValue("cutoff", cutoff);
    Long count = namedParameterJdbcTemplate.queryForObject(sql, params, Long.class);
    return count != null ? count : 0L;
  }

  /**
   * Delete every message older than {@code cutoff}, returning the row count. Irreversible: the
   * contact form is the only writer and nothing archives what this removes. Covered by {@code
   * idx_messages_created_at}.
   */
  @Transactional
  public int deleteCreatedBefore(LocalDateTime cutoff) {
    String sql = "DELETE FROM messages WHERE created_at < :cutoff";
    var params = createParameterSource().addValue("cutoff", cutoff);
    return update(sql, params);
  }
}
