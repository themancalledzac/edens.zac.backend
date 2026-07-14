package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.dao.RequestMetricRepository.RequestMetricRow;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Round-trip integration coverage for the V44 {@code request_metric} upsert. Verifies that {@link
 * RequestMetricRepository#increment} inserts a fresh row at count 1 then increments the SAME {@code
 * (day, route, slug)} row on subsequent calls — including the NULL-slug case, where the {@code
 * COALESCE(slug, '')} unique index and matching {@code ON CONFLICT} target must collapse all
 * NULL-slug rows for a {@code (day, route)} to one row (Postgres would otherwise treat NULLs as
 * distinct and insert duplicates). Requires Docker (Testcontainers Postgres) — the V44 migration
 * runs on top of {@code test-base-schema.sql} exactly as in prod.
 */
class RequestMetricRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final LocalDate DAY = LocalDate.of(2026, 7, 7);

  @Autowired private RequestMetricRepository repository;

  private long countFor(String route, String slug) {
    return repository.findByDayRange(DAY, DAY).stream()
        .filter(r -> r.route().equals(route) && java.util.Objects.equals(r.slug(), slug))
        .mapToLong(RequestMetricRow::count)
        .sum();
  }

  @Test
  void firstIncrementInsertsThenSubsequentIncrementsSameSlugRow() {
    String route = "/api/read/collections/{slug}";
    repository.increment(DAY, route, "iceland-int");
    repository.increment(DAY, route, "iceland-int");
    repository.increment(DAY, route, "iceland-int");

    assertThat(countFor(route, "iceland-int")).isEqualTo(3L);
  }

  @Test
  void nullSlugRowsCollapseToOneAndIncrement() {
    String route = "/api/read/collections-null-int";
    repository.increment(DAY, route, null);
    repository.increment(DAY, route, null);

    List<RequestMetricRow> rows =
        repository.findByDayRange(DAY, DAY).stream().filter(r -> r.route().equals(route)).toList();

    // Exactly ONE row despite two NULL-slug inserts — the COALESCE conflict target did its job.
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).slug()).isNull();
    assertThat(rows.get(0).count()).isEqualTo(2L);
  }

  @Test
  void distinctSlugsAndNullSlugAreSeparateRows() {
    String route = "/api/read/collections-distinct-int";
    repository.increment(DAY, route, "a");
    repository.increment(DAY, route, "b");
    repository.increment(DAY, route, null);
    repository.increment(DAY, route, "a");

    assertThat(countFor(route, "a")).isEqualTo(2L);
    assertThat(countFor(route, "b")).isEqualTo(1L);
    assertThat(countFor(route, null)).isEqualTo(1L);
  }
}
