package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.MessageEntity;
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
}
