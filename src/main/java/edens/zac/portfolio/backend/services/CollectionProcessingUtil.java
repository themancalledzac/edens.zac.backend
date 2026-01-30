package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.CollectionContentDao;
import edens.zac.portfolio.backend.dao.CollectionDao;
import edens.zac.portfolio.backend.dao.ContentDao;
import edens.zac.portfolio.backend.dao.LocationDao;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.LocationEntity;
import edens.zac.portfolio.backend.model.*;
import edens.zac.portfolio.backend.model.LocationUpdate;
import edens.zac.portfolio.backend.types.CollectionType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class CollectionProcessingUtil {

  private final CollectionDao collectionDao;
  private final ContentDao contentDao;
  private final CollectionContentDao collectionContentDao;
  private final ContentProcessingUtil contentProcessingUtil;
  private final LocationDao locationDao;

  // =============================================================================
  // ERROR HANDLING
  // =============================================================================

  // =============================================================================
  // ENTITY-TO-MODEL CONVERSION
  // =============================================================================

  /**
   * Helper method to populate coverImage on a model from an entity. Fetches the
   * cover image by ID
   * and converts to ContentImageModel with all metadata including imageWidth and
   * imageHeight.
   */
  private void populateCoverImage(CollectionModel model, CollectionEntity entity) {
    if (entity.getCoverImageId() != null) {
      // Fetch the ContentImageEntity by ID and convert to ContentImageModel with full
      // metadata
      ContentImageEntity coverImage = contentDao.findImageById(entity.getCoverImageId()).orElse(null);
      if (coverImage != null) {
        ContentImageModel coverImageModel = contentProcessingUtil.convertImageEntityToModel(coverImage);
        model.setCoverImage(coverImageModel);
      }
    }
  }

  /**
   * Convert a CollectionEntity to a CollectionModel with basic information. This
   * does not include
   * content.
   *
   * @param entity The entity to convert
   * @return The converted model
   */
  public CollectionModel convertToBasicModel(CollectionEntity entity) {
    if (entity == null) {
      return null;
    }

    CollectionModel model = new CollectionModel();
    model.setId(entity.getId());
    model.setType(entity.getType());
    model.setTitle(entity.getTitle());
    model.setSlug(entity.getSlug());
    model.setDescription(entity.getDescription());
    // Convert locationId to LocationModel
    if (entity.getLocationId() != null) {
      LocationEntity locationEntity = locationDao.findById(entity.getLocationId()).orElse(null);
      if (locationEntity != null) {
        model.setLocation(
            LocationModel.builder()
                .id(locationEntity.getId())
                .name(locationEntity.getLocationName())
                .build());
      }
    }
    model.setCollectionDate(entity.getCollectionDate());
    model.setVisible(entity.getVisible());

    // Populate coverImage using helper method
    populateCoverImage(model, entity);

    // TODO: Re-implement password protection after migration
    // model.setIsPasswordProtected(entity.isPasswordProtected());
    // model.setHasAccess(!entity.isPasswordProtected()); // Default access for
    // non-protected
    // collections
    model.setCreatedAt(entity.getCreatedAt());
    model.setUpdatedAt(entity.getUpdatedAt());
    // Use stored displayMode if available, otherwise compute default based on type
    CollectionBaseModel.DisplayMode mode = entity.getDisplayMode();
    if (mode == null) {
      // Fallback to computed default for existing records without displayMode
      mode = entity.getType() == CollectionType.BLOG
          ? CollectionBaseModel.DisplayMode.CHRONOLOGICAL
          : CollectionBaseModel.DisplayMode.ORDERED;
    }
    model.setDisplayMode(mode);

    // Set pagination metadata
    model.setContentCount(entity.getTotalContent());
    model.setContentPerPage(entity.getContentPerPage());
    model.setTotalPages(entity.getTotalPages());
    model.setCurrentPage(0);
    model.setRowsWide(entity.getRowsWide());

    return model;
  }

  /**
   * Convert a CollectionEntity to a CollectionModel with all content. Uses bulk
   * loading of
   * ContentEntity instances to avoid proxy issues and improve performance.
   *
   * @param entity The entity to convert
   * @return The converted model
   */
  public CollectionModel convertToFullModel(CollectionEntity entity) {
    if (entity == null) {
      return null;
    }

    CollectionModel model = convertToBasicModel(entity);

    // Fetch join table entries explicitly to get content with collection-specific
    // metadata
    List<CollectionContentEntity> joinEntries = collectionContentDao
        .findByCollectionIdOrderByOrderIndex(entity.getId());

    // Extract content IDs from join table entries
    List<Long> contentIds = joinEntries.stream()
        .map(CollectionContentEntity::getContentId)
        .filter(Objects::nonNull)
        .toList();

    // Bulk fetch all ContentEntity instances in one query (properly loads all
    // subclasses)
    final Map<Long, ContentEntity> contentMap;
    if (!contentIds.isEmpty()) {
      List<ContentEntity> contentEntities = contentDao.findAllByIds(contentIds);
      contentMap = contentEntities.stream().collect(Collectors.toMap(ContentEntity::getId, ce -> ce));
    } else {
      contentMap = new HashMap<>();
    }

    // Convert join table entries to content models with collection-specific
    // metadata
    // Use the bulk-loaded content entities instead of lazy-loaded ones
    List<ContentModel> contents = joinEntries.stream()
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
              // Use bulk-loaded conversion method directly (no temporary object needed)
              return contentProcessingUtil.convertBulkLoadedContentToModel(content, cc);
            })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

    model.setContent(contents);
    return model;
  }

  /**
   * Convert a CollectionEntity and a List of CollectionContentEntity to a
   * CollectionModel. Uses
   * bulk loading of ContentEntity instances to avoid proxy issues and improve
   * performance.
   *
   * @param entity                The entity to convert
   * @param collectionContentList The list of join table entries
   *                              (collection-content associations)
   * @param currentPage           The current page number (0-based)
   * @param pageSize              The page size
   * @param totalElements         The total number of elements
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
    List<Long> contentIds = collectionContentList.stream()
        .map(CollectionContentEntity::getContentId)
        .filter(Objects::nonNull)
        .toList();

    // Bulk fetch all ContentEntity instances in one query (properly loads all
    // subclasses)
    final Map<Long, ContentEntity> contentMap;
    if (!contentIds.isEmpty()) {
      List<ContentEntity> contentEntities = contentDao.findAllByIds(contentIds);
      contentMap = contentEntities.stream().collect(Collectors.toMap(ContentEntity::getId, ce -> ce));
    } else {
      contentMap = new HashMap<>();
    }

    // Convert join table entries to content models with collection-specific
    // metadata
    // Use the bulk-loaded content entities instead of lazy-loaded ones
    List<ContentModel> contents = collectionContentList.stream()
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
              // Use bulk-loaded conversion method directly (no temporary object needed)
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
   * Minimal create: from CollectionCreateRequest (type, title only), apply
   * defaults for the rest.
   */
  public CollectionEntity toEntity(CollectionCreateRequest request, int defaultPageSize) {
    if (request == null) {
      throw new IllegalArgumentException("Create request cannot be null");
    }
    CollectionEntity entity = new CollectionEntity();
    entity.setType(request.getType());
    entity.setTitle(request.getTitle());
    String baseSlug = generateSlug(request.getTitle());
    String uniqueSlug = validateAndEnsureUniqueSlug(baseSlug, null);
    entity.setSlug(uniqueSlug);
    entity.setDescription("");
    entity.setLocationId(null);
    entity.setCollectionDate(LocalDate.now());
    entity.setVisible(false);
    entity.setContentPerPage(defaultPageSize);
    entity.setTotalContent(0);
    // Set default displayMode based on type
    entity.setDisplayMode(
        request.getType() == CollectionType.BLOG
            ? CollectionBaseModel.DisplayMode.CHRONOLOGICAL
            : CollectionBaseModel.DisplayMode.ORDERED);
    // TODO: Re-implement password protection after migration
    // entity.setPasswordProtected(false);
    // entity.setPasswordHash(null);
    // Apply type-specific defaults (may adjust visibility etc.)
    return applyTypeSpecificDefaults(entity);
  }

  // =============================================================================
  // UPDATE HELPERS FOR SERVICE LAYER (split from updateContent)
  // =============================================================================

  /**
   * Apply basic property updates from updateDTO to the given entity. This mirrors
   * the simple field
   * updates and slug/password logic from the service. - title, description,
   * location,
   * collectionDate, visible, priority, coverImageUrl - slug uniqueness handling
   * (keeps same entity
   * allowed) - configJson - blocksPerPage (>=1) - client gallery password updates
   * via provided
   * password hasher
   */
  public void applyBasicUpdates(CollectionEntity entity, CollectionUpdateRequest updateDTO) {
    if (updateDTO.getTitle() != null) {
      entity.setTitle(updateDTO.getTitle());
    }
    if (updateDTO.getDescription() != null) {
      entity.setDescription(updateDTO.getDescription());
    }
    if (updateDTO.getType() != null) {
      entity.setType(updateDTO.getType());
    }
    // Handle location update using prev/new/remove pattern
    if (updateDTO.getLocation() != null) {
      LocationUpdate locationUpdate = updateDTO.getLocation();

      if (Boolean.TRUE.equals(locationUpdate.getRemove())) {
        // Remove location association
        entity.setLocationId(null);
        log.info("Removed location association from collection {}", entity.getId());
      } else if (locationUpdate.getNewValue() != null
          && !locationUpdate.getNewValue().trim().isEmpty()) {
        // Create new location by name
        String locationName = locationUpdate.getNewValue().trim();
        LocationEntity location = locationDao.findOrCreate(locationName);
        entity.setLocationId(location.getId());
        log.info("Set location to: {} (ID: {})", locationName, location.getId());
      } else if (locationUpdate.getPrev() != null) {
        // Use existing location by ID
        LocationEntity location = locationDao
            .findById(locationUpdate.getPrev())
            .orElseThrow(
                () -> new IllegalArgumentException(
                    "Location not found with ID: " + locationUpdate.getPrev()));
        entity.setLocationId(location.getId());
      }
    }
    if (updateDTO.getCollectionDate() != null) {
      entity.setCollectionDate(updateDTO.getCollectionDate());
    }
    if (updateDTO.getVisible() != null) {
      entity.setVisible(updateDTO.getVisible());
    }
    if (updateDTO.getSlug() != null && !updateDTO.getSlug().isBlank()) {
      String uniqueSlug = validateAndEnsureUniqueSlug(updateDTO.getSlug().trim(), entity.getId());
      entity.setSlug(uniqueSlug);
    }
    if (updateDTO.getContentPerPage() != null && updateDTO.getContentPerPage() >= 1) {
      entity.setContentPerPage(updateDTO.getContentPerPage());
    }
    if (updateDTO.getDisplayMode() != null) {
      entity.setDisplayMode(updateDTO.getDisplayMode());
    }
    if (updateDTO.getRowsWide() != null) {
      entity.setRowsWide(updateDTO.getRowsWide());
    }

    // Handle coverImageId updates - load ContentImageEntity by ID
    if (updateDTO.getCoverImageId() != null) {
      if (updateDTO.getCoverImageId() == 0) {
        // Explicitly clear cover image if ID is 0
        entity.setCoverImageId(null);
      } else {
        // Verify cover image exists
        contentDao
            .findImageById(updateDTO.getCoverImageId())
            .orElseThrow(
                () -> new IllegalArgumentException(
                    "Cover image not found with ID: " + updateDTO.getCoverImageId()));
        entity.setCoverImageId(updateDTO.getCoverImageId());
      }
    }

    // TODO: Re-implement password protection after migration
    // Handle password updates for client galleries
    // if (entity.getType() == CollectionType.CLIENT_GALLERY) {
    // if (updateDTO.getHasAccess() != null && updateDTO.getHasAccess()) {
    // entity.setPasswordProtected(false);
    // entity.setPasswordHash(null);
    // }
    // if (hasPasswordUpdate(updateDTO)) {
    // entity.setPasswordProtected(true);
    // entity.setPasswordHash(hashPassword(updateDTO.getPassword()));
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
    if (title == null || title.isEmpty()) {
      return "";
    }

    return title
        .toLowerCase()
        .replaceAll("[^a-zA-Z0-9\\s-]", "") // Remove all non-alphanumeric chars except space and -
        .replaceAll("\\s+", "-") // Replace spaces with hyphens
        .replaceAll("-+", "-") // Replace multiple hyphens with single hyphen
        .replaceAll("^-|-$", ""); // Remove leading and trailing hyphens
  }

  /**
   * Validate and ensure a slug is unique. If the slug already exists, append a
   * number to make it
   * unique.
   *
   * @param slug       The slug to validate
   * @param existingId The ID of the existing entity (null for new entities)
   * @return A unique slug
   */
  public String validateAndEnsureUniqueSlug(String slug, Long existingId) {
    if (slug == null || slug.isEmpty()) {
      throw new IllegalArgumentException("Slug cannot be empty");
    }

    // Check if slug already exists
    boolean exists = collectionDao
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
      exists = collectionDao.findBySlug(newSlug).isPresent();
    } while (exists && counter < 100); // Limit to prevent infinite loop

    if (exists) {
      throw new RuntimeException("Could not generate a unique slug after 100 attempts");
    }

    return newSlug;
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
      entity.setContentPerPage(
          edens.zac.portfolio.backend.config.DefaultValues.default_content_per_page); // Default page size
    }

    // Set type-specific visibility defaults
    if (entity.getVisible() == null) {
      // Client galleries are private by default
      entity.setVisible(entity.getType() != CollectionType.CLIENT_GALLERY);
    }

    return entity;
  }

  // =============================================================================
  // VALIDATION METHODS
  // =============================================================================

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
  public static boolean hasPasswordUpdate(CollectionUpdateRequest dto) {
    return dto.getPassword() != null && !dto.getPassword().trim().isEmpty();
  }

  /**
   * Hash a password using SHA-256. Note: For production client gallery secrets,
   * prefer BCrypt or
   * Argon2.
   */
  public static String hashPassword(String password) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1)
          hexString.append('0');
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Error hashing password", e);
    }
  }

  /** Check if a password matches a stored hash. */
  public static boolean passwordMatches(String password, String hash) {
    return hashPassword(password).equals(hash);
  }

  // =============================================================================
  // VISIBILITY HELPERS
  // =============================================================================

  /** Check if a collection is publicly visible. */
  public static boolean isVisible(CollectionBaseModel model) {
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
