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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
  private final ContentProcessingUtil contentProcessingUtil;
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

    // Batch-load all locations referenced by these collections
    List<Long> locationIds =
        entities.stream()
            .map(CollectionEntity::getLocationId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    Map<Long, LocationEntity> locationsById = locationRepository.findByIds(locationIds);

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
    List<Long> coverLocationIds =
        coverImagesById.values().stream()
            .map(ContentImageEntity::getLocationId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    Map<Long, List<TagEntity>> tagsByContentId =
        tagRepository.findTagsByContentIds(coverContentIds);
    Map<Long, List<ContentPersonEntity>> peopleByContentId =
        personRepository.findPeopleByContentIds(coverContentIds);
    // Merge cover image locations with collection locations
    Map<Long, LocationEntity> allLocationsById = new HashMap<>(locationsById);
    if (!coverLocationIds.isEmpty()) {
      allLocationsById.putAll(locationRepository.findByIds(coverLocationIds));
    }

    // Convert each entity using pre-loaded data
    return entities.stream()
        .map(
            entity ->
                buildBasicModel(
                    entity, allLocationsById, coverImagesById, tagsByContentId, peopleByContentId))
        .collect(Collectors.toList());
  }

  /** Build a single CollectionModel from pre-loaded batch data. */
  private CollectionModel buildBasicModel(
      CollectionEntity entity,
      Map<Long, LocationEntity> locationsById,
      Map<Long, ContentImageEntity> coverImagesById,
      Map<Long, List<TagEntity>> tagsByContentId,
      Map<Long, List<ContentPersonEntity>> peopleByContentId) {
    CollectionModel model = new CollectionModel();
    model.setId(entity.getId());
    model.setType(entity.getType());
    model.setTitle(entity.getTitle());
    model.setSlug(entity.getSlug());
    model.setDescription(entity.getDescription());

    if (entity.getLocationId() != null) {
      LocationEntity loc = locationsById.get(entity.getLocationId());
      if (loc != null) {
        model.setLocation(new Records.Location(loc.getId(), loc.getLocationName(), loc.getSlug()));
      }
    }

    model.setCollectionDate(entity.getCollectionDate());
    model.setVisible(entity.getVisible());

    // Populate cover image from pre-loaded data
    if (entity.getCoverImageId() != null) {
      ContentImageEntity coverImage = coverImagesById.get(entity.getCoverImageId());
      if (coverImage != null) {
        ContentModels.Image coverImageModel =
            contentProcessingUtil.buildImageModelWithBatchData(
                coverImage, null, null, tagsByContentId, peopleByContentId, locationsById);
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
    List<Long> imageLocationIds =
        contentMap.values().stream()
            .filter(c -> c.getContentType() == ContentType.IMAGE)
            .map(c -> ((ContentImageEntity) c).getLocationId())
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    Map<Long, List<TagEntity>> tagsByContentId =
        tagRepository.findTagsByContentIds(imageContentIds);
    Map<Long, List<ContentPersonEntity>> peopleByContentId =
        personRepository.findPeopleByContentIds(imageContentIds);
    Map<Long, LocationEntity> locationsById = locationRepository.findByIds(imageLocationIds);

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
                    return contentProcessingUtil.buildImageModelWithBatchData(
                        imageEntity,
                        cc.getOrderIndex(),
                        cc.getVisible(),
                        tagsByContentId,
                        peopleByContentId,
                        locationsById);
                  }
                  // For other content types, use the standard conversion
                  return contentProcessingUtil.convertBulkLoadedContentToModel(content, cc);
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
    entity.setLocationId(resolveLocationId(request.locationId(), request.locationName()));
    entity.setCollectionDate(
        request.collectionDate() != null ? request.collectionDate() : LocalDate.now());
    entity.setVisible(false);
    entity.setContentPerPage(defaultPageSize);
    entity.setTotalContent(0);
    // Set default displayMode based on type
    entity.setDisplayMode(
        request.type() == CollectionType.BLOG ? DisplayMode.CHRONOLOGICAL : DisplayMode.ORDERED);
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
    // Handle location update using prev/new/remove pattern
    if (updateDTO.location() != null) {
      CollectionRequests.LocationUpdate locationUpdate = updateDTO.location();

      if (Boolean.TRUE.equals(locationUpdate.remove())) {
        // Remove location association
        entity.setLocationId(null);
        log.info("Removed location association from collection {}", entity.getId());
      } else if (locationUpdate.newValue() != null && !locationUpdate.newValue().trim().isEmpty()) {
        // Create new location by name
        String locationName = locationUpdate.newValue().trim();
        LocationEntity location = locationRepository.findOrCreate(locationName);
        entity.setLocationId(location.getId());
        log.info("Set location to: {} (ID: {})", locationName, location.getId());
      } else if (locationUpdate.prev() != null) {
        // Use existing location by ID
        LocationEntity location =
            locationRepository
                .findById(locationUpdate.prev())
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "Location not found with ID: " + locationUpdate.prev()));
        entity.setLocationId(location.getId());
      }
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
    if (updateDTO.contentPerPage() != null && updateDTO.contentPerPage() >= 1) {
      entity.setContentPerPage(updateDTO.contentPerPage());
    }
    if (updateDTO.displayMode() != null) {
      entity.setDisplayMode(updateDTO.displayMode());
    }
    if (updateDTO.rowsWide() != null) {
      entity.setRowsWide(updateDTO.rowsWide());
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
   * Resolve a location ID from either an explicit ID or a location name. If locationId is provided,
   * validates it exists. If locationName is provided, finds or creates the location. Returns null
   * if neither is provided.
   */
  public Long resolveLocationId(Long locationId, String locationName) {
    if (locationId != null) {
      LocationEntity location =
          locationRepository
              .findById(locationId)
              .orElseThrow(
                  () -> new IllegalArgumentException("Location not found with ID: " + locationId));
      return location.getId();
    } else if (locationName != null && !locationName.trim().isEmpty()) {
      LocationEntity location = locationRepository.findOrCreate(locationName.trim());
      if (location == null) {
        throw new IllegalStateException(
            "Failed to find or create location with name: " + locationName);
      }
      return location.getId();
    }
    return null;
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

    // Set default blocks per page if not set
    if (entity.getContentPerPage() == null || entity.getContentPerPage() <= 0) {
      entity.setContentPerPage(DefaultValues.default_content_per_page); // Default page size
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

  /** Check if an UpdateDTO includes password changes. */
  public static boolean hasPasswordUpdate(CollectionRequests.Update dto) {
    return dto.password() != null && !dto.password().trim().isEmpty();
  }

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
