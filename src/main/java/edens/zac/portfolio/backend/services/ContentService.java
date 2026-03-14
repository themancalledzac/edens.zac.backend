package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.CollectionContentDao;
import edens.zac.portfolio.backend.dao.CollectionDao;
import edens.zac.portfolio.backend.dao.ContentDao;
import edens.zac.portfolio.backend.dao.ContentTextDao;
import edens.zac.portfolio.backend.dao.TagDao;
import edens.zac.portfolio.backend.entity.*;
import edens.zac.portfolio.backend.model.*;
import edens.zac.portfolio.backend.services.validator.ContentImageUpdateValidator;
import edens.zac.portfolio.backend.services.validator.ContentValidator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Service for managing content, tags, and people. */
@Service
@Slf4j
@RequiredArgsConstructor
public class ContentService {

  private final TagDao tagDao;
  private final ContentDao contentDao;
  private final CollectionContentDao collectionContentDao;
  private final CollectionDao collectionDao;
  private final ContentTextDao contentTextDao;
  private final ContentProcessingUtil contentProcessingUtil;
  private final ContentImageUpdateValidator contentImageUpdateValidator;
  private final ContentValidator contentValidator;
  private final MetadataService metadataService;

  // Virtual thread executor for parallel image processing (Java 21+)
  // Virtual threads are lightweight and don't consume OS threads while waiting on
  // I/O
  private final ExecutorService imageProcessingExecutor =
      Executors.newVirtualThreadPerTaskExecutor();

  public Map<String, Object> createTag(String tagName) {
    return metadataService.createTag(tagName);
  }

  public Map<String, Object> createPerson(String personName) {
    return metadataService.createPerson(personName);
  }

  @Transactional
  @CacheEvict(
      value = "generalMetadata",
      allEntries = true,
      condition = "#updates != null && !#updates.isEmpty()")
  public Map<String, Object> updateImages(List<ContentImageUpdateRequest> updates) {
    contentValidator.validateImageUpdates(updates);

    // Track updated images and newly created metadata
    List<ContentModels.Image> updatedImages = new ArrayList<>();
    Set<ContentTagEntity> newlyCreatedTags = new HashSet<>();
    Set<ContentPersonEntity> newlyCreatedPeople = new HashSet<>();
    Set<ContentCameraEntity> newlyCreatedCameras = new HashSet<>();
    Set<ContentLensEntity> newlyCreatedLenses = new HashSet<>();
    Set<ContentFilmTypeEntity> newlyCreatedFilmTypes = new HashSet<>();
    List<String> errors = new ArrayList<>();

    // Extract all image IDs and batch fetch them for efficiency
    List<Long> imageIds =
        updates.stream()
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

    // OPTIMIZED: Batch fetch all images in a single query (avoids N+1)
    List<ContentImageEntity> imageList = contentDao.findImagesByIds(imageIds);
    Map<Long, ContentImageEntity> imageMap =
        imageList.stream().collect(Collectors.toMap(ContentImageEntity::getId, img -> img));

    // Verify all requested images exist
    for (Long imageId : imageIds) {
      if (!imageMap.containsKey(imageId)) {
        throw new ResourceNotFoundException("Image not found: " + imageId);
      }
    }

    // OPTIMIZED: Pre-fetch all current tags and people for all images (avoids N+1)
    Map<Long, List<Long>> currentTagsByImage = tagDao.findTagIdsByContentIds(imageIds);
    Map<Long, List<Long>> currentPeopleByImage = contentDao.findPersonIdsByImageIds(imageIds);

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
          throw new ResourceNotFoundException("Image not found: " + imageId);
        }

        // Apply basic image metadata updates using the processing util
        // Note: This handles camera, lens, and filmType updates via the util
        // We'll need to track which ones were created
        applyImageUpdatesWithTracking(
            image, update, newlyCreatedCameras, newlyCreatedLenses, newlyCreatedFilmTypes);

