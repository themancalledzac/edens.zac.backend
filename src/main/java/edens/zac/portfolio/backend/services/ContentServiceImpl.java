package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.CollectionContentDao;
import edens.zac.portfolio.backend.dao.CollectionDao;
import edens.zac.portfolio.backend.dao.ContentCameraDao;
import edens.zac.portfolio.backend.dao.ContentDao;
import edens.zac.portfolio.backend.dao.ContentFilmTypeDao;
import edens.zac.portfolio.backend.dao.ContentLensDao;
import edens.zac.portfolio.backend.dao.ContentPersonDao;
import edens.zac.portfolio.backend.dao.ContentTagDao;
import edens.zac.portfolio.backend.dao.ContentTextDao;
import edens.zac.portfolio.backend.dao.LocationDao;
import edens.zac.portfolio.backend.dao.TagDao;
import edens.zac.portfolio.backend.entity.*;
import edens.zac.portfolio.backend.model.*;
import edens.zac.portfolio.backend.services.validator.ContentImageUpdateValidator;
import edens.zac.portfolio.backend.services.validator.ContentValidator;
import edens.zac.portfolio.backend.services.validator.MetadataValidator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Implementation of ContentService that provides methods for managing content,
 * tags, and people.
 */
@Service
@Slf4j
@RequiredArgsConstructor
class ContentServiceImpl implements ContentService {

  private final ContentTagDao contentTagDao;
  private final TagDao tagDao;
  private final ContentPersonDao contentPersonDao;
  private final ContentCameraDao contentCameraDao;
  private final ContentLensDao contentLensDao;
  private final ContentFilmTypeDao contentFilmTypeDao;
  private final LocationDao locationDao;
  private final ContentDao contentDao;
  private final CollectionContentDao collectionContentDao;
  private final CollectionDao collectionDao;
  private final ContentTextDao contentTextDao;
  private final ContentProcessingUtil contentProcessingUtil;
  private final ContentImageUpdateValidator contentImageUpdateValidator;
  private final MetadataValidator metadataValidator;
  private final ContentValidator contentValidator;

  @Override
  @Transactional
  @CacheEvict(value = "generalMetadata", allEntries = true)
  public Map<String, Object> createTag(String tagName) {
    metadataValidator.validateTagName(tagName);
    tagName = tagName.trim();

    // Check if tag already exists (case-insensitive)
    if (contentTagDao.existsByTagNameIgnoreCase(tagName)) {
      throw new DataIntegrityViolationException("Tag already exists: " + tagName);
    }

    ContentTagEntity tag = new ContentTagEntity(tagName);
    ContentTagEntity savedTag = contentTagDao.save(tag);

    return Map.of(
        "id", savedTag.getId(),
        "tagName", savedTag.getTagName(),
        "createdAt", savedTag.getCreatedAt());
  }

  @Override
  @Transactional
  @CacheEvict(value = "generalMetadata", allEntries = true)
  public Map<String, Object> createPerson(String personName) {
    metadataValidator.validatePersonName(personName);
    personName = personName.trim();

    // Check if person already exists (case-insensitive)
    if (contentPersonDao.existsByPersonNameIgnoreCase(personName)) {
      throw new DataIntegrityViolationException("Person already exists: " + personName);
    }

    ContentPersonEntity person = new ContentPersonEntity(personName);
    ContentPersonEntity savedPerson = contentPersonDao.save(person);

    return Map.of(
        "id", savedPerson.getId(),
        "personName", savedPerson.getPersonName(),
        "createdAt", savedPerson.getCreatedAt());
  }

  @Override
  @Transactional
  @CacheEvict(value = "generalMetadata", allEntries = true)
  public Map<String, Object> createCamera(String cameraName) {
    metadataValidator.validateCameraName(cameraName);
    cameraName = cameraName.trim();

    // Check if camera already exists (case-insensitive)
    if (contentCameraDao.existsByCameraNameIgnoreCase(cameraName)) {
      throw new DataIntegrityViolationException("Camera already exists: " + cameraName);
    }

    ContentCameraEntity camera = new ContentCameraEntity(cameraName);
    ContentCameraEntity savedCamera = contentCameraDao.save(camera);

    return Map.of(
        "id", savedCamera.getId(),
        "cameraName", savedCamera.getCameraName(),
        "createdAt", savedCamera.getCreatedAt());
  }

