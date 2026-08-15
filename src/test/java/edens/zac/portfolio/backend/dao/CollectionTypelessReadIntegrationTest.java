package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import java.time.LocalDate;
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

  /** Same as {@link #seedRow}, plus a collection date and a cover image. */
  private Long seedRowWithCover(String slug, LocalDate collectionDate, Long coverImageId) {
    jdbc.update(
        "INSERT INTO collection (is_client, is_blog, title, slug, visibility, total_content,"
            + " collection_date, cover_image_id, created_at, updated_at)"
            + " VALUES (false, false, ?, ?, 'LISTED', 0, ?, ?, NOW(), NOW())",
        "Legacy " + slug,
        slug,
        collectionDate,
        coverImageId);
    return jdbc.queryForObject("SELECT id FROM collection WHERE slug = ?", Long.class, slug);
  }

  /**
   * Insert an image. content_image shares its primary key with content, so the parent row has to go
   * in first and hand down its id.
   */
  private Long seedImage(String imageUrlWeb) {
    Long id =
        jdbc.queryForObject(
            "INSERT INTO content (content_type, created_at, updated_at)"
                + " VALUES ('IMAGE', NOW(), NOW()) RETURNING id",
            Long.class);
    jdbc.update("INSERT INTO content_image (id, image_url_web) VALUES (?, ?)", id, imageUrlWeb);
    return id;
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
  @DisplayName("findCollectionListEntries reads a row from a schema with no type column")
  void findCollectionListEntries_typelessSchema_reads() {
    Long id = seedRow("legacy-id-title-slug");

    List<Records.CollectionList> rows = collectionRepository.findCollectionListEntries();

    assertThat(rows).extracting(Records.CollectionList::id).contains(id);
  }

  @Test
  @DisplayName("findCollectionListEntries populates date and cover url, tolerating nulls")
  void findCollectionListEntries_populatesDateAndCover() {
    Long coverId = seedImage("https://cdn.example.com/list-entry-cover.jpg");
    seedRowWithCover("list-entry-with-cover", LocalDate.of(2026, 6, 1), coverId);
    seedRow("list-entry-bare");

    List<Records.CollectionList> rows = collectionRepository.findCollectionListEntries();

    Records.CollectionList withCover =
        rows.stream()
            .filter(r -> "list-entry-with-cover".equals(r.slug()))
            .findFirst()
            .orElseThrow();
    assertThat(withCover.collectionDate()).isEqualTo(LocalDate.of(2026, 6, 1));
    assertThat(withCover.coverImageUrl()).isEqualTo("https://cdn.example.com/list-entry-cover.jpg");

    Records.CollectionList bare =
        rows.stream().filter(r -> "list-entry-bare".equals(r.slug())).findFirst().orElseThrow();
    assertThat(bare.collectionDate()).isNull();
    assertThat(bare.coverImageUrl()).isNull();
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
