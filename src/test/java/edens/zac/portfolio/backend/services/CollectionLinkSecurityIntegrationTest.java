package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * S5 and S6 on the single link funnel {@link CollectionService#linkCollectionToParent}: an ancestor
 * may never be re-linked as a descendant, and a password already set on the parent waterfalls onto
 * a client-gallery child at link time. Real Postgres because both walk the collection_content ->
 * content_collection join chain.
 *
 * <p>Rows live in the SHARED singleton container (only auth tables are truncated), so every slug
 * carries an s5-/s6- prefix and assertions never use global counts.
 */
class CollectionLinkSecurityIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private CollectionService collectionService;
  @Autowired private CollectionRepository collectionRepository;
  @Autowired private JdbcTemplate jdbc;

  private long seed(String slug, boolean isClient, String galleryPassword) {
    jdbc.update(
        "INSERT INTO collection (title, slug, type, visibility, is_client, is_blog,"
            + " gallery_password) VALUES (?, ?, 'MISC', 'LISTED', ?, false, ?)",
        slug,
        slug,
        isClient,
        galleryPassword);
    return jdbc.queryForObject("SELECT id FROM collection WHERE slug = ?", Long.class, slug);
  }

  // --- S5: cycle validation ---------------------------------------------------

  @Test
  void selfLink_isRejected() {
    long only = seed("s5-self", false, null);

    assertThatThrownBy(() -> collectionService.linkCollectionToParent(only, only))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot be its own parent");
  }

  @Test
  void twoCycle_isRejected() {
    long a = seed("s5-two-a", false, null);
    long b = seed("s5-two-b", false, null);
    collectionService.linkCollectionToParent(a, b);

    assertThatThrownBy(() -> collectionService.linkCollectionToParent(b, a))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cycle detected");
  }

  @Test
  void threeCycle_isRejected() {
    // validateNoParentCycles caught only self- and 2-cycles by its own admission. The ancestor
    // walk must reject the deeper case, which is what merges role grants across a client gallery
    // and a public collection.
    long a = seed("s5-three-a", false, null);
    long b = seed("s5-three-b", false, null);
    long c = seed("s5-three-c", false, null);
    collectionService.linkCollectionToParent(a, b);
    collectionService.linkCollectionToParent(b, c);

    assertThatThrownBy(() -> collectionService.linkCollectionToParent(c, a))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cycle detected");
  }

  @Test
  void twoParentsSharingOneChild_isAllowed() {
    // A diamond is not a cycle. The guard must not reject legitimate multi-parent linkage.
    long left = seed("s5-diamond-left", false, null);
    long right = seed("s5-diamond-right", false, null);
    long shared = seed("s5-diamond-shared", false, null);
    collectionService.linkCollectionToParent(left, shared);

    collectionService.linkCollectionToParent(right, shared);

    assertThat(collectionRepository.findAllParentCollectionsByChildId(shared))
        .extracting(CollectionEntity::getId)
        .containsExactlyInAnyOrder(left, right);
  }

  // --- S6: password propagates at link time ------------------------------------

  @Test
  void linkingClientChildUnderPasswordedParent_copiesPasswordDown() {
    long parent = seed("s6-parent", false, "smith2026");
    long child = seed("s6-client-child", true, null);

    collectionService.linkCollectionToParent(parent, child);

    assertThat(collectionRepository.findById(child).orElseThrow().getGalleryPassword())
        .isEqualTo("smith2026");
  }

  @Test
  void linkingClientChildThatAlreadyHasAPassword_leavesItAlone() {
    long parent = seed("s6-parent-keep", false, "parentpw");
    long child = seed("s6-client-child-keep", true, "childpw");

    collectionService.linkCollectionToParent(parent, child);

    assertThat(collectionRepository.findById(child).orElseThrow().getGalleryPassword())
        .isEqualTo("childpw");
  }

  @Test
  void linkingNonClientChildUnderPasswordedParent_doesNotCopyPassword() {
    long parent = seed("s6-parent-nonclient", false, "parentpw");
    long child = seed("s6-plain-child", false, null);

    collectionService.linkCollectionToParent(parent, child);

    assertThat(collectionRepository.findById(child).orElseThrow().getGalleryPassword()).isNull();
  }

  @Test
  void linkingClientChildUnderAnUnprotectedParent_leavesTheChildOpen() {
    long parent = seed("s6-parent-open", false, null);
    long child = seed("s6-client-child-open", true, null);

    collectionService.linkCollectionToParent(parent, child);

    assertThat(collectionRepository.findById(child).orElseThrow().getGalleryPassword()).isNull();
  }
}
