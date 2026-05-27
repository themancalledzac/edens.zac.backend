package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.LocationRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentCameraEntity;
import edens.zac.portfolio.backend.entity.ContentFilmTypeEntity;
import edens.zac.portfolio.backend.entity.ContentGifEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.ContentLensEntity;
import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import edens.zac.portfolio.backend.entity.ContentTextEntity;
import edens.zac.portfolio.backend.entity.LocationEntity;
import edens.zac.portfolio.backend.entity.TagEntity;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.ContentImageUpdateRequest;
import edens.zac.portfolio.backend.model.ContentImageUpdateResponse;
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.ContentRequests;
import edens.zac.portfolio.backend.model.DownloadResolution;
import edens.zac.portfolio.backend.model.ImageSearchRequest;
import edens.zac.portfolio.backend.model.ImageSearchResponse;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.services.validator.ContentImageUpdateValidator;
import edens.zac.portfolio.backend.services.validator.ContentValidator;
import edens.zac.portfolio.backend.types.ContentType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Service for managing content, tags, and people. */
@Service
@Slf4j
public class ContentService {

  private final TagRepository tagRepository;
  private final ContentRepository contentRepository;
  private final CollectionRepository collectionRepository;
  private final PersonRepository personRepository;
  private final LocationRepository locationRepository;
  private final ContentMutationUtil contentMutationUtil;
  private final ContentModelConverter contentModelConverter;
  private final ImageProcessingService imageProcessingService;
  private final ContentImageUpdateValidator contentImageUpdateValidator;
  private final ContentValidator contentValidator;
  private final MetadataService metadataService;
  private final String cloudfrontDomain;

  private static final String FORMAT_WEB = "web";
  private static final String FORMAT_ORIGINAL = "original";

  public ContentService(
      TagRepository tagRepository,
      ContentRepository contentRepository,
      CollectionRepository collectionRepository,
      PersonRepository personRepository,
      LocationRepository locationRepository,
      ContentMutationUtil contentMutationUtil,
      ContentModelConverter contentModelConverter,
      ImageProcessingService imageProcessingService,
      ContentImageUpdateValidator contentImageUpdateValidator,
      ContentValidator contentValidator,
      MetadataService metadataService,
      @Value("${cloudfront.domain}") String cloudfrontDomain) {
    this.tagRepository = tagRepository;
    this.contentRepository = contentRepository;
    this.collectionRepository = collectionRepository;
    this.personRepository = personRepository;
    this.locationRepository = locationRepository;
    this.contentMutationUtil = contentMutationUtil;
    this.contentModelConverter = contentModelConverter;
    this.imageProcessingService = imageProcessingService;
    this.contentImageUpdateValidator = contentImageUpdateValidator;
    this.contentValidator = contentValidator;
    this.metadataService = metadataService;
    this.cloudfrontDomain = cloudfrontDomain;
  }

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
    Set<TagEntity> newlyCreatedTags = new HashSet<>();
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
    List<ContentImageEntity> imageList = contentRepository.findImagesByIds(imageIds);
    Map<Long, ContentImageEntity> imageMap =
        imageList.stream().collect(Collectors.toMap(ContentImageEntity::getId, img -> img));

    // Verify all requested images exist
    for (Long imageId : imageIds) {
      if (!imageMap.containsKey(imageId)) {
        throw new ResourceNotFoundException("Image not found: " + imageId);
      }
    }

    // OPTIMIZED: Pre-fetch all current tags, people, and locations for all images (avoids N+1)
    Map<Long, List<TagEntity>> currentTagsByImage = tagRepository.findTagsByContentIds(imageIds);
    Map<Long, List<ContentPersonEntity>> currentPeopleByImage =
        personRepository.findPeopleByContentIds(imageIds);
    Map<Long, List<LocationEntity>> currentLocationsByImage =
        locationRepository.findLocationsByContentIds(imageIds);
    Set<LocationEntity> newlyCreatedLocations = new HashSet<>();

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
        // Use pre-fetched full entities to avoid N+1 query
        if (update.getTags() != null) {
          List<TagEntity> currentTags = currentTagsByImage.getOrDefault(imageId, List.of());
          contentMutationUtil.updateImageTagsOptimized(
              image, update.getTags(), currentTags, newlyCreatedTags);
        }

