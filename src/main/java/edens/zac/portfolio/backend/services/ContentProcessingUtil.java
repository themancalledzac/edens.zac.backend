package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.LocationRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentCameraEntity;
import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import edens.zac.portfolio.backend.entity.ContentEntity;
import edens.zac.portfolio.backend.entity.ContentGifEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.ContentLensEntity;
import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import edens.zac.portfolio.backend.entity.ContentTextEntity;
import edens.zac.portfolio.backend.entity.LocationEntity;
import edens.zac.portfolio.backend.entity.TagEntity;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.types.ContentType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Utility class for processing content. Handles conversion between entities and models, content
 * validation, and specialized processing for different content types.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContentProcessingUtil {

  // Dependencies for repositories
  private final ContentRepository contentRepository;
  private final CollectionRepository collectionRepository;
  private final TagRepository tagRepository;
  private final PersonRepository personRepository;
  private final LocationRepository locationRepository;

  /**
   * Convert a ContentEntity to its corresponding ContentModel based on type. This version does not
   * have collection-specific metadata (orderIndex, caption, visible). Use the overloaded version
   * that accepts CollectionContentEntity for full metadata.
   *
   * @param entity The content entity to convert
   * @return The corresponding content model
   */
  public ContentModel convertRegularContentEntityToModel(ContentEntity entity) {
    if (entity == null) {
      return null;
    }

    if (entity.getContentType() == null) {
      log.error("Unknown content type: null");
      throw new IllegalArgumentException("Unknown content type: null");
    }

    // No need to unproxy - entities are loaded directly from DAOs

    return switch (entity.getContentType()) {
      case IMAGE -> convertImageToModel((ContentImageEntity) entity, null, null);
      case TEXT -> convertTextToModel((ContentTextEntity) entity, null, null);
      case GIF -> convertGifToModel((ContentGifEntity) entity, null, null);
      case COLLECTION -> {
        log.warn(
            "COLLECTION content type not supported in convertRegularContentEntityToModel, entity id={}",
            entity.getId());
        yield null;
      }
    };
  }

  /**
   * Convert a ContentEntity to its corresponding ContentModel with join table metadata. This
   * version populates collection-specific fields (orderIndex, visible) from the join table.
   *
   * @param entity The join table entry (CollectionContentEntity) containing the content and
   *     metadata
   * @return The corresponding content model with join table metadata populated
   */
  public ContentModel convertEntityToModel(CollectionContentEntity entity) {
    if (entity == null) {
      return null;
    }

    // Load the content entity from DAO using contentId
    Long contentId = entity.getContentId();
    if (contentId == null) {
      log.error("CollectionContentEntity {} has null contentId", entity.getId());
      return null;
    }

    // Load content entity - try loading as different types based on contentType
    // First, get the base content to determine type
    Optional<ContentEntity> baseContentOpt =
        contentRepository.findAllByIds(List.of(contentId)).stream().findFirst();
    if (baseContentOpt.isEmpty()) {
      log.error(
          "Content entity {} not found for CollectionContentEntity {}", contentId, entity.getId());
      return null;
    }

    ContentEntity baseContent = baseContentOpt.get();
    ContentEntity content;

    // Load typed entity based on contentType
    switch (baseContent.getContentType()) {
      case IMAGE -> {
        Optional<ContentImageEntity> imageOpt = contentRepository.findImageById(contentId);
        content = imageOpt.orElse(null);
      }
      case TEXT -> {
        Optional<ContentTextEntity> textOpt = contentRepository.findTextById(contentId);
        content = textOpt.orElse(null);
      }
      case GIF -> {
        Optional<ContentGifEntity> gifOpt = contentRepository.findGifById(contentId);
        content = gifOpt.orElse(null);
      }
      case COLLECTION -> {
        Optional<ContentCollectionEntity> collectionOpt =
            contentRepository.findCollectionContentById(contentId);
        content = collectionOpt.orElse(null);
      }
      default -> {
        log.error(
            "Unknown content type {} for content {}", baseContent.getContentType(), contentId);
        return null;
      }
    }

    if (content == null) {
      log.error(
          "Failed to load typed content entity {} for CollectionContentEntity {}",
          contentId,
          entity.getId());
      return null;
    }

    // Use the bulk-loaded conversion method for efficiency
    return convertBulkLoadedContentToModel(content, entity);
  }

  /**
   * Convert a bulk-loaded ContentEntity to its corresponding ContentModel with join table metadata.
   * This method is optimized for bulk-loaded entities that are already properly initialized (not
   * proxies). However, it defensively resolves proxies if needed, especially for COLLECTION types
   * which may still be proxies.
   *
   * @param content The bulk-loaded content entity (should be properly typed, but may still be a
   *     proxy)
   * @param joinEntry The join table entry containing collection-specific metadata (orderIndex,
   *     visible)
   * @return The corresponding content model with join table metadata populated
   */
  public ContentModel convertBulkLoadedContentToModel(
      ContentEntity content, CollectionContentEntity joinEntry) {
    if (content == null) {
      return null;
    }

    if (joinEntry == null) {
      log.warn(
          "Join entry is null for content {}, converting without join table metadata",
          content.getId());
      return convertRegularContentEntityToModel(content);
    }

    Integer orderIndex = joinEntry.getOrderIndex();
    Boolean visible = joinEntry.getVisible();

    // For COLLECTION type, cast and convert
    if (content.getContentType() == ContentType.COLLECTION) {
      if (content instanceof ContentCollectionEntity contentCollectionEntity) {
        return convertCollectionToModel(contentCollectionEntity, joinEntry);
      } else {
        log.error(
            "Content type is COLLECTION but entity is not ContentCollectionEntity: {}",
            content.getClass());
        return null;
      }
    }

    return switch (content.getContentType()) {
      case IMAGE -> convertImageToModel((ContentImageEntity) content, orderIndex, visible);
      case TEXT -> convertTextToModel((ContentTextEntity) content, orderIndex, visible);
      case GIF -> convertGifToModel((ContentGifEntity) content, orderIndex, visible);
      case COLLECTION -> null; // handled above
    };
  }

  public static Records.Camera cameraEntityToCameraModel(ContentCameraEntity entity) {
    return new Records.Camera(entity.getId(), entity.getCameraName());
  }

  public static Records.Lens lensEntityToLensModel(ContentLensEntity entity) {
    return new Records.Lens(entity.getId(), entity.getLensName());
  }

  /**
   * Convert a ContentImageEntity to a ContentModels.Image. Public method for use by other utilities
   * (e.g., CollectionProcessingUtil). orderIndex and visible are null when the image is not fetched
   * in the context of a specific collection.
   *
   * @param entity The image content entity to convert
   * @return The corresponding image content model
   */
  public ContentModels.Image convertImageEntityToModel(ContentImageEntity entity) {
    return convertImageToModel(entity, null, null);
  }

  /**
   * Batch-convert a list of ContentImageEntity to models using pre-fetched related data. Eliminates
   * N+1 queries by batch-loading tags, people, and locations for all images in 3 queries total.
   *
   * @param entities The image entities to convert
   * @return List of converted image models
   */
  public List<ContentModels.Image> batchConvertImageEntitiesToModels(
      List<ContentImageEntity> entities) {
    if (entities == null || entities.isEmpty()) {
      return new ArrayList<>();
    }

    List<Long> contentIds = entities.stream().map(ContentImageEntity::getId).toList();

    List<Long> locationIds =
        entities.stream()
            .map(ContentImageEntity::getLocationId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

    Map<Long, List<TagEntity>> tagsByContentId = tagRepository.findTagsByContentIds(contentIds);
    Map<Long, List<ContentPersonEntity>> peopleByContentId =
        personRepository.findPeopleByContentIds(contentIds);
    Map<Long, LocationEntity> locationsById = locationRepository.findByIds(locationIds);

    return entities.stream()
        .map(
            entity ->
                buildImageModelWithBatchData(
                    entity, null, null, tagsByContentId, peopleByContentId, locationsById))
        .toList();
  }

  /**
   * Build an image model using pre-fetched batch data instead of per-image queries.
   *
   * @param entity The image entity to convert
   * @param orderIndex Position within the parent collection (null outside collection context)
   * @param visible Visibility in the parent collection (null outside collection context)
   * @param tagsByContentId Pre-loaded tags grouped by content ID
   * @param peopleByContentId Pre-loaded people grouped by content ID
   * @param locationsById Pre-loaded locations keyed by location ID
   * @return The corresponding image content model
   */
  ContentModels.Image buildImageModelWithBatchData(
      ContentImageEntity entity,
      Integer orderIndex,
      Boolean visible,
      Map<Long, List<TagEntity>> tagsByContentId,
      Map<Long, List<ContentPersonEntity>> peopleByContentId,
      Map<Long, LocationEntity> locationsById) {
    if (entity == null) {
      return null;
    }

    Records.Location location = null;
    if (entity.getLocationId() != null) {
      LocationEntity locationEntity = locationsById.get(entity.getLocationId());
      if (locationEntity != null) {
        location =
            new Records.Location(
                locationEntity.getId(), locationEntity.getLocationName(), locationEntity.getSlug());
      }
    }

    Set<TagEntity> tags = new HashSet<>(tagsByContentId.getOrDefault(entity.getId(), List.of()));
    Set<ContentPersonEntity> people =
        new HashSet<>(peopleByContentId.getOrDefault(entity.getId(), List.of()));

    return new ContentModels.Image(
        entity.getId(),
        entity.getContentType(),
        entity.getTitle(),
        null,
        entity.getImageUrlWeb(),
        orderIndex,
        visible,
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getImageWidth(),
        entity.getImageHeight(),
        entity.getIso(),
        entity.getAuthor(),
        entity.getRating(),
        entity.getFStop(),
        entity.getLens() != null ? lensEntityToLensModel(entity.getLens()) : null,
        entity.getBlackAndWhite(),
        entity.getIsFilm(),
        entity.getFilmType() != null ? entity.getFilmType().getDisplayName() : null,
        entity.getFilmFormat(),
        entity.getShutterSpeed(),
        entity.getCamera() != null ? cameraEntityToCameraModel(entity.getCamera()) : null,
        entity.getFocalLength(),
        location,
        entity.getCaptureDate(),
        convertTagsToModels(tags),
        convertPeopleToModels(people),
        new ArrayList<>());
  }

  /**
   * Convert a ContentImageEntity to a ContentModels.Image record.
   *
   * @param entity The image content entity to convert
   * @param orderIndex Position within the parent collection (null outside collection context)
   * @param visible Visibility in the parent collection (null outside collection context)
   * @return The corresponding image content model
   */
  private ContentModels.Image convertImageToModel(
      ContentImageEntity entity, Integer orderIndex, Boolean visible) {
    if (entity == null) {
      return null;
    }

    // Load tags from database if not already loaded
    if (entity.getTags() == null || entity.getTags().isEmpty()) {
      loadContentTags(entity);
    }

    // Load people from database if not already loaded
    if (entity.getPeople() == null || entity.getPeople().isEmpty()) {
      loadContentPeople(entity);
    }

    Records.Location location = null;
    if (entity.getLocationId() != null) {
      LocationEntity locationEntity =
          locationRepository.findById(entity.getLocationId()).orElse(null);
      if (locationEntity != null) {
        location =
            new Records.Location(
                locationEntity.getId(), locationEntity.getLocationName(), locationEntity.getSlug());
      }
    }

    return new ContentModels.Image(
        entity.getId(),
        entity.getContentType(),
        entity.getTitle(),
        null,
        entity.getImageUrlWeb(),
        orderIndex,
        visible,
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getImageWidth(),
        entity.getImageHeight(),
        entity.getIso(),
        entity.getAuthor(),
        entity.getRating(),
        entity.getFStop(),
        entity.getLens() != null ? lensEntityToLensModel(entity.getLens()) : null,
        entity.getBlackAndWhite(),
        entity.getIsFilm(),
        entity.getFilmType() != null ? entity.getFilmType().getDisplayName() : null,
        entity.getFilmFormat(),
        entity.getShutterSpeed(),
        entity.getCamera() != null ? cameraEntityToCameraModel(entity.getCamera()) : null,
        entity.getFocalLength(),
        location,
        entity.getCaptureDate(),
        convertTagsToModels(entity.getTags()),
        convertPeopleToModels(entity.getPeople()),
        new ArrayList<>());
  }

  /**
   * Convert a ContentTextEntity to a ContentModels.Text record.
   *
   * @param entity The text content entity to convert
   * @param orderIndex Position within the parent collection (null outside collection context)
   * @param visible Visibility in the parent collection (null outside collection context)
   * @return The corresponding text content model
   */
  private ContentModels.Text convertTextToModel(
      ContentTextEntity entity, Integer orderIndex, Boolean visible) {
    if (entity == null) {
      return null;
    }

    return new ContentModels.Text(
        entity.getId(),
        entity.getContentType(),
        null,
        null,
        null,
        orderIndex,
        visible,
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getTextContent(),
        entity.getFormatType());
  }

  /**
   * Convert a ContentGifEntity to a ContentModels.Gif record.
   *
   * @param entity The gif content entity to convert
   * @param orderIndex Position within the parent collection (null outside collection context)
   * @param visible Visibility in the parent collection (null outside collection context)
   * @return The corresponding gif content model
   */
  private ContentModels.Gif convertGifToModel(
      ContentGifEntity entity, Integer orderIndex, Boolean visible) {
    if (entity == null) {
      return null;
    }

    // Load tags from database if not already loaded
    if (entity.getTags() == null || entity.getTags().isEmpty()) {
      loadContentTags(entity);
    }

    return new ContentModels.Gif(
        entity.getId(),
        entity.getContentType(),
        entity.getTitle(),
        null,
        null,
        orderIndex,
        visible,
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getGifUrl(),
        entity.getThumbnailUrl(),
        entity.getWidth(),
        entity.getHeight(),
        entity.getAuthor(),
        entity.getCreateDate(),
        convertTagsToModels(entity.getTags()));
  }

  /**
   * Convert a ContentCollectionEntity to a ContentModels.Collection. Extracts data from the
   * referenced collection and populates join table metadata (orderIndex, visible) from joinEntry.
   *
   * @param contentEntity The ContentCollectionEntity referencing another collection
   * @param joinEntry The join table entry with collection-specific metadata
   * @return The converted collection content model
   */
  private ContentModels.Collection convertCollectionToModel(
      ContentCollectionEntity contentEntity, CollectionContentEntity joinEntry) {
    if (contentEntity == null) {
      return null;
    }

    CollectionEntity referencedCollection = contentEntity.getReferencedCollection();
    if (referencedCollection == null) {
      log.error("ContentCollectionEntity {} has null referencedCollection", contentEntity.getId());
      return null;
    }

    // Load full collection data if only the ID is present (from bulk query)
    Long referencedCollectionId = referencedCollection.getId();
    if (referencedCollectionId != null && referencedCollection.getTitle() == null) {
      log.debug(
          "Loading full collection data for referencedCollectionId: {}", referencedCollectionId);
      referencedCollection =
          collectionRepository.findById(referencedCollectionId).orElse(referencedCollection);
    }

    ContentModels.Image coverImage = null;
    if (referencedCollection.getCoverImageId() != null) {
      ContentImageEntity coverImageEntity =
          contentRepository.findImageById(referencedCollection.getCoverImageId()).orElse(null);
      if (coverImageEntity != null) {
        coverImage = convertImageEntityToModel(coverImageEntity);
      }
    }

    return new ContentModels.Collection(
        contentEntity.getId(),
        contentEntity.getContentType(),
        referencedCollection.getTitle(),
        referencedCollection.getDescription(),
        null,
        joinEntry != null ? joinEntry.getOrderIndex() : null,
        joinEntry != null ? joinEntry.getVisible() : null,
        contentEntity.getCreatedAt(),
        contentEntity.getUpdatedAt(),
        referencedCollection.getId(),
        referencedCollection.getSlug(),
        referencedCollection.getType(),
        coverImage);
  }

  // =============================================================================
  // IMAGE UPDATE HELPERS (following the pattern from CollectionProcessingUtil)
  // =============================================================================

  /**
   * Handle collection visibility and orderIndex updates for an image. This method updates the
   * 'visible' flag and 'orderIndex' for the content entry in the current collection. Note: For
   * cross-collection updates (updating the same image in multiple collections), you would need to
   * add a repository method to find content by fileIdentifier. For now, this handles visibility and
   * orderIndex for the current image/collection relationship.
   *
   * <p>Typically used for single-image updates where we're adjusting the orderIndex (drag-and-drop
   * reordering) or toggling visibility within a specific collection. The API call is very
   * lightweight: ContentImageUpdateRequest with a single
   * CollectionUpdate.prev(Records.ChildCollection) containing orderIndex/visible.
   *
   * @param image The image entity being updated
   * @param collectionUpdates List of collection updates containing visibility and orderIndex
   *     information
   */
  public void handleContentChildCollectionUpdates(
      ContentImageEntity image, List<Records.ChildCollection> collectionUpdates) {
    if (collectionUpdates == null || collectionUpdates.isEmpty()) {
      return;
    }

    // Update visibility and orderIndex for the current image if its collection is
    // in the updates
    for (Records.ChildCollection collectionUpdate : collectionUpdates) {
      if (collectionUpdate.collectionId() != null) {
        Long collectionId = collectionUpdate.collectionId();
        Integer orderIndex = collectionUpdate.orderIndex();
        Boolean visible = collectionUpdate.visible();

        // Find the join table entry for this image in this collection
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

        // Update Order Index if provided
        if (orderIndex != null) {
          collectionRepository.updateContentOrderIndex(
              joinEntry.getId(), // Use join table entry ID, not collection ID
              orderIndex);
          log.info(
              "Updated orderIndex for image {} in collection {} to {}",
              image.getId(),
              collectionId,
              orderIndex);
          updated = true;
        }

        // Update visibility if provided
        if (visible != null) {
          collectionRepository.updateContentVisible(
              joinEntry.getId(), // Use join table entry ID, not collection ID
              visible);
          log.info(
              "Updated visibility for image {} in collection {} to {}",
              image.getId(),
              collectionId,
              visible);
          updated = true;
        }

        if (updated) {
          break; // Only update once for the matching collection
        }
      }
    }
  }

  // =============================================================================
  // TAG AND PEOPLE UPDATE HELPERS (Shared utility methods)
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
      // Associate tags (deduplicate names to avoid redundant DB lookups)
      if (tagNames != null && !tagNames.isEmpty()) {
        Set<Long> tagIds = new LinkedHashSet<>();
        Set<String> seenTags = new HashSet<>();
        for (String tagName : tagNames) {
          if (!seenTags.add(tagName.toLowerCase())) {
            continue;
          }
          var existing = tagRepository.findByTagNameIgnoreCase(tagName);
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

      // Associate people (deduplicate names to avoid redundant DB lookups)
      if (peopleNames != null && !peopleNames.isEmpty()) {
        Set<Long> personIds = new LinkedHashSet<>();
        Set<String> seenPeople = new HashSet<>();
        for (String personName : peopleNames) {
          if (!seenPeople.add(personName.toLowerCase())) {
            continue;
          }
          var existing = personRepository.findByPersonNameIgnoreCase(personName);
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
   * Update tags on an entity using the prev/new/remove pattern. This is a shared utility method
   * used by both Collection and Content update operations.
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

    // Remove tags if specified
    if (tagUpdate.remove() != null && !tagUpdate.remove().isEmpty()) {
      tags.removeIf(tag -> tagUpdate.remove().contains(tag.getId()));
    }

    // Add existing tags by ID (prev)
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

    // Create and add new tags by name (newValue) with optional tracking
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
   * Update people on an entity using the prev/new/remove pattern. This is a shared utility method
   * used by both Collection and Content update operations.
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

    // Remove people if specified
    if (personUpdate.remove() != null && !personUpdate.remove().isEmpty()) {
      people.removeIf(person -> personUpdate.remove().contains(person.getId()));
    }

    // Add existing people by ID (prev)
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

    // Create and add new people by name (newValue) with optional tracking
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

  // =============================================================================
  // TAG AND PEOPLE MODEL CONVERSION HELPERS
  // =============================================================================

  /**
   * Load tags for a content entity from the database. Populates the entity's tags set with
   * TagEntity objects.
   *
   * @param entity The content entity (ContentImageEntity or ContentGifEntity)
   */
  private void loadContentTags(ContentEntity entity) {
    if (entity == null || entity.getId() == null) {
      return;
    }

    // Load tags from database
    List<TagEntity> tagEntities = tagRepository.findContentTags(entity.getId());
    Set<TagEntity> tagSet = new HashSet<>(tagEntities);

    if (entity instanceof ContentImageEntity imageEntity) {
      imageEntity.setTags(tagSet);
    } else if (entity instanceof ContentGifEntity gifEntity) {
      gifEntity.setTags(tagSet);
    }
  }

  /**
   * Load people for a content entity from the database. Populates the entity's people set with
   * ContentPersonEntity objects.
   *
   * @param entity The content entity (ContentImageEntity)
   */
  private void loadContentPeople(ContentEntity entity) {
    if (entity == null || entity.getId() == null) {
      return;
    }

    // Load people from database using ContentPersonDao
    List<ContentPersonEntity> personEntities = personRepository.findContentPeople(entity.getId());

    // Convert to set and populate the entity's people set
    Set<ContentPersonEntity> contentPeopleEntities = new HashSet<>(personEntities);

    // Set people on the entity based on its type
    if (entity instanceof ContentImageEntity imageEntity) {
      imageEntity.setPeople(contentPeopleEntities);
    }
  }

  /**
   * Convert a set of TagEntity to a sorted list of ContentTagModel. Returns empty list if tags is
   * null or empty.
   *
   * @param tags Set of tag entities to convert
   * @return Sorted list of tag models (alphabetically by name)
   */
  public List<Records.Tag> convertTagsToModels(Set<TagEntity> tags) {
    if (tags == null || tags.isEmpty()) {
      return new ArrayList<>();
    }
    return tags.stream()
        .map(tag -> new Records.Tag(tag.getId(), tag.getTagName(), tag.getSlug()))
        .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
        .collect(Collectors.toList());
  }

  /**
   * Convert a set of ContentPersonEntity to a sorted list of Records.Person. Returns empty list if
   * people is null or empty.
   *
   * @param people Set of person entities to convert
   * @return Sorted list of person models (alphabetically by name)
   */
  public List<Records.Person> convertPeopleToModels(Set<ContentPersonEntity> people) {
    if (people == null || people.isEmpty()) {
      return new ArrayList<>();
    }
    return people.stream()
        .map(person -> new Records.Person(person.getId(), person.getPersonName(), person.getSlug()))
        .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
        .collect(Collectors.toList());
  }
}
