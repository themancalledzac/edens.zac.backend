package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.entity.MessageEntity;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
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
class MessageRepositoryTest {

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  @Captor private ArgumentCaptor<String> sqlCaptor;
  @Captor private ArgumentCaptor<MapSqlParameterSource> paramsCaptor;

  private MessageRepository messageRepository;

  @BeforeEach
  void setUp() {
    messageRepository = new MessageRepository(jdbcTemplate);
    injectMockTemplate(messageRepository, namedParameterJdbcTemplate);
  }

  private void injectMockTemplate(MessageRepository repo, NamedParameterJdbcTemplate template) {
    try {
      Field field = BaseDao.class.getDeclaredField("namedParameterJdbcTemplate");
      field.setAccessible(true);
      field.set(repo, template);
    } catch (Exception e) {
      throw new RuntimeException("Failed to inject mock NamedParameterJdbcTemplate", e);
    }
  }

  @Nested
  class FindAll {

    @SuppressWarnings("unchecked")
    @Test
    void queriesOrderedByCreatedAtDescWithLimitAndOffset() {
      MessageEntity row1 = new MessageEntity();
      row1.setId(2L);
      row1.setEmail("two@example.com");
      row1.setMessage("two");
      row1.setCreatedAt(LocalDateTime.of(2026, 4, 27, 12, 0));
      MessageEntity row2 = new MessageEntity();
      row2.setId(1L);
      row2.setEmail("one@example.com");
      row2.setMessage("one");
      row2.setCreatedAt(LocalDateTime.of(2026, 4, 26, 12, 0));

      when(namedParameterJdbcTemplate.query(
              anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
          .thenReturn(List.of(row1, row2));

      List<MessageEntity> rows = messageRepository.findAll(50, 100);

      verify(namedParameterJdbcTemplate)
          .query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
      assertThat(sqlCaptor.getValue()).contains("ORDER BY created_at DESC");
      assertThat(sqlCaptor.getValue()).contains("LIMIT :limit OFFSET :offset");
      assertThat(paramsCaptor.getValue().getValue("limit")).isEqualTo(50);
      assertThat(paramsCaptor.getValue().getValue("offset")).isEqualTo(100);
      assertThat(rows).hasSize(2);
      assertThat(rows.get(0).getId()).isEqualTo(2L);
      assertThat(rows.get(1).getId()).isEqualTo(1L);
    }

    @SuppressWarnings("unchecked")
    @Test
    void emptyResultReturnsEmptyList() {
      when(namedParameterJdbcTemplate.query(
              anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
          .thenReturn(List.of());

      List<MessageEntity> rows = messageRepository.findAll(10, 0);

      assertThat(rows).isEmpty();
    }
  }

  @Nested
  class Count {

    @Test
    void returnsSqlCount() {
      when(namedParameterJdbcTemplate.queryForObject(
              anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
          .thenReturn(42L);

      long total = messageRepository.count();

      assertThat(total).isEqualTo(42L);
      verify(namedParameterJdbcTemplate)
          .queryForObject(sqlCaptor.capture(), any(MapSqlParameterSource.class), eq(Long.class));
      assertThat(sqlCaptor.getValue()).isEqualToIgnoringCase("SELECT COUNT(*) FROM messages");
    }

    @Test
    void nullResultReturnsZero() {
      when(namedParameterJdbcTemplate.queryForObject(
              anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
          .thenReturn(null);

      assertThat(messageRepository.count()).isEqualTo(0L);
    }
  }
}
