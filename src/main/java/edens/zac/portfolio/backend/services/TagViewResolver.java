package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.TagEntity;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves any non-colliding tag slug into a synthetic, PARENT-shaped {@link CollectionModel} so a
 * tag becomes browsable as a collection at {@code /{slug}}. Tagged collections render first (as
 * {@link ContentModels.Collection} blocks), then tagged images (as {@link ContentModels.Image}
 * blocks). No DB schema change: membership is derived live from the {@code collection_tags} and
 * {@code content_tags} join tables.
 *
 * <p>Sibling to {@link SyntheticCollectionResolver}: it follows the same construction pattern
 * (build a PARENT model with {@code visibility=LISTED}) and the same env-aware visibility scope,
 * but is a separate class because its membership comes from a DB tag lookup (returning {@link
 * Optional#empty()} on miss) rather than a static catalog. Depends on repositories + conversion
 * utils directly — NOT on {@link CollectionService} — to avoid a circular bean dependency,
 * mirroring {@link SyntheticCollectionResolver}.
 */
@Service
@RequiredArgsConstructor
public class TagViewResolver {

  private final TagRepository tagRepository;
  private final ContentRepository contentRepository;
  private final CollectionProcessingUtil collectionProcessingUtil;
  private final ContentModelConverter contentModelConverter;

  /**
   * Resolve a slug into a tag-view PARENT model. Returns empty when the slug matches no tag, or
   * when the matching tag has zero visible members (no collections AND no images) — callers fall
   * through to a 404 rather than render an empty PARENT page.
   *
   * @param slug the requested slug (already confirmed not to be a synthetic list slug or a real
   *     collection by the caller)
   * @param isLocalEnvironment when true, expands the allowed visibility set to include UNLISTED and
   *     HIDDEN (dev only); prod sees LISTED only
   */
  @Transactional(readOnly = true)
  public Optional<CollectionModel> resolveTagView(String slug, boolean isLocalEnvironment) {
    if (slug == null) {
      return Optional.empty();
    }
    Optional<TagEntity> tagOpt = tagRepository.findBySlug(slug);
    if (tagOpt.isEmpty()) {
      return Optional.empty();
    }
    TagEntity tag = tagOpt.get();

    List<CollectionVisibility> allowed =
        isLocalEnvironment
            ? List.of(
                CollectionVisibility.LISTED,
                CollectionVisibility.UNLISTED,
                CollectionVisibility.HIDDEN)
            : List.of(CollectionVisibility.LISTED);

    // Members: tagged collections first (deliberate collection-level tags), then tagged images.
    List<CollectionEntity> collectionRows =
        tagRepository.findCollectionsByTagId(tag.getId(), allowed);
    List<CollectionModel> memberCollections =
        collectionProcessingUtil.batchConvertToBasicModels(collectionRows);

    List<Long> imageContentIds = tagRepository.findImageContentByTagId(tag.getId(), allowed);
    List<ContentImageEntity> imageEntities = contentRepository.findImagesByIds(imageContentIds);
    List<ContentModels.Image> memberImages =
        contentModelConverter.batchConvertImageEntitiesToModels(imageEntities);

    // A tag-view with zero members is not a page — fall through to 404.
    if (memberCollections.isEmpty() && memberImages.isEmpty()) {
      return Optional.empty();
    }

    List<ContentModel> content = new ArrayList<>();
    memberCollections.stream()
        .map(ContentModels.Collection::fromCollectionModel)
        .forEach(content::add);
    content.addAll(memberImages);

    return Optional.of(
        CollectionModel.builder()
            .slug(tag.getSlug())
            .title(tag.getTagName())
            .type(CollectionType.PARENT)
            .visibility(CollectionVisibility.LISTED)
            .coverImage(representativeCover(memberCollections, memberImages))
            .content(content)
            .contentCount(content.size())
            .contentPerPage(content.size())
            .currentPage(0)
            .totalPages(1)
            .build());
  }

  /**
   * Pick a representative cover: the first member collection that has a cover image, else the first
   * tagged image, else none. Member collections arrive rating-desc / date-desc ordered, so "first
   * with a cover" is the most prominent member.
   */
  private ContentModels.Image representativeCover(
      List<CollectionModel> memberCollections, List<ContentModels.Image> memberImages) {
    return memberCollections.stream()
        .map(CollectionModel::getCoverImage)
        .filter(java.util.Objects::nonNull)
        .findFirst()
        .orElseGet(() -> memberImages.isEmpty() ? null : memberImages.get(0));
  }
}
