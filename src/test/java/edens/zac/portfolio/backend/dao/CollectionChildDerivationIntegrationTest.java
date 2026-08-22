package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Real-Postgres coverage for the derived parent-ness queries that replace CollectionType.PARENT.
 * Mocked JdbcTemplate cannot catch text-block syntax errors, so these run against the shared
 * Testcontainers Postgres with every migration applied.
 */
class CollectionChildDerivationIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private CollectionRepository collectionRepository;
  @Autowired private ContentRepository contentRepository;

  private CollectionEntity save(String slug) {
    return collectionRepository.save(
        CollectionEntity.builder()
            .isClient(false)
            .isBlog(false)
            .title("Derivation " + slug)
            .slug(slug)
            .visibility(CollectionVisibility.LISTED)
            .totalContent(0)
            .build());
  }

  private void linkChild(Long parentId, Long childId, int orderIndex, boolean visible) {
    CollectionEntity child =
        collectionRepository.findById(childId).orElseThrow(IllegalStateException::new);
    ContentCollectionEntity ref =
        contentRepository.saveCollectionContent(
            ContentCollectionEntity.builder().referencedCollection(child).build());
    collectionRepository.saveContent(
        CollectionContentEntity.builder()
            .collectionId(parentId)
            .contentId(ref.getId())
            .orderIndex(orderIndex)
            .visible(visible)
            .build());
  }

  @Test
  @DisplayName("findAllReferencedCollectionIdsByParentId is empty for a childless collection")
  void findAllReferencedCollectionIds_noChildren_empty() {
    CollectionEntity leaf = save("deriv-leaf");
    assertThat(collectionRepository.findAllReferencedCollectionIdsByParentId(leaf.getId()))
        .isEmpty();
  }

  @Test
  @DisplayName("findAllReferencedCollectionIdsByParentId counts a child with a hidden membership")
  void findAllReferencedCollectionIds_hiddenMembership_stillReturned() {
    CollectionEntity parent = save("deriv-parent-hidden");
    CollectionEntity child = save("deriv-child-hidden");
    linkChild(parent.getId(), child.getId(), 0, false);
    assertThat(collectionRepository.findAllReferencedCollectionIdsByParentId(parent.getId()))
        .containsExactly(child.getId());
  }

  @Test
  @DisplayName("findAllReferencedCollectionIdsByParentId returns every child in order_index order")
  void findAllReferencedCollectionIds_ordered() {
    CollectionEntity parent = save("deriv-parent-ids");
    CollectionEntity first = save("deriv-child-a");
    CollectionEntity second = save("deriv-child-b");
    linkChild(parent.getId(), second.getId(), 5, true);
    linkChild(parent.getId(), first.getId(), 1, false);

    List<Long> ids = collectionRepository.findAllReferencedCollectionIdsByParentId(parent.getId());

    assertThat(ids).containsExactly(first.getId(), second.getId());
  }

  @Test
  @DisplayName("findAllReferencedCollectionIdsByParentId returns empty for a null id")
  void findAllReferencedCollectionIds_nullId_empty() {
    assertThat(collectionRepository.findAllReferencedCollectionIdsByParentId(null)).isEmpty();
  }
}
