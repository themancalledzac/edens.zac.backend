package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.TagEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import edens.zac.portfolio.backend.types.ContentType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class SyntheticCollectionResolverTest {

  @Mock private CollectionRepository collectionRepository;
  @Mock private CollectionProcessingUtil collectionProcessingUtil;
  @Mock private TagRepository tagRepository;
  @Mock private CollectionAccessService collectionAccessService;

  @InjectMocks private SyntheticCollectionResolver resolver;

  private static final List<CollectionVisibility> FULL_SCOPE =
      List.of(
          CollectionVisibility.LISTED, CollectionVisibility.UNLISTED, CollectionVisibility.HIDDEN);

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private static void setPrincipal(AuthPrincipal principal) {
    var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(auth);
    SecurityContextHolder.setContext(context);
  }

  @Test
  void recognizesAllCatalogSlugs() {
    assertThat(resolver.isSyntheticSlug("all-collections")).isTrue();
    assertThat(resolver.isSyntheticSlug("all-blogs")).isTrue();
    assertThat(resolver.isSyntheticSlug("all-portfolios")).isFalse();
    assertThat(resolver.isSyntheticSlug("all-client-galleries")).isTrue();
    assertThat(resolver.isSyntheticSlug("all-art-galleries")).isFalse();
    assertThat(resolver.isSyntheticSlug("all-misc")).isFalse();
  }

  @Test
  @DisplayName("resolve rejects a pruned catalog slug")
  void resolve_prunedSlug_throws() {
    assertThatThrownBy(() -> resolver.resolve("all-misc", false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Not a synthetic slug");
  }

  @Test
  void rejectsNonCatalogSlugs() {
    assertThat(resolver.isSyntheticSlug("home")).isFalse();
    assertThat(resolver.isSyntheticSlug("film")).isFalse();
    assertThat(resolver.isSyntheticSlug(null)).isFalse();
    assertThat(resolver.isSyntheticSlug("")).isFalse();
  }

  @Test
  void resolveAllCollectionsForAdminUsesFullScopeAndReturnsChildrenAsContent() {
    // all-collections is permission-scoped: an admin principal widens to every visibility
    // (regardless of environment) and still uses the chronological query.
    setPrincipal(new AuthPrincipal(1L, "admin@ezac.com", true, true));
    when(collectionRepository.findNonEmptyListedOrOwnedOrderByDate(eq(FULL_SCOPE), eq(List.of())))
        .thenReturn(List.of(new CollectionEntity(), new CollectionEntity()));
    when(collectionProcessingUtil.batchConvertToBasicModels(any()))
        .thenReturn(
            List.of(
                CollectionModel.builder().id(1L).slug("a").build(),
                CollectionModel.builder().id(2L).slug("b").build()));

    CollectionModel out = resolver.resolve("all-collections", false);

    assertThat(out.getSlug()).isEqualTo("all-collections");
    assertThat(out.getTitle()).isEqualTo("All Collections");
    assertThat(out.getVisibility()).isEqualTo(CollectionVisibility.LISTED);
    assertThat(out.getContent()).hasSize(2);
    assertThat(out.getContent().get(0)).isInstanceOf(ContentModels.Collection.class);
    ContentModels.Collection first = (ContentModels.Collection) out.getContent().get(0);
    assertThat(first.contentType()).isEqualTo(ContentType.COLLECTION);
    assertThat(first.referencedCollectionId()).isEqualTo(1L);
    assertThat(first.slug()).isEqualTo("a");
  }

  @Test
  void resolveAllCollectionsForAdminQueriesFullScopeWithNoOwnedIds() {
    setPrincipal(new AuthPrincipal(1L, "admin@ezac.com", true, true));
    when(collectionRepository.findNonEmptyListedOrOwnedOrderByDate(eq(FULL_SCOPE), eq(List.of())))
        .thenReturn(List.of(new CollectionEntity()));
    when(collectionProcessingUtil.batchConvertToBasicModels(any()))
        .thenReturn(List.of(CollectionModel.builder().id(1L).slug("a").build()));

    resolver.resolve("all-collections", false);

    verify(collectionRepository)
        .findNonEmptyListedOrOwnedOrderByDate(eq(FULL_SCOPE), eq(List.of()));
    verify(collectionAccessService, never()).memberCollectionIdsForUser(any());
  }

  @Test
  void resolveAllCollectionsForSignedInNonAdminQueriesListedPlusGrants() {
    setPrincipal(AuthPrincipal.client(42L, "client@ezac.com", true));
    when(collectionAccessService.memberCollectionIdsForUser(42L)).thenReturn(List.of(7L, 9L));
    when(collectionRepository.findNonEmptyListedOrOwnedOrderByDate(
            eq(List.of(CollectionVisibility.LISTED)), eq(List.of(7L, 9L))))
        .thenReturn(List.of(new CollectionEntity()));
    when(collectionProcessingUtil.batchConvertToBasicModels(any()))
        .thenReturn(List.of(CollectionModel.builder().id(7L).slug("g").build()));

    resolver.resolve("all-collections", false);

    verify(collectionRepository)
        .findNonEmptyListedOrOwnedOrderByDate(
            eq(List.of(CollectionVisibility.LISTED)), eq(List.of(7L, 9L)));
  }

  @Test
  void resolveAllCollectionsAnonymousQueriesListedOnlyEvenInLocalEnv() {
    // No principal set. isLocalEnvironment=true must NOT widen — the env shim is subsumed
    // for all-collections; identity is the only thing that widens this list.
    when(collectionRepository.findNonEmptyListedOrOwnedOrderByDate(
            eq(List.of(CollectionVisibility.LISTED)), eq(List.of())))
        .thenReturn(List.of());
    when(collectionProcessingUtil.batchConvertToBasicModels(any())).thenReturn(List.of());

    resolver.resolve("all-collections", true);

    verify(collectionRepository)
        .findNonEmptyListedOrOwnedOrderByDate(
            eq(List.of(CollectionVisibility.LISTED)), eq(List.of()));
  }

  @Test
  void resolveAllCollectionsNonAdminNeverWidensBeyondListedPlusGrants() {
    // isAdmin=false with mfaSatisfied=true — guards against transposed boolean reads.
    setPrincipal(new AuthPrincipal(42L, "client@ezac.com", false, true));
    when(collectionAccessService.memberCollectionIdsForUser(42L)).thenReturn(List.of());
    when(collectionRepository.findNonEmptyListedOrOwnedOrderByDate(
            eq(List.of(CollectionVisibility.LISTED)), eq(List.of())))
        .thenReturn(List.of());
    when(collectionProcessingUtil.batchConvertToBasicModels(any())).thenReturn(List.of());

    resolver.resolve("all-collections", true);

    verify(collectionRepository, never())
        .findNonEmptyListedOrOwnedOrderByDate(eq(FULL_SCOPE), any());
  }

  @Test
  void resolveAllCollectionsCarriesDateRangeOntoContentBlocks() {
    // The public date-organized showcase reads collectionDate/collectionEndDate off the
    // synthetic all-collections COLLECTION blocks, so fromCollectionModel must copy both.
    when(collectionRepository.findNonEmptyListedOrOwnedOrderByDate(any(), any()))
        .thenReturn(List.of(new CollectionEntity()));
    when(collectionProcessingUtil.batchConvertToBasicModels(any()))
        .thenReturn(
            List.of(
                CollectionModel.builder()
                    .id(7L)
                    .slug("spring-trip")
                    .collectionDate(java.time.LocalDate.of(2026, 3, 5))
                    .collectionEndDate(java.time.LocalDate.of(2026, 3, 7))
                    .build()));

    CollectionModel out = resolver.resolve("all-collections", true);

    ContentModels.Collection block = (ContentModels.Collection) out.getContent().get(0);
    assertThat(block.collectionDate()).isEqualTo(java.time.LocalDate.of(2026, 3, 5));
    assertThat(block.collectionEndDate()).isEqualTo(java.time.LocalDate.of(2026, 3, 7));
  }

  @Test
  void resolveAllCollectionsUsesDateOrderedQueryNotRatingFirst() {
    when(collectionRepository.findNonEmptyListedOrOwnedOrderByDate(
            eq(List.of(CollectionVisibility.LISTED)), eq(List.of())))
        .thenReturn(List.of(new CollectionEntity()));
    when(collectionProcessingUtil.batchConvertToBasicModels(any()))
        .thenReturn(List.of(CollectionModel.builder().id(1L).slug("x").build()));

    resolver.resolve("all-collections", false);

    verify(collectionRepository)
        .findNonEmptyListedOrOwnedOrderByDate(
            eq(List.of(CollectionVisibility.LISTED)), eq(List.of()));
    verify(collectionRepository, never()).findNonEmptyOrderedByVisibilityIn(any(), anyBoolean());
  }

  @Test
  void resolveAllCollectionsAttachesCollectionTagsToContentBlocks() {
    // Each child collection's tags must ride onto its COLLECTION content block so the frontend can
    // filter the synthetic list client-side by tag.
    when(collectionRepository.findNonEmptyListedOrOwnedOrderByDate(any(), any()))
        .thenReturn(List.of(new CollectionEntity()));
    when(collectionProcessingUtil.batchConvertToBasicModels(any()))
        .thenReturn(List.of(CollectionModel.builder().id(7L).slug("trip").build()));
    when(tagRepository.findTagsByCollectionIds(List.of(7L)))
        .thenReturn(
            Map.of(
                7L,
                List.of(
                    TagEntity.builder().id(2L).tagName("italy").slug("italy").build(),
                    TagEntity.builder().id(3L).tagName("mountains").slug("mountains").build())));

    CollectionModel out = resolver.resolve("all-collections", true);

    ContentModels.Collection block = (ContentModels.Collection) out.getContent().get(0);
    assertThat(block.tags()).extracting("name").containsExactly("italy", "mountains");
    assertThat(block.tags()).extracting("slug").containsExactly("italy", "mountains");
  }

  @Test
  void resolveAllCollectionsWithNoTagsYieldsEmptyBlockTags() {
    when(collectionRepository.findNonEmptyListedOrOwnedOrderByDate(any(), any()))
        .thenReturn(List.of(new CollectionEntity()));
    when(collectionProcessingUtil.batchConvertToBasicModels(any()))
        .thenReturn(List.of(CollectionModel.builder().id(9L).slug("bare").build()));
    // tagRepository.findTagsByCollectionIds returns an empty map by default (Mockito) -> no tags.

    CollectionModel out = resolver.resolve("all-collections", true);

    ContentModels.Collection block = (ContentModels.Collection) out.getContent().get(0);
    assertThat(block.tags()).isEmpty();
  }

  @Test
  void resolveAllCollectionsAttachesCollectionLocationsToContentBlocks() {
    // Locations must ride onto the COLLECTION block the same way tags do, or the shipped
    // /collections location filter matches against nothing. Unlike tags they need no second query:
    // batchConvertToBasicModels has already put them on the model.
    when(collectionRepository.findNonEmptyListedOrOwnedOrderByDate(any(), any()))
        .thenReturn(List.of(new CollectionEntity()));
    when(collectionProcessingUtil.batchConvertToBasicModels(any()))
        .thenReturn(
            List.of(
                CollectionModel.builder()
                    .id(7L)
                    .slug("trip")
                    .locations(
                        List.of(
                            new Records.Location(2L, "Chamonix, France", "chamonix-france"),
                            new Records.Location(3L, "Seattle", "seattle")))
                    .build()));

    CollectionModel out = resolver.resolve("all-collections", true);

    ContentModels.Collection block = (ContentModels.Collection) out.getContent().get(0);
    assertThat(block.locations()).extracting("name").containsExactly("Chamonix, France", "Seattle");
    assertThat(block.locations()).extracting("slug").containsExactly("chamonix-france", "seattle");
  }

  @Test
  void resolveAllCollectionsWithNoLocationsYieldsEmptyBlockLocations() {
    // Empty, never null. The frontend's collectionRefMatchesCriteria reads ref.locations directly,
    // and a null would be a different shape in the JSON than the tags field beside it.
    when(collectionRepository.findNonEmptyListedOrOwnedOrderByDate(any(), any()))
        .thenReturn(List.of(new CollectionEntity()));
    when(collectionProcessingUtil.batchConvertToBasicModels(any()))
        .thenReturn(List.of(CollectionModel.builder().id(9L).slug("bare").build()));

    CollectionModel out = resolver.resolve("all-collections", true);

    ContentModels.Collection block = (ContentModels.Collection) out.getContent().get(0);
    assertThat(block.locations()).isEmpty();
  }

  /**
   * People must ride onto the COLLECTION block the same way locations do, or the people filter on
   * the synthetic list views matches against nothing. Like locations and unlike tags they need no
   * second query — {@code batchConvertToBasicModels} has already put them on the model.
   */
  @Test
  void resolveAllCollectionsAttachesCollectionPeopleToContentBlocks() {
    when(collectionRepository.findNonEmptyListedOrOwnedOrderByDate(any(), any()))
        .thenReturn(List.of(new CollectionEntity()));
    when(collectionProcessingUtil.batchConvertToBasicModels(any()))
        .thenReturn(
            List.of(
                CollectionModel.builder()
                    .id(11L)
                    .slug("wedding")
                    .people(
                        List.of(
                            new Records.Person(4L, "Ada Lovelace"),
                            new Records.Person(5L, "Grace Hopper")))
                    .build()));

    CollectionModel out = resolver.resolve("all-collections", true);

    ContentModels.Collection block = (ContentModels.Collection) out.getContent().get(0);
    assertThat(block.people()).extracting("name").containsExactly("Ada Lovelace", "Grace Hopper");
    assertThat(block.people()).extracting("id").containsExactly(4L, 5L);
  }

  /**
   * Empty, never null. The frontend reads {@code ref.people} directly, and a null would be a
   * different JSON shape from the tags and locations fields beside it.
   */
  @Test
  void resolveAllCollectionsWithNoPeopleYieldsEmptyBlockPeople() {
    when(collectionRepository.findNonEmptyListedOrOwnedOrderByDate(any(), any()))
        .thenReturn(List.of(new CollectionEntity()));
    when(collectionProcessingUtil.batchConvertToBasicModels(any()))
        .thenReturn(List.of(CollectionModel.builder().id(12L).slug("bare").build()));

    CollectionModel out = resolver.resolve("all-collections", true);

    ContentModels.Collection block = (ContentModels.Collection) out.getContent().get(0);
    assertThat(block.people()).isEmpty();
  }

  @Test
  void resolveAllCollectionsAttachesVisibilityToContentBlocks() {
    // Each child's visibility must ride onto its COLLECTION block so an admin client can preview
    // the general-audience view (LISTED only) without a per-collection fetch.
    when(collectionRepository.findNonEmptyListedOrOwnedOrderByDate(any(), any()))
        .thenReturn(List.of(new CollectionEntity(), new CollectionEntity()));
    when(collectionProcessingUtil.batchConvertToBasicModels(any()))
        .thenReturn(
            List.of(
                CollectionModel.builder()
                    .id(7L)
                    .slug("public-trip")
                    .visibility(CollectionVisibility.LISTED)
                    .build(),
                CollectionModel.builder()
                    .id(8L)
                    .slug("wip")
                    .visibility(CollectionVisibility.HIDDEN)
                    .build()));

    CollectionModel out = resolver.resolve("all-collections", true);

    assertThat(out.getContent())
        .extracting(block -> ((ContentModels.Collection) block).visibility())
        .containsExactly(CollectionVisibility.LISTED, CollectionVisibility.HIDDEN);
  }

  @Test
  void resolveAllCollectionsKeepsVisibilityThroughTagEnrichment() {
    // withTags() rebuilds the record; visibility must survive that copy, not silently reset.
    when(collectionRepository.findNonEmptyListedOrOwnedOrderByDate(any(), any()))
        .thenReturn(List.of(new CollectionEntity()));
    when(collectionProcessingUtil.batchConvertToBasicModels(any()))
        .thenReturn(
            List.of(
                CollectionModel.builder()
                    .id(7L)
                    .slug("trip")
                    .visibility(CollectionVisibility.UNLISTED)
                    .build()));
    when(tagRepository.findTagsByCollectionIds(List.of(7L)))
        .thenReturn(
            Map.of(7L, List.of(TagEntity.builder().id(2L).tagName("italy").slug("italy").build())));

    CollectionModel out = resolver.resolve("all-collections", true);

    ContentModels.Collection block = (ContentModels.Collection) out.getContent().get(0);
    assertThat(block.visibility()).isEqualTo(CollectionVisibility.UNLISTED);
    assertThat(block.tags()).extracting("name").containsExactly("italy");
  }

  @Test
  void resolveAllCollectionsKeepsPasswordProtectedThroughTagEnrichment() {
    // Two mutations redden this: dropping isPasswordProtected from fromCollectionModel, and
    // dropping it from withTags(), which rebuilds the record on this exact path.
    when(collectionRepository.findNonEmptyListedOrOwnedOrderByDate(any(), any()))
        .thenReturn(List.of(new CollectionEntity()));
    when(collectionProcessingUtil.batchConvertToBasicModels(any()))
        .thenReturn(
            List.of(
                CollectionModel.builder()
                    .id(7L)
                    .slug("smith-wedding")
                    .visibility(CollectionVisibility.LISTED)
                    .isPasswordProtected(true)
                    .build(),
                CollectionModel.builder()
                    .id(8L)
                    .slug("open-trip")
                    .visibility(CollectionVisibility.LISTED)
                    .build()));
    when(tagRepository.findTagsByCollectionIds(List.of(7L, 8L)))
        .thenReturn(
            Map.of(7L, List.of(TagEntity.builder().id(2L).tagName("italy").slug("italy").build())));

    CollectionModel out = resolver.resolve("all-collections", true);

    assertThat(out.getContent())
        .extracting(block -> ((ContentModels.Collection) block).isPasswordProtected())
        // The second model leaves the Boolean null, which must read false and not throw on
        // unboxing.
        .containsExactly(true, false);
  }

  @Test
  void resolveAllBlogsInProdFiltersToBlogTypeAndListedOnly() {
    when(collectionRepository.findNonEmptyOrderedByVisibilityIn(
            eq(List.of(CollectionVisibility.LISTED)), eq(true)))
        .thenReturn(List.of(new CollectionEntity()));
    when(collectionProcessingUtil.batchConvertToBasicModels(any()))
        .thenReturn(List.of(CollectionModel.builder().id(11L).slug("trip").build()));

    CollectionModel out = resolver.resolve("all-blogs", false);

    assertThat(out.getSlug()).isEqualTo("all-blogs");
    assertThat(out.getTitle()).isEqualTo("Blogs");
    assertThat(out.getContent()).hasSize(1);
  }

  @Test
  void resolveAllClientGalleriesUsesParentInclusiveQueryAndMixesParentAndGalleryTiles() {
    // Standalone gallery + a wedding PARENT that has CLIENT_GALLERY children — the inclusive
    // repo query returns both rows and the resolver must pass each through unchanged so PARENT
    // tiles render alongside CLIENT_GALLERY tiles on the listing.
    when(collectionRepository.findClientGalleriesAndQualifyingParents(
            eq(List.of(CollectionVisibility.LISTED))))
        .thenReturn(List.of(new CollectionEntity(), new CollectionEntity()));
    when(collectionProcessingUtil.batchConvertToBasicModels(any()))
        .thenReturn(
            List.of(
                CollectionModel.builder()
                    .id(50L)
                    .slug("smith-wedding")
                    .title("Smith Wedding")
                    .build(),
                CollectionModel.builder()
                    .id(51L)
                    .slug("jones-engagement")
                    .title("Jones Engagement")
                    .build()));

    CollectionModel out = resolver.resolve("all-client-galleries", false);

    assertThat(out.getSlug()).isEqualTo("all-client-galleries");
    assertThat(out.getTitle()).isEqualTo("Client Galleries");
    assertThat(out.getContent()).hasSize(2);

    ContentModels.Collection parentTile = (ContentModels.Collection) out.getContent().get(0);
    assertThat(parentTile.slug()).isEqualTo("smith-wedding");

    ContentModels.Collection galleryTile = (ContentModels.Collection) out.getContent().get(1);
    assertThat(galleryTile.slug()).isEqualTo("jones-engagement");
  }

  @Test
  void resolveAllClientGalleriesInDevPassesAllVisibilitiesToInclusiveQuery() {
    when(collectionRepository.findClientGalleriesAndQualifyingParents(eq(FULL_SCOPE)))
        .thenReturn(List.of());
    when(collectionProcessingUtil.batchConvertToBasicModels(any())).thenReturn(List.of());

    CollectionModel out = resolver.resolve("all-client-galleries", true);

    assertThat(out.getContent()).isEmpty();
  }

  @Test
  void resolveUnknownSlugThrows() {
    assertThatThrownBy(() -> resolver.resolve("home", true))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
