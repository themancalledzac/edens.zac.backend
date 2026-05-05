package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
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
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class ContentRepositoryTest {

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
  @Captor private ArgumentCaptor<String> sqlCaptor;

  private ContentRepository repository;

  @BeforeEach
  void setUp() {
    repository = new ContentRepository(jdbcTemplate);
    injectMockTemplate(repository, namedParameterJdbcTemplate);
  }

  private void injectMockTemplate(ContentRepository repo, NamedParameterJdbcTemplate template) {
    try {
      Field field = BaseDao.class.getDeclaredField("namedParameterJdbcTemplate");
      field.setAccessible(true);
      field.set(repo, template);
    } catch (Exception e) {
      throw new RuntimeException("Failed to inject mock NamedParameterJdbcTemplate", e);
    }
  }

  @Nested
  class FindRandomImageWebUrl {

    @SuppressWarnings("unchecked")
    @Test
    void returnsTopRowFromRandomOrderQuery() {
      when(namedParameterJdbcTemplate.query(anyString(), any(RowMapper.class)))
          .thenReturn(List.of("https://cdn.example.com/random.webp"));

      Optional<String> result = repository.findRandomImageWebUrl();

      verify(namedParameterJdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class));
      String sql = sqlCaptor.getValue();
      assertThat(sql).containsIgnoringCase("FROM content_image");
      assertThat(sql).containsIgnoringCase("image_url_web IS NOT NULL");
      assertThat(sql).containsIgnoringCase("ORDER BY RANDOM()");
      assertThat(sql).containsIgnoringCase("LIMIT 1");
      assertThat(result).contains("https://cdn.example.com/random.webp");
    }

    @SuppressWarnings("unchecked")
    @Test
    void emptyResultReturnsEmptyOptional() {
      when(namedParameterJdbcTemplate.query(anyString(), any(RowMapper.class)))
          .thenReturn(List.of());

      assertThat(repository.findRandomImageWebUrl()).isEmpty();
    }
  }
}
