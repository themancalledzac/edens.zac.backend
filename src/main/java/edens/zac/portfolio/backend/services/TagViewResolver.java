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
 * Resolves a non-colliding tag slug into a synthetic PARENT {@link CollectionModel} so a tag is
 * browsable at {@code /{slug}}: tagged collections first, then tagged images. Membership is derived
 * live from {@code collection_tags}/{@code content_tags} (no schema change).
 *
 * <p>Tag-backed sibling of {@link SyntheticCollectionResolver}; depends on repositories directly
 * (not {@link CollectionService}) to avoid a circular bean dependency.
 */
@Service
@RequiredArgsConstructor
public class TagViewResolver {

  private final TagRepository tagRepository;
  private final ContentRepository contentRepository;
  private final CollectionProcessingUtil collectionProcessingUtil;
  private final ContentModelConverter contentModelConverter;

  /**
   * Resolves a slug into a tag-view PARENT model, or empty when no tag matches or the tag has no
   * visible members (caller 404s).
   *
   * @param slug requested slug (caller has ruled out synthetic + real-collection slugs)
   * @param isLocalEnvironment dev expands visibility to UNLISTED/HIDDEN; prod is LISTED only
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

    // A converted tag has handed its slug to a real collection; that collection now renders
    // instead.
    if (tag.getConvertedCollectionId() != null) {
      return Optional.empty();
    }

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
            .derived(true)
            .visibility(CollectionVisibility.LISTED)
            .coverImage(representativeCover(memberCollections, memberImages))
            .content(content)
            .contentCount(content.size())
            .contentPerPage(content.size())
            .currentPage(0)
            .totalPages(1)
            .build());
  }

  /** Cover: first member collection with a cover image, else first tagged image, else none. */
  private ContentModels.Image representativeCover(
      List<CollectionModel> memberCollections, List<ContentModels.Image> memberImages) {
    return memberCollections.stream()
        .map(CollectionModel::getCoverImage)
        .filter(java.util.Objects::nonNull)
        .findFirst()
        .orElseGet(() -> memberImages.isEmpty() ? null : memberImages.get(0));
  }
}
