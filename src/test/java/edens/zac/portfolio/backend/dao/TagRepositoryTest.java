package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@ExtendWith(MockitoExtension.class)
class TagRepositoryTest {

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  @Captor private ArgumentCaptor<String> sqlCaptor;
  @Captor private ArgumentCaptor<SqlParameterSource> paramsCaptor;

  private TagRepository tagRepository;

  @BeforeEach
  void setUp() {
    tagRepository = new TagRepository(jdbcTemplate);
    setNamedParameterJdbcTemplate(tagRepository, namedParameterJdbcTemplate);
  }

  private void setNamedParameterJdbcTemplate(
      TagRepository repository, NamedParameterJdbcTemplate template) {
    try {
      java.lang.reflect.Field field = BaseDao.class.getDeclaredField("namedParameterJdbcTemplate");
      field.setAccessible(true);
      field.set(repository, template);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set mock NamedParameterJdbcTemplate", e);
    }
  }

  @Test
  void updateConvertedCollectionId_emitsUpdateSqlAndParams() {
    when(namedParameterJdbcTemplate.update(anyString(), any(SqlParameterSource.class)))
        .thenReturn(1);

    tagRepository.updateConvertedCollectionId(7L, 42L);

    verify(namedParameterJdbcTemplate).update(sqlCaptor.capture(), paramsCaptor.capture());

    assertThat(sqlCaptor.getValue())
        .isEqualTo("UPDATE tag SET converted_collection_id = :collectionId WHERE id = :id");

    MapSqlParameterSource params = (MapSqlParameterSource) paramsCaptor.getValue();
    assertThat(params.getValue("id")).isEqualTo(7L);
    assertThat(params.getValue("collectionId")).isEqualTo(42L);
  }

  @Test
  void updateConvertedCollectionId_nullCollectionId_clearsColumn() {
    when(namedParameterJdbcTemplate.update(anyString(), any(SqlParameterSource.class)))
        .thenReturn(1);

    tagRepository.updateConvertedCollectionId(7L, null);

    verify(namedParameterJdbcTemplate).update(anyString(), paramsCaptor.capture());
    MapSqlParameterSource params = (MapSqlParameterSource) paramsCaptor.getValue();
    assertThat(params.getValue("collectionId")).isNull();
  }
}
