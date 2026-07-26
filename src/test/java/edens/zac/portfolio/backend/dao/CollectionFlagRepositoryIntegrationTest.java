package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration coverage for the V50 is_client / is_blog boolean predicates. Runs the real V50
 * migration on Testcontainers Postgres. Verifies: the boolean-keyed repository queries key on the
 * flags (NOT the legacy type column), the derived parent-of-galleries query (no parent-side type
 * filter), the blog-by-date get-or-create key, save round-tripping of the flags, and the V50
 * label-tag seeds.
 *
 * <p>This class inserts roughly 20 collections into the SHARED singleton container, whose harness
 * truncates only auth tables. Assertions here are therefore containment-based, never exact counts,
 * and any future exact-count test in this package must seed its own container.
 */
class CollectionFlagRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private CollectionRepository collectionRepository;
  @Autowired private ContentRepository contentRepository;
  @Autowired private JdbcTemplate jdbc;

  private CollectionEntity saveCollection(
      String slug,
      CollectionType type,
      boolean isClient,
      boolean isBlog,
      CollectionVisibility visibility,
      LocalDate date) {
    return collectionRepository.save(
        CollectionEntity.builder()
            .type(type)
            .isClient(isClient)
            .isBlog(isBlog)
            .title("Flag " + slug)
            .slug(slug)
            .collectionDate(date)
            .visibility(visibility)
            .totalContent(0)
            .build());
  }

  /** Link child under parent through the content_collection join chain. */
  private void linkChild(Long parentId, Long childId, boolean visible) {
    CollectionEntity childRef =
        collectionRepository.findById(childId).orElseThrow(IllegalStateException::new);
    ContentCollectionEntity contentRef =
        contentRepository.saveCollectionContent(
            ContentCollectionEntity.builder().referencedCollection(childRef).build());
    collectionRepository.saveContent(
        CollectionContentEntity.builder()
            .collectionId(parentId)
            .contentId(contentRef.getId())
            .orderIndex(0)
            .visible(visible)
            .build());
  }

  @Test
  void migration_v50_seedsArtGalleryAndPortfolioLabelTags() {
    // V50 idempotently ensures the art-gallery and portfolio label tags exist so grouping
    // survives the eventual type-column drop.
    List<String> slugs =
        jdbc.queryForList(
            "SELECT slug FROM tag WHERE slug IN ('art-gallery', 'portfolio') ORDER BY slug",
            String.class);
    assertThat(slugs).containsExactly("art-gallery", "portfolio");
  }

  @Test
  void save_clientGallery_roundTripsFlagsAndType() {
    CollectionEntity saved =
        saveCollection(
            "flag-roundtrip",
            CollectionType.CLIENT_GALLERY,
            true,
            false,
            CollectionVisibility.LISTED,
            LocalDate.of(2026, 1, 5));

    CollectionEntity reloaded = collectionRepository.findById(saved.getId()).orElseThrow();

    assertThat(reloaded.isClient()).isTrue();
    assertThat(reloaded.isBlog()).isFalse();
    assertThat(reloaded.getType()).isEqualTo(CollectionType.CLIENT_GALLERY);
  }

  @Test
  void findListedBlogsOrdered_keysOnIsBlogNotType() {
    CollectionEntity blogFlagged =
        saveCollection(
            "flag-blog-flagged",
            CollectionType.BLOG,
            false,
            true,
            CollectionVisibility.LISTED,
            LocalDate.of(2026, 2, 1));
    // Boolean is the truth: a MISC-typed row with is_blog=true must be returned...
    CollectionEntity miscButBlog =
        saveCollection(
            "flag-misc-but-blog",
            CollectionType.MISC,
            false,
            true,
            CollectionVisibility.LISTED,
            LocalDate.of(2026, 2, 2));
    // ...and a BLOG-typed row with is_blog=false must NOT be.
    CollectionEntity blogTypeNoFlag =
        saveCollection(
            "flag-blog-type-no-flag",
            CollectionType.BLOG,
            false,
            false,
            CollectionVisibility.LISTED,
            LocalDate.of(2026, 2, 3));
    CollectionEntity unlistedBlog =
        saveCollection(
            "flag-unlisted-blog",
            CollectionType.BLOG,
            false,
            true,
            CollectionVisibility.UNLISTED,
            LocalDate.of(2026, 2, 4));

    List<Long> ids =
        collectionRepository.findListedBlogsOrdered().stream()
            .map(CollectionEntity::getId)
            .toList();

    assertThat(ids).contains(blogFlagged.getId(), miscButBlog.getId());
    assertThat(ids).doesNotContain(blogTypeNoFlag.getId(), unlistedBlog.getId());
  }

  @Test
  void findBlogsByCollectionDate_keysOnIsBlogAndReturnsOldestFirst() {
    LocalDate day = LocalDate.of(2026, 3, 15);
    CollectionEntity older =
        collectionRepository.save(
            CollectionEntity.builder()
                .type(CollectionType.BLOG)
                .isBlog(true)
                .title("Flag blog older")
                .slug("flag-blog-day-older")
                .collectionDate(day)
                .visibility(CollectionVisibility.LISTED)
                .totalContent(0)
                .createdAt(LocalDateTime.of(2026, 3, 15, 8, 0))
                .build());
    CollectionEntity newer =
        collectionRepository.save(
            CollectionEntity.builder()
                .type(CollectionType.BLOG)
                .isBlog(true)
                .title("Flag blog newer")
                .slug("flag-blog-day-newer")
                .collectionDate(day)
                .visibility(CollectionVisibility.LISTED)
                .totalContent(0)
                .createdAt(LocalDateTime.of(2026, 3, 15, 12, 0))
                .build());
    // Same day but is_blog=false: must not be part of the get-or-create key.
    CollectionEntity sameDayNotBlog =
        saveCollection(
            "flag-day-not-blog",
            CollectionType.MISC,
            false,
            false,
            CollectionVisibility.LISTED,
            day);

    List<CollectionEntity> found = collectionRepository.findBlogsByCollectionDate(day);

    List<Long> ids = found.stream().map(CollectionEntity::getId).toList();
    assertThat(ids).containsExactly(older.getId(), newer.getId());
    assertThat(ids).doesNotContain(sameDayNotBlog.getId());
  }

  @Test
  void findClientGalleriesByVisibilityIn_keysOnIsClient() {
    CollectionEntity listedGallery =
        saveCollection(
            "flag-cg-listed",
            CollectionType.CLIENT_GALLERY,
            true,
            false,
            CollectionVisibility.LISTED,
            LocalDate.of(2026, 4, 1));
    CollectionEntity unlistedGallery =
        saveCollection(
            "flag-cg-unlisted",
            CollectionType.CLIENT_GALLERY,
            true,
            false,
            CollectionVisibility.UNLISTED,
            LocalDate.of(2026, 4, 2));
    CollectionEntity hiddenGallery =
        saveCollection(
            "flag-cg-hidden",
            CollectionType.CLIENT_GALLERY,
            true,
            false,
            CollectionVisibility.HIDDEN,
            LocalDate.of(2026, 4, 3));
    CollectionEntity notClient =
        saveCollection(
            "flag-cg-not-client",
            CollectionType.PORTFOLIO,
            false,
            false,
            CollectionVisibility.LISTED,
            LocalDate.of(2026, 4, 4));

    List<Long> ids =
        collectionRepository
            .findClientGalleriesByVisibilityIn(
                List.of(CollectionVisibility.LISTED, CollectionVisibility.UNLISTED))
            .stream()
            .map(CollectionEntity::getId)
            .toList();

    assertThat(ids).contains(listedGallery.getId(), unlistedGallery.getId());
    assertThat(ids).doesNotContain(hiddenGallery.getId(), notClient.getId());
  }

  @Test
  void findClientGalleriesAndQualifyingParents_includesDerivedParentsWithoutTypeFilter() {
    List<CollectionVisibility> scope = List.of(CollectionVisibility.LISTED);

    CollectionEntity standaloneGallery =
        saveCollection(
            "flag-parent-standalone",
            CollectionType.CLIENT_GALLERY,
            true,
            false,
            CollectionVisibility.LISTED,
            LocalDate.of(2026, 5, 1));

    // PARENT-typed wrapper with a visible client child: qualifies (as before).
    CollectionEntity parentTyped =
        saveCollection(
            "flag-parent-typed",
            CollectionType.PARENT,
            false,
            false,
            CollectionVisibility.LISTED,
            LocalDate.of(2026, 5, 2));
    CollectionEntity childOfParentTyped =
        saveCollection(
            "flag-parent-typed-child",
            CollectionType.CLIENT_GALLERY,
            true,
            false,
            CollectionVisibility.LISTED,
            LocalDate.of(2026, 5, 3));
    linkChild(parentTyped.getId(), childOfParentTyped.getId(), true);

    // Derived parent: NOT typed PARENT, but has a visible is_client child -> must now qualify.
    CollectionEntity derivedParent =
        saveCollection(
            "flag-parent-derived",
            CollectionType.MISC,
            false,
            false,
            CollectionVisibility.LISTED,
            LocalDate.of(2026, 5, 4));
    CollectionEntity childOfDerived =
        saveCollection(
            "flag-parent-derived-child",
            CollectionType.CLIENT_GALLERY,
            true,
            false,
            CollectionVisibility.LISTED,
            LocalDate.of(2026, 5, 5));
    linkChild(derivedParent.getId(), childOfDerived.getId(), true);

    // Parent whose only client child is HIDDEN: child fails the visibility scope -> excluded.
    CollectionEntity parentOfHiddenChild =
        saveCollection(
            "flag-parent-hidden-child",
            CollectionType.PARENT,
            false,
            false,
            CollectionVisibility.LISTED,
            LocalDate.of(2026, 5, 6));
    CollectionEntity hiddenChild =
        saveCollection(
            "flag-parent-hidden-child-c",
            CollectionType.CLIENT_GALLERY,
            true,
            false,
            CollectionVisibility.HIDDEN,
            LocalDate.of(2026, 5, 7));
    linkChild(parentOfHiddenChild.getId(), hiddenChild.getId(), true);

    // Parent linked to a client child through a soft-removed (visible=false) row: excluded.
    CollectionEntity parentOfSoftRemoved =
        saveCollection(
            "flag-parent-soft-removed",
            CollectionType.PARENT,
            false,
            false,
            CollectionVisibility.LISTED,
            LocalDate.of(2026, 5, 8));
    CollectionEntity softRemovedChild =
        saveCollection(
            "flag-parent-soft-removed-c",
            CollectionType.CLIENT_GALLERY,
            true,
            false,
            CollectionVisibility.LISTED,
            LocalDate.of(2026, 5, 9));
    linkChild(parentOfSoftRemoved.getId(), softRemovedChild.getId(), false);

    // Parent of only non-client children: excluded.
    CollectionEntity parentOfNonClient =
        saveCollection(
            "flag-parent-non-client",
            CollectionType.PARENT,
            false,
            false,
            CollectionVisibility.LISTED,
            LocalDate.of(2026, 5, 10));
    CollectionEntity nonClientChild =
        saveCollection(
            "flag-parent-non-client-c",
            CollectionType.PORTFOLIO,
            false,
            false,
            CollectionVisibility.LISTED,
            LocalDate.of(2026, 5, 11));
    linkChild(parentOfNonClient.getId(), nonClientChild.getId(), true);

    List<Long> ids =
        collectionRepository.findClientGalleriesAndQualifyingParents(scope).stream()
            .map(CollectionEntity::getId)
            .toList();

    assertThat(ids)
        .contains(
            standaloneGallery.getId(),
            parentTyped.getId(),
            derivedParent.getId(),
            childOfParentTyped.getId(),
            childOfDerived.getId(),
            softRemovedChild.getId());
    assertThat(ids)
        .doesNotContain(
            parentOfHiddenChild.getId(),
            parentOfSoftRemoved.getId(),
            parentOfNonClient.getId(),
            hiddenChild.getId(),
            nonClientChild.getId());
  }

  @Test
  void findClientGalleriesAndQualifyingParents_dualRoleCollectionAppearsOnce() {
    // A collection that satisfies BOTH arms of the OR (it is itself is_client AND it parents a
    // visible client child) must not be duplicated in the listing.
    List<CollectionVisibility> scope = List.of(CollectionVisibility.LISTED);

    CollectionEntity dualRole =
        saveCollection(
            "flag-dual-role",
            CollectionType.CLIENT_GALLERY,
            true,
            false,
            CollectionVisibility.LISTED,
            LocalDate.of(2026, 6, 1));
    CollectionEntity dualRoleChild =
        saveCollection(
            "flag-dual-role-child",
            CollectionType.CLIENT_GALLERY,
            true,
            false,
            CollectionVisibility.LISTED,
            LocalDate.of(2026, 6, 2));
    linkChild(dualRole.getId(), dualRoleChild.getId(), true);

    List<Long> ids =
        collectionRepository.findClientGalleriesAndQualifyingParents(scope).stream()
            .map(CollectionEntity::getId)
            .toList();

    assertThat(ids).contains(dualRole.getId()).doesNotHaveDuplicates();
  }

  @Test
  void findListedBlogsOrdered_ordersByRatingThenDate() {
    // The ORDER BY is otherwise unpinned on real Postgres: rating first (NULLS LAST), then
    // newest collection_date.
    CollectionEntity lowRated =
        collectionRepository.save(
            CollectionEntity.builder()
                .type(CollectionType.BLOG)
                .isBlog(true)
                .title("Flag order low")
                .slug("flag-order-low")
                .collectionDate(LocalDate.of(2026, 7, 3))
                .visibility(CollectionVisibility.LISTED)
                .rating(1)
                .totalContent(0)
                .build());
    CollectionEntity highRated =
        collectionRepository.save(
            CollectionEntity.builder()
                .type(CollectionType.BLOG)
                .isBlog(true)
                .title("Flag order high")
                .slug("flag-order-high")
                .collectionDate(LocalDate.of(2026, 7, 1))
                .visibility(CollectionVisibility.LISTED)
                .rating(5)
                .totalContent(0)
                .build());
    CollectionEntity midRatedNewer =
        collectionRepository.save(
            CollectionEntity.builder()
                .type(CollectionType.BLOG)
                .isBlog(true)
                .title("Flag order mid")
                .slug("flag-order-mid")
                .collectionDate(LocalDate.of(2026, 7, 2))
                .visibility(CollectionVisibility.LISTED)
                .rating(3)
                .totalContent(0)
                .build());

    List<Long> ids =
        collectionRepository.findListedBlogsOrdered().stream()
            .map(CollectionEntity::getId)
            .toList();

    assertThat(ids).containsSubsequence(highRated.getId(), midRatedNewer.getId(), lowRated.getId());
  }
}
