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
 * Proof that no read or write path names collection.type any more. U4 stopped projecting the
 * column; V52 (U5) dropped it outright, so Postgres itself now rejects any statement that
 * reintroduces it -- every test below fails loudly the moment one does. Real Postgres because a
 * mocked JdbcTemplate cannot catch text-block or StringBuilder-concatenation syntax errors in these
 * hand-written statements.
 */
class CollectionTypelessReadIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private CollectionRepository collectionRepository;
  @Autowired private CollectionSiblingRepository collectionSiblingRepository;
  @Autowired private JdbcTemplate jdbc;

  private Long seedRow(String slug) {
    jdbc.update(
        "INSERT INTO collection (is_client, is_blog, title, slug, visibility, total_content,"
            + " created_at, updated_at) VALUES (false, false, ?, ?, 'LISTED', 0, NOW(), NOW())",
        "Legacy " + slug,
        slug);
    return jdbc.queryForObject("SELECT id FROM collection WHERE slug = ?", Long.class, slug);
  }

  @Test
  @DisplayName("findBySlug reads a row from a schema with no type column")
  void findBySlug_typelessSchema_reads() {
    seedRow("legacy-find-by-slug");

    Optional<CollectionEntity> found = collectionRepository.findBySlug("legacy-find-by-slug");

    assertThat(found).isPresent();
    assertThat(found.orElseThrow().getSlug()).isEqualTo("legacy-find-by-slug");
  }

  @Test
  @DisplayName("findIdTitleAndSlug reads a row from a schema with no type column")
  void findIdTitleAndSlug_typelessSchema_reads() {
    Long id = seedRow("legacy-id-title-slug");

    List<Records.CollectionList> rows = collectionRepository.findIdTitleAndSlug();

    assertThat(rows).extracting(Records.CollectionList::id).contains(id);
  }

  @Test
  @DisplayName("findSiblings reads a sibling from a schema with no type column")
  void findSiblings_typelessSchema_reads() {
    Long a = seedRow("legacy-sibling-a");
    Long b = seedRow("legacy-sibling-b");
    collectionSiblingRepository.setSibling(a, b, true);

    List<Records.SiblingRow> siblings = collectionSiblingRepository.findSiblings(a, true);

    assertThat(siblings).extracting(Records.SiblingRow::id).containsExactly(b);
  }

  @Test
  @DisplayName("save INSERT succeeds against a schema with no type column")
  void save_insert_doesNotNameTheDroppedColumn() {
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
    assertThat(collectionRepository.findById(saved.getId()).orElseThrow().getSlug())
        .isEqualTo("typeless-insert");
  }

  @Test
  @DisplayName("save UPDATE succeeds against a schema with no type column")
  void save_update_doesNotNameTheDroppedColumn() {
    Long id = seedRow("legacy-update");
    CollectionEntity entity = collectionRepository.findById(id).orElseThrow();
    entity.setTitle("Legacy Renamed");

    collectionRepository.save(entity);

    assertThat(collectionRepository.findById(id).orElseThrow().getTitle())
        .isEqualTo("Legacy Renamed");
  }

  @Test
  @DisplayName("the dropped column really is absent, so the tests above are not vacuous")
  void typeColumnIsAbsentFromTheSchema() {
    Integer columns =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public'"
                + " AND table_name = 'collection' AND column_name = 'type'",
            Integer.class);
    assertThat(columns).isZero();
  }
}
