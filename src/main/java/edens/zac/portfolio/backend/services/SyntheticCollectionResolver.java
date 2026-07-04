package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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

  private static final Map<String, Synthetic> CATALOG =
      Map.of(
          "all-collections",
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

  private final CollectionRepository collectionRepository;
  private final CollectionProcessingUtil collectionProcessingUtil;

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
    // standalone CLIENT_GALLERYs. Other synthetic slugs use the simple type filter, and exclude
    // collections that have zero content rows so the listing never shows empty tiles.
    List<CollectionEntity> rows =
        ALL_CLIENT_GALLERIES.equals(slug)
            ? collectionRepository.findClientGalleriesAndQualifyingParents(allowed)
            : collectionRepository.findNonEmptyOrderedByVisibilityIn(allowed, spec.typeFilter());
    List<CollectionModel> children = collectionProcessingUtil.batchConvertToBasicModels(rows);

    List<ContentModel> content =
        children.stream()
            .map(ContentModels.Collection::fromCollectionModel)
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

  private record Synthetic(String title, CollectionType typeFilter) {}
}