        // Update tags using prev/new/remove pattern (with tracking)
        // Use pre-fetched tag IDs to avoid N+1 query
        if (update.getTags() != null) {
          List<Long> currentTags = currentTagsByImage.getOrDefault(imageId, List.of());
          updateImageTagsOptimized(image, update.getTags(), currentTags, newlyCreatedTags);
        }

        // Update people using prev/new/remove pattern (with tracking)
        // Use pre-fetched person IDs to avoid N+1 query
        if (update.getPeople() != null) {
          List<Long> currentPeople = currentPeopleByImage.getOrDefault(imageId, List.of());
          updateImagePeopleOptimized(image, update.getPeople(), currentPeople, newlyCreatedPeople);
        }

        // Handle collection updates using prev/new/remove pattern
        if (update.getCollections() != null) {
          CollectionRequests.CollectionUpdate collectionUpdate = update.getCollections();

          // Remove from collections if specified
          if (collectionUpdate.remove() != null && !collectionUpdate.remove().isEmpty()) {
            for (Long collectionIdToRemove : collectionUpdate.remove()) {
              collectionContentDao.removeContentFromCollection(
                  collectionIdToRemove, List.of(image.getId()));
              log.info("Removed image {} from collection {}", image.getId(), collectionIdToRemove);
            }
          }

          // Update existing collection relationships (visibility, orderIndex)
          if (collectionUpdate.prev() != null && !collectionUpdate.prev().isEmpty()) {
            contentProcessingUtil.handleContentChildCollectionUpdates(
                image, collectionUpdate.prev());
          }

          // Add to new collections if specified
          if (collectionUpdate.newValue() != null && !collectionUpdate.newValue().isEmpty()) {
            handleAddToCollections(image, collectionUpdate.newValue());
          }
        }

        // Add to batch save list
        imagesToSave.add(image);

        // Convert to model and add to results
        ContentModels.Image imageModel =
            (ContentModels.Image) contentProcessingUtil.convertRegularContentEntityToModel(image);
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
    ContentImageUpdateResponse.NewMetadata newMetadata =
        ContentImageUpdateResponse.NewMetadata.builder()
            .tags(
                newlyCreatedTags.isEmpty()
                    ? null
                    : newlyCreatedTags.stream()
                        .map(e -> new Records.Tag(e.getId(), e.getTagName()))
                        .collect(Collectors.toList()))
            .people(
                newlyCreatedPeople.isEmpty()
                    ? null
                    : newlyCreatedPeople.stream()
                        .map(e -> new Records.Person(e.getId(), e.getPersonName()))
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
                        .map(metadataService::toFilmTypeModel)
                        .collect(Collectors.toList()))
            .build();

    ContentImageUpdateResponse response =
        ContentImageUpdateResponse.builder()
            .updatedImages(updatedImages)
            .newMetadata(newMetadata)
            .errors(errors.isEmpty() ? null : errors)
            .build();

