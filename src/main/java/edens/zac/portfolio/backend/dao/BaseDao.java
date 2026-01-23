package edens.zac.portfolio.backend.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * Base DAO utility class providing common JDBC operations. All entity-specific DAOs should extend
 * this class. Uses NamedParameterJdbcTemplate for safer parameter binding.
 */
@Slf4j
abstract class BaseDao {

  protected final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
  protected final JdbcTemplate jdbcTemplate;

  protected BaseDao(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
    this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
  }

  /**
   * Execute a SELECT query and return a list of results.
   *
   * @param sql SQL query with :paramName placeholders
   * @param rowMapper RowMapper to convert ResultSet rows to entities
   * @param paramSource Parameter source (MapSqlParameterSource or Map)
   * @param <T> Entity type
   * @return List of entities
   */
  protected <T> List<T> query(String sql, RowMapper<T> rowMapper, SqlParameterSource paramSource) {
    return namedParameterJdbcTemplate.query(sql, paramSource, rowMapper);
  }

  /**
   * Execute a SELECT query and return a list of results (using Map).
   *
   * @param sql SQL query with :paramName placeholders
   * @param rowMapper RowMapper to convert ResultSet rows to entities
   * @param params Parameter map
   * @param <T> Entity type
   * @return List of entities
   */
  protected <T> List<T> query(String sql, RowMapper<T> rowMapper, Map<String, Object> params) {
    return namedParameterJdbcTemplate.query(sql, params, rowMapper);
  }

  /**
   * Execute a SELECT query with no parameters.
   *
   * @param sql SQL query
   * @param rowMapper RowMapper to convert ResultSet rows to entities
   * @param <T> Entity type
   * @return List of entities
   */
  protected <T> List<T> query(String sql, RowMapper<T> rowMapper) {
    return namedParameterJdbcTemplate.query(sql, rowMapper);
  }

  /**
   * Execute a SELECT query and return a single result.
   *
   * @param sql SQL query with :paramName placeholders
   * @param rowMapper RowMapper to convert ResultSet rows to entities
   * @param paramSource Parameter source
   * @param <T> Entity type
   * @return Optional containing the entity if found
   */
  protected <T> Optional<T> queryForObject(
      String sql, RowMapper<T> rowMapper, SqlParameterSource paramSource) {
    try {
      T result = namedParameterJdbcTemplate.queryForObject(sql, paramSource, rowMapper);
      return Optional.ofNullable(result);
    } catch (org.springframework.dao.EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  /**
   * Execute a SELECT query and return a single result (using Map).
   *
   * @param sql SQL query with :paramName placeholders
   * @param rowMapper RowMapper to convert ResultSet rows to entities
   * @param params Parameter map
   * @param <T> Entity type
   * @return Optional containing the entity if found
   */
  protected <T> Optional<T> queryForObject(
      String sql, RowMapper<T> rowMapper, Map<String, Object> params) {
    try {
      T result = namedParameterJdbcTemplate.queryForObject(sql, params, rowMapper);
      return Optional.ofNullable(result);
    } catch (org.springframework.dao.EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  /**
   * Execute an INSERT/UPDATE/DELETE query.
   *
   * @param sql SQL statement with :paramName placeholders
   * @param paramSource Parameter source
   * @return Number of rows affected
   */
  protected int update(String sql, SqlParameterSource paramSource) {
    return namedParameterJdbcTemplate.update(sql, paramSource);
  }

  /**
   * Execute an INSERT/UPDATE/DELETE query (using Map).
   *
   * @param sql SQL statement with :paramName placeholders
   * @param params Parameter map
   * @return Number of rows affected
   */
  protected int update(String sql, Map<String, Object> params) {
    return namedParameterJdbcTemplate.update(sql, params);
  }

  /**
   * Execute an INSERT query and return the generated ID.
   *
   * @param sql INSERT statement with :paramName placeholders
   * @param idColumnName Name of the ID column (for PostgreSQL sequences)
   * @param paramSource Parameter source
   * @return Generated ID
   */
  protected Long insertAndReturnId(
      String sql, String idColumnName, SqlParameterSource paramSource) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    namedParameterJdbcTemplate.update(sql, paramSource, keyHolder, new String[] {idColumnName});
    Number key = keyHolder.getKey();
    return key != null ? key.longValue() : null;
  }

  /**
   * Execute a batch update.
   *
   * @param sql SQL statement with :paramName placeholders
   * @param batchParams Array of parameter sources
   * @return Array of update counts
   */
  protected int[] batchUpdate(String sql, SqlParameterSource[] batchParams) {
    return namedParameterJdbcTemplate.batchUpdate(sql, batchParams);
  }

  /**
   * Create a MapSqlParameterSource for building named parameters.
   *
   * @return New MapSqlParameterSource instance
   */
  protected MapSqlParameterSource createParameterSource() {
    return new MapSqlParameterSource();
  }

  /** Helper method to safely get a Long from ResultSet. */
  protected static Long getLong(ResultSet rs, String columnName) throws SQLException {
    long value = rs.getLong(columnName);
    return rs.wasNull() ? null : value;
  }

  /** Helper method to safely get an Integer from ResultSet. */
  protected static Integer getInteger(ResultSet rs, String columnName) throws SQLException {
    int value = rs.getInt(columnName);
    return rs.wasNull() ? null : value;
  }

  /** Helper method to safely get a Boolean from ResultSet. */
  protected static Boolean getBoolean(ResultSet rs, String columnName) throws SQLException {
    boolean value = rs.getBoolean(columnName);
    return rs.wasNull() ? null : value;
  }

  /** Helper method to safely get a String from ResultSet. */
  protected static String getString(ResultSet rs, String columnName) throws SQLException {
    return rs.getString(columnName);
  }

  /** Helper method to safely get a LocalDateTime from ResultSet. */
  protected static LocalDateTime getLocalDateTime(ResultSet rs, String columnName)
      throws SQLException {
    Timestamp timestamp = rs.getTimestamp(columnName);
    return timestamp != null ? timestamp.toLocalDateTime() : null;
  }

  /** Helper method to safely get a LocalDate from ResultSet. */
  protected static LocalDate getLocalDate(ResultSet rs, String columnName) throws SQLException {
    java.sql.Date date = rs.getDate(columnName);
    return date != null ? date.toLocalDate() : null;
  }
}
