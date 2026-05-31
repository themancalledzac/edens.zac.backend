package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.model.Records;
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
class CollectionSiblingRepositoryTest {

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
  @Captor private ArgumentCaptor<String> sqlCaptor;
  @Captor private ArgumentCaptor<MapSqlParameterSource> paramsCaptor;

  private CollectionSiblingRepository repository;

  @BeforeEach
  void setUp() {
    repository = new CollectionSiblingRepository(jdbcTemplate);
    setNamedParameterJdbcTemplate(repository, namedParameterJdbcTemplate);
  }

  private void setNamedParameterJdbcTemplate(
      CollectionSiblingRepository repo, NamedParameterJdbcTemplate template) {
    try {
      java.lang.reflect.Field field = BaseDao.class.getDeclaredField("namedParameterJdbcTemplate");
      field.setAccessible(true);
      field.set(repo, template);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set mock NamedParameterJdbcTemplate", e);
    }
  }

  @Nested
  class AddSibling {
    @Test
    void addSibling_issuesReciprocalInsertWithOnConflictDoNothing() {
      when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
          .thenReturn(2);
      repository.addSibling(1L, 2L);
      verify(namedParameterJdbcTemplate).update(sqlCaptor.capture(), paramsCaptor.capture());
      String sql = sqlCaptor.getValue();
      assertThat(sql)
          .containsIgnoringCase(
              "INSERT INTO collection_sibling (collection_id, sibling_collection_id)");
      assertThat(sql).containsIgnoringCase("VALUES (:a, :b), (:b, :a)");
      assertThat(sql).containsIgnoringCase("ON CONFLICT DO NOTHING");
      MapSqlParameterSource params = paramsCaptor.getValue();
      assertThat(params.getValue("a")).isEqualTo(1L);
      assertThat(params.getValue("b")).isEqualTo(2L);
    }
  }

  @Nested
  class RemoveSibling {
    @Test
    void removeSibling_issuesBidirectionalDelete() {
      when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
          .thenReturn(2);
      repository.removeSibling(3L, 4L);
      verify(namedParameterJdbcTemplate).update(sqlCaptor.capture(), paramsCaptor.capture());
      String sql = sqlCaptor.getValue();
      assertThat(sql).containsIgnoringCase("DELETE FROM collection_sibling");
      assertThat(sql).containsIgnoringCase("(collection_id = :a AND sibling_collection_id = :b)");
      assertThat(sql).containsIgnoringCase("(collection_id = :b AND sibling_collection_id = :a)");
      MapSqlParameterSource params = paramsCaptor.getValue();
      assertThat(params.getValue("a")).isEqualTo(3L);
      assertThat(params.getValue("b")).isEqualTo(4L);
    }
  }

  @Nested
  class FindSiblings {
    @SuppressWarnings("unchecked")
    @Test
    void findSiblings_listedOnlyTrue_appendsVisibilityClause() {
      when(namedParameterJdbcTemplate.query(
              anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
          .thenReturn(List.of());
      List<Records.CollectionList> result = repository.findSiblings(7L, true);
      assertThat(result).isEmpty();
      verify(namedParameterJdbcTemplate)
          .query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
      String sql = sqlCaptor.getValue();
      assertThat(sql).containsIgnoringCase("SELECT c.id, c.title AS name, c.slug, c.type");
      assertThat(sql).containsIgnoringCase("FROM collection_sibling cs");
      assertThat(sql).containsIgnoringCase("JOIN collection c ON c.id = cs.sibling_collection_id");
      assertThat(sql).containsIgnoringCase("WHERE cs.collection_id = :id");
      assertThat(sql).containsIgnoringCase("AND c.visibility = 'LISTED'");
      assertThat(sql).containsIgnoringCase("ORDER BY c.title ASC");
      assertThat(paramsCaptor.getValue().getValue("id")).isEqualTo(7L);
    }

    @SuppressWarnings("unchecked")
    @Test
    void findSiblings_listedOnlyFalse_omitsVisibilityClause() {
      when(namedParameterJdbcTemplate.query(
              anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
          .thenReturn(List.of());
      repository.findSiblings(8L, false);
      verify(namedParameterJdbcTemplate)
          .query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
      String sql = sqlCaptor.getValue();
      assertThat(sql).containsIgnoringCase("WHERE cs.collection_id = :id");
      assertThat(sql).doesNotContainIgnoringCase("visibility = 'LISTED'");
      assertThat(sql).containsIgnoringCase("ORDER BY c.title ASC");
    }
  }
}
