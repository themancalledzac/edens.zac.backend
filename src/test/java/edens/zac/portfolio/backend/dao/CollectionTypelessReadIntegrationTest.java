package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proof that no read path projects collection.type any more. Each test seeds a row whose type
 * column holds a value no Java enum would ever have accepted; every query below must return it
 * without inspecting the column. Real Postgres because mocked JdbcTemplate cannot catch text-block
 * or StringBuilder-concatenation syntax errors in these hand-written statements.
 */
class CollectionTypelessReadIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private CollectionRepository collectionRepository;
  @Autowired private CollectionSiblingRepository collectionSiblingRepository;
  @Autowired private JdbcTemplate jdbc;

  private Long seedLegacyRow(String slug) {
    jdbc.update(
        "INSERT INTO collection (type, is_client, is_blog, title, slug, visibility, total_content,"
            + " created_at, updated_at) VALUES ('LEGACY_GONE', false, false, ?, ?, 'LISTED', 0,"
            + " NOW(), NOW())",
        "Legacy " + slug,
        slug);
    return jdbc.queryForObject("SELECT id FROM collection WHERE slug = ?", Long.class, slug);
  }

  @Test
  @DisplayName("findBySlug reads a row whose type column holds an unknown legacy value")
  void findBySlug_legacyTypeValue_reads() {
    seedLegacyRow("legacy-find-by-slug");

    Optional<CollectionEntity> found = collectionRepository.findBySlug("legacy-find-by-slug");

    assertThat(found).isPresent();
    assertThat(found.orElseThrow().getSlug()).isEqualTo("legacy-find-by-slug");
  }

  @Test
  @DisplayName("findIdTitleAndSlug reads a row whose type column holds an unknown legacy value")
  void findIdTitleAndSlug_legacyTypeValue_reads() {
    Long id = seedLegacyRow("legacy-id-title-slug");

    List<Records.CollectionList> rows = collectionRepository.findIdTitleAndSlug();

    assertThat(rows).extracting(Records.CollectionList::id).contains(id);
  }

  @Test
  @DisplayName("findSiblings reads a sibling whose type column holds an unknown legacy value")
  void findSiblings_legacyTypeValue_reads() {
    Long a = seedLegacyRow("legacy-sibling-a");
    Long b = seedLegacyRow("legacy-sibling-b");
    collectionSiblingRepository.addSibling(a, b);

    List<Records.SiblingRow> siblings = collectionSiblingRepository.findSiblings(a, true);

    assertThat(siblings).extracting(Records.SiblingRow::id).containsExactly(b);
  }

  @Test
  @DisplayName("save INSERT omits type and relies on the V51 column default")
  void save_insert_omitsTypeColumn() {
    CollectionEntity saved =
        collectionRepository.save(
            CollectionEntity.builder()
                .isClient(false)
                .isBlog(false)
                .title("Typeless Insert")
                .slug("typeless-insert")
                .visibility(CollectionVisibility.LISTED)
                .totalContent(0)
                .build());

    assertThat(saved.getId()).isNotNull();
    String storedType =
        jdbc.queryForObject(
            "SELECT type FROM collection WHERE id = ?", String.class, saved.getId());
    assertThat(storedType).isEqualTo("MISC");
  }

  @Test
  @DisplayName("save UPDATE omits type and leaves the stored legacy value untouched")
  void save_update_leavesTypeColumnUntouched() {
    Long id = seedLegacyRow("legacy-update");
    CollectionEntity entity = collectionRepository.findById(id).orElseThrow();
    entity.setTitle("Legacy Renamed");

    collectionRepository.save(entity);

    String storedType =
        jdbc.queryForObject("SELECT type FROM collection WHERE id = ?", String.class, id);
    assertThat(storedType).isEqualTo("LEGACY_GONE");
    assertThat(collectionRepository.findById(id).orElseThrow().getTitle())
        .isEqualTo("Legacy Renamed");
  }
}
