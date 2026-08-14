package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.model.Records;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Direction of collection_sibling links against real Postgres. The central claim is that a one-way
 * link is invisible from the target because the reverse ROW DOES NOT EXIST, not because the
 * LISTED-only read filter caught it -- so every collection seeded here is LISTED, which is exactly
 * the case the old visibility-filter behavior could not express.
 */
class CollectionSiblingDirectionIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private CollectionSiblingRepository collectionSiblingRepository;
  @Autowired private JdbcTemplate jdbc;

  private Long seedListed(String slug) {
    jdbc.update(
        "INSERT INTO collection (is_client, is_blog, title, slug, visibility, total_content,"
            + " created_at, updated_at) VALUES (false, false, ?, ?, 'LISTED', 0, NOW(), NOW())",
        "Direction " + slug,
        slug);
    return jdbc.queryForObject("SELECT id FROM collection WHERE slug = ?", Long.class, slug);
  }

  @Test
  @DisplayName("a one-way link is visible from the source and absent from the target, both LISTED")
  void oneWayLink_isAsymmetric_evenWhenBothCollectionsAreListed() {
    Long gallery = seedListed("direction-client-gallery");
    Long publicCollection = seedListed("direction-public-collection");

    collectionSiblingRepository.setSibling(gallery, publicCollection, false);

    assertThat(collectionSiblingRepository.findSiblings(gallery, true))
        .extracting(Records.SiblingRow::id)
        .containsExactly(publicCollection);
    assertThat(collectionSiblingRepository.findSiblings(publicCollection, true)).isEmpty();
  }

  @Test
  @DisplayName("a one-way link reports mutual false, a reciprocal link reports true")
  void findSiblings_projectsMutualFromTheReverseRow() {
    Long a = seedListed("direction-mutual-a");
    Long b = seedListed("direction-mutual-b");
    Long c = seedListed("direction-oneway-c");
    collectionSiblingRepository.setSibling(a, b, true);
    collectionSiblingRepository.setSibling(a, c, false);

    List<Records.SiblingRow> siblings = collectionSiblingRepository.findSiblings(a, false);

    assertThat(siblings)
        .filteredOn(row -> row.id().equals(b))
        .singleElement()
        .extracting(Records.SiblingRow::mutual)
        .isEqualTo(true);
    assertThat(siblings)
        .filteredOn(row -> row.id().equals(c))
        .singleElement()
        .extracting(Records.SiblingRow::mutual)
        .isEqualTo(false);
  }

  @Test
  @DisplayName("re-sending an existing mutual link as one-way removes the reverse row")
  void setSibling_downgradesMutualToOneWay() {
    Long a = seedListed("direction-downgrade-a");
    Long b = seedListed("direction-downgrade-b");
    collectionSiblingRepository.setSibling(a, b, true);
    assertThat(collectionSiblingRepository.findSiblings(b, false)).hasSize(1);

    collectionSiblingRepository.setSibling(a, b, false);

    assertThat(collectionSiblingRepository.findSiblings(a, false))
        .extracting(Records.SiblingRow::id)
        .containsExactly(b);
    assertThat(collectionSiblingRepository.findSiblings(b, false)).isEmpty();
  }

  @Test
  @DisplayName("removing a one-way link deletes exactly one row and leaves no orphan")
  void removeSibling_onOneWayLink_isANoOpInReverse() {
    Long a = seedListed("direction-remove-a");
    Long b = seedListed("direction-remove-b");
    collectionSiblingRepository.setSibling(a, b, false);

    collectionSiblingRepository.removeSibling(a, b);

    assertThat(collectionSiblingRepository.findSiblings(a, false)).isEmpty();
    assertThat(collectionSiblingRepository.findSiblings(b, false)).isEmpty();
  }

  @Test
  @DisplayName("setSibling is idempotent, so re-applying the same link does not throw")
  void setSibling_isIdempotent() {
    Long a = seedListed("direction-idempotent-a");
    Long b = seedListed("direction-idempotent-b");

    collectionSiblingRepository.setSibling(a, b, true);
    collectionSiblingRepository.setSibling(a, b, true);

    assertThat(collectionSiblingRepository.findSiblings(a, false)).hasSize(1);
    assertThat(collectionSiblingRepository.findSiblings(b, false)).hasSize(1);
  }
}