    // Return as Map for backward compatibility with interface
    return Map.of(
        "updatedImages",
        response.getUpdatedImages(),
        "newMetadata",
        response.getNewMetadata(),
        "errors",
        response.getErrors() != null ? response.getErrors() : List.of());
  }

  /**
   * Apply image metadata updates and track newly created entities (cameras, lenses, film types).
   */
  private void applyImageUpdatesWithTracking(
      ContentImageEntity image,
      ContentImageUpdateRequest updateRequest,
      Set<ContentCameraEntity> newCameras,
      Set<ContentLensEntity> newLenses,
      Set<ContentFilmTypeEntity> newFilmTypes) {

    // Update basic metadata fields
    if (updateRequest.getTitle() != null) image.setTitle(updateRequest.getTitle());
    if (updateRequest.getRating() != null) image.setRating(updateRequest.getRating());
    if (updateRequest.getAuthor() != null) image.setAuthor(updateRequest.getAuthor());
    if (updateRequest.getIsFilm() != null) image.setIsFilm(updateRequest.getIsFilm());
    if (updateRequest.getFilmFormat() != null) image.setFilmFormat(updateRequest.getFilmFormat());
    if (updateRequest.getBlackAndWhite() != null)
      image.setBlackAndWhite(updateRequest.getBlackAndWhite());
    if (updateRequest.getFocalLength() != null)
      image.setFocalLength(updateRequest.getFocalLength());
    if (updateRequest.getFStop() != null) image.setFStop(updateRequest.getFStop());
    if (updateRequest.getShutterSpeed() != null)
      image.setShutterSpeed(updateRequest.getShutterSpeed());
    if (updateRequest.getIso() != null) image.setIso(updateRequest.getIso());
    if (updateRequest.getCreateDate() != null) image.setCreateDate(updateRequest.getCreateDate());

    // Handle camera update with tracking
    if (updateRequest.getCamera() != null) {
      ContentImageUpdateRequest.CameraUpdate cameraUpdate = updateRequest.getCamera();
      if (Boolean.TRUE.equals(cameraUpdate.getRemove())) {
        image.setCamera(null);
      } else if (cameraUpdate.getNewValue() != null
          && !cameraUpdate.getNewValue().trim().isEmpty()) {
        String cameraName = cameraUpdate.getNewValue().trim();
        // Use helper method - no serial number provided, will generate UUID
        // Pass tracking set so newly created cameras are automatically tracked
        ContentCameraEntity camera =
            contentProcessingUtil.createCamera(cameraName, null, newCameras);
        image.setCamera(camera);
      } else if (cameraUpdate.getPrev() != null) {
        image.setCamera(metadataService.findCameraById(cameraUpdate.getPrev()));
      }
    }

    // Handle lens update with tracking
    if (updateRequest.getLens() != null) {
      ContentImageUpdateRequest.LensUpdate lensUpdate = updateRequest.getLens();
      if (Boolean.TRUE.equals(lensUpdate.getRemove())) {
        image.setLens(null);
      } else if (lensUpdate.getNewValue() != null && !lensUpdate.getNewValue().trim().isEmpty()) {
        String lensName = lensUpdate.getNewValue().trim();
        // Use helper method - no serial number provided, will generate UUID
        // Pass tracking set so newly created lenses are automatically tracked
        ContentLensEntity lens = contentProcessingUtil.createLens(lensName, null, newLenses);
        image.setLens(lens);
      } else if (lensUpdate.getPrev() != null) {
        image.setLens(metadataService.findLensById(lensUpdate.getPrev()));
      }
    }

    // Handle film type update with tracking
    if (updateRequest.getFilmType() != null) {
      ContentImageUpdateRequest.FilmTypeUpdate filmTypeUpdate = updateRequest.getFilmType();
      if (Boolean.TRUE.equals(filmTypeUpdate.getRemove())) {
        image.setFilmType(null);
      } else if (filmTypeUpdate.getNewValue() != null) {
        ContentRequests.NewFilmType newFilmTypeRequest = filmTypeUpdate.getNewValue();
        String displayName = newFilmTypeRequest.filmTypeName().trim();
        image.setFilmType(
            metadataService.findOrCreateFilmType(
                displayName, newFilmTypeRequest.defaultIso(), newFilmTypes));
      } else if (filmTypeUpdate.getPrev() != null) {
        image.setFilmType(metadataService.findFilmTypeById(filmTypeUpdate.getPrev()));
      }
    }

    // Handle location update using prev/new/remove pattern
    if (updateRequest.getLocation() != null) {
      CollectionRequests.LocationUpdate locationUpdate = updateRequest.getLocation();

      if (Boolean.TRUE.equals(locationUpdate.remove())) {
        // Remove location association
        image.setLocationId(null);
        log.info("Removed location association from image {}", image.getId());
      } else if (locationUpdate.newValue() != null && !locationUpdate.newValue().trim().isEmpty()) {
        // Create new location by name
        String locationName = locationUpdate.newValue().trim();
        LocationEntity location = metadataService.findOrCreateLocation(locationName);
        image.setLocationId(location.getId());
        log.info("Set location to: {} (ID: {})", locationName, location.getId());
      } else if (locationUpdate.prev() != null) {
        // Use existing location by ID
        LocationEntity location = metadataService.findLocationById(locationUpdate.prev());
        image.setLocationId(location.getId());
        log.info("Set location to existing location ID: {}", location.getId());
      }
    }
  }

  /**
   * OPTIMIZED: Update image tags with pre-fetched current tag IDs (avoids N+1 query). Used in batch
   * update operations.
   */
  private void updateImageTagsOptimized(
      ContentImageEntity image,
      CollectionRequests.TagUpdate tagUpdate,
      List<Long> currentTagIds,
      Set<ContentTagEntity> newTags) {
    // Convert pre-fetched IDs to entities
    Set<ContentTagEntity> currentTags =
        currentTagIds.stream()
            .map(
                tagId -> {
                  ContentTagEntity tag = new ContentTagEntity();
                  tag.setId(tagId);
                  return tag;
                })
            .collect(Collectors.toSet());

    Set<ContentTagEntity> updatedTags =
        contentProcessingUtil.updateTags(
            currentTags, tagUpdate, newTags // Track newly created tags
            // for response
            );
    image.setTags(updatedTags);

    // Save updated tags to database
    List<Long> updatedTagIds =
        updatedTags.stream()
            .map(ContentTagEntity::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    tagDao.saveContentTags(image.getId(), updatedTagIds);
  }

  /**
   * Add content (image) to new collections with specified visibility and orderIndex. Creates join
   * table entries for the content in the specified collections.
   *
   * @param image The image to add to collections
   * @param collections List of ChildCollection objects containing collectionId, visible, and
   *     orderIndex
   */
  private void handleAddToCollections(
      ContentImageEntity image, List<Records.ChildCollection> collections) {
    for (Records.ChildCollection childCollection : collections) {
      if (childCollection.collectionId() == null) {
        log.warn("Skipping collection addition: collectionId is null");
        continue;
      }

      // Verify collection exists
      collectionDao
          .findById(childCollection.collectionId())
          .orElseThrow(
              () ->
                  new ResourceNotFoundException(
                      "Collection not found: " + childCollection.collectionId()));

      // Check if this content is already in the collection
      Optional<CollectionContentEntity> existingOpt =
          collectionContentDao.findByCollectionIdAndContentId(
              childCollection.collectionId(), image.getId());

      if (existingOpt.isPresent()) {
        log.warn(
            "Image {} is already in collection {}. Skipping duplicate add.",
            image.getId(),
            childCollection.collectionId());
        continue;
      }

      // Determine orderIndex: use provided value or append to end
      Integer orderIndex = childCollection.orderIndex();
      if (orderIndex == null) {
        Integer maxOrder =
            collectionContentDao.getMaxOrderIndexForCollection(childCollection.collectionId());
        orderIndex = maxOrder != null ? maxOrder + 1 : 0;
      }

      // Create join table entry
      CollectionContentEntity joinEntry =
          CollectionContentEntity.builder()
              .collectionId(childCollection.collectionId())
              .contentId(image.getId())
              .orderIndex(orderIndex)
              .visible(childCollection.visible() != null ? childCollection.visible() : true)
              .build();

      collectionContentDao.save(joinEntry);
      log.info(
          "Added image {} to collection {} at orderIndex {} with visible={}",
          image.getId(),
          childCollection.collectionId(),
          orderIndex,
          joinEntry.getVisible());
    }
  }

  /**
   * OPTIMIZED: Update image people with pre-fetched current person IDs (avoids N+1 query). Used in
   * batch update operations.
   */
  private void updateImagePeopleOptimized(
      ContentImageEntity image,
      CollectionRequests.PersonUpdate personUpdate,
      List<Long> currentPersonIds,
      Set<ContentPersonEntity> newPeople) {
    // Convert pre-fetched IDs to entities
    Set<ContentPersonEntity> currentPeople =
        currentPersonIds.stream()
            .map(
                personId -> {
                  ContentPersonEntity person = new ContentPersonEntity();
                  person.setId(personId);
                  return person;
                })
            .collect(Collectors.toSet());

    Set<ContentPersonEntity> updatedPeople =
        contentProcessingUtil.updatePeople(
            currentPeople, personUpdate, newPeople // Track newly
            // created people
            // for response
            );
    image.setPeople(updatedPeople);

    // Save updated people to database
    List<Long> updatedPersonIds =
        updatedPeople.stream()
            .map(ContentPersonEntity::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    contentDao.saveImagePeople(image.getId(), updatedPersonIds);
  }

  @Transactional
  public Map<String, Object> deleteImages(List<Long> imageIds) {
    contentValidator.validateImageIds(imageIds);

    List<Long> deletedIds = new ArrayList<>();
    List<String> errors = new ArrayList<>();

    for (Long imageId : imageIds) {
      try {
        Optional<ContentImageEntity> imageOpt = contentDao.findImageById(imageId);
        if (!imageOpt.isPresent()) {
          errors.add("Image not found: " + imageId);
          continue;
        }

        ContentImageEntity image = imageOpt.get();

        // Delete from S3 before deleting from database
        contentProcessingUtil.deleteImageFromS3(image);

        // Delete from database
        contentDao.deleteImageById(imageId);
        deletedIds.add(imageId);

      } catch (Exception e) {
        errors.add("Failed to delete image " + imageId + ": " + e.getMessage());
        log.error("Error deleting image {}: {}", imageId, e.getMessage(), e);
      }
    }

    return Map.of("deletedIds", deletedIds, "deletedCount", deletedIds.size(), "errors", errors);
  }

  @Transactional(readOnly = true)
  public List<ContentModels.Image> getAllImages() {
    return contentDao.findAllImagesOrderByCreateDateDesc().stream()
        .map(
            entity ->
                (ContentModels.Image)
                    contentProcessingUtil.convertRegularContentEntityToModel(entity))
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public org.springframework.data.domain.Page<ContentModels.Image> getAllImages(
      org.springframework.data.domain.Pageable pageable) {
    // Get total count for pagination
    int total = contentDao.countImages();

    // Fetch paginated results from database
    List<ContentImageEntity> imageEntities =
        contentDao.findAllImagesOrderByCreateDateDesc(
            pageable.getPageSize(), (int) pageable.getOffset());

    // Convert to models
    List<ContentModels.Image> imageModels =
        imageEntities.stream()
            .map(
                entity ->
                    (ContentModels.Image)
                        contentProcessingUtil.convertRegularContentEntityToModel(entity))
            .collect(Collectors.toList());

    return new org.springframework.data.domain.PageImpl<>(imageModels, pageable, total);
  }

  /**
   * ORIGINAL METHOD: Sequential image processing. DEPRECATED: Use createImagesParallel() for better
   * performance. Kept for backward compatibility.
   */
  @Transactional
  public List<ContentModels.Image> createImages(Long collectionId, List<MultipartFile> files) {
    log.warn(
        "Using deprecated sequential image processing. Consider using parallel processing for better performance.");
    log.debug("Creating images for collection ID: {}", collectionId);

    contentValidator.validateFiles(files);

    // Verify collection exists
    collectionDao
        .findById(collectionId)
        .orElseThrow(() -> new ResourceNotFoundException("Collection not found: " + collectionId));

    List<ContentModels.Image> createdImages = new ArrayList<>();

    // Get the next order index for this collection
    Integer maxOrder = collectionContentDao.getMaxOrderIndexForCollection(collectionId);
    Integer orderIndex = maxOrder != null ? maxOrder + 1 : 0;

    for (MultipartFile file : files) {
      try {
        // Process file based on content type
        if (file.getContentType() != null && file.getContentType().startsWith("image/")) {
          if (file.getContentType().equals("image/gif")) {
            // Process as GIF - skip for now (future implementation)
            log.debug("Skipping GIF file (not yet implemented): {}", file.getOriginalFilename());
          } else {
            // STEP 1: Process and save the image content (NO collection reference)
            ContentImageEntity img = contentProcessingUtil.processImageContent(file, null);

            // STEP 2: Create join table entry linking content to collection
            CollectionContentEntity joinEntry =
                CollectionContentEntity.builder()
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
            if (contentModel instanceof ContentModels.Image imageModel) {
              createdImages.add(imageModel);
            } else {
              log.error(
                  "Expected ContentModels.Image but got {}",
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

  /** Batch size for parallel image processing to avoid overwhelming resources */
  private static final int PARALLEL_BATCH_SIZE = 10;

  /**
   * OPTIMIZED: Create and upload images with parallel processing.
   *
   * <p>Architecture: 1. PARALLEL: S3 upload, resize, convert using virtual threads (NO database
   * calls) 2. SEQUENTIAL: Save all results to database in a single short transaction
   *
   * <p>Images are processed in batches of PARALLEL_BATCH_SIZE to avoid overwhelming S3/memory.
   * Virtual threads handle I/O concurrency without blocking OS threads.
   *
   * @param collectionId ID of the collection to add images to
   * @param files List of image files to upload
   * @return List of successfully created images
   */
  public List<ContentModels.Image> createImagesParallel(
      Long collectionId, List<MultipartFile> files) {
    log.info(
        "Creating {} images for collection {} with parallel processing (batch size: {})",
        files.size(),
        collectionId,
        PARALLEL_BATCH_SIZE);

    contentValidator.validateFiles(files);

    // Verify collection exists (outside transaction)
    collectionDao
        .findById(collectionId)
        .orElseThrow(() -> new ResourceNotFoundException("Collection not found: " + collectionId));

    // PHASE 1: Prepare images in PARALLEL batches (S3 upload, resize, convert)
    // NO database calls happen here - only S3 I/O and CPU work
    List<PreparedImage> allPrepared = new ArrayList<>();

    for (int i = 0; i < files.size(); i += PARALLEL_BATCH_SIZE) {
      int end = Math.min(i + PARALLEL_BATCH_SIZE, files.size());
      List<MultipartFile> batch = files.subList(i, end);
      log.info("Processing batch {}-{} of {} files", i + 1, end, files.size());

      List<CompletableFuture<PreparedImage>> futures =
          batch.stream()
              .map(
                  file ->
                      CompletableFuture.supplyAsync(
                          () -> prepareImageAsync(file), imageProcessingExecutor))
              .toList();

      // Wait for this batch to complete before starting the next
      List<PreparedImage> batchResults =
          futures.stream().map(CompletableFuture::join).filter(Objects::nonNull).toList();

      allPrepared.addAll(batchResults);
      log.info(
          "Batch complete: {}/{} images prepared successfully so far",
          allPrepared.size(),
          files.size());
    }

    log.info(
        "All parallel processing complete: {}/{} images prepared successfully",
        allPrepared.size(),
        files.size());

    // PHASE 2: Save to database in a SINGLE SHORT TRANSACTION
    // All DB calls happen here: camera/lens/location lookups, duplicate detection, saves
    return saveProcessedImages(collectionId, allPrepared);
  }

  /**
   * Prepare a single image asynchronously (S3 upload, resize, convert). This method runs in a
   * virtual thread and does NOT touch the database.
   *
   * @param file The image file to process
   * @return Prepared image data, or null if processing failed
   */
  private PreparedImage prepareImageAsync(MultipartFile file) {
    String filename = file.getOriginalFilename();
    try {
      log.debug("Preparing image: {}", filename);

      // Skip non-images and GIFs
      if (file.getContentType() == null
          || !file.getContentType().startsWith("image/")
          || file.getContentType().equals("image/gif")) {
        log.debug("Skipping non-image or GIF: {}", filename);
        return null;
      }

      // S3 upload + resize + WebP conversion only - NO database calls
      ContentProcessingUtil.PreparedImageData prepared =
          contentProcessingUtil.prepareImageForUpload(file);

      return new PreparedImage(prepared, filename);

    } catch (Exception e) {
      log.error("Failed to prepare image {}: {}", filename, e.getMessage(), e);
      return null;
    }
  }

  /**
   * Save prepared images to database in a single transaction. Handles all DB work: camera/lens
   * lookups, duplicate detection, entity saves, and collection join entries.
   *
   * @param collectionId The collection to add images to
   * @param preparedImages List of prepared image data (S3 URLs + metadata)
   * @return List of created image models
   */
  @Transactional
  private List<ContentModels.Image> saveProcessedImages(
      Long collectionId, List<PreparedImage> preparedImages) {
    log.debug("Saving {} prepared images to database", preparedImages.size());

    List<ContentModels.Image> createdImages = new ArrayList<>();

    // Get the next order index for this collection
    Integer maxOrder = collectionContentDao.getMaxOrderIndexForCollection(collectionId);
    Integer orderIndex = maxOrder != null ? maxOrder + 1 : 0;

    for (PreparedImage prepared : preparedImages) {
      try {
        // Save to DB: camera/lens/location lookups + duplicate check + insert
        ContentImageEntity entity = contentProcessingUtil.savePreparedImage(prepared.data(), null);

        // Create join table entry linking content to collection
        CollectionContentEntity joinEntry =
            CollectionContentEntity.builder()
                .collectionId(collectionId)
                .contentId(entity.getId())
                .orderIndex(orderIndex)
                .visible(true)
                .build();

        collectionContentDao.save(joinEntry);

        // Convert to model
        ContentModel contentModel = contentProcessingUtil.convertEntityToModel(joinEntry);
        if (contentModel instanceof ContentModels.Image imageModel) {
          createdImages.add(imageModel);
        }

        orderIndex++;

      } catch (Exception e) {
        log.error("Failed to save image {}: {}", prepared.filename(), e.getMessage());
      }
    }

    log.info("Successfully saved {} images to collection {}", createdImages.size(), collectionId);
    return createdImages;
  }

  /** Record to hold prepared image data before database save */
  private record PreparedImage(ContentProcessingUtil.PreparedImageData data, String filename) {}

  @Transactional
  public ContentModels.Text createTextContent(ContentRequests.CreateTextContent request) {
    log.debug("Creating text content for collection ID: {}", request.collectionId());

    contentValidator.validateTextContent(request.textContent());

    // Verify collection exists
    collectionDao
        .findById(request.collectionId())
        .orElseThrow(
            () -> new ResourceNotFoundException("Collection not found: " + request.collectionId()));

    // Get the next order index for this collection
    Integer maxOrder = collectionContentDao.getMaxOrderIndexForCollection(request.collectionId());
    Integer orderIndex = maxOrder != null ? maxOrder + 1 : 0;

    // Create text content entity
    ContentTextEntity textEntity =
        ContentTextEntity.builder()
            .textContent(request.textContent().trim())
            .formatType(request.formType() != null ? request.formType().name() : "PLAIN")
            .build();

    // Save the text content
    textEntity = contentTextDao.save(textEntity);

    // Create join table entry linking content to collection
    CollectionContentEntity joinEntry =
        CollectionContentEntity.builder()
            .collectionId(request.collectionId())
            .contentId(textEntity.getId())
            .orderIndex(orderIndex)
            .visible(true)
            .build();

    collectionContentDao.save(joinEntry);

    log.info(
        "Created text content {} in collection {} at orderIndex {}",
        textEntity.getId(),
        request.collectionId(),
        orderIndex);

    // Convert to model and return
    ContentModel contentModel = contentProcessingUtil.convertEntityToModel(joinEntry);
    if (contentModel instanceof ContentModels.Text textModel) {
      return textModel;
    } else {
      throw new IllegalStateException(
          "Expected ContentModels.Text but got "
              + (contentModel != null ? contentModel.getClass() : "null"));
    }
  }
}
