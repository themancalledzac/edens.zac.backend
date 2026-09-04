package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.config.DefaultValues;
import edens.zac.portfolio.backend.dao.CollectionPeopleRepository;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.CollectionSiblingRepository;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.LocationRepository;
import edens.zac.portfolio.backend.dao.PersonRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
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
import edens.zac.portfolio.backend.types.CollectionVisibility;
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
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class CollectionProcessingUtil {

  private final CollectionRepository collectionRepository;
  private final CollectionPeopleRepository collectionPeopleRepository;
  private final CollectionSiblingRepository collectionSiblingRepository;
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
   * Batch-convert a list of CollectionEntity to basic models. Pre-fetches all locations, people and
   * cover images in batch queries to avoid N+1. Each collection with a cover image would otherwise
   * trigger 5 individual queries (location + cover image + cover's tags/people/location).
   *
   * <p>People are one query for the whole list, so there is no N+1 even when this is reached one
   * collection at a time through {@link #convertToBasicModel}.
   *
   * @param entities The collection entities to convert
   * @return List of converted collection models with locations and cover images populated
   */
  public List<CollectionModel> batchConvertToBasicModels(List<CollectionEntity> entities) {
    if (entities == null || entities.isEmpty()) {
      return new ArrayList<>();
    }

    List<Long> collectionIds = entities.stream().map(CollectionEntity::getId).toList();
    Map<Long, List<LocationEntity>> locationsByCollectionId =
        locationRepository.findLocationsByCollectionIds(collectionIds);

    Map<Long, List<Records.Person>> peopleByCollectionId =
        collectionPeopleRepository.findPeopleForCollections(collectionIds);

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

    List<Long> coverContentIds = new ArrayList<>(coverImagesById.keySet());
    Map<Long, List<TagEntity>> tagsByContentId =
        tagRepository.findTagsByContentIds(coverContentIds);
    Map<Long, List<ContentPersonEntity>> peopleByContentId =
        personRepository.findPeopleByContentIds(coverContentIds);
    Map<Long, List<LocationEntity>> coverLocationsByContentId =
        locationRepository.findLocationsByContentIds(coverContentIds);

    return entities.stream()
        .map(
            entity ->
                buildBasicModel(
                    entity,
                    locationsByCollectionId,
                    peopleByCollectionId,
                    coverImagesById,
                    tagsByContentId,
                    peopleByContentId,
                    coverLocationsByContentId))
        .collect(Collectors.toList());
  }

  /**
   * Build a single CollectionModel from pre-loaded batch data. The people set here are those on the
   * collection itself (the {@code collection_people} join), not the people tagged on its images.
   *
   * <p>Display mode falls back to CHRONOLOGICAL whenever none is stored; ORDERED is a
   * per-collection opt-in, and an explicit displayMode in a request is always respected and
   * persisted.
   */
  private CollectionModel buildBasicModel(
      CollectionEntity entity,
      Map<Long, List<LocationEntity>> locationsByCollectionId,
      Map<Long, List<Records.Person>> peopleByCollectionId,
      Map<Long, ContentImageEntity> coverImagesById,
      Map<Long, List<TagEntity>> tagsByContentId,
      Map<Long, List<ContentPersonEntity>> peopleByContentId,
      Map<Long, List<LocationEntity>> coverLocationsByContentId) {
    CollectionModel model = new CollectionModel();
    model.setId(entity.getId());
    model.setClient(entity.isClient());
    model.setBlog(entity.isBlog());
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

    model.setPeople(new ArrayList<>(peopleByCollectionId.getOrDefault(entity.getId(), List.of())));

    model.setCollectionDate(entity.getCollectionDate());
    model.setCollectionEndDate(entity.getCollectionEndDate());
    model.setVisibility(entity.getVisibility());
    model.setRating(entity.getRating());

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

    model.setIsPasswordProtected(entity.getGalleryPassword() != null);

    model.setCreatedAt(entity.getCreatedAt());
    model.setUpdatedAt(entity.getUpdatedAt());
    DisplayMode mode = entity.getDisplayMode();
    if (mode == null) {
      mode = DisplayMode.CHRONOLOGICAL;
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
   * bulk loading of ContentEntity instances to avoid proxy issues and improve performance -- one
   * query loads every content row with its subclasses properly resolved.
   *
   * <p>Child-collection tile blocks get the same treatment: their referenced collections and cover
   * images are batch-loaded, so a parent or home collection with N tiles stays at a constant query
   * count instead of firing findById + findImageById (plus per-image metadata) for every tile.
   * Tags, people and locations for all IMAGE content AND all tile cover images are then loaded in
   * three queries total.
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

    List<Long> contentIds =
        collectionContentList.stream()
            .map(CollectionContentEntity::getContentId)
            .filter(Objects::nonNull)
            .toList();

    final Map<Long, ContentEntity> contentMap;
    if (!contentIds.isEmpty()) {
      List<ContentEntity> contentEntities = contentRepository.findAllByIds(contentIds);
      contentMap =
          contentEntities.stream().collect(Collectors.toMap(ContentEntity::getId, ce -> ce));
    } else {
      contentMap = new HashMap<>();
    }

    List<Long> referencedCollectionIds =
        contentMap.values().stream()
            .filter(ContentCollectionEntity.class::isInstance)
            .map(c -> ((ContentCollectionEntity) c).getReferencedCollection())
            .filter(Objects::nonNull)
            .map(CollectionEntity::getId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    Map<Long, CollectionEntity> referencedCollectionsById =
        referencedCollectionIds.isEmpty()
            ? Map.of()
            : collectionRepository.findByIds(referencedCollectionIds).stream()
                .collect(Collectors.toMap(CollectionEntity::getId, c -> c));
    List<Long> coverImageIds =
        referencedCollectionsById.values().stream()
            .map(CollectionEntity::getCoverImageId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    Map<Long, ContentImageEntity> coverImagesById =
        coverImageIds.isEmpty()
            ? Map.of()
            : contentRepository.findImagesByIds(coverImageIds).stream()
                .collect(Collectors.toMap(ContentImageEntity::getId, img -> img));

    List<Long> imageContentIds =
        contentMap.values().stream()
            .filter(c -> c.getContentType() == ContentType.IMAGE)
            .map(ContentEntity::getId)
            .toList();
    List<Long> metadataContentIds = new ArrayList<>(imageContentIds);
    coverImageIds.stream()
        .filter(id -> !metadataContentIds.contains(id))
        .forEach(metadataContentIds::add);
    Map<Long, List<TagEntity>> tagsByContentId =
        tagRepository.findTagsByContentIds(metadataContentIds);
    Map<Long, List<ContentPersonEntity>> peopleByContentId =
        personRepository.findPeopleByContentIds(metadataContentIds);
    Map<Long, List<LocationEntity>> locationsByContentId =
        locationRepository.findLocationsByContentIds(metadataContentIds);

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
                  if (content instanceof ContentCollectionEntity collectionContent) {
                    return contentModelConverter.buildCollectionModelWithBatchData(
                        collectionContent,
                        cc,
                        referencedCollectionsById,
                        coverImagesById,
                        tagsByContentId,
                        peopleByContentId,
                        locationsByContentId);
                  }
                  return contentModelConverter.convertBulkLoadedContentToModel(content, cc);
                })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    model.setContent(contents);

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
      populateSiblings(model, false);
      return model;
    }

    CollectionModel model = convertToModel(entity, joinEntries, 0, 0, joinEntries.size());
    populateCollectionsOnContent(model);
    populateSiblings(model, false);
    return model;
  }

  /**
   * Populate child collection metadata on image AND GIF content items. For each one, finds all
   * collections it belongs to and attaches them as ChildCollection records. Uses batch queries to
   * avoid N+1.
   *
   * <p>GIFs participate in many-to-many collection membership exactly as images do. The content
   * records are immutable, so the memberships are attached with {@code withCollections} rather than
   * a setter.
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

    List<CollectionContentEntity> allCollections =
        collectionRepository.findContentByContentIdsIn(contentIds);
    Map<Long, List<CollectionContentEntity>> collectionsByContentId =
        allCollections.stream()
            .collect(Collectors.groupingBy(CollectionContentEntity::getContentId));

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

    List<ContentModel> contents =
        model.getContent().stream()
            .map(
                content -> {
                  Long contentId = content.id();
                  List<CollectionContentEntity> contentCollections =
                      collectionsByContentId.getOrDefault(contentId, Collections.emptyList());
                  if (contentCollections.isEmpty()) {
                    return content;
                  }
                  List<Records.ChildCollection> childCollections =
                      contentCollections.stream()
                          .map(
                              joinEntry ->
                                  convertToChildCollection(
                                      joinEntry, collectionsById, coverImageUrlsById))
                          .filter(Objects::nonNull)
                          .collect(Collectors.toList());

                  if (content instanceof ContentModels.Image imageModel) {
                    return (ContentModel) imageModel.withCollections(childCollections);
                  }
                  if (content instanceof ContentModels.Gif gifModel) {
                    return (ContentModel) gifModel.withCollections(childCollections);
                  }
                  return content;
                })
            .collect(Collectors.toList());

    model.setContent(contents);
  }

  /**
   * Populate {@code model.siblings} from the collection_sibling join. {@code listedOnly=true} on
   * the public read path (LISTED siblings only — no dead links leak); {@code listedOnly=false} on
   * the admin manage payload. No-op when the model or its id is null.
   *
   * <p>Cover image URLs are batch-loaded in a single query (mirroring {@link
   * #convertToChildCollection}) so siblings can be rendered as cover-image cards on the frontend
   * without an N+1. The {@code coverImageUrl} on each {@link Records.CollectionList} is null when
   * the sibling has no cover image assigned.
   *
   * <p>On the admin path ({@code listedOnly=false}) {@code model.oneWaySiblingIds} is also filled
   * with the subset of siblings that do not link back, so the admin UI can badge each link as
   * MUTUAL or ONE-WAY. Left null on the public path: direction is an admin-manage concern only, the
   * public read path never renders a badge, and a one-way sibling is already absent from the
   * target's list because the row does not exist.
   */
  public void populateSiblings(CollectionModel model, boolean listedOnly) {
    if (model == null || model.getId() == null) {
      return;
    }

    List<Records.SiblingRow> siblingRows =
        collectionSiblingRepository.findSiblings(model.getId(), listedOnly);

    List<Long> coverImageIds =
        siblingRows.stream()
            .map(Records.SiblingRow::coverImageId)
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

    List<Records.CollectionList> siblings =
        siblingRows.stream()
            .map(
                row ->
                    Records.CollectionList.fromSibling(
                        row,
                        row.coverImageId() != null
                            ? coverImageUrlsById.get(row.coverImageId())
                            : null))
            .toList();

    model.setSiblings(siblings);

    if (!listedOnly) {
      model.setOneWaySiblingIds(
          siblingRows.stream().filter(row -> !row.mutual()).map(Records.SiblingRow::id).toList());
    }
  }

  /**
   * Populate {@code parents} from the inverse of the parent/child join.
   *
   * <p>Cover images are deliberately not loaded: parents render as text links, and fetching covers
   * would add a query per read for something nothing displays yet.
   *
   * @param listedOnly LISTED parents only. The public read path must pass {@code true} -- a HIDDEN
   *     or UNLISTED parent there is a dead link and a disclosure at once.
   */
  public void populateParents(CollectionModel model, boolean listedOnly) {
    if (model == null || model.getId() == null) {
      return;
    }

    model.setParents(
        collectionRepository.findAllParentCollectionsByChildId(model.getId(), listedOnly).stream()
            .map(
                p ->
                    new Records.CollectionList(
                        p.getId(),
                        p.getTitle(),
                        p.getSlug(),
                        p.getCollectionDate(),
                        null,
                        p.isClient(),
                        p.isBlog()))
            .toList());
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
   * Create a CollectionEntity from a Create request. Required field: title. Optional fields:
   * isClient/isBlog, description, locationId/locationName, collectionDate -- use defaults when not
   * provided. The two flags are mutually exclusive (see {@link CollectionFlags}).
   *
   * <p>Visibility is a privacy-first default: new collections are UNLISTED -- reachable by direct
   * slug, absent from public listings -- until an admin explicitly lists them. Display mode
   * defaults to CHRONOLOGICAL, with ORDERED an explicit opt-in via a later update. The Create
   * request carries neither a visibility nor a displayMode field, so every create takes both
   * defaults regardless of type.
   */
  public CollectionEntity toEntity(CollectionRequests.Create request, int defaultPageSize) {
    if (request == null) {
      throw new IllegalArgumentException("Create request cannot be null");
    }
    CollectionEntity entity = new CollectionEntity();
    CollectionFlags.Resolved resolved =
        CollectionFlags.forCreate(request.isClient(), request.isBlog());
    resolved.applyTo(entity);
    if (request.isClient() == null && request.isBlog() == null) {
      log.info("Create for '{}' carried no flags -- neither client nor blog", request.title());
    }
    entity.setTitle(request.title());
    String baseSlug = generateSlug(request.title());
    String uniqueSlug = validateAndEnsureUniqueSlug(baseSlug, null);
    entity.setSlug(uniqueSlug);
    entity.setDescription(request.description() != null ? request.description() : "");
    entity.setCollectionDate(
        request.collectionDate() != null ? request.collectionDate() : LocalDate.now());
    entity.setVisibility(CollectionVisibility.UNLISTED);
    entity.setTotalContent(0);
    entity.setContentPerPage(defaultPageSize);
    entity.setDisplayMode(DisplayMode.CHRONOLOGICAL);
    return applyPaginationDefaults(entity);
  }

  // =============================================================================
  // UPDATE HELPERS FOR SERVICE LAYER (split from updateContent)
  // =============================================================================

  /**
   * Apply partial-field updates from updateDTO to the given entity: title (auto-regenerating the
   * slug unless an explicit one is supplied), slug, description, the isClient/isBlog flag
   * resolution (see {@link CollectionFlags}), locations, collection start/end dates and their
   * explicit clear flags, visibility, rating, displayMode, contentPerPage/rowsWide and
   * coverImageId. Null request fields leave the entity untouched.
   *
   * <p>Layout fields are written for every collection: the old suppression on parent types went out
   * with {@code CollectionType}, since any collection may now hold any mix of content.
   *
   * <p>The isClient/isBlog block is a guard only -- a request carrying neither flag leaves both
   * untouched. The resolution rules themselves live in {@link CollectionFlags}.
   *
   * <p>{@code coverImageId} carries a sentinel: 0 explicitly clears the cover image, any other id
   * is verified to exist before being written.
   */
  public void applyBasicUpdates(CollectionEntity entity, CollectionRequests.Update updateDTO) {
    if (updateDTO.title() != null) {
      entity.setTitle(updateDTO.title());
      if (updateDTO.slug() == null || updateDTO.slug().isBlank()) {
        String newSlug = generateSlug(updateDTO.title());
        String uniqueSlug = validateAndEnsureUniqueSlug(newSlug, entity.getId());
        entity.setSlug(uniqueSlug);
      }
    }
    if (updateDTO.description() != null) {
      entity.setDescription(updateDTO.description());
    }
    if (updateDTO.isClient() != null || updateDTO.isBlog() != null) {
      boolean wasClient = entity.isClient();
      boolean wasBlog = entity.isBlog();
      CollectionFlags.Resolved resolved =
          CollectionFlags.forUpdate(updateDTO.isClient(), updateDTO.isBlog(), entity);
      if (resolved.isClient() != wasClient || resolved.isBlog() != wasBlog) {
        log.info(
            "Collection {} category change: isClient {} -> {}, isBlog {} -> {}",
            entity.getId(),
            wasClient,
            resolved.isClient(),
            wasBlog,
            resolved.isBlog());
      }
      resolved.applyTo(entity);
      clearGalleryAccessOnClientDemotion(entity, wasClient, resolved.isClient());
    }
    if (updateDTO.locations() != null) {
      CollectionRequests.LocationUpdate locationUpdate = updateDTO.locations();
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
    if (Boolean.TRUE.equals(updateDTO.clearCollectionEndDate())) {
      entity.setCollectionEndDate(null);
    } else if (updateDTO.collectionEndDate() != null) {
      entity.setCollectionEndDate(updateDTO.collectionEndDate());
    }
    validateDateRange(entity.getCollectionDate(), entity.getCollectionEndDate());
    if (updateDTO.visibility() != null) {
      entity.setVisibility(updateDTO.visibility());
    }
    if (updateDTO.rating() != null) {
      entity.setRating(updateDTO.rating());
    }
    if (updateDTO.slug() != null && !updateDTO.slug().isBlank()) {
      String uniqueSlug = validateAndEnsureUniqueSlug(updateDTO.slug().trim(), entity.getId());
      entity.setSlug(uniqueSlug);
    }
    if (updateDTO.contentPerPage() != null && updateDTO.contentPerPage() >= 1) {
      entity.setContentPerPage(updateDTO.contentPerPage());
    }
    if (updateDTO.rowsWide() != null) {
      entity.setRowsWide(updateDTO.rowsWide());
    }
    if (updateDTO.displayMode() != null) {
      entity.setDisplayMode(updateDTO.displayMode());
    }

    if (updateDTO.coverImageId() != null) {
      if (updateDTO.coverImageId() == 0) {
        entity.setCoverImageId(null);
      } else {
        contentRepository
            .findImageById(updateDTO.coverImageId())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Cover image not found with ID: " + updateDTO.coverImageId()));
        entity.setCoverImageId(updateDTO.coverImageId());
      }
    }
  }

  /**
   * Clear the gallery password and recipient list when an update demotes a collection out of
   * client-gallery status. The public read gate keys on {@code galleryPassword != null}, and {@link
   * CollectionService#updateGalleryAccess} only *sets* a password on a collection that {@code
   * isClient() || hasClientGalleryChildren(id)} -- so without this a demoted collection would keep
   * an enforced password that only the D8 clear path could remove. Written through {@code
   * saveGalleryAccess}, the sole owner of the password/recipients pair ({@link
   * edens.zac.portfolio.backend.dao.CollectionRepository#save} deliberately omits them on UPDATE).
   */
  private void clearGalleryAccessOnClientDemotion(
      CollectionEntity entity, boolean wasClient, boolean isClient) {
    if (!wasClient || isClient || entity.getId() == null || entity.getGalleryPassword() == null) {
      return;
    }
    collectionRepository.saveGalleryAccess(entity.getId(), null, List.of());
    entity.setGalleryPassword(null);
    entity.setRecipientEmails(new ArrayList<>());
    log.info(
        "Cleared gallery password and recipients after isClient demotion (id={}, slug={})",
        entity.getId(),
        entity.getSlug());
  }

  /**
   * Validate the collection's resolved date range. Rejects (400 via {@link
   * edens.zac.portfolio.backend.config.GlobalExceptionHandler}) when the end date precedes the
   * start date, or when an end date is present without a start date (an open-ended range with no
   * anchor is meaningless). A null end date is always valid (single-day / open collection). The
   * end-before-start message names the conflicting dates and states that an admin must adjust the
   * end date -- collaborators cannot set {@code collectionEndDate} themselves (denied field on
   * {@link edens.zac.portfolio.backend.model.CollaboratorRequests.CollaboratorUpdate}), so a
   * range-order violation on that tier can only be resolved by an admin. This method is private and
   * shared by both the admin and collaborator write paths, so the wording serves both.
   *
   * @param start the resolved collection start date (may be null)
   * @param end the resolved collection end date (may be null)
   */
  private void validateDateRange(LocalDate start, LocalDate end) {
    if (end == null) {
      return;
    }
    if (start == null) {
      throw new IllegalArgumentException(
          "collectionEndDate requires a collectionDate (start date) to be set");
    }
    if (end.isBefore(start)) {
      throw new IllegalArgumentException(
          "collectionEndDate ("
              + end
              + ") must not be before collectionDate ("
              + start
              + "); adjusting or clearing the end date requires an admin");
    }
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
   * @throws IllegalArgumentException if the slug is null or empty
   * @throws IllegalStateException if no unique slug can be found within 100 attempts
   */
  public String validateAndEnsureUniqueSlug(String slug, Long existingId) {
    if (slug == null || slug.isEmpty()) {
      throw new IllegalArgumentException("Slug cannot be empty");
    }

    boolean exists =
        collectionRepository
            .findBySlug(slug)
            .map(entity -> !entity.getId().equals(existingId))
            .orElse(false);

    if (!exists) {
      return slug; // Slug is unique
    }

    int counter = 1;
    String newSlug;
    do {
      newSlug = slug + "-" + counter++;
      exists = collectionRepository.findBySlug(newSlug).isPresent();
    } while (exists && counter < 100); // Limit to prevent infinite loop

    if (exists) {
      throw new IllegalStateException("Could not generate a unique slug after 100 attempts");
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
            throw new RuntimeException(
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
   * Batch-load all images from the given child collection IDs. Used by the manage page for a
   * collection that holds child collections, to aggregate images across them for cover image
   * selection and content management.
   *
   * <p>Results are deduplicated by content id: the same image may appear in several child
   * collections, and the manage page wants it once.
   *
   * @param childCollectionIds IDs of child collections to aggregate images from
   * @return List of image models from all child collections
   */
  public List<ContentModels.Image> loadImagesFromChildCollections(List<Long> childCollectionIds) {
    if (childCollectionIds == null || childCollectionIds.isEmpty()) {
      return List.of();
    }

    List<CollectionContentEntity> imageJoinEntries =
        collectionRepository.findImageContentByCollectionIds(childCollectionIds);

    if (imageJoinEntries.isEmpty()) {
      return List.of();
    }

    List<Long> contentIds =
        imageJoinEntries.stream().map(CollectionContentEntity::getContentId).distinct().toList();

    List<ContentEntity> contentEntities = contentRepository.findAllByIds(contentIds);
    Map<Long, ContentEntity> contentMap =
        contentEntities.stream().collect(Collectors.toMap(ContentEntity::getId, ce -> ce));

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

  /**
   * Fill in the default pagination size for a collection that has none. Visibility is intentionally
   * NOT touched here: new collections default to UNLISTED in {@link #toEntity} (privacy-first), and
   * updates only change visibility when explicitly requested.
   *
   * @param entity The entity to update
   * @return The updated entity
   */
  public CollectionEntity applyPaginationDefaults(CollectionEntity entity) {
    if (entity == null) {
      return entity;
    }
    if (entity.getContentPerPage() == null || entity.getContentPerPage() <= 0) {
      entity.setContentPerPage(DefaultValues.default_content_per_page);
    }
    return entity;
  }
}
