package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.model.Records;
import java.lang.reflect.Field;
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
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class AdminHomeTileRepositoryTest {

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
  @Captor private ArgumentCaptor<String> sqlCaptor;

  private AdminHomeTileRepository repository;

  @BeforeEach
  void setUp() {
    repository = new AdminHomeTileRepository(jdbcTemplate);
    injectMockTemplate(repository, namedParameterJdbcTemplate);
  }

  private void injectMockTemplate(
      AdminHomeTileRepository repo, NamedParameterJdbcTemplate template) {
    try {
      Field field = BaseDao.class.getDeclaredField("namedParameterJdbcTemplate");
      field.setAccessible(true);
      field.set(repo, template);
    } catch (Exception e) {
      throw new RuntimeException("Failed to inject mock NamedParameterJdbcTemplate", e);
    }
  }

  @Nested
  class FindAllWithCover {

    @SuppressWarnings("unchecked")
    @Test
    void returnsProjectionOrderedByDisplayOrder() {
      var tile0 = new Records.AdminHomeTileResponse("home", "https://cdn.example.com/home.jpg", 0);
      var tile1 = new Records.AdminHomeTileResponse("all-collections", null, 1);
      when(namedParameterJdbcTemplate.query(anyString(), any(RowMapper.class)))
          .thenReturn(List.of(tile0, tile1));

      List<Records.AdminHomeTileResponse> result = repository.findAllWithCover();

      verify(namedParameterJdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class));
      String sql = sqlCaptor.getValue();
      assertThat(sql).containsIgnoringCase("LEFT JOIN content_image");
      assertThat(sql).containsIgnoringCase("ORDER BY t.display_order ASC");
      assertThat(result).hasSize(2);
      assertThat(result.get(0).tileKey()).isEqualTo("home");
      assertThat(result.get(0).coverImageUrl()).isEqualTo("https://cdn.example.com/home.jpg");
      assertThat(result.get(1).tileKey()).isEqualTo("all-collections");
      assertThat(result.get(1).coverImageUrl()).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    void emptyTableReturnsEmptyList() {
      when(namedParameterJdbcTemplate.query(anyString(), any(RowMapper.class)))
          .thenReturn(List.of());

      assertThat(repository.findAllWithCover()).isEmpty();
    }
  }
}