  @Override
  @Transactional
  @CacheEvict(value = "generalMetadata", allEntries = true, condition = "#updates != null && !#updates.isEmpty()")
  public Map<String, Object> updateImages(List<ContentImageUpdateRequest> updates) {
    contentValidator.validateImageUpdates(updates);

    // Track updated images and newly created metadata
    List<ContentImageModel> updatedImages = new ArrayList<>();
    Set<ContentTagEntity> newlyCreatedTags = new HashSet<>();
    Set<ContentPersonEntity> newlyCreatedPeople = new HashSet<>();
    Set<ContentCameraEntity> newlyCreatedCameras = new HashSet<>();
    Set<ContentLensEntity> newlyCreatedLenses = new HashSet<>();
    Set<ContentFilmTypeEntity> newlyCreatedFilmTypes = new HashSet<>();
    List<String> errors = new ArrayList<>();

    // Extract all image IDs and batch fetch them for efficiency
    List<Long> imageIds = updates.stream()
        .map(ContentImageUpdateRequest::getId)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

    if (imageIds.isEmpty()) {
      throw new IllegalArgumentException("No valid image IDs found in update requests");
    }

    // Validate all update requests
    for (ContentImageUpdateRequest update : updates) {
      contentImageUpdateValidator.validate(update);
    }

    // Bulk fetch all images upfront to avoid N+1 queries
    // Note: ContentDao.findAllByIds returns base ContentEntity, need to fetch
    // images specifically
    Map<Long, ContentImageEntity> imageMap = new HashMap<>();
    for (Long imageId : imageIds) {
      contentDao.findImageById(imageId).ifPresent(img -> imageMap.put(img.getId(), img));
    }

    // Track successfully updated images for batch save
    List<ContentImageEntity> imagesToSave = new ArrayList<>();

    for (ContentImageUpdateRequest update : updates) {
      try {
        Long imageId = update.getId();
        if (imageId == null) {
          errors.add("Missing image ID in update request");
          continue;
        }

        ContentImageEntity image = imageMap.get(imageId);
        if (image == null) {
          throw new IllegalArgumentException("Image not found: " + imageId);
        }

        // Apply basic image metadata updates using the processing util
        // Note: This handles camera, lens, and filmType updates via the util
        // We'll need to track which ones were created
        applyImageUpdatesWithTracking(
            image, update, newlyCreatedCameras, newlyCreatedLenses, newlyCreatedFilmTypes);

        // Update tags using prev/new/remove pattern (with tracking)
        if (update.getTags() != null) {
          updateImageTags(image, update.getTags(), newlyCreatedTags);
        }

        // Update people using prev/new/remove pattern (with tracking)
        if (update.getPeople() != null) {
          updateImagePeople(image, update.getPeople(), newlyCreatedPeople);
        }

        // Handle collection updates using prev/new/remove pattern
        if (update.getCollections() != null) {
          CollectionUpdate collectionUpdate = update.getCollections();

          // Remove from collections if specified
          if (collectionUpdate.getRemove() != null && !collectionUpdate.getRemove().isEmpty()) {
            for (Long collectionIdToRemove : collectionUpdate.getRemove()) {
              collectionContentDao.removeContentFromCollection(
                  collectionIdToRemove, List.of(image.getId()));
              log.info("Removed image {} from collection {}", image.getId(), collectionIdToRemove);
            }
          }

          // Update existing collection relationships (visibility, orderIndex)
          if (collectionUpdate.getPrev() != null && !collectionUpdate.getPrev().isEmpty()) {
            contentProcessingUtil.handleContentChildCollectionUpdates(
                image, collectionUpdate.getPrev());
          }

          // Add to new collections if specified
          if (collectionUpdate.getNewValue() != null && !collectionUpdate.getNewValue().isEmpty()) {
            handleAddToCollections(image, collectionUpdate.getNewValue());
          }
        }

        // Add to batch save list
        imagesToSave.add(image);

        // Convert to model and add to results
        ContentImageModel imageModel = (ContentImageModel) contentProcessingUtil
            .convertRegularContentEntityToModel(image);
        updatedImages.add(imageModel);

      } catch (IllegalArgumentException e) {
        errors.add(e.getMessage());
        log.warn("Entity not found during update: {}", e.getMessage());
      } catch (ClassCastException e) {
        errors.add("Content is not an image: " + update.getId());
        log.warn("Attempted to update non-image Content as image: {}", update.getId());
      } catch (Exception e) {
        errors.add("Error updating image " + update.getId() + ": " + e.getMessage());
        log.error("Error updating image {}: {}", update.getId(), e.getMessage(), e);
      }
    }

    // Batch save all successfully updated images for efficiency
    if (!imagesToSave.isEmpty()) {
      for (ContentImageEntity image : imagesToSave) {
        contentDao.saveImage(image);
      }
      log.debug("Batch saved {} updated images", imagesToSave.size());
    }

    // Build the response with updated images and new metadata
    ContentImageUpdateResponse.NewMetadata newMetadata = ContentImageUpdateResponse.NewMetadata.builder()
        .tags(
            newlyCreatedTags.isEmpty()
                ? null
                : newlyCreatedTags.stream().map(this::toTagModel).collect(Collectors.toList()))
        .people(
            newlyCreatedPeople.isEmpty()
                ? null
                : newlyCreatedPeople.stream()
                    .map(this::toPersonModel)
                    .collect(Collectors.toList()))
        .cameras(
            newlyCreatedCameras.isEmpty()
                ? null
                : newlyCreatedCameras.stream()
                    .map(ContentProcessingUtil::cameraEntityToCameraModel)
                    .collect(Collectors.toList()))
        .lenses(
            newlyCreatedLenses.isEmpty()
                ? null
                : newlyCreatedLenses.stream()
                    .map(ContentProcessingUtil::lensEntityToLensModel)
                    .collect(Collectors.toList()))
        .filmTypes(
            newlyCreatedFilmTypes.isEmpty()
                ? null
                : newlyCreatedFilmTypes.stream()
                    .map(this::toFilmTypeModel)
                    .collect(Collectors.toList()))
        .build();

    ContentImageUpdateResponse response = ContentImageUpdateResponse.builder()
        .updatedImages(updatedImages)
        .newMetadata(newMetadata)
        .errors(errors.isEmpty() ? null : errors)
        .build();

    // Return as Map for backward compatibility with interface
    return Map.of(
        "updatedImages", response.getUpdatedImages(),
        "newMetadata", response.getNewMetadata(),
        "errors", response.getErrors() != null ? response.getErrors() : List.of());
  }

