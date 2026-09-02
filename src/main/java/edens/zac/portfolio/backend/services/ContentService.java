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
import edens.zac.portfolio.backend.model.PagedResponse;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.services.validator.ContentImageUpdateValidator;
import edens.zac.portfolio.backend.services.validator.ContentValidator;
import edens.zac.portfolio.backend.types.ContentType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

  /**
   * Apply a batch of image metadata updates, collecting per-item failures into the response rather
   * than aborting the batch.
   *
   * <p>Two invariants are established before the per-item loop and relied on inside it: {@link
   * ContentImageUpdateValidator#validate} rejects any request with a null id, and the containment
   * check over {@code imageMap} throws for any id with no matching row. So inside the loop {@code
   * update.getId()} is non-null and {@code imageMap.get(id)} is present.
   *
   * <p>Every read the loop needs is hoisted above it to avoid N+1: the images themselves in one
   * query, then the current tags, people and locations for all of them in one query each. The
   * per-item helpers take those pre-fetched entities rather than re-querying.
   *
   * <p>The writes are not hoisted and are one statement per image by design. The loop already
   * writes per image through {@code saveContentTags} and {@code saveContentPeople}, so batching
   * only the {@code saveImage} calls would leave the endpoint O(N) in statements while adding a
   * second persistence path for images.
   *
   * @param updates the image updates to apply; must be non-empty and each must carry an id
   * @return updated image models, per-item errors, and any metadata entities created along the way
   */
  @Transactional
  @CacheEvict(
      value = "generalMetadata",
      allEntries = true,
      condition = "#updates != null && !#updates.isEmpty()")
  public Map<String, Object> updateImages(List<ContentImageUpdateRequest> updates) {
    contentValidator.validateImageUpdates(updates);

    List<ContentModels.Image> updatedImages = new ArrayList<>();
    Set<TagEntity> newlyCreatedTags = new HashSet<>();
    Set<ContentPersonEntity> newlyCreatedPeople = new HashSet<>();
    Set<ContentCameraEntity> newlyCreatedCameras = new HashSet<>();
    Set<ContentLensEntity> newlyCreatedLenses = new HashSet<>();
    Set<ContentFilmTypeEntity> newlyCreatedFilmTypes = new HashSet<>();
    List<String> errors = new ArrayList<>();

    List<Long> imageIds =
        updates.stream()
            .map(ContentImageUpdateRequest::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    if (imageIds.isEmpty()) {
      throw new IllegalArgumentException("No valid image IDs found in update requests");
    }

    for (ContentImageUpdateRequest update : updates) {
      contentImageUpdateValidator.validate(update);
    }

    List<ContentImageEntity> imageList = contentRepository.findImagesByIds(imageIds);
    Map<Long, ContentImageEntity> imageMap =
        imageList.stream().collect(Collectors.toMap(ContentImageEntity::getId, img -> img));

    for (Long imageId : imageIds) {
      if (!imageMap.containsKey(imageId)) {
        throw new ResourceNotFoundException("Image not found: " + imageId);
      }
    }

    Map<Long, List<TagEntity>> currentTagsByImage = tagRepository.findTagsByContentIds(imageIds);
    Map<Long, List<ContentPersonEntity>> currentPeopleByImage =
        personRepository.findPeopleByContentIds(imageIds);
    Map<Long, List<LocationEntity>> currentLocationsByImage =
        locationRepository.findLocationsByContentIds(imageIds);
    Set<LocationEntity> newlyCreatedLocations = new HashSet<>();

    List<ContentImageEntity> imagesToSave = new ArrayList<>();

    for (ContentImageUpdateRequest update : updates) {
      try {
        Long imageId = update.getId();
        ContentImageEntity image = imageMap.get(imageId);

        applyImageUpdatesWithTracking(
            image, update, newlyCreatedCameras, newlyCreatedLenses, newlyCreatedFilmTypes);

        if (update.getTags() != null) {
          List<TagEntity> currentTags = currentTagsByImage.getOrDefault(imageId, List.of());
          contentMutationUtil.updateImageTagsOptimized(
              image, update.getTags(), currentTags, newlyCreatedTags);
        }

        if (update.getPeople() != null) {
          List<ContentPersonEntity> currentPeople =
              currentPeopleByImage.getOrDefault(imageId, List.of());
          contentMutationUtil.updateImagePeopleOptimized(
              image, update.getPeople(), currentPeople, newlyCreatedPeople);
        }

        if (update.getLocations() != null) {
          List<LocationEntity> currentLocations =
              currentLocationsByImage.getOrDefault(imageId, List.of());
          contentMutationUtil.updateImageLocationsOptimized(
              image, update.getLocations(), currentLocations, newlyCreatedLocations);
        }

        if (update.getCollections() != null) {
          CollectionRequests.CollectionUpdate collectionUpdate = update.getCollections();

          if (collectionUpdate.remove() != null && !collectionUpdate.remove().isEmpty()) {
            for (Long collectionIdToRemove : collectionUpdate.remove()) {
              collectionRepository.removeContentFromCollection(
                  collectionIdToRemove, List.of(image.getId()));
              log.info("Removed image {} from collection {}", image.getId(), collectionIdToRemove);
            }
          }

          if (collectionUpdate.prev() != null && !collectionUpdate.prev().isEmpty()) {
            contentMutationUtil.handleContentChildCollectionUpdates(
                image.getId(), collectionUpdate.prev());
          }

          if (collectionUpdate.newValue() != null && !collectionUpdate.newValue().isEmpty()) {
            contentMutationUtil.handleAddToCollections(image.getId(), collectionUpdate.newValue());
          }
        }

        imagesToSave.add(image);

        ContentModels.Image imageModel =
            (ContentModels.Image) contentModelConverter.convertRegularContentEntityToModel(image);
        updatedImages.add(imageModel);

      } catch (IllegalArgumentException e) {
        errors.add(e.getMessage());
        log.warn("Entity not found during update: {}", e.getMessage());
      } catch (Exception e) {
        errors.add("Error updating image " + update.getId() + ": " + e.getMessage());
        log.error("Error updating image {}: {}", update.getId(), e.getMessage(), e);
      }
    }

    if (!imagesToSave.isEmpty()) {
      for (ContentImageEntity image : imagesToSave) {
        contentRepository.saveImage(image);
      }
      log.debug("Saved {} updated images", imagesToSave.size());
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
   *
   * <p>Cameras and lenses are created with no serial number, so one is generated as a UUID. The
   * tracking sets are passed down so anything created along the way is reported back to the caller.
   *
   * <p>Locations are NOT handled here -- {@link #updateImages} applies them via {@code
   * contentMutationUtil.updateImageLocationsOptimized}, using its pre-fetched location map.
   */
  private void applyImageUpdatesWithTracking(
      ContentImageEntity image,
      ContentImageUpdateRequest updateRequest,
      Set<ContentCameraEntity> newCameras,
      Set<ContentLensEntity> newLenses,
      Set<ContentFilmTypeEntity> newFilmTypes) {

    if (updateRequest.getTitle() != null) image.setTitle(updateRequest.getTitle());
    if (updateRequest.getCaption() != null) image.setCaption(updateRequest.getCaption());
    if (updateRequest.getAlt() != null) image.setAlt(updateRequest.getAlt());
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

    if (updateRequest.getCamera() != null) {
      ContentImageUpdateRequest.CameraUpdate cameraUpdate = updateRequest.getCamera();
      if (Boolean.TRUE.equals(cameraUpdate.getRemove())) {
        image.setCamera(null);
      } else if (cameraUpdate.getNewValue() != null
          && !cameraUpdate.getNewValue().trim().isEmpty()) {
        String cameraName = cameraUpdate.getNewValue().trim();
        ContentCameraEntity camera =
            imageProcessingService.createCamera(cameraName, null, newCameras);
        image.setCamera(camera);
      } else if (cameraUpdate.getPrev() != null) {
        image.setCamera(metadataService.findCameraById(cameraUpdate.getPrev()));
      }
    }

    if (updateRequest.getLens() != null) {
      ContentImageUpdateRequest.LensUpdate lensUpdate = updateRequest.getLens();
      if (Boolean.TRUE.equals(lensUpdate.getRemove())) {
        image.setLens(null);
      } else if (lensUpdate.getNewValue() != null && !lensUpdate.getNewValue().trim().isEmpty()) {
        String lensName = lensUpdate.getNewValue().trim();
        ContentLensEntity lens = imageProcessingService.createLens(lensName, null, newLenses);
        image.setLens(lens);
      } else if (lensUpdate.getPrev() != null) {
        image.setLens(metadataService.findLensById(lensUpdate.getPrev()));
      }
    }

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
  }

  /**
   * Bulk-delete content blocks by id. The admin grid mixes images and GIF/MP4 blocks in one
   * selection, so ids are dispatched on their stored {@link ContentType} rather than assumed to be
   * images -- a GIF id sent here used to fall through as "Image not found".
   *
   * <p>S3 objects are deleted before the database rows, so a failed S3 delete aborts the item and
   * leaves the row rather than orphaning the object.
   */
  @Transactional
  public Map<String, Object> deleteImages(List<Long> imageIds) {
    contentValidator.validateImageIds(imageIds);

    List<Long> deletedIds = new ArrayList<>();
    List<String> errors = new ArrayList<>();

    for (Long imageId : imageIds) {
      try {
        ContentType contentType = contentRepository.findContentTypeById(imageId).orElse(null);
        if (contentType == null) {
          errors.add("Content not found: " + imageId);
          continue;
        }

        switch (contentType) {
          case IMAGE -> {
            ContentImageEntity image = contentRepository.findImageById(imageId).orElse(null);
            if (image == null) {
              errors.add("Image not found: " + imageId);
              continue;
            }

            imageProcessingService.deleteImageFromS3(image);

            contentRepository.deleteImageById(imageId);
            deletedIds.add(imageId);
          }
          case GIF -> {
            Long deletedGifId = deleteGif(imageId);
            if (deletedGifId == null) {
              errors.add("GIF not found: " + imageId);
              continue;
            }
            deletedIds.add(deletedGifId);
          }
          default ->
              errors.add(
                  "Content " + imageId + " is a " + contentType + " and cannot be deleted here");
        }

      } catch (Exception e) {
        errors.add("Failed to delete image " + imageId + ": " + e.getMessage());
        log.error("Error deleting image {}: {}", imageId, e.getMessage(), e);
      }
    }

    return Map.of("deletedIds", deletedIds, "deletedCount", deletedIds.size(), "errors", errors);
  }

  /**
   * Paged image search.
   *
   * <p>Results are batch-converted -- three queries total for tags, people and locations -- rather
   * than mapped through the singular converter, which fires three per-image queries and becomes N+1
   * on large pages.
   */
  @Transactional(readOnly = true)
  public PagedResponse<ContentModels.Image> searchImages(ImageSearchRequest request) {
    int limit = request.size();
    int offset = request.page() * request.size();

    List<ContentImageEntity> entities = contentRepository.searchImages(request, limit, offset);
    long totalElements = contentRepository.countSearchImages(request);
    int totalPages = limit > 0 ? (int) Math.ceil((double) totalElements / limit) : 0;

    List<ContentModels.Image> images =
        contentModelConverter.batchConvertImageEntitiesToModels(entities);

    return new PagedResponse<>(
        images, totalElements, totalPages, request.page(), request.page() + 1 >= totalPages);
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

    collectionRepository
        .findById(request.collectionId())
        .orElseThrow(
            () -> new ResourceNotFoundException("Collection not found: " + request.collectionId()));

    int orderIndex = nextOrderIndex(request.collectionId());

    ContentTextEntity textEntity =
        ContentTextEntity.builder()
            .textContent(request.textContent().trim())
            .formatType(request.formType() != null ? request.formType().getValue() : "plain")
            .build();

    textEntity = contentRepository.saveText(textEntity);

    linkContentToCollection(request.collectionId(), textEntity.getId(), orderIndex);

    log.info(
        "Created text content {} in collection {} at orderIndex {}",
        textEntity.getId(),
        request.collectionId(),
        orderIndex);

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
    return collectionRepository.getNextOrderIndexForCollection(collectionId);
  }

  /**
   * Create a GIF/MP4 content block and link it to a collection. {@code processGifContent} uploads
   * the file to S3, extracts a first-frame WebP thumbnail, and saves the entity. A null {@code
   * orderIndex} appends to the end of the collection.
   */
  @Transactional
  public ContentModels.Gif createGif(
      Long collectionId, MultipartFile file, String title, Integer orderIndex) {
    log.debug("Creating GIF/MP4 for collection ID: {}", collectionId);

    collectionRepository
        .findById(collectionId)
        .orElseThrow(() -> new ResourceNotFoundException("Collection not found: " + collectionId));

    ContentGifEntity gifEntity = imageProcessingService.processGifContent(file, title);

    int resolvedOrderIndex = orderIndex != null ? orderIndex : nextOrderIndex(collectionId);

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
   * surface: title, rating, tags, people, locations, and collection memberships
   * (prev/newValue/remove pattern). The membership fields reuse the same mutation utilities and
   * join-table semantics that drive image updates — see {@link
   * ContentMutationUtil#handleAddToCollections(Long, java.util.List)} and {@link
   * ContentMutationUtil#handleContentChildCollectionUpdates(Long, java.util.List)}. People and
   * locations write to the content-level joins via {@link ContentRepository#saveContentPeople(Long,
   * java.util.List)} and {@link LocationRepository#saveContentLocations(Long, java.util.List)}.
   *
   * <p>Tags reuse the optimized image-tag helper, which needs only the content id, the current tag
   * list, and the prev/newValue/remove payload; {@link ContentGifEntity} exposes {@code setTags}
   * like an image does. People and locations go to their content-level joins ({@code
   * content_image_people} and {@code content_image_locations}, both content_id-keyed) in the same
   * merge-then-persist shape, via the content-agnostic helpers. {@link
   * LocationRepository#findLocationsByContentIds} returns a map keyed by content id, so this gif's
   * list is extracted before merging.
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
    if (request.captureDate() != null) {
      gif.setCaptureDate(request.captureDate());
    }

    if (request.tags() != null) {
      List<TagEntity> currentTagEntities = tagRepository.findContentTags(gif.getId());
      Set<TagEntity> currentTags = new HashSet<>(currentTagEntities);
      Set<TagEntity> newlyCreatedTags = new HashSet<>();
      Set<TagEntity> updatedTags =
          contentMutationUtil.updateTags(currentTags, request.tags(), newlyCreatedTags);
      List<Long> updatedTagIds =
          updatedTags.stream()
              .map(TagEntity::getId)
              .filter(Objects::nonNull)
              .distinct()
              .collect(Collectors.toList());
      tagRepository.saveContentTags(gif.getId(), updatedTagIds);
    }

    if (request.people() != null) {
      List<ContentPersonEntity> currentPeople = personRepository.findContentPeople(gif.getId());
      Set<ContentPersonEntity> updatedPeople =
          contentMutationUtil.updatePeople(
              new HashSet<>(currentPeople), request.people(), new HashSet<>());
      List<Long> updatedPersonIds =
          updatedPeople.stream()
              .map(ContentPersonEntity::getId)
              .filter(Objects::nonNull)
              .distinct()
              .collect(Collectors.toList());
      contentRepository.saveContentPeople(gif.getId(), updatedPersonIds);
    }

    if (request.locations() != null) {
      List<LocationEntity> currentLocations =
          locationRepository
              .findLocationsByContentIds(List.of(gif.getId()))
              .getOrDefault(gif.getId(), List.of());
      Set<LocationEntity> updatedLocations =
          contentMutationUtil.updateLocations(
              new HashSet<>(currentLocations), request.locations(), new HashSet<>());
      List<Long> updatedLocationIds =
          updatedLocations.stream()
              .map(LocationEntity::getId)
              .filter(Objects::nonNull)
              .distinct()
              .collect(Collectors.toList());
      locationRepository.saveContentLocations(gif.getId(), updatedLocationIds);
    }

    ContentGifEntity saved = contentRepository.saveGif(gif);
    log.info("Updated GIF {} (title={}, rating={})", id, saved.getTitle(), saved.getRating());

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
   * Every password-protected collection that contains this image. The per-image download endpoint
   * must satisfy the gate for ALL of them: an image can belong to several collections at once
   * (many-to-many via {@code collection_content}), and resolving a single arbitrary parent let an
   * unprotected wrapper waive a protected gallery's password on a nondeterministic fraction of
   * requests. Returns an empty list when the image is orphaned or every parent is unprotected.
   */
  @Transactional(readOnly = true)
  public List<CollectionEntity> findProtectedCollectionsForImage(Long imageId) {
    log.debug("Finding protected parent collections for image ID: {}", imageId);
    return findProtectedCollectionsForContent(List.of(imageId));
  }

  /**
   * Every password-protected collection that gates any image a collection ZIP download would
   * return. The password on the slug in the URL is only half the question: under Rule B the same
   * image may sit in a public wrapper and a protected gallery at once, so authorizing the requested
   * collection alone let the wrapper waive the gallery's password -- the exact defect S1 closed on
   * the per-image endpoint, on the endpoint that hands out whole ZIPs and, for a one-image subset,
   * a 302 straight to a presigned full-resolution original.
   *
   * <p>Resolves precisely the images the download would serve (collection membership, then the
   * optional {@code imageIds} subset filter, mirroring {@link #resolveCollectionDownloadEntries}),
   * then batch-loads every collection those images belong to and keeps the protected ones. The
   * membership read is deliberately repeated rather than threaded through the resolver: a download
   * is rare and dominated by the ZIP itself, and an auth pass that computes its own answer cannot
   * silently drift from the resolver's.
   *
   * <p>Returns an empty list when nothing the download would serve is gated.
   */
  @Transactional(readOnly = true)
  public List<CollectionEntity> findProtectedCollectionsForCollectionDownload(
      Long collectionId, Collection<Long> imageIds) {
    log.debug("Finding protected parent collections for collection download {}", collectionId);
    List<Long> resolvedIds =
        findImagesForCollection(collectionId).stream().map(ContentImageEntity::getId).toList();
    if (imageIds != null && !imageIds.isEmpty()) {
      Set<Long> wanted = new HashSet<>(imageIds);
      resolvedIds = resolvedIds.stream().filter(wanted::contains).toList();
    }
    return findProtectedCollectionsForContent(resolvedIds);
  }

  /**
   * Shared fail-closed lookup behind both download gates: every password-protected collection that
   * any of these content ids belongs to, batch-loaded in two queries regardless of id count.
   */
  private List<CollectionEntity> findProtectedCollectionsForContent(List<Long> contentIds) {
    if (contentIds.isEmpty()) {
      return List.of();
    }
    List<Long> collectionIds =
        collectionRepository.findContentByContentIdsIn(contentIds).stream()
            .map(CollectionContentEntity::getCollectionId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    if (collectionIds.isEmpty()) {
      return List.of();
    }
    return collectionRepository.findByIds(collectionIds).stream()
        .filter(collection -> collection.getGalleryPassword() != null)
        .toList();
  }

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
    return new DownloadResolution(s3Key, contentType, filename);
  }

  /**
   * Resolve the per-image download targets for a collection ZIP, optionally restricted to a subset
   * of image ids. When {@code imageIds} is {@code null} or empty the whole collection is resolved;
   * otherwise the collection's images are filtered to those whose id is in {@code imageIds},
   * preserving collection display order. Filtering only ever narrows the collection's own images,
   * so an id that does not belong to this collection is silently dropped -- this is the auth
   * boundary, and no image outside the authorized collection can be requested.
   *
   * <p>Format resolution semantics are unchanged: for {@code format=original}, prefers {@code
   * imageUrlOriginal} per image but transparently falls back to {@code imageUrlWeb} (and the {@code
   * .webp} extension) when an original is not stored, so the ZIP is always complete. Images whose
   * configured CloudFront URL cannot be parsed into an S3 key are skipped with a WARN log.
   *
   * <p>Throws {@link IllegalArgumentException} for unsupported formats.
   */
  @Transactional(readOnly = true)
  public List<DownloadResolution> resolveCollectionDownloadEntries(
      Long collectionId, String format, Collection<Long> imageIds) {
    requireSupportedFormat(format);
    boolean isOriginal = FORMAT_ORIGINAL.equalsIgnoreCase(format);
    List<ContentImageEntity> images = findImagesForCollection(collectionId);

    if (imageIds != null && !imageIds.isEmpty()) {
      Set<Long> wanted = new HashSet<>(imageIds);
      images = images.stream().filter(img -> wanted.contains(img.getId())).toList();
    }

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
      resolutions.add(new DownloadResolution(s3Key, contentType, filename));
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
      int slashIdx = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
      if (slashIdx >= 0) {
        base = base.substring(slashIdx + 1);
      }
      base = base.replaceAll("[\\p{Cntrl}\"\\\\]", "");
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
   * <p>The collection is checked for existence only. Any collection may hold any content type (Rule
   * B), so nothing about the collection itself is inspected beyond it existing.
   *
   * @param collectionId The collection to link to
   * @param contentId The content to link
   * @param orderIndex The order index within the collection
   * @param visible Whether the content is visible in the collection
   */
  void linkContentToCollection(Long collectionId, Long contentId, int orderIndex, boolean visible) {
    collectionRepository
        .findById(collectionId)
        .orElseThrow(() -> new ResourceNotFoundException("Collection not found: " + collectionId));

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
    throw new RuntimeException(
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
                    newlyCreatedPeople, e -> new Records.Person(e.getId(), e.getPersonName())))
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
