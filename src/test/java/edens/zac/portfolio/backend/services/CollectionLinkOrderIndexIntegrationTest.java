package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.Records;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Pins the append index written by both writers of a parent-to-child join row: {@link
 * CollectionService#linkCollectionToParent} and the admin Structure tab, which routes through
 * {@code handleCollectionToCollectionUpdates}. Both read the index from {@code
 * CollectionRepository#getNextOrderIndexForCollection}, so this runs against real Postgres to
 * execute the MAX(order_index) SQL rather than a stubbed return.
 *
 * <p>Rows live in the SHARED singleton container and the collection tables are never truncated, so
 * every slug carries an oi- prefix and every assertion is scoped to one parent id.
 */
class CollectionLinkOrderIndexIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private CollectionService collectionService;
  @Autowired private JdbcTemplate jdbc;

  private long seed(String slug) {
    jdbc.update(
        "INSERT INTO collection (title, slug, visibility, is_client, is_blog)"
            + " VALUES (?, ?, 'LISTED', false, false)",
        slug,
        slug);
    return jdbc.queryForObject("SELECT id FROM collection WHERE slug = ?", Long.class, slug);
  }

  private List<Integer> orderIndexesOf(long parentId) {
    return jdbc.queryForList(
        "SELECT order_index FROM collection_content WHERE collection_id = ? ORDER BY order_index",
        Integer.class,
        parentId);
  }

  private void updateChildren(long parentId, List<Records.ChildCollection> children) {
    collectionService.updateContent(
        parentId,
        new CollectionRequests.Update(
            parentId,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new CollectionRequests.CollectionUpdate(null, children, null),
            null));
  }

  @Test
  void linkCollectionToParent_appendsEachChildAtTheNextIndex() {
    long parent = seed("oi-link-parent");
    long first = seed("oi-link-first");
    long second = seed("oi-link-second");
    long third = seed("oi-link-third");

    collectionService.linkCollectionToParent(parent, first);
    collectionService.linkCollectionToParent(parent, second);
    collectionService.linkCollectionToParent(parent, third);

    assertThat(orderIndexesOf(parent)).containsExactly(0, 1, 2);
  }

  @Test
  void structureTab_withoutOrderIndex_appendsEachChildAtTheNextIndex() {
    long parent = seed("oi-struct-parent");
    long first = seed("oi-struct-first");
    long second = seed("oi-struct-second");
    long third = seed("oi-struct-third");

    updateChildren(
        parent,
        List.of(
            new Records.ChildCollection(first, null, null, null, true, null),
            new Records.ChildCollection(second, null, null, null, true, null),
            new Records.ChildCollection(third, null, null, null, true, null)));

    assertThat(orderIndexesOf(parent)).containsExactly(0, 1, 2);
  }

  @Test
  void structureTab_withExplicitOrderIndex_usesItInsteadOfAppending() {
    long parent = seed("oi-explicit-parent");
    long existing = seed("oi-explicit-existing");
    long placed = seed("oi-explicit-placed");

    collectionService.linkCollectionToParent(parent, existing);
    updateChildren(parent, List.of(new Records.ChildCollection(placed, null, null, null, true, 9)));

    assertThat(orderIndexesOf(parent)).containsExactly(0, 9);
  }
}