        // Update people using prev/new/remove pattern (with tracking)
        // Use pre-fetched full entities to avoid N+1 query
        if (update.getPeople() != null) {
          List<ContentPersonEntity> currentPeople =
              currentPeopleByImage.getOrDefault(imageId, List.of());
          contentMutationUtil.updateImagePeopleOptimized(
              image, update.getPeople(), currentPeople, newlyCreatedPeople);
        }

        // Update locations using prev/new/remove pattern (with tracking)
        if (update.getLocations() != null) {
          List<LocationEntity> currentLocations =
              currentLocationsByImage.getOrDefault(imageId, List.of());
          contentMutationUtil.updateImageLocationsOptimized(
              image, update.getLocations(), currentLocations, newlyCreatedLocations);
        }

        // Handle collection updates using prev/new/remove pattern
        if (update.getCollections() != null) {
          CollectionRequests.CollectionUpdate collectionUpdate = update.getCollections();

          // Remove from collections if specified
          if (collectionUpdate.remove() != null && !collectionUpdate.remove().isEmpty()) {
            for (Long collectionIdToRemove : collectionUpdate.remove()) {
              collectionRepository.removeContentFromCollection(
                  collectionIdToRemove, List.of(image.getId()));
              log.info("Removed image {} from collection {}", image.getId(), collectionIdToRemove);
            }
          }

          // Update existing collection relationships (visibility, orderIndex)
          if (collectionUpdate.prev() != null && !collectionUpdate.prev().isEmpty()) {
            contentMutationUtil.handleContentChildCollectionUpdates(image, collectionUpdate.prev());
          }

          // Add to new collections if specified
          if (collectionUpdate.newValue() != null && !collectionUpdate.newValue().isEmpty()) {
            contentMutationUtil.handleAddToCollections(image, collectionUpdate.newValue());
          }
        }

        // Add to batch save list
        imagesToSave.add(image);

