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
 * Real-Postgres coverage for the {@code listedOnly} gate in {@link
 * CollectionRepository#findAllParentCollectionsByChildId}, which no test executed: every
 * repository-level call in main passes {@code false}, and the two {@code true} call sites are
 * mocked in {@code CollectionProcessingUtilTest}, so the appended SQL never ran.
 *
 * <p>Both conjuncts get their own parent, because a test with only one cannot tell a dropped half
 * from a dropped whole. The LISTED-and-visible parent is the control: without it, a gate that
 * returned nothing at all would pass every other assertion here.
 */
class CollectionParentListedGateIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private CollectionRepository collectionRepository;
  @Autowired private ContentRepository contentRepository;

  private CollectionEntity save(String slug, CollectionVisibility visibility) {
    return collectionRepository.save(
        CollectionEntity.builder()
            .isClient(false)
            .isBlog(false)
            .title("Parent gate " + slug)
            .slug(slug)
            .visibility(visibility)
            .totalContent(0)
            .build());
  }

  private void linkChild(Long parentId, Long childId, boolean visible) {
    CollectionEntity child =
        collectionRepository.findById(childId).orElseThrow(IllegalStateException::new);
    ContentCollectionEntity ref =
        contentRepository.saveCollectionContent(
            ContentCollectionEntity.builder().referencedCollection(child).build());
    collectionRepository.saveContent(
        CollectionContentEntity.builder()
            .collectionId(parentId)
            .contentId(ref.getId())
            .orderIndex(0)
            .visible(visible)
            .build());
  }

  private List<Long> parentIds(Long childId, boolean listedOnly) {
    return collectionRepository.findAllParentCollectionsByChildId(childId, listedOnly).stream()
        .map(CollectionEntity::getId)
        .toList();
  }

  @Test
  @DisplayName("listedOnly=true drops a HIDDEN parent, an UNLISTED parent and a hidden membership")
  void listedOnly_true_appliesBothGates() {
    CollectionEntity child = save("gate-child-true", CollectionVisibility.LISTED);
    CollectionEntity listed = save("gate-listed-true", CollectionVisibility.LISTED);
    CollectionEntity hidden = save("gate-hidden-true", CollectionVisibility.HIDDEN);
    CollectionEntity unlisted = save("gate-unlisted-true", CollectionVisibility.UNLISTED);
    CollectionEntity hiddenMembership = save("gate-membership-true", CollectionVisibility.LISTED);

    linkChild(listed.getId(), child.getId(), true);
    linkChild(hidden.getId(), child.getId(), true);
    linkChild(unlisted.getId(), child.getId(), true);
    linkChild(hiddenMembership.getId(), child.getId(), false);

    assertThat(parentIds(child.getId(), true)).containsExactly(listed.getId());
  }

  @Test
  @DisplayName("listedOnly=false keeps every parent relationship, however the parent is visible")
  void listedOnly_false_dropsBothGates() {
    CollectionEntity child = save("gate-child-false", CollectionVisibility.LISTED);
    CollectionEntity listed = save("gate-listed-false", CollectionVisibility.LISTED);
    CollectionEntity hidden = save("gate-hidden-false", CollectionVisibility.HIDDEN);
    CollectionEntity unlisted = save("gate-unlisted-false", CollectionVisibility.UNLISTED);
    CollectionEntity hiddenMembership = save("gate-membership-false", CollectionVisibility.LISTED);

    linkChild(listed.getId(), child.getId(), true);
    linkChild(hidden.getId(), child.getId(), true);
    linkChild(unlisted.getId(), child.getId(), true);
    linkChild(hiddenMembership.getId(), child.getId(), false);

    assertThat(parentIds(child.getId(), false))
        .containsExactlyInAnyOrder(
            listed.getId(), hidden.getId(), unlisted.getId(), hiddenMembership.getId());
  }
}
