package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import edens.zac.portfolio.backend.entity.TagEntity;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.Records;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles content mutation operations: tag/people updates, keyword association, and collection
 * membership changes. Renamed from ContentProcessingUtil after model conversion was extracted to
 * ContentModelConverter.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContentMutationUtil {

  private final ContentRepository contentRepository;
  private final CollectionRepository collectionRepository;
  private final TagRepository tagRepository;
  private final PersonRepository personRepository;

  // =============================================================================
  // IMAGE UPDATE HELPERS
  // =============================================================================

  /**
   * Handle collection visibility and orderIndex updates for an image. Updates the 'visible' flag
   * and 'orderIndex' for the content entry in the current collection.
   *
   * @param image The image entity being updated
   * @param collectionUpdates List of collection updates containing visibility and orderIndex
   */
  public void handleContentChildCollectionUpdates(
      ContentImageEntity image, List<Records.ChildCollection> collectionUpdates) {
    if (collectionUpdates == null || collectionUpdates.isEmpty()) {
      return;
    }

    for (Records.ChildCollection collectionUpdate : collectionUpdates) {
      if (collectionUpdate.collectionId() != null) {
        Long collectionId = collectionUpdate.collectionId();
        Integer orderIndex = collectionUpdate.orderIndex();
        Boolean visible = collectionUpdate.visible();

        Optional<CollectionContentEntity> joinEntryOpt =
            collectionRepository.findContentByCollectionIdAndContentId(collectionId, image.getId());

        if (joinEntryOpt.isEmpty()) {
          log.warn(
              "No join table entry found for content {} in collection {}. Skipping update.",
              image.getId(),
              collectionId);
          continue;
        }

        CollectionContentEntity joinEntry = joinEntryOpt.get();
        boolean updated = false;

        if (orderIndex != null) {
          collectionRepository.updateContentOrderIndex(joinEntry.getId(), orderIndex);
          log.info(
              "Updated orderIndex for image {} in collection {} to {}",
              image.getId(),
              collectionId,
              orderIndex);
          updated = true;
        }

        if (visible != null) {
          collectionRepository.updateContentVisible(joinEntry.getId(), visible);
          log.info(
              "Updated visibility for image {} in collection {} to {}",
              image.getId(),
              collectionId,
              visible);
          updated = true;
        }

        if (updated) {
          break;
        }
      }
    }
  }

  /**
   * Add content (image) to new collections with specified visibility and orderIndex. Creates join
   * table entries for the content in the specified collections.
   */
  public void handleAddToCollections(
      ContentImageEntity image, List<Records.ChildCollection> collections) {
    for (Records.ChildCollection childCollection : collections) {
      if (childCollection.collectionId() == null) {
        log.warn("Skipping collection addition: collectionId is null");
        continue;
      }

      collectionRepository
          .findById(childCollection.collectionId())
          .orElseThrow(
              () ->
                  new ResourceNotFoundException(
                      "Collection not found: " + childCollection.collectionId()));

      Optional<CollectionContentEntity> existingOpt =
          collectionRepository.findContentByCollectionIdAndContentId(
              childCollection.collectionId(), image.getId());

      if (existingOpt.isPresent()) {
        log.warn(
            "Image {} is already in collection {}. Skipping duplicate add.",
            image.getId(),
            childCollection.collectionId());
        continue;
      }

      int orderIndex =
          childCollection.orderIndex() != null
              ? childCollection.orderIndex()
              : nextOrderIndex(childCollection.collectionId());

      boolean visible = childCollection.visible() != null ? childCollection.visible() : true;

      CollectionContentEntity joinEntry =
          CollectionContentEntity.builder()
              .collectionId(childCollection.collectionId())
              .contentId(image.getId())
              .orderIndex(orderIndex)
              .visible(visible)
              .build();
      collectionRepository.saveContent(joinEntry);
      log.info(
          "Added image {} to collection {} at orderIndex {} with visible={}",
          image.getId(),
          childCollection.collectionId(),
          orderIndex,
          visible);
    }
  }

  /** Returns the next available orderIndex for a collection (max + 1, or 0 if empty). */
  private int nextOrderIndex(Long collectionId) {
    Integer maxOrder = collectionRepository.getMaxOrderIndexForCollection(collectionId);
    return maxOrder != null ? maxOrder + 1 : 0;
  }

  // =============================================================================
  // OPTIMIZED BATCH UPDATE HELPERS (pre-fetched entities to avoid N+1)
  // =============================================================================

  /**
   * Update image tags with pre-fetched current tags (avoids N+1 query). Applies the prev/new/remove
   * pattern, sets updated tags on the entity, and persists to DB.
   */
  public void updateImageTagsOptimized(
      ContentImageEntity image,
      CollectionRequests.TagUpdate tagUpdate,
      List<TagEntity> currentTagEntities,
      Set<TagEntity> newTags) {
    Set<TagEntity> currentTags = new HashSet<>(currentTagEntities);
    Set<TagEntity> updatedTags = updateTags(currentTags, tagUpdate, newTags);
    image.setTags(updatedTags);

    List<Long> updatedTagIds =
        updatedTags.stream()
            .map(TagEntity::getId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
    tagRepository.saveContentTags(image.getId(), updatedTagIds);
  }

  /**
   * Update image people with pre-fetched current people (avoids N+1 query). Applies the
   * prev/new/remove pattern, sets updated people on the entity, and persists to DB.
   */
  public void updateImagePeopleOptimized(
      ContentImageEntity image,
      CollectionRequests.PersonUpdate personUpdate,
      List<ContentPersonEntity> currentPeopleEntities,
      Set<ContentPersonEntity> newPeople) {
    Set<ContentPersonEntity> currentPeople = new HashSet<>(currentPeopleEntities);
    Set<ContentPersonEntity> updatedPeople = updatePeople(currentPeople, personUpdate, newPeople);
    image.setPeople(updatedPeople);

    List<Long> updatedPersonIds =
        updatedPeople.stream()
            .map(ContentPersonEntity::getId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
    contentRepository.saveImagePeople(image.getId(), updatedPersonIds);
  }

  // =============================================================================
  // TAG AND PEOPLE UPDATE HELPERS
  // =============================================================================

  /**
   * Associate tags and people extracted from image XMP metadata with a saved image. Creates new
   * tag/person entities if they don't already exist (case-insensitive dedup). Failures are logged
   * but do not propagate -- the image save is not affected.
   *
   * @param imageId The saved image entity ID
   * @param tagNames Tag names extracted from XMP keywords
   * @param peopleNames Person names extracted from XMP keywords
   */
  public void associateExtractedKeywords(
      Long imageId, List<String> tagNames, List<String> peopleNames) {
    if ((tagNames == null || tagNames.isEmpty())
        && (peopleNames == null || peopleNames.isEmpty())) {
      return;
    }

    try {
      if (tagNames != null && !tagNames.isEmpty()) {
        Set<Long> tagIds = new LinkedHashSet<>();
        Set<String> seenTags = new HashSet<>();
        for (String tagName : tagNames) {
          if (!seenTags.add(tagName.toLowerCase())) {
            continue;
          }
          String slug = SlugUtil.generateSlug(tagName);
          var existing = tagRepository.findBySlug(slug);
          if (existing.isPresent()) {
            tagIds.add(existing.get().getId());
          } else {
            TagEntity newTag = tagRepository.save(new TagEntity(tagName));
            tagIds.add(newTag.getId());
            log.info("Created new tag from XMP keyword: {}", tagName);
          }
        }
        tagRepository.saveContentTags(imageId, new ArrayList<>(tagIds));
        log.info("Associated {} tags with image {}", tagIds.size(), imageId);
      }

      if (peopleNames != null && !peopleNames.isEmpty()) {
        Set<Long> personIds = new LinkedHashSet<>();
        Set<String> seenPeople = new HashSet<>();
        for (String personName : peopleNames) {
          if (!seenPeople.add(personName.toLowerCase())) {
            continue;
          }
          String personSlug = SlugUtil.generateSlug(personName);
          var existing = personRepository.findBySlug(personSlug);
          if (existing.isPresent()) {
            personIds.add(existing.get().getId());
          } else {
            ContentPersonEntity newPerson =
                personRepository.save(new ContentPersonEntity(personName));
            personIds.add(newPerson.getId());
            log.info("Created new person from XMP keyword: {}", personName);
          }
        }
        contentRepository.saveImagePeople(imageId, new ArrayList<>(personIds));
        log.info("Associated {} people with image {}", personIds.size(), imageId);
      }
    } catch (Exception e) {
      log.warn(
          "Failed to associate extracted keywords with image {}: {}", imageId, e.getMessage(), e);
    }
  }

  /**
   * Update tags on an entity using the prev/new/remove pattern.
   *
   * @param currentTags Current set of tags on the entity
   * @param tagUpdate The tag update containing remove/prev/newValue operations
   * @param newTags Optional set to track newly created tags (for response metadata)
   * @return Updated set of tags
   */
  public Set<TagEntity> updateTags(
      Set<TagEntity> currentTags, CollectionRequests.TagUpdate tagUpdate, Set<TagEntity> newTags) {
    if (tagUpdate == null) {
      return currentTags;
    }

    Set<TagEntity> tags = new HashSet<>(currentTags);

    if (tagUpdate.remove() != null && !tagUpdate.remove().isEmpty()) {
      tags.removeIf(tag -> tagUpdate.remove().contains(tag.getId()));
    }

    if (tagUpdate.prev() != null && !tagUpdate.prev().isEmpty()) {
      Set<TagEntity> existingTags =
          tagUpdate.prev().stream()
              .map(
                  tagId ->
                      tagRepository
                          .findById(tagId)
                          .orElseThrow(
                              () -> new IllegalArgumentException("Tag not found: " + tagId)))
              .collect(Collectors.toSet());
      tags.addAll(existingTags);
    }

    if (tagUpdate.newValue() != null && !tagUpdate.newValue().isEmpty()) {
      for (String tagName : tagUpdate.newValue()) {
        if (tagName != null && !tagName.trim().isEmpty()) {
          String trimmedName = tagName.trim();
          var existing = tagRepository.findByTagNameIgnoreCase(trimmedName);
          if (existing.isPresent()) {
            tags.add(existing.get());
          } else {
            TagEntity newTag = new TagEntity(trimmedName);
            newTag = tagRepository.save(newTag);
            tags.add(newTag);
            if (newTags != null) {
              newTags.add(newTag);
            }
            log.info("Created new tag: {}", trimmedName);
          }
        }
      }
    }

    return tags;
  }

  /**
   * Update people on an entity using the prev/new/remove pattern.
   *
   * @param currentPeople Current set of people on the entity
   * @param personUpdate The person update containing remove/prev/newValue operations
   * @param newPeople Optional set to track newly created people (for response metadata)
   * @return Updated set of people
   */
  public Set<ContentPersonEntity> updatePeople(
      Set<ContentPersonEntity> currentPeople,
      CollectionRequests.PersonUpdate personUpdate,
      Set<ContentPersonEntity> newPeople) {
    if (personUpdate == null) {
      return currentPeople;
    }

    Set<ContentPersonEntity> people = new HashSet<>(currentPeople);

    if (personUpdate.remove() != null && !personUpdate.remove().isEmpty()) {
      people.removeIf(person -> personUpdate.remove().contains(person.getId()));
    }

    if (personUpdate.prev() != null && !personUpdate.prev().isEmpty()) {
      Set<ContentPersonEntity> existingPeople =
          personUpdate.prev().stream()
              .map(
                  personId ->
                      personRepository
                          .findById(personId)
                          .orElseThrow(
                              () -> new IllegalArgumentException("Person not found: " + personId)))
              .collect(Collectors.toSet());
      people.addAll(existingPeople);
    }

    if (personUpdate.newValue() != null && !personUpdate.newValue().isEmpty()) {
      for (String personName : personUpdate.newValue()) {
        if (personName != null && !personName.trim().isEmpty()) {
          String trimmedName = personName.trim();
          var existing = personRepository.findByPersonNameIgnoreCase(trimmedName);
          if (existing.isPresent()) {
            people.add(existing.get());
          } else {
            ContentPersonEntity newPerson = new ContentPersonEntity(trimmedName);
            newPerson = personRepository.save(newPerson);
            people.add(newPerson);
            if (newPeople != null) {
              newPeople.add(newPerson);
            }
            log.info("Created new person: {}", trimmedName);
          }
        }
      }
    }

    return people;
  }
}
