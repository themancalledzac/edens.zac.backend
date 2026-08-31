package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import java.sql.Array;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  /**
   * Pins the append arithmetic shared by ContentService, ContentMutationUtil, CollectionService and
   * TagService. Those four mock this repository, so this is the only place the max-plus-one rule is
   * actually executed.
   */
  @Nested
  class GetNextOrderIndexForCollection {

    @Test
    void withExistingContent_returnsMaxPlusOne() {
      when(namedParameterJdbcTemplate.queryForObject(
              anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
          .thenReturn(4);

      assertThat(collectionRepository.getNextOrderIndexForCollection(1L)).isEqualTo(5);
    }

    @Test
    void withEmptyCollection_returnsZero() {
      when(namedParameterJdbcTemplate.queryForObject(
              anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
          .thenReturn(null);

      assertThat(collectionRepository.getNextOrderIndexForCollection(1L)).isZero();
    }
  }

  @Nested
  class FindNonEmptyOrderedByVisibilityIn {

    @SuppressWarnings("unchecked")
    @Test
    void sqlGatesOnExistsCollectionContentRowsAndPassesVisibilities() {
      // Pin the SQL: caller relies on the EXISTS gate to drop empty collections from
      // synthetic listings (/all-collections, /all-blogs, etc.). Soft-removed memberships
      // (cc.visible=false) must NOT count as content -- must mirror the gate used by
      // findReferencedCollectionsByParentId.
      when(namedParameterJdbcTemplate.query(
              anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
          .thenReturn(List.of());

      collectionRepository.findNonEmptyOrderedByVisibilityIn(
          List.of(CollectionVisibility.LISTED, CollectionVisibility.UNLISTED), true);

      verify(namedParameterJdbcTemplate)
          .query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
      String sql = sqlCaptor.getValue();
      assertThat(sql).containsIgnoringCase("EXISTS");
      assertThat(sql).containsIgnoringCase("collection_content cc");
      assertThat(sql).containsIgnoringCase("cc.collection_id = collection.id");
      assertThat(sql).containsIgnoringCase("cc.visible = true");
      assertThat(sql).containsIgnoringCase("WHERE visibility IN (:visibilities)");
      assertThat(sql).containsIgnoringCase("AND is_blog = true");
      assertThat(sql).containsIgnoringCase("ORDER BY rating DESC NULLS LAST, collection_date DESC");
      MapSqlParameterSource params = paramsCaptor.getValue();
      assertThat((List<String>) params.getValue("visibilities"))
          .containsExactly("LISTED", "UNLISTED");
      // hasValue, not getValue: MapSqlParameterSource.getValue throws on an unregistered key.
      assertThat(params.hasValue("type")).isFalse();
    }

    @Test
    void sqlOmitsBlogPredicateWhenBlogsOnlyIsFalse() {
      when(namedParameterJdbcTemplate.query(
              anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
          .thenReturn(List.of());

      collectionRepository.findNonEmptyOrderedByVisibilityIn(
          List.of(CollectionVisibility.LISTED), false);

      verify(namedParameterJdbcTemplate)
          .query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
      String sql = sqlCaptor.getValue();
      assertThat(sql).containsIgnoringCase("EXISTS");
      // Not a bare "is_blog": the canonical column list projects it in the SELECT.
      assertThat(sql).doesNotContainIgnoringCase("AND is_blog = true");
      assertThat(sql).doesNotContainIgnoringCase("type = :type");
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

  @Nested
  class SaveCollectionEndDate {

    @Test
    void updateSqlWritesCollectionEndDateColumnAndBindsParam() {
      // Existing-id entity => UPDATE path, which routes through the 2-arg
      // namedParameterJdbcTemplate.update(sql, params) overload we can capture.
      when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
          .thenReturn(1);

      CollectionEntity entity =
          CollectionEntity.builder()
              .id(5L)
              .title("Trip")
              .slug("trip")
              .collectionDate(java.time.LocalDate.of(2026, 3, 5))
              .collectionEndDate(java.time.LocalDate.of(2026, 3, 7))
              .visibility(CollectionVisibility.LISTED)
              .build();

      collectionRepository.save(entity);

      verify(namedParameterJdbcTemplate).update(sqlCaptor.capture(), paramsCaptor.capture());
      String sql = sqlCaptor.getValue();
      assertThat(sql).containsIgnoringCase("collection_end_date = :collectionEndDate");
      MapSqlParameterSource params = paramsCaptor.getValue();
      assertThat(params.getValue("collectionEndDate"))
          .isEqualTo(java.time.LocalDate.of(2026, 3, 7));
      assertThat(params.getValue("collectionDate")).isEqualTo(java.time.LocalDate.of(2026, 3, 5));
    }

    @SuppressWarnings("unchecked")
    @Test
    void selectSqlListsCollectionEndDateColumn() {
      when(namedParameterJdbcTemplate.query(
              anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
          .thenReturn(List.of());

      collectionRepository.findByIds(List.of(5L));

      verify(namedParameterJdbcTemplate)
          .query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
      assertThat(sqlCaptor.getValue()).containsIgnoringCase("collection_end_date");
    }
  }

  @Nested
  class FindAllParentCollectionsByChildId {

    @SuppressWarnings("unchecked")
    @Test
    void buildsInverseJoinSql_andPassesChildIdParam() {
      CollectionEntity stub = CollectionEntity.builder().id(99L).title("Parent A").build();
      when(namedParameterJdbcTemplate.query(
              anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
          .thenReturn(List.of(stub));

      List<CollectionEntity> parents = collectionRepository.findAllParentCollectionsByChildId(42L);

      verify(namedParameterJdbcTemplate)
          .query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
      String sql = sqlCaptor.getValue();
      assertThat(sql).contains("JOIN collection_content cc ON cc.collection_id = c.id");
      assertThat(sql).contains("JOIN content_collection cct ON cct.id = cc.content_id");
      assertThat(sql).contains("WHERE cct.referenced_collection_id = :childId");
      assertThat(sql).doesNotContain("cc.visible");
      assertThat(sql).doesNotContain("c.visibility =");
      assertThat(paramsCaptor.getValue().getValue("childId")).isEqualTo(42L);
      assertThat(parents).hasSize(1).first().extracting(CollectionEntity::getId).isEqualTo(99L);
    }

    @SuppressWarnings("unchecked")
    @Test
    void returnsEmpty_whenJdbcReturnsEmpty() {
      when(namedParameterJdbcTemplate.query(
              anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
          .thenReturn(List.of());

      assertThat(collectionRepository.findAllParentCollectionsByChildId(42L)).isEmpty();
    }
  }

  @Nested
  class FindGalleryAccessBySlug {

    @SuppressWarnings("unchecked")
    private RowMapper<CollectionRepository.GalleryAccessRow> captureRowMapper() {
      ArgumentCaptor<RowMapper<CollectionRepository.GalleryAccessRow>> mapperCaptor =
          ArgumentCaptor.forClass(RowMapper.class);
      verify(namedParameterJdbcTemplate)
          .queryForObject(
              sqlCaptor.capture(), paramsCaptor.capture(), (RowMapper<?>) mapperCaptor.capture());
      return mapperCaptor.getValue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void selectsOnlyTheTwoAccessColumns() {
      when(namedParameterJdbcTemplate.queryForObject(
              anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
          .thenReturn(new CollectionRepository.GalleryAccessRow("pw", List.of("a@b.com")));

      Optional<CollectionRepository.GalleryAccessRow> row =
          collectionRepository.findGalleryAccessBySlug("smith-wedding");

      captureRowMapper();
      assertThat(sqlCaptor.getValue())
          .isEqualTo(
              "SELECT gallery_password, recipient_emails FROM collection WHERE slug = :slug");
      assertThat(paramsCaptor.getValue().getValue("slug")).isEqualTo("smith-wedding");
      assertThat(row).contains(new CollectionRepository.GalleryAccessRow("pw", List.of("a@b.com")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void rowMapper_readsPasswordAndEmailArray() throws Exception {
      when(namedParameterJdbcTemplate.queryForObject(
              anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
          .thenReturn(null);
      collectionRepository.findGalleryAccessBySlug("smith-wedding");
      RowMapper<CollectionRepository.GalleryAccessRow> mapper = captureRowMapper();

      ResultSet rs = mock(ResultSet.class);
      Array emails = mock(Array.class);
      when(rs.getString("gallery_password")).thenReturn("hunter2");
      when(rs.getArray("recipient_emails")).thenReturn(emails);
      when(emails.getArray()).thenReturn(new String[] {"bride@example.com"});

      CollectionRepository.GalleryAccessRow mapped = mapper.mapRow(rs, 0);

      assertThat(mapped.galleryPassword()).isEqualTo("hunter2");
      assertThat(mapped.recipientEmails()).containsExactly("bride@example.com");
    }

    @SuppressWarnings("unchecked")
    @Test
    void rowMapper_nullEmailArray_mapsToEmptyList() throws Exception {
      when(namedParameterJdbcTemplate.queryForObject(
              anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
          .thenReturn(null);
      collectionRepository.findGalleryAccessBySlug("smith-wedding");
      RowMapper<CollectionRepository.GalleryAccessRow> mapper = captureRowMapper();

      ResultSet rs = mock(ResultSet.class);
      when(rs.getString("gallery_password")).thenReturn(null);
      when(rs.getArray("recipient_emails")).thenReturn(null);

      CollectionRepository.GalleryAccessRow mapped = mapper.mapRow(rs, 0);

      assertThat(mapped.galleryPassword()).isNull();
      assertThat(mapped.recipientEmails()).isEmpty();
    }
  }
}
