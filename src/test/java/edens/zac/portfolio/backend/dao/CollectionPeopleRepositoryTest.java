package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@ExtendWith(MockitoExtension.class)
class CollectionPeopleRepositoryTest {

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  private CollectionPeopleRepository repository;

  @BeforeEach
  void setUp() {
    repository = new CollectionPeopleRepository(jdbcTemplate);
    injectMockTemplate(repository, namedParameterJdbcTemplate);
  }

  private void injectMockTemplate(
      CollectionPeopleRepository repo, NamedParameterJdbcTemplate template) {
    try {
      Field field = BaseDao.class.getDeclaredField("namedParameterJdbcTemplate");
      field.setAccessible(true);
      field.set(repo, template);
    } catch (Exception e) {
      throw new RuntimeException("Failed to inject mock NamedParameterJdbcTemplate", e);
    }
  }

  @Nested
  class FindPeopleForCollections {

    @Test
    void emptyOrNullInputReturnsEmptyMapWithoutQuery() {
      assertThat(repository.findPeopleForCollections(List.of())).isEmpty();
      assertThat(repository.findPeopleForCollections(null)).isEmpty();
      verify(namedParameterJdbcTemplate, never())
          .query(
              anyString(),
              any(SqlParameterSource.class),
              any(org.springframework.jdbc.core.RowCallbackHandler.class));
    }
  }

  @Nested
  class SetPeopleForCollection {

    @Test
    void deletesThenBatchInsertsDistinctIds() {
      repository.setPeopleForCollection(7L, List.of(101L, 102L, 102L, 103L));

      verify(namedParameterJdbcTemplate)
          .update(
              eq("DELETE FROM collection_people WHERE collection_id = :collectionId"),
              any(SqlParameterSource.class));
      ArgumentCaptor<SqlParameterSource[]> batchCaptor =
          ArgumentCaptor.forClass(SqlParameterSource[].class);
      verify(namedParameterJdbcTemplate)
          .batchUpdate(
              eq(
                  "INSERT INTO collection_people (collection_id, person_id) VALUES"
                      + " (:collectionId, :personId)"),
              batchCaptor.capture());
      // Distinct dedupes 102 -> 3 inserts
      assertThat(batchCaptor.getValue()).hasSize(3);
    }

    @Test
    void emptyListSkipsBatchInsertButStillDeletes() {
      repository.setPeopleForCollection(7L, List.of());

      verify(namedParameterJdbcTemplate)
          .update(
              eq("DELETE FROM collection_people WHERE collection_id = :collectionId"),
              any(SqlParameterSource.class));
      verify(namedParameterJdbcTemplate, never())
          .batchUpdate(anyString(), any(SqlParameterSource[].class));
    }

    @Test
    void nullListSkipsBatchInsertButStillDeletes() {
      repository.setPeopleForCollection(7L, null);

      verify(namedParameterJdbcTemplate)
          .update(
              eq("DELETE FROM collection_people WHERE collection_id = :collectionId"),
              any(SqlParameterSource.class));
      verify(namedParameterJdbcTemplate, never())
          .batchUpdate(anyString(), any(SqlParameterSource[].class));
    }
  }
}
