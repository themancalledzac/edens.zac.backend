package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Round-trip integration coverage for the V43 {@code collection_end_date} column. Verifies that the
 * repository INSERT persists both dates, that reading back through {@link
 * CollectionRepository#findBySlug} materializes {@code collectionEndDate}, and that an UPDATE can
 * both change and clear the end date. Requires Docker (Testcontainers Postgres) — the V43 migration
 * runs on top of {@code test-base-schema.sql} exactly as in prod.
 */
class CollectionDateRangeRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private CollectionRepository collectionRepository;

  private CollectionEntity newCollection(String slug, LocalDate start, LocalDate end) {
    return CollectionEntity.builder()
        .type(CollectionType.BLOG)
        .title("Date Range " + slug)
        .slug(slug)
        .collectionDate(start)
        .collectionEndDate(end)
        .visibility(CollectionVisibility.LISTED)
        .totalContent(0)
        .build();
  }

  @Test
  void insertThenReadBackCarriesCollectionEndDate() {
    CollectionEntity saved =
        collectionRepository.save(
            newCollection("dr-insert", LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 7)));
    assertThat(saved.getId()).isNotNull();

    Optional<CollectionEntity> found = collectionRepository.findBySlug("dr-insert");
    assertThat(found).isPresent();
    assertThat(found.get().getCollectionDate()).isEqualTo(LocalDate.of(2026, 3, 5));
    assertThat(found.get().getCollectionEndDate()).isEqualTo(LocalDate.of(2026, 3, 7));
  }

  @Test
  void nullEndDatePersistsAndReadsBackAsNull() {
    collectionRepository.save(newCollection("dr-null-end", LocalDate.of(2026, 4, 1), null));

    Optional<CollectionEntity> found = collectionRepository.findBySlug("dr-null-end");
    assertThat(found).isPresent();
    assertThat(found.get().getCollectionDate()).isEqualTo(LocalDate.of(2026, 4, 1));
    assertThat(found.get().getCollectionEndDate()).isNull();
  }

  @Test
  void updateChangesThenClearsCollectionEndDate() {
    CollectionEntity saved =
        collectionRepository.save(
            newCollection("dr-update", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3)));

    // Change the end date.
    saved.setCollectionEndDate(LocalDate.of(2026, 5, 9));
    collectionRepository.save(saved);
    assertThat(collectionRepository.findBySlug("dr-update"))
        .get()
        .extracting(CollectionEntity::getCollectionEndDate)
        .isEqualTo(LocalDate.of(2026, 5, 9));

    // Clear the end date (single-day / open collection).
    saved.setCollectionEndDate(null);
    collectionRepository.save(saved);
    assertThat(collectionRepository.findBySlug("dr-update"))
        .get()
        .extracting(CollectionEntity::getCollectionEndDate)
        .isNull();
  }
}