  /**
   * Apply image metadata updates and track newly created entities (cameras,
   * lenses, film types).
   */
  private void applyImageUpdatesWithTracking(
      ContentImageEntity image,
      ContentImageUpdateRequest updateRequest,
      Set<ContentCameraEntity> newCameras,
      Set<ContentLensEntity> newLenses,
      Set<ContentFilmTypeEntity> newFilmTypes) {

    // Update basic metadata fields
    if (updateRequest.getTitle() != null)
      image.setTitle(updateRequest.getTitle());
    if (updateRequest.getRating() != null)
      image.setRating(updateRequest.getRating());
    if (updateRequest.getAuthor() != null)
      image.setAuthor(updateRequest.getAuthor());
    if (updateRequest.getIsFilm() != null)
      image.setIsFilm(updateRequest.getIsFilm());
    if (updateRequest.getFilmFormat() != null)
      image.setFilmFormat(updateRequest.getFilmFormat());
    if (updateRequest.getBlackAndWhite() != null)
      image.setBlackAndWhite(updateRequest.getBlackAndWhite());
    if (updateRequest.getFocalLength() != null)
      image.setFocalLength(updateRequest.getFocalLength());
    if (updateRequest.getFStop() != null)
      image.setFStop(updateRequest.getFStop());
    if (updateRequest.getShutterSpeed() != null)
      image.setShutterSpeed(updateRequest.getShutterSpeed());
    if (updateRequest.getIso() != null)
      image.setIso(updateRequest.getIso());
    if (updateRequest.getCreateDate() != null)
      image.setCreateDate(updateRequest.getCreateDate());

    // Handle camera update with tracking
    if (updateRequest.getCamera() != null) {
      ContentImageUpdateRequest.CameraUpdate cameraUpdate = updateRequest.getCamera();
      if (Boolean.TRUE.equals(cameraUpdate.getRemove())) {
        image.setCamera(null);
      } else if (cameraUpdate.getNewValue() != null
          && !cameraUpdate.getNewValue().trim().isEmpty()) {
        String cameraName = cameraUpdate.getNewValue().trim();
        var existing = contentCameraDao.findByCameraNameIgnoreCase(cameraName);
        if (existing.isPresent()) {
          image.setCamera(existing.get());
        } else {
          ContentCameraEntity newCamera = new ContentCameraEntity(cameraName);
          newCamera = contentCameraDao.save(newCamera);
          image.setCamera(newCamera);
          newCameras.add(newCamera);
          log.info("Created new camera: {}", cameraName);
        }
      } else if (cameraUpdate.getPrev() != null) {
        ContentCameraEntity camera = contentCameraDao
            .findById(cameraUpdate.getPrev())
            .orElseThrow(
                () -> new IllegalArgumentException(
                    "Camera not found: " + cameraUpdate.getPrev()));
        image.setCamera(camera);
      }
    }

    // Handle lens update with tracking
    if (updateRequest.getLens() != null) {
      ContentImageUpdateRequest.LensUpdate lensUpdate = updateRequest.getLens();
      if (Boolean.TRUE.equals(lensUpdate.getRemove())) {
        image.setLens(null);
      } else if (lensUpdate.getNewValue() != null && !lensUpdate.getNewValue().trim().isEmpty()) {
        String lensName = lensUpdate.getNewValue().trim();
        var existing = contentLensDao.findByLensNameIgnoreCase(lensName);
        if (existing.isPresent()) {
          image.setLens(existing.get());
        } else {
          ContentLensEntity newLens = new ContentLensEntity(lensName);
          newLens = contentLensDao.save(newLens);
          image.setLens(newLens);
          newLenses.add(newLens);
          log.info("Created new lens: {}", lensName);
        }
      } else if (lensUpdate.getPrev() != null) {
        ContentLensEntity lens = contentLensDao
            .findById(lensUpdate.getPrev())
            .orElseThrow(
                () -> new IllegalArgumentException("Lens not found: " + lensUpdate.getPrev()));
        image.setLens(lens);
      }
    }

    // Handle film type update with tracking
    if (updateRequest.getFilmType() != null) {
      ContentImageUpdateRequest.FilmTypeUpdate filmTypeUpdate = updateRequest.getFilmType();
      if (Boolean.TRUE.equals(filmTypeUpdate.getRemove())) {
        image.setFilmType(null);
      } else if (filmTypeUpdate.getNewValue() != null) {
        NewFilmTypeRequest newFilmTypeRequest = filmTypeUpdate.getNewValue();
        String displayName = newFilmTypeRequest.getFilmTypeName().trim();
        String technicalName = displayName.toUpperCase().replaceAll("\\s+", "_");

        var existing = contentFilmTypeDao.findByFilmTypeNameIgnoreCase(technicalName);
        if (existing.isPresent()) {
          image.setFilmType(existing.get());
        } else {
          ContentFilmTypeEntity newFilmType = new ContentFilmTypeEntity(
              technicalName, displayName, newFilmTypeRequest.getDefaultIso());
          newFilmType = contentFilmTypeDao.save(newFilmType);
          image.setFilmType(newFilmType);
          newFilmTypes.add(newFilmType);
          log.info("Created new film type: {}", displayName);
        }
      } else if (filmTypeUpdate.getPrev() != null) {
        ContentFilmTypeEntity filmType = contentFilmTypeDao
            .findById(filmTypeUpdate.getPrev())
            .orElseThrow(
                () -> new IllegalArgumentException(
                    "Film type not found: " + filmTypeUpdate.getPrev()));
        image.setFilmType(filmType);
      }
    }
  }

