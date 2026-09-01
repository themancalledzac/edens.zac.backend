package edens.zac.portfolio.backend.dao;

import edens.zac.portfolio.backend.entity.MessageEntity;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

  private static final String SELECT_COLUMNS = "id, email, message, created_at, read_at";

  private static final RowMapper<MessageEntity> MESSAGE_ROW_MAPPER =
      (rs, rowNum) -> {
        MessageEntity entity = new MessageEntity();
        entity.setId(rs.getLong("id"));
        entity.setEmail(rs.getString("email"));
        entity.setMessage(rs.getString("message"));
        entity.setCreatedAt(getLocalDateTime(rs, "created_at"));
        entity.setReadAt(getLocalDateTime(rs, "read_at"));
        return entity;
      };

  /**
   * Build the WHERE fragment the admin list and its count share, binding any values into {@code
   * params}. Empty string when neither filter is set.
   *
   * <p>Both callers must use this one fragment. The admin list prints "N of M", so a total counted
   * over a different row set than the page is a wrong number rather than a stale one.
   */
  private static String appendFilters(Boolean unread, String q, MapSqlParameterSource params) {
    List<String> clauses = new ArrayList<>();
    if (unread != null) {
      clauses.add(unread ? "read_at IS NULL" : "read_at IS NOT NULL");
    }
    if (q != null && !q.isBlank()) {
      clauses.add("(LOWER(email) LIKE :q ESCAPE '\\' OR LOWER(message) LIKE :q ESCAPE '\\')");
      params.addValue("q", "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%");
    }
    return clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
  }

  /**
   * Neutralise LIKE wildcards in operator input, so searching "50%" matches that text instead of
   * every row. Pairs with the {@code ESCAPE '\'} in the clause above.
   */
  private static String escapeLike(String raw) {
    return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  @Transactional
  public MessageEntity insert(String email, String message) {
    String insertSql =
        """
        INSERT INTO messages (email, message)
        VALUES (:email, :message)
        """;
    var params = createParameterSource().addValue("email", email).addValue("message", message);
    Long id = insertAndReturnId(insertSql, "id", params);

    String selectSql = "SELECT " + SELECT_COLUMNS + " FROM messages WHERE id = :id";
    var selectParams = createParameterSource().addValue("id", id);
    return queryForObject(selectSql, MESSAGE_ROW_MAPPER, selectParams)
        .orElseThrow(() -> new IllegalStateException("Inserted message not found: " + id));
  }

  /**
   * One page of messages, newest first. {@code unread} null means both states; {@code q} null or
   * blank means no text filter, otherwise it is a case-insensitive substring of email or body.
   */
  @Transactional(readOnly = true)
  public List<MessageEntity> findAll(int limit, int offset, Boolean unread, String q) {
    MapSqlParameterSource params =
        createParameterSource().addValue("limit", limit).addValue("offset", offset);
    String sql =
        "SELECT "
            + SELECT_COLUMNS
            + " FROM messages"
            + appendFilters(unread, q, params)
            + " ORDER BY created_at DESC LIMIT :limit OFFSET :offset";
    return query(sql, MESSAGE_ROW_MAPPER, params);
  }

  /** How many messages match the same filters {@link #findAll} would page over. */
  @Transactional(readOnly = true)
  public long count(Boolean unread, String q) {
    MapSqlParameterSource params = createParameterSource();
    String sql = "SELECT COUNT(*) FROM messages" + appendFilters(unread, q, params);
    Long count = namedParameterJdbcTemplate.queryForObject(sql, params, Long.class);
    return count != null ? count : 0L;
  }

  /**
   * Set or clear the read marker, returning the row count so a missing id is distinguishable from a
   * no-op. Marking read is idempotent and keeps the first read time: {@code COALESCE} leaves an
   * already-set {@code read_at} alone rather than moving it to now.
   */
  @Transactional
  public int markRead(long id, boolean read) {
    String sql =
        read
            ? "UPDATE messages SET read_at = COALESCE(read_at, NOW()) WHERE id = :id"
            : "UPDATE messages SET read_at = NULL WHERE id = :id";
    var params = createParameterSource().addValue("id", id);
    return update(sql, params);
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
