package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.config.CurrentUser;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.TagEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recognizes the synthetic admin/list slugs ("all-collections", "all-blogs", etc.) and synthesizes
 * a container-shaped {@link CollectionModel} populated with {@link ContentModels.Collection}
 * content blocks pointing to each child. Bypasses the regular DB lookup in {@code
 * CollectionService.getCollectionWithPagination}.
 *
 * <p>Depends on {@link CollectionRepository} + {@link CollectionProcessingUtil} directly (NOT on
 * {@link CollectionService}) to avoid a circular bean dependency: CollectionService injects this
 * resolver to dispatch synthetic slugs.
 */
@Service
@RequiredArgsConstructor
public class SyntheticCollectionResolver {

  static final String ALL_CLIENT_GALLERIES = "all-client-galleries";
  static final String ALL_COLLECTIONS = "all-collections";
  static final String ALL_BLOGS = "all-blogs";

  // PORTFOLIO / ART_GALLERY / MISC had no successor concept, so all-portfolios,
  // all-art-galleries and all-misc are gone rather than re-keyed (spec D5). Re-pointing
  // all-misc at a null filter would have been an exposure: findNonEmptyOrderedByVisibilityIn
  // is only environment-scoped, unlike all-collections which is permission-scoped.
  private static final Map<String, Synthetic> CATALOG =
      Map.of(
          ALL_COLLECTIONS,
          new Synthetic("All Collections", false),
          ALL_BLOGS,
          new Synthetic("Blogs", true),
          ALL_CLIENT_GALLERIES,
          new Synthetic("Client Galleries", false));

  private static final List<CollectionVisibility> ADMIN_SCOPE =
      List.of(
          CollectionVisibility.LISTED, CollectionVisibility.UNLISTED, CollectionVisibility.HIDDEN);

  private final CollectionRepository collectionRepository;
  private final CollectionProcessingUtil collectionProcessingUtil;
  private final TagRepository tagRepository;
  private final CollectionAccessService collectionAccessService;

  /** Returns true if the slug matches a synthetic-list catalog entry. */
  public boolean isSyntheticSlug(String slug) {
    return slug != null && CATALOG.containsKey(slug);
  }

  /**
   * Resolve a synthetic slug into a container-shaped {@link CollectionModel}. Caller is responsible
   * for verifying the slug via {@link #isSyntheticSlug(String)} first.
   *
   * <p>Row selection differs per slug. {@code all-client-galleries} also includes wrapper
   * collections holding at least one client-gallery child (wedding wrappers with ceremony/reception
   * sub-galleries), so they appear alongside standalone client galleries. {@code all-collections}
   * is permission-scoped by the caller's verified identity, not by the environment, and stays
   * chronological (newest first) for its first paint; the frontend reorders client-side thereafter.
   * {@code all-blogs} keys on {@code is_blog} with rating-first ordering and the environment-based
   * scope. Every non-gallery slug excludes collections with zero content rows so the listing never
   * shows empty tiles.
   *
   * <p>Each child's tags are batch-loaded in a single query and attached to the COLLECTION
   * content-ref blocks, letting the frontend filter the synthetic list client-side by tag without a
   * per-collection fetch. Collections with no tags get an empty list. Locations ride along the same
   * way but need no call here: {@code batchConvertToBasicModels} has already batch-loaded them onto
   * the model, so {@code fromCollectionModel} copies them across.
   */
  @Transactional(readOnly = true)
  public CollectionModel resolve(String slug, boolean isLocalEnvironment) {
    Synthetic spec = CATALOG.get(slug);
    if (spec == null) {
      throw new IllegalArgumentException("Not a synthetic slug: " + slug);
    }

    List<CollectionVisibility> allowed = CollectionVisibility.visibleScope(isLocalEnvironment);

    List<CollectionEntity> rows;
    if (ALL_CLIENT_GALLERIES.equals(slug)) {
      rows = collectionRepository.findClientGalleriesAndQualifyingParents(allowed);
    } else if (ALL_COLLECTIONS.equals(slug)) {
      rows = findAllCollectionsForCurrentViewer();
    } else {
      rows = collectionRepository.findNonEmptyOrderedByVisibilityIn(allowed, spec.blogsOnly());
    }
    List<CollectionModel> children = collectionProcessingUtil.batchConvertToBasicModels(rows);

    List<Long> childIds = children.stream().map(CollectionModel::getId).toList();
    Map<Long, List<TagEntity>> tagsByCollectionId = tagRepository.findTagsByCollectionIds(childIds);

    List<ContentModel> content =
        children.stream()
            .map(
                child ->
                    ContentModels.Collection.fromCollectionModel(child)
                        .withTags(toTagRecords(tagsByCollectionId.get(child.getId()))))
            .map(ContentModel.class::cast)
            .toList();

    return CollectionModel.builder()
        .slug(slug)
        .title(spec.title())
        .visibility(CollectionVisibility.LISTED)
        .content(content)
        .contentCount(content.size())
        .contentPerPage(content.size())
        .currentPage(0)
        .totalPages(1)
        .build();
  }

  /**
   * Permission-scoped rows for the "all-collections" list. Unlike the other synthetic slugs
   * (environment-scoped), this list widens strictly on server-verified identity: an admin gets
   * every visibility; a signed-in non-admin gets LISTED plus the specific collections reached
   * through their role grants (their client galleries), even when UNLISTED/HIDDEN; anonymous gets
   * LISTED only. Nothing client-supplied can widen the scope.
   */
  private List<CollectionEntity> findAllCollectionsForCurrentViewer() {
    AuthPrincipal principal = CurrentUser.principal();
    if (principal != null && principal.isAdmin()) {
      return collectionRepository.findNonEmptyListedOrOwnedOrderByDate(ADMIN_SCOPE, List.of());
    }
    List<Long> ownedIds =
        (principal == null || principal.userId() == null)
            ? List.of()
            : collectionAccessService.memberCollectionIdsForUser(principal.userId());
    return collectionRepository.findNonEmptyListedOrOwnedOrderByDate(
        List.of(CollectionVisibility.LISTED), ownedIds);
  }

  /** Map collection tag entities to serializable Records.Tag, tolerating a null/absent list. */
  private static List<Records.Tag> toTagRecords(List<TagEntity> tags) {
    if (tags == null || tags.isEmpty()) {
      return List.of();
    }
    return tags.stream().map(t -> new Records.Tag(t.getId(), t.getTagName(), t.getSlug())).toList();
  }

  private record Synthetic(String title, boolean blogsOnly) {}
}