        // Convert to model and add to results
        ContentModels.Image imageModel =
            (ContentModels.Image) contentModelConverter.convertRegularContentEntityToModel(image);
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
        contentRepository.saveImage(image);
      }
      log.debug("Batch saved {} updated images", imagesToSave.size());
    }

    return buildUpdateResponse(
        updatedImages,
        errors,
        newlyCreatedTags,
        newlyCreatedPeople,
        newlyCreatedCameras,
        newlyCreatedLenses,
        newlyCreatedFilmTypes);
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
    if (updateRequest.getCaptureDate() != null)
      image.setCaptureDate(updateRequest.getCaptureDate());

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
            imageProcessingService.createCamera(cameraName, null, newCameras);
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
        ContentLensEntity lens = imageProcessingService.createLens(lensName, null, newLenses);
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

    // Location updates are now handled in updateImages() via
    // contentMutationUtil.updateImageLocationsOptimized
  }

  @Transactional
  public Map<String, Object> deleteImages(List<Long> imageIds) {
    contentValidator.validateImageIds(imageIds);

    List<Long> deletedIds = new ArrayList<>();
    List<String> errors = new ArrayList<>();

    for (Long imageId : imageIds) {
      try {
        Optional<ContentImageEntity> imageOpt = contentRepository.findImageById(imageId);
        if (imageOpt.isEmpty()) {
          errors.add("Image not found: " + imageId);
          continue;
        }

        ContentImageEntity image = imageOpt.get();

        // Delete from S3 before deleting from database
        imageProcessingService.deleteImageFromS3(image);

        // Delete from database
        contentRepository.deleteImageById(imageId);
        deletedIds.add(imageId);

      } catch (Exception e) {
        errors.add("Failed to delete image " + imageId + ": " + e.getMessage());
        log.error("Error deleting image {}: {}", imageId, e.getMessage(), e);
      }
    }

    return Map.of("deletedIds", deletedIds, "deletedCount", deletedIds.size(), "errors", errors);
  }

  @Transactional(readOnly = true)
  public ImageSearchResponse searchImages(ImageSearchRequest request) {
    int limit = request.size();
    int offset = request.page() * request.size();

    List<ContentImageEntity> entities = contentRepository.searchImages(request, limit, offset);
    long totalElements = contentRepository.countSearchImages(request);
    int totalPages = limit > 0 ? (int) Math.ceil((double) totalElements / limit) : 0;

    List<ContentModels.Image> images =
        entities.stream()
            .map(contentModelConverter::convertImageEntityToModel)
            .collect(Collectors.toList());

    return new ImageSearchResponse(images, totalElements, totalPages);
  }

  /**
   * Set locations on a collection if it doesn't already have any. Used when uploading to an
   * existing collection that is missing location metadata.
   */
  @Transactional
  public void setCollectionLocationsIfMissing(Long collectionId, List<Long> locationIds) {
    if (locationIds == null || locationIds.isEmpty()) {
      return;
    }
    collectionRepository
        .findById(collectionId)
        .orElseThrow(() -> new ResourceNotFoundException("Collection not found: " + collectionId));
    List<Long> existing = locationRepository.findCollectionLocationIds(collectionId);
    if (existing.isEmpty()) {
      locationRepository.saveCollectionLocations(collectionId, locationIds);
      log.info("Set locations {} on collection {}", locationIds, collectionId);
    }
  }

  @Transactional
  public ContentModels.Text createTextContent(ContentRequests.CreateTextContent request) {
    log.debug("Creating text content for collection ID: {}", request.collectionId());

    contentValidator.validateTextContent(request.textContent());

    // Verify collection exists
    collectionRepository
        .findById(request.collectionId())
        .orElseThrow(
            () -> new ResourceNotFoundException("Collection not found: " + request.collectionId()));

    int orderIndex = nextOrderIndex(request.collectionId());

    // Create text content entity
    ContentTextEntity textEntity =
        ContentTextEntity.builder()
            .textContent(request.textContent().trim())
            .formatType(request.formType() != null ? request.formType().name() : "PLAIN")
            .build();

    // Save the text content
    textEntity = contentRepository.saveText(textEntity);

    // Link to collection
    linkContentToCollection(request.collectionId(), textEntity.getId(), orderIndex);

    log.info(
        "Created text content {} in collection {} at orderIndex {}",
        textEntity.getId(),
        request.collectionId(),
        orderIndex);

    // Convert to model and return
    ContentModel contentModel =
        contentModelConverter.convertEntityToModel(
            CollectionContentEntity.builder()
                .collectionId(request.collectionId())
                .contentId(textEntity.getId())
                .orderIndex(orderIndex)
                .visible(true)
                .build());
    return castContentModel(contentModel, ContentModels.Text.class);
  }

  /** Returns the next available orderIndex for a collection (max + 1, or 0 if empty). */
  int nextOrderIndex(Long collectionId) {
    Integer maxOrder = collectionRepository.getMaxOrderIndexForCollection(collectionId);
    return maxOrder != null ? maxOrder + 1 : 0;
  }

  @Transactional
  public ContentModels.Gif createGif(
      Long collectionId, MultipartFile file, String title, Integer orderIndex) {
    log.debug("Creating GIF/MP4 for collection ID: {}", collectionId);

    collectionRepository
        .findById(collectionId)
        .orElseThrow(() -> new ResourceNotFoundException("Collection not found: " + collectionId));

    // Process file: upload to S3 + extract first-frame WebP thumbnail + save entity
    ContentGifEntity gifEntity = imageProcessingService.processGifContent(file, title);

    // Resolve order index: use provided value or append to end
    int resolvedOrderIndex = orderIndex != null ? orderIndex : nextOrderIndex(collectionId);

    // Link to collection
    linkContentToCollection(collectionId, gifEntity.getId(), resolvedOrderIndex);

    log.info(
        "Created GIF {} in collection {} at orderIndex {}",
        gifEntity.getId(),
        collectionId,
        resolvedOrderIndex);

    ContentModel contentModel =
        contentModelConverter.convertEntityToModel(
            CollectionContentEntity.builder()
                .collectionId(collectionId)
                .contentId(gifEntity.getId())
                .orderIndex(resolvedOrderIndex)
                .visible(true)
                .build());
    return castContentModel(contentModel, ContentModels.Gif.class);
  }

  /**
   * Delete a GIF/MP4 content block — removes the join-table linkage, the S3 objects (full media +
   * thumbnail), and the entity rows. Mirrors {@link #deleteImages} for the single-id case.
   *
   * @return id of the deleted gif, or null if no entity was found
   */
  @Transactional
  public Long deleteGif(Long id) {
    ContentGifEntity gif = contentRepository.findGifById(id).orElse(null);
    if (gif == null) {
      log.warn("Attempted to delete missing GIF: {}", id);
      return null;
    }
    imageProcessingService.deleteGifFromS3(gif);
    contentRepository.deleteGifById(id);
    log.info("Deleted GIF {}", id);
    return id;
  }

  /**
   * Patch a GIF/MP4 content block. Only non-null fields on the request are applied. Today's
   * surface: title, rating, tags, and collection memberships (prev/newValue/remove pattern). The
   * latter two reuse the same mutation utilities and join-table semantics that drive image updates
   * — see {@link ContentMutationUtil#handleAddToCollections(Long, java.util.List)} and {@link
   * ContentMutationUtil#handleContentChildCollectionUpdates(Long, java.util.List)}.
   *
   * <p>EXIF/equipment fields (camera, lens, ISO, etc.) intentionally have no analog for GIF — the
   * frontend modal greys them out for animated content.
   */
  @Transactional
  public ContentModels.Gif updateGif(Long id, ContentRequests.UpdateGif request) {
    ContentGifEntity gif =
        contentRepository
            .findGifById(id)
            .orElseThrow(() -> new ResourceNotFoundException("GIF not found: " + id));

    if (request.title() != null) {
      gif.setTitle(request.title());
    }
    if (request.rating() != null) {
      gif.setRating(request.rating());
    }

    // Tags: reuse the optimized image-tag helper — it only needs the content id + current tag
    // list + the prev/newValue/remove update payload. ContentGifEntity also exposes setTags.
    if (request.tags() != null) {
      List<TagEntity> currentTagEntities = tagRepository.findContentTags(gif.getId());
      Set<TagEntity> currentTags = new HashSet<>(currentTagEntities);
      Set<TagEntity> newlyCreatedTags = new HashSet<>();
      Set<TagEntity> updatedTags =
          contentMutationUtil.updateTags(currentTags, request.tags(), newlyCreatedTags);
      gif.setTags(updatedTags);
      List<Long> updatedTagIds =
          updatedTags.stream()
              .map(TagEntity::getId)
              .filter(Objects::nonNull)
              .distinct()
              .collect(Collectors.toList());
      tagRepository.saveContentTags(gif.getId(), updatedTagIds);
    }

    ContentGifEntity saved = contentRepository.saveGif(gif);
    log.info("Updated GIF {} (title={}, rating={})", id, saved.getTitle(), saved.getRating());

    // Collections: prev/newValue/remove pattern, same as images.
    if (request.collections() != null) {
      CollectionRequests.CollectionUpdate cu = request.collections();
      if (cu.remove() != null && !cu.remove().isEmpty()) {
        for (Long collectionIdToRemove : cu.remove()) {
          collectionRepository.removeContentFromCollection(collectionIdToRemove, List.of(id));
          log.info("Removed GIF {} from collection {}", id, collectionIdToRemove);
        }
      }
      if (cu.prev() != null && !cu.prev().isEmpty()) {
        contentMutationUtil.handleContentChildCollectionUpdates(id, cu.prev());
      }
      if (cu.newValue() != null && !cu.newValue().isEmpty()) {
        contentMutationUtil.handleAddToCollections(id, cu.newValue());
      }
    }

    ContentModel model =
        contentModelConverter.convertEntityToModel(
            CollectionContentEntity.builder()
                .contentId(saved.getId())
                .orderIndex(null)
                .visible(null)
                .build());
    return castContentModel(model, ContentModels.Gif.class);
  }

  // ---------------------------------------------------------------------------
  //  Read helpers for download endpoints
  // ---------------------------------------------------------------------------

  /**
   * Find a single {@link ContentImageEntity} by ID. Throws {@link ResourceNotFoundException} when
   * no row matches.
   */
  @Transactional(readOnly = true)
  public ContentImageEntity findImageById(Long id) {
    log.debug("Finding image by ID: {}", id);
    return contentRepository
        .findImageById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Image not found with ID: " + id));
  }

  /**
   * Find all images that belong to a collection, in the collection's display order.
   *
   * <p>Walks the {@code collection_content} join table for the collection, filters to entries whose
   * underlying content row is an IMAGE, and returns the image entities in {@code order_index}
   * order. Used by the per-collection ZIP download to assemble the archive.
   */
  @Transactional(readOnly = true)
  public List<ContentImageEntity> findImagesForCollection(Long collectionId) {
    log.debug("Finding images for collection ID: {}", collectionId);
    List<CollectionContentEntity> joinEntries =
        collectionRepository.findContentByCollectionIdOrderByOrderIndex(collectionId);
    if (joinEntries.isEmpty()) {
      return List.of();
    }
    List<Long> contentIds =
        joinEntries.stream().map(CollectionContentEntity::getContentId).distinct().toList();
    List<ContentImageEntity> images = contentRepository.findImagesByIds(contentIds);
    Map<Long, ContentImageEntity> imagesById =
        images.stream().collect(Collectors.toMap(ContentImageEntity::getId, img -> img));

    return joinEntries.stream()
        .map(entry -> imagesById.get(entry.getContentId()))
        .filter(Objects::nonNull)
        .toList();
  }

  /**
   * Resolve a parent {@link CollectionEntity} for an image, used by the per-image download endpoint
   * to decide whether to enforce a {@code passwordHash}-gated cookie. Images can belong to multiple
   * collections (many-to-many via {@code collection_content}); this returns any matching parent
   * (deterministic via {@code findContentByContentIdsIn} ordering). Returns empty when the image is
   * orphaned.
   */
  @Transactional(readOnly = true)
  public Optional<CollectionEntity> findCollectionForImage(Long imageId) {
    log.debug("Finding parent collection for image ID: {}", imageId);
    List<CollectionContentEntity> joinEntries =
        collectionRepository.findContentByContentIdsIn(List.of(imageId));
    if (joinEntries.isEmpty()) {
      return Optional.empty();
    }
    Long collectionId = joinEntries.getFirst().getCollectionId();
    if (collectionId == null) {
      return Optional.empty();
    }
    return collectionRepository.findById(collectionId);
  }

  // ---------------------------------------------------------------------------
  //  Download resolution
  // ---------------------------------------------------------------------------

  /**
   * Resolve which S3 object to serve for a single-image download. Throws {@link
   * IllegalArgumentException} for unsupported formats and {@link ResourceNotFoundException} when
   * {@code format=original} is requested but the image has no stored original.
   *
   * <p>The controller is responsible only for HTTP concerns (auth, streaming) -- all
   * format-vs-field, extension, and MIME selection lives here.
   */
  @Transactional(readOnly = true)
  public DownloadResolution resolveImageDownload(Long imageId, String format) {
    requireSupportedFormat(format);
    ContentImageEntity image = findImageById(imageId);
    boolean isOriginal = FORMAT_ORIGINAL.equalsIgnoreCase(format);

    String url;
    String extension;
    String contentType;
    if (isOriginal) {
      url = image.getImageUrlOriginal();
      if (url == null) {
        throw new ResourceNotFoundException("No original available for image " + imageId);
      }
      extension = ".jpg";
      contentType = "image/jpeg";
    } else {
      url = image.getImageUrlWeb();
      extension = ".webp";
      contentType = "image/webp";
    }

    String s3Key = extractS3Key(url);
    if (s3Key == null) {
      throw new ResourceNotFoundException(
          "Image " + imageId + " has no resolvable S3 key (url=" + url + ")");
    }
    String filename = sanitizeFilename(image.getOriginalFilename(), imageId, extension);
    return new DownloadResolution(s3Key, extension, contentType, filename);
  }

  /**
   * Resolve the per-image download targets for a collection ZIP. For {@code format=original},
   * prefers {@code imageUrlOriginal} per image but transparently falls back to {@code imageUrlWeb}
   * (and the {@code .webp} extension) when an original is not stored, so the ZIP is always
   * complete. Images whose configured CloudFront URL cannot be parsed into an S3 key are skipped
   * with a WARN log.
   *
   * <p>Throws {@link IllegalArgumentException} for unsupported formats.
   */
  @Transactional(readOnly = true)
  public List<DownloadResolution> resolveCollectionDownloadEntries(
      Long collectionId, String format) {
    requireSupportedFormat(format);
    boolean isOriginal = FORMAT_ORIGINAL.equalsIgnoreCase(format);
    List<ContentImageEntity> images = findImagesForCollection(collectionId);
    List<DownloadResolution> resolutions = new ArrayList<>(images.size());
    for (ContentImageEntity image : images) {
      String url = image.getImageUrlWeb();
      String extension = ".webp";
      String contentType = "image/webp";
      if (isOriginal) {
        String origUrl = image.getImageUrlOriginal();
        if (origUrl != null) {
          url = origUrl;
          extension = ".jpg";
          contentType = "image/jpeg";
        } else {
          log.warn(
              "No original for image {} in ZIP (collectionId={}); using web version",
              image.getId(),
              collectionId);
        }
      }
      String s3Key = extractS3Key(url);
      if (s3Key == null) {
        log.warn("Skipping image {} in ZIP (no resolvable S3 key, url={})", image.getId(), url);
        continue;
      }
      String filename = sanitizeFilename(image.getOriginalFilename(), image.getId(), extension);
      resolutions.add(new DownloadResolution(s3Key, extension, contentType, filename));
    }
    return resolutions;
  }

  private void requireSupportedFormat(String format) {
    if (!FORMAT_WEB.equalsIgnoreCase(format) && !FORMAT_ORIGINAL.equalsIgnoreCase(format)) {
      throw new IllegalArgumentException(
          "Unsupported download format: " + format + " (supported: web, original)");
    }
  }

  /**
   * Build a sanitized {@code Content-Disposition} filename for a collection ZIP. Same sanitization
   * rules as per-image entries -- strips path components, control characters, and quotes so the
   * value is safe to embed in an HTTP header. The slug is already constrained at write time, but
   * routing it through here keeps the ZIP filename consistent with the per-entry names and adds
   * defense-in-depth against any legacy slug that slipped past validation.
   */
  public String collectionZipFilename(String slug, Long collectionId) {
    String base = slug + "-" + collectionId;
    return sanitizeFilename(base, collectionId, ".zip");
  }

  /**
   * Translate a CloudFront URL stored on the entity (e.g. {@code
   * https://{cloudfront-domain}/Image/Web/2025/01/foo.webp}) back to the underlying S3 key. Returns
   * {@code null} when the URL is empty or doesn't match the configured CloudFront domain.
   */
  private String extractS3Key(String cloudfrontUrl) {
    if (cloudfrontUrl == null || cloudfrontUrl.isEmpty()) {
      return null;
    }
    String prefix = "https://" + cloudfrontDomain + "/";
    if (cloudfrontUrl.startsWith(prefix)) {
      return cloudfrontUrl.substring(prefix.length());
    }
    log.warn("Cloudfront URL doesn't match configured domain: {}", cloudfrontUrl);
    return null;
  }

  /**
   * Sanitize a filename for use in {@code Content-Disposition} or as a ZIP entry. Strips path
   * traversal and control characters, normalises the extension, falls back to a uuid if the input
   * is unusable.
   */
  private String sanitizeFilename(String original, Object idForFallback, String extension) {
    String base = original;
    if (base != null) {
      // Drop any path component to prevent traversal (`/`, `\`).
      int slashIdx = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
      if (slashIdx >= 0) {
        base = base.substring(slashIdx + 1);
      }
      base = base.replaceAll("[\\p{Cntrl}\"\\\\]", "");
      // Strip any existing image extension, we'll reapply the canonical one.
      base = base.replaceAll("(?i)\\.(jpg|jpeg|webp|png|tif|tiff)$", "");
      base = base.trim();
    }
    if (base == null || base.isEmpty()) {
      base =
          (idForFallback != null ? idForFallback.toString() : "download")
              + "-"
              + UUID.randomUUID().toString().substring(0, 8);
    }
    return base + extension;
  }

  // ---------------------------------------------------------------------------
  //  Shared helpers (package-private for ImageUploadPipelineService)
  // ---------------------------------------------------------------------------

  /**
   * Link content to a collection with the given orderIndex and visible=true.
   *
   * @param collectionId The collection to link to
   * @param contentId The content to link
   * @param orderIndex The order index within the collection
   */
  void linkContentToCollection(Long collectionId, Long contentId, int orderIndex) {
    linkContentToCollection(collectionId, contentId, orderIndex, true);
  }

  /**
   * Link content to a collection with the given orderIndex and visibility.
   *
   * @param collectionId The collection to link to
   * @param contentId The content to link
   * @param orderIndex The order index within the collection
   * @param visible Whether the content is visible in the collection
   */
  void linkContentToCollection(Long collectionId, Long contentId, int orderIndex, boolean visible) {
    CollectionEntity collection =
        collectionRepository
            .findById(collectionId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Collection not found: " + collectionId));

    if (collection.getType().isParentType()) {
      ContentType contentType =
          contentRepository
              .findContentTypeById(contentId)
              .orElseThrow(() -> new ResourceNotFoundException("Content not found: " + contentId));
      if (contentType != ContentType.COLLECTION) {
        throw new IllegalArgumentException(
            "Parent-type collections can only contain child collections, not "
                + contentType
                + " content");
      }
    }

    CollectionContentEntity joinEntry =
        CollectionContentEntity.builder()
            .collectionId(collectionId)
            .contentId(contentId)
            .orderIndex(orderIndex)
            .visible(visible)
            .build();
    collectionRepository.saveContent(joinEntry);
  }

  /**
   * Cast a ContentModel to the expected subtype, throwing IllegalStateException on mismatch.
   *
   * @param model The content model to cast
   * @param expectedType The expected concrete type
   * @return The cast model
   */
  @SuppressWarnings("unchecked")
  static <T extends ContentModel> T castContentModel(ContentModel model, Class<T> expectedType) {
    if (expectedType.isInstance(model)) return expectedType.cast(model);
    throw new IllegalStateException(
        "Expected "
            + expectedType.getSimpleName()
            + " but got "
            + (model != null ? model.getClass().getSimpleName() : "null"));
  }

  /**
   * Build the response map for updateImages, assembling updated images and newly created metadata.
   */
  private Map<String, Object> buildUpdateResponse(
      List<ContentModels.Image> updatedImages,
      List<String> errors,
      Set<TagEntity> newlyCreatedTags,
      Set<ContentPersonEntity> newlyCreatedPeople,
      Set<ContentCameraEntity> newlyCreatedCameras,
      Set<ContentLensEntity> newlyCreatedLenses,
      Set<ContentFilmTypeEntity> newlyCreatedFilmTypes) {
    var newMetadata =
        ContentImageUpdateResponse.NewMetadata.builder()
            .tags(
                mapOrNull(
                    newlyCreatedTags, e -> new Records.Tag(e.getId(), e.getTagName(), e.getSlug())))
            .people(
                mapOrNull(
                    newlyCreatedPeople,
                    e -> new Records.Person(e.getId(), e.getPersonName(), e.getSlug())))
            .cameras(
                mapOrNull(newlyCreatedCameras, ContentModelConverter::cameraEntityToCameraModel))
            .lenses(mapOrNull(newlyCreatedLenses, ContentModelConverter::lensEntityToLensModel))
            .filmTypes(mapOrNull(newlyCreatedFilmTypes, metadataService::toFilmTypeModel))
            .build();

    return Map.of(
        "updatedImages", updatedImages,
        "newMetadata", newMetadata,
        "errors", errors.isEmpty() ? List.of() : errors);
  }

  /** Map a set to a list using the given mapper, returning null if the set is empty. */
  private static <T, R> List<R> mapOrNull(Set<T> set, Function<T, R> mapper) {
    return set.isEmpty() ? null : set.stream().map(mapper).collect(Collectors.toList());
  }
}
