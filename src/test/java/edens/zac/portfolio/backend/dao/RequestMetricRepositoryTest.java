package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.dao.RequestMetricRepository.RequestMetricRow;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class RequestMetricRepositoryTest {

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  @Captor private ArgumentCaptor<String> sqlCaptor;
  @Captor private ArgumentCaptor<MapSqlParameterSource> paramsCaptor;

  private RequestMetricRepository repository;

  @BeforeEach
  void setUp() {
    repository = new RequestMetricRepository(jdbcTemplate);
    injectMockTemplate(repository, namedParameterJdbcTemplate);
  }

  private void injectMockTemplate(
      RequestMetricRepository repo, NamedParameterJdbcTemplate template) {
    try {
      Field field = BaseDao.class.getDeclaredField("namedParameterJdbcTemplate");
      field.setAccessible(true);
      field.set(repo, template);
    } catch (Exception e) {
      throw new RuntimeException("Failed to inject mock NamedParameterJdbcTemplate", e);
    }
  }

  @Nested
  class Increment {

    @Test
    void issuesUpsertWithCoalesceConflictTargetAndBoundParams() {
      when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
          .thenReturn(1);

      repository.increment(LocalDate.of(2026, 7, 7), "/api/read/collections/{slug}", "iceland");

      verify(namedParameterJdbcTemplate).update(sqlCaptor.capture(), paramsCaptor.capture());
      String sql = sqlCaptor.getValue();
      assertThat(sql).contains("INSERT INTO request_metric (day, route, slug, count)");
      assertThat(sql).contains("ON CONFLICT (day, route, (COALESCE(slug, '')))");
      assertThat(sql).contains("count = request_metric.count + 1");
      assertThat(paramsCaptor.getValue().getValue("day")).isEqualTo(LocalDate.of(2026, 7, 7));
      assertThat(paramsCaptor.getValue().getValue("route"))
          .isEqualTo("/api/read/collections/{slug}");
      assertThat(paramsCaptor.getValue().getValue("slug")).isEqualTo("iceland");
    }

    @Test
    void bindsNullSlugForSlugLessRoute() {
      when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
          .thenReturn(1);

      repository.increment(LocalDate.of(2026, 7, 7), "/api/read/collections", null);

      verify(namedParameterJdbcTemplate).update(sqlCaptor.capture(), paramsCaptor.capture());
      // The COALESCE conflict target is what lets a NULL slug collapse to one canonical row.
      assertThat(sqlCaptor.getValue()).contains("ON CONFLICT (day, route, (COALESCE(slug, '')))");
      assertThat(paramsCaptor.getValue().getValue("slug")).isNull();
    }
  }

  @Nested
  class FindByDayRange {

    @SuppressWarnings("unchecked")
    @Test
    void queriesBetweenBoundsInclusive() {
      RequestMetricRow row =
          new RequestMetricRow(LocalDate.of(2026, 7, 6), "/api/read/collections", null, 12L);
      when(namedParameterJdbcTemplate.query(
              anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
          .thenReturn(List.of(row));

      List<RequestMetricRow> rows =
          repository.findByDayRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 7));

      verify(namedParameterJdbcTemplate)
          .query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
      assertThat(sqlCaptor.getValue()).contains("WHERE day BETWEEN :from AND :to");
      assertThat(paramsCaptor.getValue().getValue("from")).isEqualTo(LocalDate.of(2026, 7, 1));
      assertThat(paramsCaptor.getValue().getValue("to")).isEqualTo(LocalDate.of(2026, 7, 7));
      assertThat(rows).hasSize(1);
      assertThat(rows.get(0).slug()).isNull();
      assertThat(rows.get(0).count()).isEqualTo(12L);
    }
  }
}