  /**
   * Update image tags and track newly created ones. Uses shared utility method
   * from
   * ContentProcessingUtil.
   */
  private void updateImageTags(
      ContentImageEntity image, TagUpdate tagUpdate, Set<ContentTagEntity> newTags) {
    // Load current tags from database
    List<Long> currentTagIds = tagDao.findContentTagIds(image.getId());
    Set<ContentTagEntity> currentTags = currentTagIds.stream()
        .map(
            tagId -> {
              ContentTagEntity tag = new ContentTagEntity();
              tag.setId(tagId);
              return tag;
            })
        .collect(Collectors.toSet());

    Set<ContentTagEntity> updatedTags = contentProcessingUtil.updateTags(
        currentTags, tagUpdate, newTags // Track newly created tags for response
    );
    image.setTags(updatedTags);

    // Save updated tags to database
    List<Long> updatedTagIds = updatedTags.stream()
        .map(ContentTagEntity::getId)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    tagDao.saveContentTags(image.getId(), updatedTagIds);
  }

  /**
   * Add content (image) to new collections with specified visibility and
   * orderIndex. Creates join
   * table entries for the content in the specified collections.
   *
   * @param image       The image to add to collections
   * @param collections List of ChildCollection objects containing collectionId,
   *                    visible, and
   *                    orderIndex
   */
  private void handleAddToCollections(ContentImageEntity image, List<ChildCollection> collections) {
    for (ChildCollection childCollection : collections) {
      if (childCollection.getCollectionId() == null) {
        log.warn("Skipping collection addition: collectionId is null");
        continue;
      }

      // Verify collection exists
      collectionDao
          .findById(childCollection.getCollectionId())
          .orElseThrow(
              () -> new IllegalArgumentException(
                  "Collection not found: " + childCollection.getCollectionId()));

      // Check if this content is already in the collection
      Optional<CollectionContentEntity> existingOpt = collectionContentDao.findByCollectionIdAndContentId(
          childCollection.getCollectionId(), image.getId());

      if (existingOpt.isPresent()) {
        log.warn(
            "Image {} is already in collection {}. Skipping duplicate add.",
            image.getId(),
            childCollection.getCollectionId());
        continue;
      }

      // Determine orderIndex: use provided value or append to end
      Integer orderIndex = childCollection.getOrderIndex();
      if (orderIndex == null) {
        Integer maxOrder = collectionContentDao.getMaxOrderIndexForCollection(childCollection.getCollectionId());
        orderIndex = maxOrder != null ? maxOrder + 1 : 0;
      }

      // Create join table entry
      CollectionContentEntity joinEntry = CollectionContentEntity.builder()
          .collectionId(childCollection.getCollectionId())
          .contentId(image.getId())
          .orderIndex(orderIndex)
          .visible(childCollection.getVisible() != null ? childCollection.getVisible() : true)
          .build();

      collectionContentDao.save(joinEntry);
      log.info(
          "Added image {} to collection {} at orderIndex {} with visible={}",
          image.getId(),
          childCollection.getCollectionId(),
          orderIndex,
          joinEntry.getVisible());
    }
  }

