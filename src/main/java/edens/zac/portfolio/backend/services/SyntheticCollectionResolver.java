package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.TagEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recognizes the synthetic admin/list slugs ("all-collections", "all-blogs", etc.) and synthesizes
 * a PARENT-shaped {@link CollectionModel} populated with {@link ContentModels.Collection} content
 * blocks pointing to each child. Bypasses the regular DB lookup in {@code
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

  private static final Map<String, Synthetic> CATALOG =
      Map.of(
          ALL_COLLECTIONS,
          new Synthetic("All Collections", null),
          "all-blogs",
          new Synthetic("Blogs", CollectionType.BLOG),
          "all-portfolios",
          new Synthetic("Portfolios", CollectionType.PORTFOLIO),
          ALL_CLIENT_GALLERIES,
          new Synthetic("Client Galleries", CollectionType.CLIENT_GALLERY),
          "all-art-galleries",
          new Synthetic("Art Galleries", CollectionType.ART_GALLERY),
          "all-misc",
          new Synthetic("Misc", CollectionType.MISC));

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
   * Resolve a synthetic slug into a PARENT-shaped {@link CollectionModel}. Caller is responsible
   * for verifying the slug via {@link #isSyntheticSlug(String)} first.
   */
  @Transactional(readOnly = true)
  public CollectionModel resolve(String slug, boolean isLocalEnvironment) {
    Synthetic spec = CATALOG.get(slug);
    if (spec == null) {
      throw new IllegalArgumentException("Not a synthetic slug: " + slug);
    }

    List<CollectionVisibility> allowed = CollectionVisibility.visibleScope(isLocalEnvironment);

    // "all-client-galleries" includes PARENT collections that have ≥1 CLIENT_GALLERY child
    // (e.g. wedding wrappers with ceremony/reception sub-galleries) so they appear alongside
    // standalone CLIENT_GALLERYs. "all-collections" is permission-scoped by the caller's verified
    // identity (NOT the environment) and stays chronological (newest first) for its first paint;
    // the frontend reorders client-side thereafter. Other synthetic slugs use the simple type
    // filter with rating-first ordering and the env-based scope. All non-gallery slugs exclude
    // collections that have zero content rows so the listing never shows empty tiles.
    List<CollectionEntity> rows;
    if (ALL_CLIENT_GALLERIES.equals(slug)) {
      rows = collectionRepository.findClientGalleriesAndQualifyingParents(allowed);
    } else if (ALL_COLLECTIONS.equals(slug)) {
      rows = findAllCollectionsForCurrentViewer();
    } else {
      rows = collectionRepository.findNonEmptyOrderedByVisibilityIn(allowed, spec.typeFilter());
    }
    List<CollectionModel> children = collectionProcessingUtil.batchConvertToBasicModels(rows);

    // Batch-load each child collection's tags (single query) and attach them to the COLLECTION
    // content-ref blocks so the frontend can filter the synthetic list client-side by tag without a
    // per-collection fetch. Collections with no tags get an empty list.
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
        .type(CollectionType.PARENT)
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
    AuthPrincipal principal = currentPrincipal();
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

  /** The authenticated principal, or null when the request is anonymous. */
  private static AuthPrincipal currentPrincipal() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return (auth != null && auth.getPrincipal() instanceof AuthPrincipal p) ? p : null;
  }

  /** Map collection tag entities to serializable Records.Tag, tolerating a null/absent list. */
  private static List<Records.Tag> toTagRecords(List<TagEntity> tags) {
    if (tags == null || tags.isEmpty()) {
      return List.of();
    }
    return tags.stream().map(t -> new Records.Tag(t.getId(), t.getTagName(), t.getSlug())).toList();
  }

  private record Synthetic(String title, CollectionType typeFilter) {}
}
