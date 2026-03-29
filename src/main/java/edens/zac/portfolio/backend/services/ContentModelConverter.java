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
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.types.ContentType;
import java.util.ArrayList;
import java.util.HashSet;
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
 * Handles all entity-to-model conversion for content types. Centralizes the mapping logic that was
 * previously spread across ContentProcessingUtil.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class ContentModelConverter {

  private final ContentRepository contentRepository;
  private final CollectionRepository collectionRepository;
  private final TagRepository tagRepository;
  private final PersonRepository personRepository;
  private final LocationRepository locationRepository;

  // =============================================================================
  // PUBLIC CONVERSION ENTRY POINTS
  // =============================================================================

  /**
   * Convert a ContentEntity to its corresponding ContentModel based on type. This version does not
   * have collection-specific metadata (orderIndex, caption, visible).
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

    return switch (entity.getContentType()) {
      case IMAGE -> convertImageToModel((ContentImageEntity) entity, null, null);
      case TEXT -> convertTextToModel((ContentTextEntity) entity, null, null);
      case GIF -> convertGifToModel((ContentGifEntity) entity, null, null);
      case COLLECTION -> {
        log.warn(
            "COLLECTION content type not supported in convertRegularContentEntityToModel,"
                + " entity id={}",
            entity.getId());
        yield null;
      }
    };
  }

  /**
   * Convert a ContentEntity to its corresponding ContentModel with join table metadata. Loads the
   * typed content entity from the repository and populates collection-specific fields.
   *
   * @param entity The join table entry (CollectionContentEntity) containing content and metadata
   * @return The corresponding content model with join table metadata populated
   */
  public ContentModel convertEntityToModel(CollectionContentEntity entity) {
    if (entity == null) {
      return null;
    }

    Long contentId = entity.getContentId();
    if (contentId == null) {
      log.error("CollectionContentEntity {} has null contentId", entity.getId());
      return null;
    }

    Optional<ContentEntity> baseContentOpt =
        contentRepository.findAllByIds(List.of(contentId)).stream().findFirst();
    if (baseContentOpt.isEmpty()) {
      log.error(
          "Content entity {} not found for CollectionContentEntity {}", contentId, entity.getId());
      return null;
    }

    ContentEntity baseContent = baseContentOpt.get();
    ContentEntity content =
        switch (baseContent.getContentType()) {
          case IMAGE -> contentRepository.findImageById(contentId).orElse(null);
          case TEXT -> contentRepository.findTextById(contentId).orElse(null);
          case GIF -> contentRepository.findGifById(contentId).orElse(null);
          case COLLECTION -> contentRepository.findCollectionContentById(contentId).orElse(null);
        };

    if (content == null) {
      log.error(
          "Failed to load typed content entity {} for CollectionContentEntity {}",
          contentId,
          entity.getId());
      return null;
    }

    return convertBulkLoadedContentToModel(content, entity);
  }

  /**
   * Convert a bulk-loaded ContentEntity to its corresponding ContentModel with join table metadata.
   * Optimized for entities that are already properly typed from batch queries.
   *
   * @param content The bulk-loaded content entity
   * @param joinEntry The join table entry with collection-specific metadata (orderIndex, visible)
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

  // =============================================================================
  // STATIC ENTITY-TO-RECORD CONVERTERS
  // =============================================================================

  /** Convert a ContentCameraEntity to a Records.Camera model. */
  public static Records.Camera cameraEntityToCameraModel(ContentCameraEntity entity) {
    return new Records.Camera(entity.getId(), entity.getCameraName());
  }

  /** Convert a ContentLensEntity to a Records.Lens model. */
  public static Records.Lens lensEntityToLensModel(ContentLensEntity entity) {
    return new Records.Lens(entity.getId(), entity.getLensName());
  }

  // =============================================================================
  // SINGLE-ENTITY CONVERSION (public entry points)
  // =============================================================================

  /**
   * Convert a single ContentImageEntity to a ContentModels.Image. Convenience method for callers
   * that don't have collection context (orderIndex/visible are null).
   *
   * @param entity The image content entity to convert
   * @return The corresponding image content model
   */
  public ContentModels.Image convertImageEntityToModel(ContentImageEntity entity) {
    return convertImageToModel(entity, null, null);
  }

  // =============================================================================
  // BATCH CONVERSION
  // =============================================================================

  /**
   * Batch-convert a list of ContentImageEntity to models using pre-fetched related data. Eliminates
   * N+1 queries by batch-loading tags, people, and locations in 3 queries total.
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
   * Build an image model using pre-fetched batch data instead of per-image queries. Unified
   * conversion path used by both single-entity and batch-entity flows.
   *
   * @param entity The image entity to convert
   * @param orderIndex Position within the parent collection (null outside collection context)
   * @param visible Visibility in the parent collection (null outside collection context)
   * @param tagsByContentId Pre-loaded tags grouped by content ID
   * @param peopleByContentId Pre-loaded people grouped by content ID
   * @param locationsById Pre-loaded locations keyed by location ID
   * @return The corresponding image content model
   */
  public ContentModels.Image buildImageModelWithBatchData(
      ContentImageEntity entity,
      Integer orderIndex,
      Boolean visible,
      Map<Long, List<TagEntity>> tagsByContentId,
      Map<Long, List<ContentPersonEntity>> peopleByContentId,
      Map<Long, LocationEntity> locationsById) {
    if (entity == null) {
      return null;
    }

    Records.Location location = resolveLocation(entity.getLocationId(), locationsById);

    Set<TagEntity> tags = new HashSet<>(tagsByContentId.getOrDefault(entity.getId(), List.of()));
    Set<ContentPersonEntity> people =
        new HashSet<>(peopleByContentId.getOrDefault(entity.getId(), List.of()));

    return buildImageRecord(entity, orderIndex, visible, location, tags, people);
  }

  // =============================================================================
  // TAG AND PEOPLE MODEL CONVERSION HELPERS
  // =============================================================================

  /**
   * Convert a set of TagEntity to a sorted list of Records.Tag. Returns empty list if tags is null
   * or empty.
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

  // =============================================================================
  // PRIVATE CONVERSION METHODS
  // =============================================================================

  /**
   * Convert a ContentImageEntity to a ContentModels.Image record. Loads tags, people, and location
   * per-entity (use buildImageModelWithBatchData for batch contexts).
   */
  private ContentModels.Image convertImageToModel(
      ContentImageEntity entity, Integer orderIndex, Boolean visible) {
    if (entity == null) {
      return null;
    }

    // Load tags from database if not already loaded
    Set<TagEntity> tags = entity.getTags();
    if (tags == null || tags.isEmpty()) {
      List<TagEntity> tagEntities = tagRepository.findContentTags(entity.getId());
      tags = new HashSet<>(tagEntities);
    }

    // Load people from database if not already loaded
    Set<ContentPersonEntity> people = entity.getPeople();
    if (people == null || people.isEmpty()) {
      List<ContentPersonEntity> personEntities = personRepository.findContentPeople(entity.getId());
      people = new HashSet<>(personEntities);
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

    return buildImageRecord(entity, orderIndex, visible, location, tags, people);
  }

  /**
   * Shared image record builder used by both convertImageToModel and buildImageModelWithBatchData.
   */
  private ContentModels.Image buildImageRecord(
      ContentImageEntity entity,
      Integer orderIndex,
      Boolean visible,
      Records.Location location,
      Set<TagEntity> tags,
      Set<ContentPersonEntity> people) {
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

  /** Convert a ContentTextEntity to a ContentModels.Text record. */
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

  /** Convert a ContentGifEntity to a ContentModels.Gif record. */
  private ContentModels.Gif convertGifToModel(
      ContentGifEntity entity, Integer orderIndex, Boolean visible) {
    if (entity == null) {
      return null;
    }

    // Load tags from database if not already loaded
    Set<TagEntity> tags = entity.getTags();
    if (tags == null || tags.isEmpty()) {
      List<TagEntity> tagEntities = tagRepository.findContentTags(entity.getId());
      tags = new HashSet<>(tagEntities);
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
        convertTagsToModels(tags));
  }

  /**
   * Convert a ContentCollectionEntity to a ContentModels.Collection. Extracts data from the
   * referenced collection and populates join table metadata.
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
  // PRIVATE HELPERS
  // =============================================================================

  /** Resolve a location from a pre-loaded map, returning null if not found. */
  private Records.Location resolveLocation(
      Long locationId, Map<Long, LocationEntity> locationsById) {
    if (locationId == null) {
      return null;
    }
    LocationEntity locationEntity = locationsById.get(locationId);
    if (locationEntity == null) {
      return null;
    }
    return new Records.Location(
        locationEntity.getId(), locationEntity.getLocationName(), locationEntity.getSlug());
  }
}