  /**
   * Update image people and track newly created ones. Uses shared utility method
   * from
   * ContentProcessingUtil.
   */
  private void updateImagePeople(
      ContentImageEntity image, PersonUpdate personUpdate, Set<ContentPersonEntity> newPeople) {
    Set<ContentPersonEntity> updatedPeople = contentProcessingUtil.updatePeople(
        image.getPeople(), personUpdate, newPeople // Track newly created people for response
    );
    image.setPeople(updatedPeople);
  }

  @Override
  @Transactional
  public Map<String, Object> deleteImages(List<Long> imageIds) {
    contentValidator.validateImageIds(imageIds);

    List<Long> deletedIds = new ArrayList<>();
    List<String> errors = new ArrayList<>();

    for (Long imageId : imageIds) {
      try {
        if (!contentDao.findImageById(imageId).isPresent()) {
          errors.add("Image not found: " + imageId);
          continue;
        }

        contentDao.deleteImageById(imageId);
        deletedIds.add(imageId);

      } catch (Exception e) {
        errors.add("Failed to delete image " + imageId + ": " + e.getMessage());
        log.error("Error deleting image {}: {}", imageId, e.getMessage());
      }
    }

    return Map.of(
        "deletedIds", deletedIds,
        "deletedCount", deletedIds.size(),
        "errors", errors);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ContentTagModel> getAllTags() {
    return contentTagDao.findAllByOrderByTagNameAsc().stream()
        .map(this::toTagModel)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<ContentPersonModel> getAllPeople() {
    return contentPersonDao.findAllByOrderByPersonNameAsc().stream()
        .map(this::toPersonModel)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<ContentCameraModel> getAllCameras() {
    return contentCameraDao.findAllByOrderByCameraNameAsc().stream()
        .map(ContentProcessingUtil::cameraEntityToCameraModel)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<ContentFilmTypeModel> getAllFilmTypes() {
    return contentFilmTypeDao.findAllByOrderByDisplayNameAsc().stream()
        .map(this::toFilmTypeModel)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<ContentLensModel> getAllLenses() {
    return contentLensDao.findAllByOrderByLensNameAsc().stream()
        .map(ContentProcessingUtil::lensEntityToLensModel)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<LocationModel> getAllLocations() {
    return locationDao.findAllByOrderByLocationNameAsc().stream()
        .map(this::toLocationModel)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional
  @CacheEvict(value = "generalMetadata", allEntries = true)
  public Map<String, Object> createFilmType(
      String filmTypeName, String displayName, Integer defaultIso) {
    metadataValidator.validateFilmType(filmTypeName, displayName, defaultIso);
    filmTypeName = filmTypeName.trim();
    displayName = displayName.trim();

    // Check if film type already exists
    if (contentFilmTypeDao.existsByFilmTypeNameIgnoreCase(filmTypeName)) {
      throw new DataIntegrityViolationException("Film type already exists: " + filmTypeName);
    }

    // Create and save
    ContentFilmTypeEntity filmType = new ContentFilmTypeEntity(filmTypeName, displayName, defaultIso);
    filmType = contentFilmTypeDao.save(filmType);
    log.info("Created film type: {} (ID: {})", filmType.getDisplayName(), filmType.getId());

    // Return result
    return Map.of(
        "success",
        true,
        "message",
        "Film type created successfully",
        "filmType",
        toFilmTypeModel(filmType));
  }

  // ========== Helper Methods: Entity to Model Conversion ==========

  /** Convert ContentTagEntity to ContentTagModel */
  private ContentTagModel toTagModel(ContentTagEntity entity) {
    return ContentTagModel.builder().id(entity.getId()).name(entity.getTagName()).build();
  }

  /** Convert ContentPersonEntity to ContentPersonModel */
  private ContentPersonModel toPersonModel(ContentPersonEntity entity) {
    return ContentPersonModel.builder().id(entity.getId()).name(entity.getPersonName()).build();
  }

  /** Convert LocationEntity to LocationModel */
  private LocationModel toLocationModel(LocationEntity entity) {
    return LocationModel.builder().id(entity.getId()).name(entity.getLocationName()).build();
  }

  /** Convert ContentFilmTypeEntity to ContentFilmTypeModel */
  private ContentFilmTypeModel toFilmTypeModel(ContentFilmTypeEntity entity) {
    return ContentFilmTypeModel.builder()
        .id(entity.getId())
        .filmTypeName(entity.getFilmTypeName())
        .name(entity.getDisplayName())
        .defaultIso(entity.getDefaultIso())
        .build();
  }

  @Override
  @Transactional(readOnly = true)
  public List<ContentImageModel> getAllImages() {
    return contentDao.findAllImagesOrderByCreateDateDesc().stream()
        .map(
            entity -> (ContentImageModel) contentProcessingUtil.convertRegularContentEntityToModel(entity))
        .collect(Collectors.toList());
  }

  @Override
  @Transactional
  public List<ContentImageModel> createImages(Long collectionId, List<MultipartFile> files) {
    log.debug("Creating images for collection ID: {}", collectionId);

    contentValidator.validateFiles(files);

    // Verify collection exists
    collectionDao
        .findById(collectionId)
        .orElseThrow(() -> new IllegalArgumentException("Collection not found: " + collectionId));

    List<ContentImageModel> createdImages = new ArrayList<>();

    // Get the next order index for this collection
    Integer maxOrder = collectionContentDao.getMaxOrderIndexForCollection(collectionId);
    Integer orderIndex = maxOrder != null ? maxOrder + 1 : 0;

    for (MultipartFile file : files) {
      try {
        // First, check if this image already exists in the database (duplicate
        // detection)
        if (file.getContentType() != null
            && file.getContentType().startsWith("image/")
            && !file.getContentType().equals("image/gif")) {
          // Generate file identifier to check for duplicates
          String originalFilename = file.getOriginalFilename();
          if (originalFilename != null) {
            String date = java.time.LocalDate.now().toString();
            String fileIdentifier = date + "/" + originalFilename;

            // Check if image already exists
            if (contentDao.existsByFileIdentifier(fileIdentifier)) {
              log.info("Skipping duplicate image: {}", originalFilename);
              continue; // Skip this file and move to the next
            }
          }
        }

        // Process file based on content type
        if (file.getContentType() != null && file.getContentType().startsWith("image/")) {
          if (file.getContentType().equals("image/gif")) {
            // Process as GIF - skip for now (future implementation)
            log.debug("Skipping GIF file (not yet implemented): {}", file.getOriginalFilename());
          } else {
            // STEP 1: Process and save the image content (NO collection reference)
            ContentImageEntity img = contentProcessingUtil.processImageContent(file, null);

            // STEP 2: Create join table entry linking content to collection
            CollectionContentEntity joinEntry = CollectionContentEntity.builder()
                .collectionId(collectionId)
                .contentId(img.getId())
                .orderIndex(orderIndex)
                .visible(true) // Visible by default
                .build();

            collectionContentDao.save(joinEntry);

            log.debug(
                "Created join table entry for image {} in collection {} at orderIndex {}",
                img.getId(),
                collectionId,
                orderIndex);

            // STEP 3: Convert to model with join table metadata and add to results
            ContentModel contentModel = contentProcessingUtil.convertEntityToModel(joinEntry);
            if (contentModel instanceof ContentImageModel imageModel) {
              createdImages.add(imageModel);
            } else {
              log.error(
                  "Expected ContentImageModel but got {}",
                  contentModel != null ? contentModel.getClass() : "null");
            }

            // Increment order index for next image
            orderIndex++;
          }
        }
      } catch (Exception e) {
        log.error("Error processing file: {}", e.getMessage(), e);
      }
    }

    return createdImages;
  }

  @Override
  @Transactional
  public ContentTextModel createTextContent(CreateTextContentRequest request) {
    log.debug("Creating text content for collection ID: {}", request.getCollectionId());

    contentValidator.validateTextContent(request.getTextContent());

    // Verify collection exists
    collectionDao
        .findById(request.getCollectionId())
        .orElseThrow(
            () -> new IllegalArgumentException("Collection not found: " + request.getCollectionId()));

    // Get the next order index for this collection
    Integer maxOrder = collectionContentDao.getMaxOrderIndexForCollection(request.getCollectionId());
    Integer orderIndex = maxOrder != null ? maxOrder + 1 : 0;

    // Create text content entity
    ContentTextEntity textEntity = ContentTextEntity.builder()
        .textContent(request.getTextContent().trim())
        .formatType(request.getFormType() != null ? request.getFormType().name() : "PLAIN")
        .build();

    // Save the text content
    textEntity = contentTextDao.save(textEntity);

    // Create join table entry linking content to collection
    CollectionContentEntity joinEntry = CollectionContentEntity.builder()
        .collectionId(request.getCollectionId())
        .contentId(textEntity.getId())
        .orderIndex(orderIndex)
        .visible(true)
        .build();

    collectionContentDao.save(joinEntry);

    log.info(
        "Created text content {} in collection {} at orderIndex {}",
        textEntity.getId(),
        request.getCollectionId(),
        orderIndex);

    // Convert to model and return
    ContentModel contentModel = contentProcessingUtil.convertEntityToModel(joinEntry);
    if (contentModel instanceof ContentTextModel textModel) {
      return textModel;
    } else {
      throw new IllegalStateException(
          "Expected ContentTextModel but got "
              + (contentModel != null ? contentModel.getClass() : "null"));
    }
  }
}
