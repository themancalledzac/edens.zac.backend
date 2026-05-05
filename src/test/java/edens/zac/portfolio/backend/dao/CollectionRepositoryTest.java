package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.types.CollectionType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
class CollectionRepositoryTest {

  @Mock private JdbcTemplate jdbcTemplate;

  @Mock private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  @Captor private ArgumentCaptor<String> sqlCaptor;

  @Captor private ArgumentCaptor<MapSqlParameterSource> paramsCaptor;

  private CollectionRepository collectionRepository;

  @BeforeEach
  void setUp() {
    collectionRepository = new CollectionRepository(jdbcTemplate);
    // Replace the internal namedParameterJdbcTemplate with our mock
    setNamedParameterJdbcTemplate(collectionRepository, namedParameterJdbcTemplate);
  }

  private void setNamedParameterJdbcTemplate(
      CollectionRepository repository, NamedParameterJdbcTemplate template) {
    try {
      java.lang.reflect.Field field = BaseDao.class.getDeclaredField("namedParameterJdbcTemplate");
      field.setAccessible(true);
      field.set(repository, template);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set mock NamedParameterJdbcTemplate", e);
    }
  }

  @Nested
  class BatchUpdateContentOrderIndexes {

    @Test
    void batchUpdateContentOrderIndexes_withMultipleItems_buildsCaseStatement() {
      // Arrange
      Long collectionId = 1L;
      Map<Long, Integer> contentIdToOrderIndex = new HashMap<>();
      contentIdToOrderIndex.put(100L, 2);
      contentIdToOrderIndex.put(101L, 0);
      contentIdToOrderIndex.put(102L, 1);

      when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
          .thenReturn(3);

      // Act
      int result =
          collectionRepository.batchUpdateContentOrderIndexes(collectionId, contentIdToOrderIndex);

      // Assert
      assertThat(result).isEqualTo(3);

      verify(namedParameterJdbcTemplate).update(sqlCaptor.capture(), paramsCaptor.capture());

      String sql = sqlCaptor.getValue();
      assertThat(sql).startsWith("UPDATE collection_content SET order_index = CASE content_id");
      assertThat(sql).contains("WHEN :contentId0 THEN :orderIndex0");
      assertThat(sql).contains("WHEN :contentId1 THEN :orderIndex1");
      assertThat(sql).contains("WHEN :contentId2 THEN :orderIndex2");
      assertThat(sql).contains("END WHERE collection_id = :collectionId");
      assertThat(sql).contains("AND content_id IN (:contentIds)");

      MapSqlParameterSource params = paramsCaptor.getValue();
      assertThat(params.getValue("collectionId")).isEqualTo(1L);
    }

    @Test
    void batchUpdateContentOrderIndexes_withSingleItem_buildsCaseStatement() {
      // Arrange
      Long collectionId = 1L;
      Map<Long, Integer> contentIdToOrderIndex = Map.of(100L, 5);

      when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
          .thenReturn(1);

      // Act
      int result =
          collectionRepository.batchUpdateContentOrderIndexes(collectionId, contentIdToOrderIndex);

      // Assert
      assertThat(result).isEqualTo(1);

      verify(namedParameterJdbcTemplate).update(sqlCaptor.capture(), paramsCaptor.capture());

      String sql = sqlCaptor.getValue();
      assertThat(sql).startsWith("UPDATE collection_content SET order_index = CASE content_id");
      assertThat(sql).contains("WHEN :contentId0 THEN :orderIndex0");
      assertThat(sql)
          .endsWith("END WHERE collection_id = :collectionId AND content_id IN (:contentIds)");
    }

    @Test
    void batchUpdateContentOrderIndexes_withEmptyMap_returnsZero() {
      // Arrange
      Long collectionId = 1L;
      Map<Long, Integer> contentIdToOrderIndex = Map.of();

      // Act
      int result =
          collectionRepository.batchUpdateContentOrderIndexes(collectionId, contentIdToOrderIndex);

      // Assert
      assertThat(result).isZero();
      verify(namedParameterJdbcTemplate, never())
          .update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void batchUpdateContentOrderIndexes_withNullMap_returnsZero() {
      // Arrange
      Long collectionId = 1L;

      // Act
      int result = collectionRepository.batchUpdateContentOrderIndexes(collectionId, null);

      // Assert
      assertThat(result).isZero();
      verify(namedParameterJdbcTemplate, never())
          .update(anyString(), any(MapSqlParameterSource.class));
    }
  }

  @Nested
  class FindByTypeAndListedOrdered {

    @SuppressWarnings("unchecked")
    @Test
    void findByTypeAndListedOrderedSqlOrdersByRatingThenDate() {
      when(namedParameterJdbcTemplate.query(
              anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
          .thenReturn(List.of());

      collectionRepository.findByTypeAndListedOrdered(CollectionType.BLOG);

      verify(namedParameterJdbcTemplate)
          .query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
      String sql = sqlCaptor.getValue();
      assertThat(sql).containsIgnoringCase("ORDER BY rating DESC NULLS LAST, collection_date DESC");
      assertThat(sql).containsIgnoringCase("WHERE type = :type AND visibility = 'LISTED'");
    }
  }

  @Nested
  class UpdateRating {

    @Test
    void updateRatingExecutesParameterizedSqlAndReturnsRowCount() {
      when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
          .thenReturn(1);

      int rows = collectionRepository.updateRating(7L, 4);

      assertThat(rows).isEqualTo(1);
      verify(namedParameterJdbcTemplate).update(sqlCaptor.capture(), paramsCaptor.capture());
      String sql = sqlCaptor.getValue();
      assertThat(sql).containsIgnoringCase("UPDATE collection SET rating = :rating");
      assertThat(sql).containsIgnoringCase("WHERE id = :id");
      MapSqlParameterSource params = paramsCaptor.getValue();
      assertThat(params.getValue("id")).isEqualTo(7L);
      assertThat(params.getValue("rating")).isEqualTo(4);
    }
  }
}
