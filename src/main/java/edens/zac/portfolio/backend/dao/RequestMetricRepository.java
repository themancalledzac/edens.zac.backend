package edens.zac.portfolio.backend.dao;

import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists private, admin-only aggregate API request counts. Stores exactly one row per {@code
 * (day, route, slug)}: no PII (no ip, user id, user-agent, or per-request rows) ever touches this
 * table. See {@code V44__create_request_metric.sql}.
 */
@Component
@Slf4j
public class RequestMetricRepository extends BaseDao {

  /** A single aggregated request-count row. {@code slug} is {@code null} for slug-less routes. */
  public record RequestMetricRow(LocalDate day, String route, String slug, long count) {}

  private static final RowMapper<RequestMetricRow> ROW_MAPPER =
      (rs, rowNum) ->
          new RequestMetricRow(
              getLocalDate(rs, "day"),
              rs.getString("route"),
              rs.getString("slug"),
              rs.getLong("count"));

  public RequestMetricRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
  }

  /**
   * Increment the count for a single {@code (day, route, slug)}, inserting the row at count 1 if it
   * does not yet exist. The {@code ON CONFLICT} target {@code (day, route, (COALESCE(slug, '')))}
   * matches the unique index in V44 exactly, so a NULL {@code slug} increments the one canonical
   * NULL-slug row for that {@code (day, route)} rather than inserting a duplicate.
   *
   * @param day the UTC-local calendar day of the request
   * @param route the bounded Spring handler pattern (e.g. {@code /api/read/collections/{slug}})
   * @param slug the resolved path variable, or {@code null} for routes without one
   */
  @Transactional
  public void increment(LocalDate day, String route, String slug) {
    String sql =
        """
        INSERT INTO request_metric (day, route, slug, count)
        VALUES (:day, :route, :slug, 1)
        ON CONFLICT (day, route, (COALESCE(slug, '')))
        DO UPDATE SET count = request_metric.count + 1
        """;
    MapSqlParameterSource params =
        createParameterSource()
            .addValue("day", day)
            .addValue("route", route)
            .addValue("slug", slug);
    update(sql, params);
  }

  /**
   * Read aggregated rows whose {@code day} falls within {@code [from, to]} inclusive, ordered by
   * day then count descending. No PII is selectable — the table has none.
   */
  @Transactional(readOnly = true)
  public List<RequestMetricRow> findByDayRange(LocalDate from, LocalDate to) {
    String sql =
        """
        SELECT day, route, slug, count
        FROM request_metric
        WHERE day BETWEEN :from AND :to
        ORDER BY day DESC, count DESC, route ASC
        """;
    MapSqlParameterSource params =
        createParameterSource().addValue("from", from).addValue("to", to);
    return query(sql, ROW_MAPPER, params);
  }
}
