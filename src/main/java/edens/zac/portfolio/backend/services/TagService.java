package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.TagEntity;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.SaveAsCollectionRequest;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tag admin operations: promoting a tag-view into a real, editable collection. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagService {

  private static final List<CollectionVisibility> ALL_VISIBILITIES =
      List.of(
          CollectionVisibility.LISTED, CollectionVisibility.UNLISTED, CollectionVisibility.HIDDEN);

  private final TagRepository tagRepository;
  private final CollectionRepository collectionRepository;
  private final CollectionService collectionService;

  /**
   * Promotes a tag into a real collection at {@code tag.slug}: creates the collection, snapshots
   * the tag's current members into it, and flags the tag converted so its tag-view stops rendering.
   *
   * @param tagId tag to convert
   * @param request optional type/visibility (default PORTFOLIO / UNLISTED)
   * @return the new collection in the same shape {@code createChildCollection} returns
   */
  @Transactional
  public CollectionRequests.UpdateResponse convertTagToCollection(
      Long tagId, SaveAsCollectionRequest request) {
    TagEntity tag =
        tagRepository
            .findById(tagId)
            .orElseThrow(() -> new ResourceNotFoundException("Tag not found with ID: " + tagId));

    if (tag.getConvertedCollectionId() != null) {
      throw new IllegalStateException("Tag " + tagId + " is already converted to a collection");
    }
    if (collectionRepository.findBySlug(tag.getSlug()).isPresent()) {
      throw new IllegalStateException("A collection already owns slug '" + tag.getSlug() + "'");
    }

    CollectionType type =
        request != null && request.type() != null ? request.type() : CollectionType.PORTFOLIO;
    CollectionVisibility visibility =
        request != null && request.visibility() != null
            ? request.visibility()
            : CollectionVisibility.UNLISTED;

    // Create the collection, then take over the tag's slug and requested visibility.
    CollectionRequests.UpdateResponse created =
        collectionService.createCollection(new CollectionRequests.Create(type, tag.getTagName()));
    Long newCollectionId = created.collection().getId();

    CollectionEntity newCollection =
        collectionRepository
            .findById(newCollectionId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Created collection not found with ID: " + newCollectionId));
    newCollection.setSlug(tag.getSlug());
    newCollection.setVisibility(visibility);
    collectionRepository.save(newCollection);

    snapshotMembers(tagId, newCollectionId);

    tagRepository.updateConvertedCollectionId(tagId, newCollectionId);
    log.info(
        "Converted tag {} into collection {} at slug '{}'", tagId, newCollectionId, tag.getSlug());

    return collectionService.getUpdateCollectionData(tag.getSlug());
  }

  /** Snapshots tagged member collections (first) then tagged images into collection_content. */
  private void snapshotMembers(Long tagId, Long collectionId) {
    List<CollectionEntity> memberCollections =
        tagRepository.findCollectionsByTagId(tagId, ALL_VISIBILITIES);
    for (CollectionEntity member : memberCollections) {
      collectionService.linkCollectionToParent(collectionId, member.getId());
    }

    List<Long> imageContentIds = tagRepository.findImageContentByTagId(tagId, ALL_VISIBILITIES);
    Integer maxOrderIndex = collectionRepository.getMaxOrderIndexForCollection(collectionId);
    int orderIndex = maxOrderIndex != null ? maxOrderIndex + 1 : 0;
    for (Long imageContentId : imageContentIds) {
      collectionRepository.saveContent(
          CollectionContentEntity.builder()
              .collectionId(collectionId)
              .contentId(imageContentId)
              .orderIndex(orderIndex++)
              .visible(true)
              .createdAt(LocalDateTime.now())
              .updatedAt(LocalDateTime.now())
              .build());
    }
  }
}
