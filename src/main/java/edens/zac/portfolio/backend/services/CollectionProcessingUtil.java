package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.config.DefaultValues;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.LocationRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import edens.zac.portfolio.backend.entity.LocationEntity;
import edens.zac.portfolio.backend.entity.TagEntity;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.ContentType;
import edens.zac.portfolio.backend.types.DisplayMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class CollectionProcessingUtil {

  private final CollectionRepository collectionRepository;
  private final ContentRepository contentRepository;
  private final ContentModelConverter contentModelConverter;
  private final ContentMutationUtil contentMutationUtil;
  private final LocationRepository locationRepository;
  private final TagRepository tagRepository;
  private final PersonRepository personRepository;

  // =============================================================================
  // ENTITY-TO-MODEL CONVERSION
  // =============================================================================

  /**
   * Convert a single CollectionEntity to a CollectionModel with basic information. Delegates to
   * batch conversion for consistency (same code path, just for a list of 1).
   *
   * @param entity The entity to convert
   * @return The converted model
   */
  public CollectionModel convertToBasicModel(CollectionEntity entity) {
    if (entity == null) {
      return null;
    }
    List<CollectionModel> results = batchConvertToBasicModels(List.of(entity));
    return results.isEmpty() ? null : results.getFirst();
  }

  /**
   * Batch-convert a list of CollectionEntity to basic models. Pre-fetches all locations and cover
   * images in batch queries to avoid N+1. Each collection with a cover image would otherwise
   * trigger 5 individual queries (location + cover image + cover's tags/people/location).
   *
   * @param entities The collection entities to convert
   * @return List of converted collection models with locations and cover images populated
   */
  public List<CollectionModel> batchConvertToBasicModels(List<CollectionEntity> entities) {
    if (entities == null || entities.isEmpty()) {
      return new ArrayList<>();
    }

    // Batch-load all locations referenced by these collections (many-to-many)
    List<Long> collectionIds = entities.stream().map(CollectionEntity::getId).toList();
    Map<Long, List<LocationEntity>> locationsByCollectionId =
        locationRepository.findLocationsByCollectionIds(collectionIds);

    // Batch-load all cover images
    List<Long> coverImageIds =
        entities.stream()
            .map(CollectionEntity::getCoverImageId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    Map<Long, ContentImageEntity> coverImagesById = new HashMap<>();
    if (!coverImageIds.isEmpty()) {
      List<ContentImageEntity> coverImages = contentRepository.findImagesByIds(coverImageIds);
      coverImages.forEach(img -> coverImagesById.put(img.getId(), img));
    }

    // Batch-load tags, people, and locations for all cover images
    List<Long> coverContentIds = new ArrayList<>(coverImagesById.keySet());
    Map<Long, List<TagEntity>> tagsByContentId =
        tagRepository.findTagsByContentIds(coverContentIds);
    Map<Long, List<ContentPersonEntity>> peopleByContentId =
        personRepository.findPeopleByContentIds(coverContentIds);
    Map<Long, List<LocationEntity>> coverLocationsByContentId =
        locationRepository.findLocationsByContentIds(coverContentIds);

    // Convert each entity using pre-loaded data
    return entities.stream()
        .map(
            entity ->
                buildBasicModel(
                    entity,
                    locationsByCollectionId,
                    coverImagesById,
                    tagsByContentId,
                    peopleByContentId,
                    coverLocationsByContentId))
        .collect(Collectors.toList());
  }

  /** Build a single CollectionModel from pre-loaded batch data. */
  private CollectionModel buildBasicModel(
      CollectionEntity entity,
      Map<Long, List<LocationEntity>> locationsByCollectionId,
      Map<Long, ContentImageEntity> coverImagesById,
      Map<Long, List<TagEntity>> tagsByContentId,
      Map<Long, List<ContentPersonEntity>> peopleByContentId,
      Map<Long, List<LocationEntity>> coverLocationsByContentId) {
    CollectionModel model = new CollectionModel();
    model.setId(entity.getId());
    model.setType(entity.getType());
    model.setTitle(entity.getTitle());
    model.setSlug(entity.getSlug());
    model.setDescription(entity.getDescription());

    List<LocationEntity> collectionLocations =
        locationsByCollectionId.getOrDefault(entity.getId(), List.of());
    model.setLocations(
        collectionLocations.stream()
            .map(loc -> new Records.Location(loc.getId(), loc.getLocationName(), loc.getSlug()))
            .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
            .collect(Collectors.toList()));

    model.setCollectionDate(entity.getCollectionDate());
    model.setVisible(entity.getVisible());

    // Populate cover image from pre-loaded data
    if (entity.getCoverImageId() != null) {
      ContentImageEntity coverImage = coverImagesById.get(entity.getCoverImageId());
      if (coverImage != null) {
        ContentModels.Image coverImageModel =
            contentModelConverter.buildImageModelWithBatchData(
                coverImage,
                null,
                null,
                tagsByContentId,
                peopleByContentId,
                coverLocationsByContentId);
        model.setCoverImage(coverImageModel);
      }
    }

    model.setIsPasswordProtected(entity.getPasswordHash() != null);
    model.setCreatedAt(entity.getCreatedAt());
    model.setUpdatedAt(entity.getUpdatedAt());
    DisplayMode mode = entity.getDisplayMode();
    if (mode == null) {
      mode =
          entity.getType() == CollectionType.BLOG ? DisplayMode.CHRONOLOGICAL : DisplayMode.ORDERED;
    }
    model.setDisplayMode(mode);
    model.setContentCount(entity.getTotalContent());
    model.setContentPerPage(entity.getContentPerPage());
    model.setTotalPages(entity.getTotalPages());
    model.setCurrentPage(0);
    model.setRowsWide(entity.getRowsWide());
    return model;
  }

  /**
   * Convert a CollectionEntity and a List of CollectionContentEntity to a CollectionModel. Uses
   * bulk loading of ContentEntity instances to avoid proxy issues and improve performance.
   *
   * @param entity The entity to convert
   * @param collectionContentList The list of join table entries (collection-content associations)
   * @param currentPage The current page number (0-based)
   * @param pageSize The page size
   * @param totalElements The total number of elements
   * @return The converted model
   */
  public CollectionModel convertToModel(
      CollectionEntity entity,
      List<CollectionContentEntity> collectionContentList,
      int currentPage,
      int pageSize,
      long totalElements) {
    if (entity == null) {
      return null;
    }

    CollectionModel model = convertToBasicModel(entity);

    // Extract content IDs from join table entries
    List<Long> contentIds =
        collectionContentList.stream()
            .map(CollectionContentEntity::getContentId)
            .filter(Objects::nonNull)
            .toList();

    // Bulk fetch all ContentEntity instances in one query (properly loads all
    // subclasses)
    final Map<Long, ContentEntity> contentMap;
    if (!contentIds.isEmpty()) {
      List<ContentEntity> contentEntities = contentRepository.findAllByIds(contentIds);
      contentMap =
          contentEntities.stream().collect(Collectors.toMap(ContentEntity::getId, ce -> ce));
    } else {
      contentMap = new HashMap<>();
    }

    // Batch-load tags, people, and locations for all IMAGE content to avoid N+1 queries
    List<Long> imageContentIds =
        contentMap.values().stream()
            .filter(c -> c.getContentType() == ContentType.IMAGE)
            .map(ContentEntity::getId)
            .toList();
    Map<Long, List<TagEntity>> tagsByContentId =
        tagRepository.findTagsByContentIds(imageContentIds);
    Map<Long, List<ContentPersonEntity>> peopleByContentId =
        personRepository.findPeopleByContentIds(imageContentIds);
    Map<Long, List<LocationEntity>> locationsByContentId =
        locationRepository.findLocationsByContentIds(imageContentIds);

    // Convert join table entries to content models with collection-specific metadata
    List<ContentModel> contents =
        collectionContentList.stream()
            .filter(Objects::nonNull)
            .map(
                cc -> {
                  ContentEntity content = contentMap.get(cc.getContentId());
                  if (content == null) {
                    log.warn(
                        "Content entity {} not found in bulk load for collection {}",
                        cc.getContentId(),
                        entity.getId());
                    return null;
                  }
                  // For IMAGE content, use batch-loaded data to avoid N+1 queries
                  if (content.getContentType() == ContentType.IMAGE
                      && content instanceof ContentImageEntity imageEntity) {
                    return contentModelConverter.buildImageModelWithBatchData(
                        imageEntity,
                        cc.getOrderIndex(),
                        cc.getVisible(),
                        tagsByContentId,
                        peopleByContentId,
                        locationsByContentId);
                  }
                  // For other content types, use the standard conversion
                  return contentModelConverter.convertBulkLoadedContentToModel(content, cc);
                })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    model.setContent(contents);

    // Set pagination metadata
    int totalPages = pageSize > 0 ? (int) Math.ceil((double) totalElements / pageSize) : 0;
    model.setCurrentPage(currentPage);
    model.setTotalPages(totalPages);
    model.setContentCount((int) totalElements);
    model.setContentPerPage(pageSize);
    return model;
  }

  /**
   * Convert a CollectionEntity to a fully populated CollectionModel with all content and child
   * collection metadata. Fetches all join entries (no pagination), batch-loads content, and
   * populates child collections on image content.
   *
   * @param entity The collection entity to convert
   * @return Fully populated collection model
   */
  public CollectionModel convertToFullModel(CollectionEntity entity) {
    List<CollectionContentEntity> joinEntries =
        collectionRepository.findContentByCollectionIdOrderByOrderIndex(entity.getId());

    if (joinEntries.isEmpty()) {
      CollectionModel model = convertToBasicModel(entity);
      model.setContent(Collections.emptyList());
      return model;
    }

    CollectionModel model = convertToModel(entity, joinEntries, 0, 0, joinEntries.size());
    populateCollectionsOnContent(model);
    return model;
  }

  /**
   * Populate child collection metadata on image content items. For each image in the model, finds
   * all collections it belongs to and attaches them as ChildCollection records. Uses batch queries
   * to avoid N+1.
   *
   * @param model The CollectionModel with content items to populate
   */
  public void populateCollectionsOnContent(CollectionModel model) {
    if (model == null || model.getContent() == null || model.getContent().isEmpty()) {
      return;
    }

    List<Long> contentIds =
        model.getContent().stream()
            .map(ContentModel::id)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

    if (contentIds.isEmpty()) {
      return;
    }

    // Batch-load all collections for all content items
    List<CollectionContentEntity> allCollections =
        collectionRepository.findContentByContentIdsIn(contentIds);
    Map<Long, List<CollectionContentEntity>> collectionsByContentId =
        allCollections.stream()
            .collect(Collectors.groupingBy(CollectionContentEntity::getContentId));

    // Batch-load collection entities
    List<Long> collectionIds =
        allCollections.stream()
            .map(CollectionContentEntity::getCollectionId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

    Map<Long, CollectionEntity> collectionsById =
        collectionIds.isEmpty()
            ? Collections.emptyMap()
            : collectionRepository.findByIds(collectionIds).stream()
                .collect(Collectors.toMap(CollectionEntity::getId, c -> c));

    // Batch-load cover image URLs
    List<Long> coverImageIds =
        collectionsById.values().stream()
            .map(CollectionEntity::getCoverImageId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

    Map<Long, String> coverImageUrlsById =
        coverImageIds.isEmpty()
            ? Collections.emptyMap()
            : contentRepository.findImagesByIds(coverImageIds).stream()
                .collect(
                    Collectors.toMap(
                        ContentImageEntity::getId, ContentImageEntity::getImageUrlWeb));

    // Populate collections on image content (records are immutable -- use withCollections)
    List<ContentModel> contents =
        model.getContent().stream()
            .map(
                content -> {
                  if (content instanceof ContentModels.Image imageModel) {
                    Long contentId = content.id();
                    List<CollectionContentEntity> contentCollections =
                        collectionsByContentId.getOrDefault(contentId, Collections.emptyList());
                    List<Records.ChildCollection> childCollections =
                        contentCollections.stream()
                            .map(
                                joinEntry ->
                                    convertToChildCollection(
                                        joinEntry, collectionsById, coverImageUrlsById))
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                    return (ContentModel) imageModel.withCollections(childCollections);
                  }
                  return content;
                })
            .collect(Collectors.toList());

    model.setContent(contents);
  }

  /**
   * Convert a join table entry to a ChildCollection record using pre-loaded data.
   *
   * @param joinEntry The join table entry
   * @param collectionsById Map of collection ID to CollectionEntity (pre-loaded)
   * @param coverImageUrlsById Map of cover image ID to image URL (pre-loaded)
   * @return The ChildCollection model, or null if collection not found
   */
  private Records.ChildCollection convertToChildCollection(
      CollectionContentEntity joinEntry,
      Map<Long, CollectionEntity> collectionsById,
      Map<Long, String> coverImageUrlsById) {
    if (joinEntry == null || joinEntry.getCollectionId() == null) {
      return null;
    }

    CollectionEntity collection = collectionsById.get(joinEntry.getCollectionId());
    if (collection == null) {
      log.warn(
          "Collection {} not found in pre-loaded map for join entry", joinEntry.getCollectionId());
      return null;
    }

    final String coverImageUrl =
        collection.getCoverImageId() != null
            ? coverImageUrlsById.get(collection.getCoverImageId())
            : null;

    return new Records.ChildCollection(
        collection.getId(),
        collection.getTitle(),
        collection.getSlug(),
        coverImageUrl,
        joinEntry.getVisible(),
        null);
  }

  // =============================================================================
  // DTO-TO-ENTITY CONVERSION
  // =============================================================================

  /**
   * Create a CollectionEntity from a Create request. Required fields: type, title. Optional fields:
   * description, locationId/locationName, collectionDate — use defaults when not provided.
   */
  public CollectionEntity toEntity(CollectionRequests.Create request, int defaultPageSize) {
    if (request == null) {
      throw new IllegalArgumentException("Create request cannot be null");
    }
    CollectionEntity entity = new CollectionEntity();
    entity.setType(request.type());
    entity.setTitle(request.title());
    String baseSlug = generateSlug(request.title());
    String uniqueSlug = validateAndEnsureUniqueSlug(baseSlug, null);
    entity.setSlug(uniqueSlug);
    entity.setDescription(request.description() != null ? request.description() : "");
    entity.setCollectionDate(
        request.collectionDate() != null ? request.collectionDate() : LocalDate.now());
    entity.setVisible(false);
    entity.setTotalContent(0);
    if (request.type().isParentType()) {
      // Parent-type collections don't use pagination or row layout
      entity.setContentPerPage(null);
      entity.setRowsWide(null);
      entity.setDisplayMode(DisplayMode.ORDERED);
    } else {
      entity.setContentPerPage(defaultPageSize);
      // Set default displayMode based on type
      entity.setDisplayMode(
          request.type() == CollectionType.BLOG ? DisplayMode.CHRONOLOGICAL : DisplayMode.ORDERED);
    }
    // Apply type-specific defaults (may adjust visibility etc.)
    return applyTypeSpecificDefaults(entity);
  }

  // =============================================================================
  // UPDATE HELPERS FOR SERVICE LAYER (split from updateContent)
  // =============================================================================

  /**
   * Apply basic property updates from updateDTO to the given entity. This mirrors the simple field
   * updates and slug/password logic from the service. - title, description, location,
   * collectionDate, visible, priority, coverImageUrl - slug uniqueness handling (keeps same entity
   * allowed) - configJson - blocksPerPage (>=1) - client gallery password updates via provided
   * password hasher
   */
  public void applyBasicUpdates(CollectionEntity entity, CollectionRequests.Update updateDTO) {
    if (updateDTO.title() != null) {
      entity.setTitle(updateDTO.title());
      // Auto-regenerate slug from new title unless an explicit slug was also provided
      if (updateDTO.slug() == null || updateDTO.slug().isBlank()) {
        String newSlug = generateSlug(updateDTO.title());
        String uniqueSlug = validateAndEnsureUniqueSlug(newSlug, entity.getId());
        entity.setSlug(uniqueSlug);
      }
    }
    if (updateDTO.description() != null) {
      entity.setDescription(updateDTO.description());
    }
    if (updateDTO.type() != null) {
      entity.setType(updateDTO.type());
    }
    // Handle location update using prev/new/remove pattern (many-to-many)
    if (updateDTO.location() != null) {
      CollectionRequests.LocationUpdate locationUpdate = updateDTO.location();
      List<LocationEntity> currentLocations =
          locationRepository.findCollectionLocations(entity.getId());
      Set<LocationEntity> updatedLocations =
          contentMutationUtil.updateLocations(
              new HashSet<>(currentLocations), locationUpdate, null);
      List<Long> updatedLocationIds =
          updatedLocations.stream()
              .map(LocationEntity::getId)
              .filter(Objects::nonNull)
              .distinct()
              .collect(Collectors.toList());
      locationRepository.saveCollectionLocations(entity.getId(), updatedLocationIds);
      log.info("Updated locations for collection {}: {}", entity.getId(), updatedLocationIds);
    }
    if (Boolean.TRUE.equals(updateDTO.clearCollectionDate())) {
      entity.setCollectionDate(null);
    } else if (updateDTO.collectionDate() != null) {
      entity.setCollectionDate(updateDTO.collectionDate());
    }
    if (updateDTO.visible() != null) {
      entity.setVisible(updateDTO.visible());
    }
    if (updateDTO.slug() != null && !updateDTO.slug().isBlank()) {
      String uniqueSlug = validateAndEnsureUniqueSlug(updateDTO.slug().trim(), entity.getId());
      entity.setSlug(uniqueSlug);
    }
    // Parent-type collections don't use pagination or row layout
    if (!entity.getType().isParentType()) {
      if (updateDTO.contentPerPage() != null && updateDTO.contentPerPage() >= 1) {
        entity.setContentPerPage(updateDTO.contentPerPage());
      }
      if (updateDTO.rowsWide() != null) {
        entity.setRowsWide(updateDTO.rowsWide());
      }
    }
    if (updateDTO.displayMode() != null) {
      entity.setDisplayMode(updateDTO.displayMode());
    }

    // Handle coverImageId updates - load ContentImageEntity by ID
    if (updateDTO.coverImageId() != null) {
      if (updateDTO.coverImageId() == 0) {
        // Explicitly clear cover image if ID is 0
        entity.setCoverImageId(null);
      } else {
        // Verify cover image exists
        contentRepository
            .findImageById(updateDTO.coverImageId())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Cover image not found with ID: " + updateDTO.coverImageId()));
        entity.setCoverImageId(updateDTO.coverImageId());
      }
    }

    // TODO: Re-implement password protection after migration
    // Handle password updates for client galleries
    // if (entity.getType() == CollectionType.CLIENT_GALLERY) {
    // if (updateDTO.HasAccess() != null && updateDTO.HasAccess()) {
    // entity.setPasswordProtected(false);
    // entity.setPasswordHash(null);
    // }
    // if (hasPasswordUpdate(updateDTO)) {
    // entity.setPasswordProtected(true);
    // entity.setPasswordHash(hashPassword(updateDTO.Password()));
    // }
    // }
  }

  // =============================================================================
  // SLUG GENERATION AND VALIDATION
  // =============================================================================

  /**
   * Generate a slug from a title.
   *
   * @param title The title to generate a slug from
   * @return The generated slug
   */
  public String generateSlug(String title) {
    return SlugUtil.generateSlug(title);
  }

  /**
   * Validate and ensure a slug is unique. If the slug already exists, append a number to make it
   * unique.
   *
   * @param slug The slug to validate
   * @param existingId The ID of the existing entity (null for new entities)
   * @return A unique slug
   */
  public String validateAndEnsureUniqueSlug(String slug, Long existingId) {
    if (slug == null || slug.isEmpty()) {
      throw new IllegalArgumentException("Slug cannot be empty");
    }

    // Check if slug already exists
    boolean exists =
        collectionRepository
            .findBySlug(slug)
            .map(entity -> !entity.getId().equals(existingId))
            .orElse(false);

    if (!exists) {
      return slug; // Slug is unique
    }

    // Slug exists, append a number to make it unique
    int counter = 1;
    String newSlug;
    do {
      newSlug = slug + "-" + counter++;
      exists = collectionRepository.findBySlug(newSlug).isPresent();
    } while (exists && counter < 100); // Limit to prevent infinite loop

    if (exists) {
      throw new RuntimeException("Could not generate a unique slug after 100 attempts");
    }

    return newSlug;
  }

  // =============================================================================
  // LOCATION HELPERS
  // =============================================================================

  /**
   * Resolve location IDs from explicit IDs and/or location names. Validates each ID exists and
   * finds or creates each named location. Returns an empty list if neither is provided.
   */
  public List<Long> resolveLocationIds(List<Long> locationIds, List<String> locationNames) {
    Set<Long> resolvedIds = new java.util.LinkedHashSet<>();

    if (locationIds != null) {
      for (Long locationId : locationIds) {
        LocationEntity location =
            locationRepository
                .findById(locationId)
                .orElseThrow(
                    () ->
                        new IllegalArgumentException("Location not found with ID: " + locationId));
        resolvedIds.add(location.getId());
      }
    }

    if (locationNames != null) {
      for (String locationName : locationNames) {
        if (locationName != null && !locationName.trim().isEmpty()) {
          LocationEntity location = locationRepository.findOrCreate(locationName.trim());
          if (location == null) {
            throw new IllegalStateException(
                "Failed to find or create location with name: " + locationName);
          }
          resolvedIds.add(location.getId());
        }
      }
    }

    return new ArrayList<>(resolvedIds);
  }

  // =============================================================================
  // PARENT COLLECTION HELPERS
  // =============================================================================

  /**
   * Batch-load all images from the given child collection IDs. Used by the manage page for
   * parent-type collections to aggregate images across child collections for cover image selection
   * and content management.
   *
   * @param childCollectionIds IDs of child collections to aggregate images from
   * @return List of image models from all child collections
   */
  public List<ContentModels.Image> loadImagesFromChildCollections(List<Long> childCollectionIds) {
    if (childCollectionIds == null || childCollectionIds.isEmpty()) {
      return List.of();
    }

    // Batch-fetch all IMAGE content join entries across child collections
    List<CollectionContentEntity> imageJoinEntries =
        collectionRepository.findImageContentByCollectionIds(childCollectionIds);

    if (imageJoinEntries.isEmpty()) {
      return List.of();
    }

    // Extract content IDs and bulk-fetch all image entities
    List<Long> contentIds =
        imageJoinEntries.stream().map(CollectionContentEntity::getContentId).distinct().toList();

    List<ContentEntity> contentEntities = contentRepository.findAllByIds(contentIds);
    Map<Long, ContentEntity> contentMap =
        contentEntities.stream().collect(Collectors.toMap(ContentEntity::getId, ce -> ce));

    // Batch-load tags, people, and locations for all images
    List<Long> imageContentIds =
        contentMap.values().stream()
            .filter(c -> c.getContentType() == ContentType.IMAGE)
            .map(ContentEntity::getId)
            .toList();
    Map<Long, List<TagEntity>> tagsByContentId =
        tagRepository.findTagsByContentIds(imageContentIds);
    Map<Long, List<ContentPersonEntity>> peopleByContentId =
        personRepository.findPeopleByContentIds(imageContentIds);
    Map<Long, List<LocationEntity>> locationsByContentId =
        locationRepository.findLocationsByContentIds(imageContentIds);

    // Convert to image models, deduplicating by content ID (same image may appear in multiple
    // child collections)
    Set<Long> seen = new HashSet<>();
    return imageJoinEntries.stream()
        .filter(cc -> seen.add(cc.getContentId()))
        .map(
            cc -> {
              ContentEntity content = contentMap.get(cc.getContentId());
              if (content instanceof ContentImageEntity imageEntity) {
                return contentModelConverter.buildImageModelWithBatchData(
                    imageEntity,
                    cc.getOrderIndex(),
                    cc.getVisible(),
                    tagsByContentId,
                    peopleByContentId,
                    locationsByContentId);
              }
              return null;
            })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  // =============================================================================
  // TYPE-SPECIFIC PROCESSING
  // =============================================================================

  /**
   * Update entity with type-specific defaults.
   *
   * @param entity The entity to update
   * @return The updated entity
   */
  public CollectionEntity applyTypeSpecificDefaults(CollectionEntity entity) {
    if (entity == null || entity.getType() == null) {
      return entity;
    }

    // Parent-type collections don't use pagination
    if (!entity.getType().isParentType()) {
      if (entity.getContentPerPage() == null || entity.getContentPerPage() <= 0) {
        entity.setContentPerPage(DefaultValues.default_content_per_page);
      }
    }

    // Set type-specific visibility defaults
    if (entity.getVisible() == null) {
      // Client galleries are private by default
      entity.setVisible(entity.getType() != CollectionType.CLIENT_GALLERY);
    }

    return entity;
  }

  // =============================================================================
  // PASSWORD PROTECTION HELPERS
  // =============================================================================

  // TODO: Re-implement password protection after migration
  // /**
  // * Check if a collection (any type) is password-protected.
  // */
  // public static boolean isPasswordProtected(CollectionBaseModel model) {
  // return model.getIsPasswordProtected() != null &&
  // model.getIsPasswordProtected();
  // }

  private static final BCryptPasswordEncoder BCRYPT_ENCODER = new BCryptPasswordEncoder();

  /** Hash a password using BCrypt. */
  public static String hashPassword(String password) {
    return BCRYPT_ENCODER.encode(password);
  }

  /** Check if a raw password matches a stored BCrypt hash. */
  public static boolean passwordMatches(String rawPassword, String storedHash) {
    if (rawPassword == null || storedHash == null) return false;
    return BCRYPT_ENCODER.matches(rawPassword, storedHash);
  }

  // =============================================================================
  // VISIBILITY HELPERS
  // =============================================================================

  /** Check if a collection is publicly visible. */
  public static boolean isVisible(CollectionModel model) {
    return model.getVisible() != null && model.getVisible();
  }

  // =============================================================================
  // PAGINATION HELPERS
  // =============================================================================

  /** Check if a collection model is empty. */
  public static boolean isEmpty(CollectionModel model) {
    return model.getContentCount() == null || model.getContentCount() == 0;
  }
}
