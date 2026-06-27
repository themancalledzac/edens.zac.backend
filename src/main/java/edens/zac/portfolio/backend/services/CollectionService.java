package edens.zac.portfolio.backend.services;

import static edens.zac.portfolio.backend.config.DefaultValues.default_content_per_page;

import edens.zac.portfolio.backend.config.GalleryAccessCookies;
import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.CollectionPeopleRepository;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.CollectionSiblingRepository;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.LocationRepository;
import edens.zac.portfolio.backend.dao.TagRepository;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import edens.zac.portfolio.backend.entity.LocationEntity;
import edens.zac.portfolio.backend.entity.TagEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.model.CollectionRequests;
import edens.zac.portfolio.backend.model.CollectionRequests.GalleryAccessRequest;
import edens.zac.portfolio.backend.model.CollectionRequests.GalleryAccessResponse;
import edens.zac.portfolio.backend.model.ContentFilmTypeModel;
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.GeneralMetadataDTO;
import edens.zac.portfolio.backend.model.LocationPageResponse;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import edens.zac.portfolio.backend.types.ContentType;
import edens.zac.portfolio.backend.types.FilmFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for managing Collection entities with pagination and client gallery access. */
@Service
@Slf4j
@RequiredArgsConstructor
public class CollectionService {

  private final CollectionRepository collectionRepository;
  private final CollectionPeopleRepository collectionPeopleRepository;
  private final CollectionSiblingRepository collectionSiblingRepository;
  private final ContentRepository contentRepository;
  private final LocationRepository locationRepository;
  private final TagRepository tagRepository;
  private final ContentMutationUtil contentMutationUtil;
  private final ContentModelConverter contentModelConverter;
  private final CollectionProcessingUtil collectionProcessingUtil;
  private final MetadataService metadataService;
  private final EmailService emailService;
  private final SyntheticCollectionResolver syntheticResolver;
  private final ClientGalleryAuthService clientGalleryAuthService;
  private final UserCollectionService userCollectionService;
  private final Environment springEnv;

  private static final int DEFAULT_PAGE_SIZE = default_content_per_page;
  private static final String HOME_SLUG = "home";

  @Transactional(readOnly = true)
  public CollectionModel getCollectionWithPagination(String slug, int page, int size) {
    log.debug("Getting collection with slug: {} (page: {}, size: {})", slug, page, size);

    // Synthetic list slugs (e.g. "all-collections", "all-blogs") bypass the DB lookup
    // and are resolved into a PARENT-shaped CollectionModel populated with children.
    if (syntheticResolver.isSyntheticSlug(slug)) {
      return syntheticResolver.resolve(slug, isLocalEnvironment());
    }

    // Get collection metadata
    CollectionEntity collection =
        collectionRepository
            .findBySlug(slug)
            .orElseThrow(
                () -> new ResourceNotFoundException("Collection not found with slug: " + slug));

    // Enforce visibility: invisible collections are not publicly accessible (except "home")
    enforceVisibility(collection, slug, isLocalEnvironment());

    // Normalize pagination parameters
    int normalizedPage = Math.max(0, page);
    int normalizedSize = size <= 0 ? DEFAULT_PAGE_SIZE : size;
    int offset = normalizedPage * normalizedSize;

    List<CollectionContentEntity> collectionContentList;
    long totalElements;

    if (collection.getType().isParentType()) {
      // Parent-type collections only show child collection content
      collectionContentList =
          collectionRepository.findContentByCollectionIdAndContentType(
              collection.getId(), "COLLECTION");
      totalElements = collectionContentList.size();
    } else {
      totalElements = collectionRepository.countContentByCollectionId(collection.getId());
      collectionContentList =
          collectionRepository.findContentByCollectionId(
              collection.getId(), normalizedSize, offset);
    }

    // Convert to model (now using join table data)
    CollectionModel model =
        collectionProcessingUtil.convertToModel(
            collection, collectionContentList, normalizedPage, normalizedSize, totalElements);

    // Populate collections on content items
    collectionProcessingUtil.populateCollectionsOnContent(model);

    // Populate siblings — public read path shows LISTED siblings only (no dead links leak)
    collectionProcessingUtil.populateSiblings(model, true);

    // Filter out child collection content that references non-LISTED collections
    filterNonListedChildCollections(model);

    return model;
  }

  @Transactional(readOnly = true)
  public Page<CollectionModel> findByType(CollectionType type, Pageable pageable) {
    log.debug("Finding collections by type: {}", type);

    // Get total count
    long totalElements = collectionRepository.countByType(type);

    // Get paginated collections directly from DB
    int page = pageable.getPageNumber();
    int size = pageable.getPageSize();
    List<CollectionEntity> paginatedCollections =
        collectionRepository.findByTypeOrderByCollectionDateDesc(type, size, page * size);

    // Convert to models using batch loading
    List<CollectionModel> models =
        collectionProcessingUtil.batchConvertToBasicModels(paginatedCollections);

    return new PageImpl<>(models, pageable, totalElements);
  }

  /**
   * Find LISTED collections of a given type, ordered by rating then collection_date. Used by
   * AdminHomeService to pick cover images for type-specific admin home tiles.
   */
  @Transactional(readOnly = true)
  public List<CollectionModel> findVisibleByTypeOrderByDate(CollectionType type) {
    log.debug("Finding visible collections by type ordered by date: {}", type);
    List<CollectionEntity> collections = collectionRepository.findByTypeAndListedOrdered(type);
    return collectionProcessingUtil.batchConvertToBasicModels(collections);
  }

  /**
   * Find non-HIDDEN collections of a given type (LISTED + UNLISTED) for admin-only contexts where
   * UNLISTED is acceptable to surface. Used by {@link AdminHomeService} to pick cover images for
   * tiles like {@code client-galleries}, where the typical visibility is UNLISTED — the regular
   * "visible" lookup would return no candidates and the tile would render with no cover.
   */
  @Transactional(readOnly = true)
  public List<CollectionModel> findByTypeForAdminCovers(CollectionType type) {
    log.debug("Finding admin-cover candidates by type: {}", type);
    List<CollectionEntity> collections =
        collectionRepository.findOrderedByVisibilityIn(
            List.of(CollectionVisibility.LISTED, CollectionVisibility.UNLISTED), type);
    return collectionProcessingUtil.batchConvertToBasicModels(collections);
  }

  /**
   * Return child collections referenced by the "home" parent collection. Used by AdminHomeService
   * to pick a cover image for the home tile. Returns an empty list if the home collection does not
   * exist.
   */
  @Transactional(readOnly = true)
  public List<CollectionModel> findChildCollectionsForHome() {
    return collectionRepository
        .findBySlug(HOME_SLUG)
        .map(home -> collectionRepository.findReferencedCollectionsByParentId(home.getId()))
        .map(collectionProcessingUtil::batchConvertToBasicModels)
        .orElseGet(List::of);
  }

  /**
   * Return all visible collections that have a cover image set. Used by AdminHomeService to pick a
   * cover for the all-collections tile.
   */
  @Transactional(readOnly = true)
  public List<CollectionModel> findAllListedWithCovers() {
    List<CollectionEntity> entities = collectionRepository.findAllListedWithCovers();
    return collectionProcessingUtil.batchConvertToBasicModels(entities);
  }

  @Transactional(readOnly = true)
  public LocationPageResponse getLocationPage(
      String locationName, int collectionPage, int collectionSize, int imagePage, int imageSize) {
    log.debug("Getting location page for: {}", locationName);

    // Get visible collections at this location
    long totalCollections = collectionRepository.countListedByLocationName(locationName);
    int collectionOffset = collectionPage * collectionSize;
    List<CollectionEntity> collectionEntities =
        collectionRepository.findListedByLocationName(
            locationName, collectionSize, collectionOffset);

    List<CollectionModel> collections =
        collectionProcessingUtil.batchConvertToBasicModels(collectionEntities);

    // Get IDs of ALL visible collections at this location (for orphan exclusion).
    // If the paginated result already covers all collections, extract IDs directly
    // to avoid a redundant query.
    List<Long> allCollectionIds;
    if (totalCollections <= collectionSize) {
      allCollectionIds = collectionEntities.stream().map(CollectionEntity::getId).toList();
    } else {
      allCollectionIds = collectionRepository.findListedIdsByLocationName(locationName);
    }

    // Get orphan images (at this location but not in any of those collections)
    int imageOffset = imagePage * imageSize;
    List<ContentImageEntity> orphanImageEntities =
        contentRepository.findOrphanImagesByLocationName(
            locationName, allCollectionIds, imageSize, imageOffset);
    long totalImages =
        contentRepository.countOrphanImagesByLocationName(locationName, allCollectionIds);

    List<ContentModels.Image> images =
        contentModelConverter.batchConvertImageEntitiesToModels(orphanImageEntities);

    // Resolve the location record from the location entity (looked up by name)
    LocationEntity locationEntity =
        locationRepository.findByLocationName(locationName).orElse(null);
    Records.Location location =
        locationEntity != null
            ? new Records.Location(
                locationEntity.getId(), locationEntity.getLocationName(), locationEntity.getSlug())
            : new Records.Location(null, locationName, SlugUtil.generateSlug(locationName));

    return new LocationPageResponse(location, collections, images, totalCollections, totalImages);
  }

  @Transactional(readOnly = true)
  public LocationPageResponse getLocationPageBySlug(
      String slug, int collectionPage, int collectionSize, int imagePage, int imageSize) {
    log.debug("Getting location page by slug: {}", slug);

    LocationEntity locationEntity =
        locationRepository
            .findBySlug(slug)
            .orElseThrow(
                () -> new ResourceNotFoundException("Location not found with slug: " + slug));

    return getLocationPage(
        locationEntity.getLocationName(), collectionPage, collectionSize, imagePage, imageSize);
  }

  @Transactional(readOnly = true)
  public CollectionModel findMetaBySlug(String slug) {
    log.debug("Finding collection metadata by slug: {}", slug);
    CollectionEntity entity =
        collectionRepository
            .findBySlug(slug)
            .orElseThrow(
                () -> new ResourceNotFoundException("Collection not found with slug: " + slug));

    // Enforce visibility: invisible collections are not publicly accessible (except "home")
    enforceVisibility(entity, slug, isLocalEnvironment());

    return collectionProcessingUtil.convertToBasicModel(entity);
  }

  @Transactional(readOnly = true)
  public Optional<CollectionModel> findBySlug(String slug) {
    log.debug("Finding collection by slug: {}", slug);

    // Get collection metadata only - content is fetched via join table in
    // convertToFullModel
    return collectionRepository.findBySlug(slug).map(collectionProcessingUtil::convertToFullModel);
  }

  @Transactional
  @CacheEvict(value = "generalMetadata", allEntries = true)
  public CollectionRequests.UpdateResponse createCollection(
      CollectionRequests.Create createRequest) {
    log.debug("Creating new collection: {}", createRequest.title());

    // Create entity using utility converter
    CollectionEntity entity = collectionProcessingUtil.toEntity(createRequest, DEFAULT_PAGE_SIZE);

    // Save entity
    CollectionEntity savedEntity = collectionRepository.save(entity);

    // Save locations via join table (after entity has an ID)
    List<Long> locationIds =
        collectionProcessingUtil.resolveLocationIds(
            createRequest.locationIds(), createRequest.locationNames());
    if (!locationIds.isEmpty()) {
      locationRepository.saveCollectionLocations(savedEntity.getId(), locationIds);
    }

    // Return full update response with all metadata (tags, people, cameras, etc.)
    return getUpdateCollectionData(savedEntity.getSlug());
  }

  @Transactional
  @CacheEvict(value = "generalMetadata", allEntries = true)
  public CollectionRequests.UpdateResponse createChildCollection(
      Long parentId, CollectionRequests.Create createRequest) {
    log.debug(
        "Creating new child collection: {} under parent: {}", createRequest.title(), parentId);

    // Create the child collection entity
    CollectionEntity childEntity =
        collectionProcessingUtil.toEntity(createRequest, DEFAULT_PAGE_SIZE);
    CollectionEntity savedChildEntity = collectionRepository.save(childEntity);
    log.info("Created child collection with ID: {}", savedChildEntity.getId());

    // Save locations via join table
    List<Long> childLocationIds =
        collectionProcessingUtil.resolveLocationIds(
            createRequest.locationIds(), createRequest.locationNames());
    if (!childLocationIds.isEmpty()) {
      locationRepository.saveCollectionLocations(savedChildEntity.getId(), childLocationIds);
    }

    // Link to parent
    linkCollectionToParent(parentId, savedChildEntity.getId());

    // Return full update response for the child collection
    return getUpdateCollectionData(savedChildEntity.getSlug());
  }

  /**
   * Link an existing collection as a child of a parent collection. Creates the
   * ContentCollectionEntity if needed and adds the join table entry. No-op if already linked.
   */
  @Transactional
  public void linkCollectionToParent(Long parentId, Long childCollectionId) {
    collectionRepository
        .findById(parentId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException("Parent collection not found with ID: " + parentId));

    CollectionEntity childEntity =
        collectionRepository
            .findById(childCollectionId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Child collection not found with ID: " + childCollectionId));

    ContentCollectionEntity contentCollectionEntity =
        findOrCreateContentCollectionEntity(childEntity);

    // Check if already linked
    Optional<CollectionContentEntity> existing =
        collectionRepository.findContentByCollectionIdAndContentId(
            parentId, contentCollectionEntity.getId());
    if (existing.isPresent()) {
      log.debug("Collection {} already linked to parent {}", childCollectionId, parentId);
      return;
    }

    // Get next order index for parent collection
    Integer orderIndex = collectionRepository.getMaxOrderIndexForCollection(parentId);
    orderIndex = (orderIndex != null) ? orderIndex + 1 : 0;

    // Link child to parent via join table
    CollectionContentEntity joinEntry =
        CollectionContentEntity.builder()
            .collectionId(parentId)
            .contentId(contentCollectionEntity.getId())
            .orderIndex(orderIndex)
            .visible(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    collectionRepository.saveContent(joinEntry);
    log.info(
        "Linked child collection {} to parent {} at index {}",
        childCollectionId,
        parentId,
        orderIndex);
  }

  @Transactional(readOnly = true)
  public CollectionModel findById(Long id) {
    log.debug("Finding collection by ID: {}", id);

    // Get collection entity with content blocks
    CollectionEntity entity =
        collectionRepository
            .findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("Collection not found with ID: " + id));

    // Convert to full model (includes content blocks)
    return collectionProcessingUtil.convertToFullModel(entity);
  }

  /**
   * Find the raw {@link CollectionEntity} by ID. Used by admin/download flows that need direct
   * entity access (e.g. updating {@code galleryPassword}, reading bucket-relative S3 keys). Throws
   * {@link ResourceNotFoundException} when no row matches.
   */
  @Transactional(readOnly = true)
  public CollectionEntity findEntityById(Long id) {
    log.debug("Finding collection entity by ID: {}", id);
    return collectionRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Collection not found with ID: " + id));
  }

  /**
   * Find the raw {@link CollectionEntity} by slug. Used by download endpoints that key off the
   * public slug. Throws {@link ResourceNotFoundException} when no row matches.
   */
  @Transactional(readOnly = true)
  public CollectionEntity findEntityBySlug(String slug) {
    log.debug("Finding collection entity by slug: {}", slug);
    return collectionRepository
        .findBySlug(slug)
        .orElseThrow(
            () -> new ResourceNotFoundException("Collection not found with slug: " + slug));
  }

  /**
   * Decide whether an incoming request is authorized to read the gated content of a gallery.
   * Encapsulates both the per-slug cookie check and the shared password-fingerprint cookie check
   * (the latter is what makes a PARENT password also unlock its propagated CLIENT_GALLERY children,
   * and vice versa, without re-prompting). Returns {@code true} for unprotected or missing
   * collections — the GET handler still returns 200 with the stripped/empty model.
   */
  @Transactional(readOnly = true)
  public boolean isGalleryAccessAuthorized(
      String slug, jakarta.servlet.http.HttpServletRequest request) {
    return collectionRepository
        .findBySlug(slug)
        .map(
            entity -> {
              Long userId = currentUserId();
              if (userId != null && userCollectionService.canView(userId, entity.getId())) {
                return true;
              }
              return GalleryAccessCookies.hasValidAccess(
                  request, slug, entity.getGalleryPassword(), clientGalleryAuthService);
            })
        .orElse(true);
  }

  /** The authenticated principal's user id, or null when the request is anonymous. */
  private static Long currentUserId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return (auth != null && auth.getPrincipal() instanceof AuthPrincipal p) ? p.userId() : null;
  }

  @Transactional
  @CacheEvict(
      value = "generalMetadata",
      allEntries = true,
      condition = "#updateDTO != null && (#updateDTO.title != null || #updateDTO.slug != null)")
  public CollectionModel updateContent(Long id, CollectionRequests.Update updateDTO) {
    log.debug("Updating collection with ID: {}", id);

    // Get existing entity
    CollectionEntity entity =
        collectionRepository
            .findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("Collection not found with ID: " + id));

    // Update basic properties via utility helper
    collectionProcessingUtil.applyBasicUpdates(entity, updateDTO);

    // Handle tag updates using prev/new/remove pattern
    if (updateDTO.tags() != null) {
      updateCollectionTags(entity, updateDTO.tags());
    }

    // Handle people updates using prev/new/remove pattern
    if (updateDTO.people() != null) {
      updateCollectionPeople(entity, updateDTO.people());
    }

    // Handle collection updates using prev/new/remove pattern
    // This manages child collections within this parent collection
    if (updateDTO.collections() != null) {
      handleCollectionToCollectionUpdates(entity, updateDTO.collections());
    }

    // Handle sibling (mutual) collection updates
    handleSiblingUpdates(entity.getId(), updateDTO.siblings());

    handleParentCollectionUpdates(entity, updateDTO.parents());

    // Update total blocks count from join table before saving
    long totalBlocks = collectionRepository.countContentByCollectionId(entity.getId());
    entity.setTotalContent((int) totalBlocks);

    // Save updated entity
    CollectionEntity savedEntity = collectionRepository.save(entity);

    // Return lightweight model without loading all content to avoid N+1 queries
    // Frontend can refetch full content if needed
    return collectionProcessingUtil.convertToBasicModel(savedEntity);
  }

  @Transactional
  @CacheEvict(
      value = "generalMetadata",
      allEntries = true,
      condition = "#updateDTO != null && (#updateDTO.title != null || #updateDTO.slug != null)")
  public CollectionRequests.UpdateResponse updateContentWithMetadata(
      Long id, CollectionRequests.Update updateDTO) {
    log.debug("Updating collection with ID: {} (with metadata response)", id);

    // Perform the update
    CollectionModel updatedCollection = updateContent(id, updateDTO);

    // Get the full update response with metadata using the new slug
    return getUpdateCollectionData(updatedCollection.getSlug());
  }

  /**
   * Set the rating for a collection. Throws ResourceNotFoundException if no row matched.
   *
   * @param id collection id
   * @param rating 0-5 (nullable to clear)
   * @return true on success
   */
  @Transactional
  public boolean updateRating(Long id, Integer rating) {
    int rows = collectionRepository.updateRating(id, rating);
    if (rows == 0) {
      throw new ResourceNotFoundException("Collection not found: " + id);
    }
    return true;
  }

  /**
   * Replace the entire {@code collection_people} list. Membership (user_collection) is not
   * auto-materialized here — it must be granted explicitly via the /admin Users module.
   */
  @Transactional
  public void setCollectionPeople(Long collectionId, List<Long> personIds) {
    collectionPeopleRepository.setPeopleForCollection(collectionId, personIds);
  }

  /**
   * Auto-fill {@code collection_people} from the distinct people tagged on the collection's visible
   * images. Manual {@link #setCollectionPeople} can still overwrite this afterwards.
   */
  @Transactional
  public void regeneratePeopleFromContents(Long collectionId) {
    List<Long> distinctPersonIds =
        contentRepository.findDistinctPersonIdsInCollection(collectionId);
    collectionPeopleRepository.setPeopleForCollection(collectionId, distinctPersonIds);
  }

  @Transactional
  @CacheEvict(value = "generalMetadata", allEntries = true)
  public void deleteCollection(Long id) {
    log.debug("Deleting collection with ID: {}", id);

    // Check if collection exists
    if (collectionRepository.findById(id).isEmpty()) {
      throw new ResourceNotFoundException("Collection not found with ID: " + id);
    }

    // Disassociate this collection from any parent collections that reference it as a child.
    // Capture the parents before removing the back-references, then recount each parent's
    // totalContent so their stored counts stay accurate.
    List<CollectionEntity> parents = collectionRepository.findAllParentCollectionsByChildId(id);
    contentRepository.deleteContentCollectionsReferencing(id);
    for (CollectionEntity parent : parents) {
      recountParentTotalContent(parent);
    }

    // Dissociate this collection's own content membership and its tags. Content itself is reusable
    // and is NOT deleted. collection_locations, collection_people, and collection_sibling rows are
    // removed by ON DELETE CASCADE when the collection row is deleted.
    collectionRepository.deleteContentByCollectionId(id);
    tagRepository.deleteCollectionTags(id);
    log.debug("Disassociated content, tags, and parent references for collection ID: {}", id);

    // Delete collection
    collectionRepository.deleteById(id);
    log.info("Successfully deleted collection with ID: {}", id);
  }

  @Transactional(readOnly = true)
  public Page<CollectionModel> getAllCollections(Pageable pageable) {
    log.debug("Getting all collections with pagination");

    // Get total count for pagination
    long totalElements = collectionRepository.countAllCollections();

    // Get paginated collections from database (not in-memory)
    int offset = pageable.getPageNumber() * pageable.getPageSize();
    List<CollectionEntity> paginatedCollections =
        collectionRepository.findAllByOrderByCollectionDateDesc(pageable.getPageSize(), offset);

    // Convert to models using batch loading
    List<CollectionModel> models =
        collectionProcessingUtil.batchConvertToBasicModels(paginatedCollections);

    return new PageImpl<>(models, pageable, totalElements);
  }

  @Transactional(readOnly = true)
  public Page<CollectionModel> getVisibleCollections(Pageable pageable) {
    log.debug("Getting visible collections with pagination");

    long totalElements = collectionRepository.countVisibleCollections();

    int offset = pageable.getPageNumber() * pageable.getPageSize();
    List<CollectionEntity> paginatedCollections =
        collectionRepository.findAllListedOrdered(pageable.getPageSize(), offset);

    List<CollectionModel> models =
        collectionProcessingUtil.batchConvertToBasicModels(paginatedCollections);

    return new PageImpl<>(models, pageable, totalElements);
  }

  @Transactional(readOnly = true)
  public List<CollectionModel> getAllCollectionsOrderedByDate() {
    log.debug("Getting all collections ordered by collection date");

    // Get all collections ordered by collection date descending
    List<CollectionEntity> collections = collectionRepository.findAllByOrderByCollectionDateDesc();

    // Convert to basic models (no content blocks) using batch loading
    return collectionProcessingUtil.batchConvertToBasicModels(collections);
  }

  @Transactional(readOnly = true)
  public CollectionRequests.UpdateResponse getUpdateCollectionData(String slug) {
    log.debug("Getting update collection data for slug: {}", slug);

    // Get the collection
    CollectionModel collection =
        findBySlug(slug)
            .orElseThrow(
                () -> new ResourceNotFoundException("Collection not found with slug: " + slug));

    // Get all general metadata using helper method
    final GeneralMetadataDTO metadata = getGeneralMetadata();

    // For parent-type collections, aggregate images from child collections
    List<ContentModels.Image> childCollectionImages = null;
    CollectionEntity entity =
        collectionRepository
            .findBySlug(slug)
            .orElseThrow(
                () -> new ResourceNotFoundException("Collection not found with slug: " + slug));

    if (entity.getType().isParentType() && collection.getContent() != null) {
      List<Long> childCollectionIds =
          collection.getContent().stream()
              .filter(c -> c instanceof ContentModels.Collection)
              .map(c -> ((ContentModels.Collection) c).referencedCollectionId())
              .filter(Objects::nonNull)
              .toList();

      childCollectionImages =
          collectionProcessingUtil.loadImagesFromChildCollections(childCollectionIds);
      log.debug(
          "Aggregated {} images from {} child collections for parent collection '{}'",
          childCollectionImages.size(),
          childCollectionIds.size(),
          slug);
    }

    // Populate admin-only fields so the manage page can display/edit them.
    collection.setGalleryPassword(entity.getGalleryPassword());
    collection.setRecipientEmails(entity.getRecipientEmails());

    collection.setParents(
        collectionRepository.findAllParentCollectionsByChildId(entity.getId()).stream()
            .map(
                p ->
                    new Records.CollectionList(
                        p.getId(), p.getTitle(), p.getSlug(), p.getType(), p.getCollectionDate()))
            .toList());

    return new CollectionRequests.UpdateResponse(collection, metadata, childCollectionImages);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "generalMetadata", unless = "#result == null")
  public GeneralMetadataDTO getGeneralMetadata() {
    log.debug("Getting general metadata (cache miss)");

    // Get all tags, people, locations, cameras, lenses, and film types from MetadataService
    List<Records.Tag> tags = metadataService.getAllTags();
    List<Records.Person> people = metadataService.getAllPeople();
    List<Records.Location> locations = metadataService.getAllLocations();
    List<Records.Camera> cameras = metadataService.getAllCameras();
    List<Records.Lens> lenses = metadataService.getAllLenses();
    List<ContentFilmTypeModel> filmTypes = metadataService.getAllFilmTypes();

    // Get all collections as Records.CollectionList (using projection for
    // efficiency)
    List<Records.CollectionList> collections = collectionRepository.findIdTitleSlugAndType();

    // Convert FilmFormat enums to DTOs
    List<Records.FilmFormat> filmFormats =
        Arrays.stream(FilmFormat.values())
            .map(ff -> new Records.FilmFormat(ff.name(), ff.getDisplayName()))
            .collect(Collectors.toList());

    // Build and return metadata DTO
    return new GeneralMetadataDTO(
        tags, people, locations, collections, cameras, lenses, filmTypes, filmFormats);
  }

  /**
   * Update collection tags using prev/new/remove pattern. Uses shared utility method from
   * ContentMutationUtil.
   *
   * @param collection The collection to update
   * @param tagUpdate The tag update containing remove/prev/newValue operations
   */
  private void updateCollectionTags(
      CollectionEntity collection, CollectionRequests.TagUpdate tagUpdate) {
    // Load current tags
    List<Long> tagIds = tagRepository.findCollectionTagIds(collection.getId());
    Set<TagEntity> currentTags =
        tagIds.stream()
            .map(
                tagId -> {
                  // Create minimal tag entity with just ID - full loading not needed for update
                  TagEntity tag = new TagEntity();
                  tag.setId(tagId);
                  return tag;
                })
            .collect(Collectors.toSet());

    Set<TagEntity> updatedTags =
        contentMutationUtil.updateTags(
            currentTags, tagUpdate, null // No tracking needed for collection updates
            );

    // Save updated tags
    List<Long> updatedTagIds =
        updatedTags.stream()
            .map(TagEntity::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    tagRepository.saveCollectionTags(collection.getId(), updatedTagIds);
    log.info("Updated tags for collection {}", collection.getId());
  }

  /**
   * Update collection people using prev/new/remove pattern. Uses shared utility method from
   * ContentMutationUtil.
   *
   * @param collection The collection to update
   * @param personUpdate The person update containing remove/prev/newValue operations
   */
  private void updateCollectionPeople(
      CollectionEntity collection, CollectionRequests.PersonUpdate personUpdate) {
    // Load current people
    List<Long> personIds = collectionRepository.findCollectionPersonIds(collection.getId());
    Set<ContentPersonEntity> currentPeople =
        personIds.stream()
            .map(
                personId -> {
                  // Create minimal person entity with just ID - full loading not needed for
                  // update
                  ContentPersonEntity person = new ContentPersonEntity();
                  person.setId(personId);
                  return person;
                })
            .collect(Collectors.toSet());

    Set<ContentPersonEntity> updatedPeople =
        contentMutationUtil.updatePeople(
            currentPeople, personUpdate, null // No tracking needed for collection updates
            );

    // Save updated people
    List<Long> updatedPersonIds =
        updatedPeople.stream()
            .map(ContentPersonEntity::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    collectionPeopleRepository.setPeopleForCollection(collection.getId(), updatedPersonIds);
    log.info("Updated people for collection {}", collection.getId());
  }

  /**
   * Handle collection-to-collection relationship updates. This manages which child collections
   * belong to this parent collection.
   *
   * @param parentCollection The collection being updated (parent collection)
   * @param collectionUpdates The collection update containing remove/prev/newValue operations
   */
  private void handleCollectionToCollectionUpdates(
      CollectionEntity parentCollection, CollectionRequests.CollectionUpdate collectionUpdates) {
    log.debug(
        "Handling collection-to-collection updates for collection {}", parentCollection.getId());

    // Step 1: Remove - unassociate child collections from parent collection
    if (collectionUpdates.remove() != null && !collectionUpdates.remove().isEmpty()) {
      List<ContentCollectionEntity> contentColEntities =
          findCurrentContentCollections(parentCollection, collectionUpdates.remove());

      // Continue even if no matching content collections are found
      if (!contentColEntities.isEmpty()) {
        List<Long> contentIdsToRemove =
            contentColEntities.stream().map(ContentCollectionEntity::getId).toList();

        collectionRepository.removeContentFromCollection(
            parentCollection.getId(), contentIdsToRemove);
        log.info(
            "Removed {} collection references from parent collection {}",
            contentIdsToRemove.size(),
            parentCollection.getId());
      } else {
        log.debug(
            "No matching content collections found to remove from collection {}",
            parentCollection.getId());
      }
    }

    // Step 2: New Value - add new child collections to parent collection
    if (collectionUpdates.newValue() != null && !collectionUpdates.newValue().isEmpty()) {
      for (Records.ChildCollection childCollection : collectionUpdates.newValue()) {
        // Find the child collection entity
        CollectionEntity childCollectionEntity =
            collectionRepository
                .findById(childCollection.collectionId())
                .orElseThrow(
                    () ->
                        new ResourceNotFoundException(
                            "Child collection not found: " + childCollection.collectionId()));

        // Check if ContentCollectionEntity already exists for this referenced
        // collection
        ContentCollectionEntity existingContentCollection =
            findOrCreateContentCollectionEntity(childCollectionEntity);

        Integer maxIndex =
            collectionRepository.getMaxOrderIndexForCollection(parentCollection.getId());
        Integer orderIndex =
            childCollection.orderIndex() != null
                ? childCollection.orderIndex()
                : (maxIndex != null ? maxIndex + 1 : 0);

        // Check if this content is already in the parent collection
        CollectionContentEntity existingJoinEntry =
            collectionRepository
                .findContentByCollectionIdAndContentId(
                    parentCollection.getId(), existingContentCollection.getId())
                .orElse(null);

        if (existingJoinEntry == null) {
          // Create new join table entry
          CollectionContentEntity newEntry =
              CollectionContentEntity.builder()
                  .collectionId(parentCollection.getId())
                  .contentId(existingContentCollection.getId())
                  .orderIndex(orderIndex)
                  .visible(childCollection.visible() != null ? childCollection.visible() : true)
                  .createdAt(LocalDateTime.now())
                  .updatedAt(LocalDateTime.now())
                  .build();

          collectionRepository.saveContent(newEntry);
          log.info(
              "Added collection {} to parent collection {} at index {}",
              childCollectionEntity.getId(),
              parentCollection.getId(),
              orderIndex);
        } else {
          // Update existing entry
          if (childCollection.orderIndex() != null) {
            collectionRepository.updateContentOrderIndex(
                existingJoinEntry.getId(), childCollection.orderIndex());
          }
          if (childCollection.visible() != null) {
            collectionRepository.updateContentVisible(
                existingJoinEntry.getId(), childCollection.visible());
          }
          log.info(
              "Updated existing collection reference in parent collection {}",
              parentCollection.getId());
        }
      }
    }

    // Step 3: Prev - update existing associations (orderIndex, visible)
    if (collectionUpdates.prev() != null && !collectionUpdates.prev().isEmpty()) {
      for (Records.ChildCollection prev : collectionUpdates.prev()) {
        // Find ContentCollectionEntity that references this collection
        ContentCollectionEntity contentCollectionEntity =
            findContentCollectionEntityByReferencedCollectionId(prev.collectionId());

        if (contentCollectionEntity == null) {
          log.warn(
              "No ContentCollectionEntity found for collection ID {} in prev update for parent collection {}",
              prev.collectionId(),
              parentCollection.getId());
          continue;
        }

        Optional<CollectionContentEntity> joinEntryOpt =
            collectionRepository.findContentByCollectionIdAndContentId(
                parentCollection.getId(), contentCollectionEntity.getId());

        if (joinEntryOpt.isPresent()) {
          CollectionContentEntity joinEntry = joinEntryOpt.get();
          if (prev.orderIndex() != null) {
            collectionRepository.updateContentOrderIndex(joinEntry.getId(), prev.orderIndex());
          }
          if (prev.visible() != null) {
            collectionRepository.updateContentVisible(joinEntry.getId(), prev.visible());
          }
          log.debug(
              "Updated existing collection reference {} in parent collection {}",
              contentCollectionEntity.getId(),
              parentCollection.getId());
        }
      }
    }
  }

  /**
   * Apply mutual sibling updates. Each {@code newValue} entry's collectionId is added via a
   * reciprocal INSERT; each {@code remove} id is deleted bidirectionally. Self-links are skipped
   * defensively (the DB CHECK also blocks them). No-op when {@code siblings} is null.
   */
  private void handleSiblingUpdates(Long parentId, CollectionRequests.CollectionUpdate siblings) {
    if (siblings == null) {
      return;
    }
    if (siblings.remove() != null) {
      for (Long siblingId : siblings.remove()) {
        if (siblingId == null || siblingId.equals(parentId)) {
          continue;
        }
        collectionSiblingRepository.removeSibling(parentId, siblingId);
      }
    }
    if (siblings.newValue() != null) {
      for (Records.ChildCollection entry : siblings.newValue()) {
        Long siblingId = entry.collectionId();
        if (siblingId == null || siblingId.equals(parentId)) {
          continue;
        }
        collectionSiblingRepository.addSibling(parentId, siblingId);
      }
    }
    log.info("Applied sibling updates for collection {}", parentId);
  }

  /**
   * Apply parent-collection updates by inverting the request and delegating to {@link
   * #handleCollectionToCollectionUpdates}. Each {@code newValue} parent gains the current
   * collection as a child; each {@code remove} parent drops it. Cycle validation (self-parent +
   * direct 2-cycle) runs first and throws {@link IllegalArgumentException} (mapped to 400 Bad
   * Request). No-op when {@code parents} is null.
   */
  private void handleParentCollectionUpdates(
      CollectionEntity currentCollection, CollectionRequests.CollectionUpdate parents) {
    if (parents == null) {
      return;
    }
    validateNoParentCycles(currentCollection, parents);
    if (parents.newValue() != null) {
      for (Records.ChildCollection entry : parents.newValue()) {
        Long parentId = entry.collectionId();
        if (parentId == null) {
          continue;
        }
        CollectionEntity parent =
            collectionRepository
                .findById(parentId)
                .orElseThrow(
                    () ->
                        new ResourceNotFoundException(
                            "Parent collection not found with ID: " + parentId));
        Records.ChildCollection currentAsChild =
            new Records.ChildCollection(
                currentCollection.getId(),
                currentCollection.getTitle(),
                currentCollection.getSlug(),
                null,
                null,
                null);
        handleCollectionToCollectionUpdates(
            parent, new CollectionRequests.CollectionUpdate(null, List.of(currentAsChild), null));
        recountParentTotalContent(parent);
      }
    }
    if (parents.remove() != null) {
      for (Long parentId : parents.remove()) {
        if (parentId == null || parentId.equals(currentCollection.getId())) {
          continue;
        }
        CollectionEntity parent =
            collectionRepository
                .findById(parentId)
                .orElseThrow(
                    () ->
                        new ResourceNotFoundException(
                            "Parent collection not found with ID: " + parentId));
        handleCollectionToCollectionUpdates(
            parent,
            new CollectionRequests.CollectionUpdate(
                null, null, List.of(currentCollection.getId())));
        recountParentTotalContent(parent);
      }
    }
    log.info("Applied parent collection updates for collection {}", currentCollection.getId());
  }

  /**
   * Recount a parent collection's join-table membership and persist its {@code totalContent}. The
   * inverse parent-update path mutates each parent's children directly, so without this the
   * parent's stored count drifts until it is next edited (mirrors the recount {@link
   * #updateContent} runs for the edited collection).
   */
  private void recountParentTotalContent(CollectionEntity parent) {
    parent.setTotalContent((int) collectionRepository.countContentByCollectionId(parent.getId()));
    collectionRepository.save(parent);
  }

  /**
   * Reject direct cycles before applying parent updates: a collection cannot be its own parent, and
   * a candidate parent that is already a child of the current collection would form a 2-cycle.
   * Deeper N-cycles are an accepted limitation.
   */
  private void validateNoParentCycles(
      CollectionEntity current, CollectionRequests.CollectionUpdate parents) {
    if (parents == null || parents.newValue() == null) {
      return;
    }
    Set<Long> existingChildIds =
        collectionRepository.findAllReferencedCollectionsByParentId(current.getId()).stream()
            .map(CollectionEntity::getId)
            .collect(Collectors.toSet());
    for (Records.ChildCollection entry : parents.newValue()) {
      Long parentId = entry.collectionId();
      if (parentId == null) {
        continue;
      }
      if (parentId.equals(current.getId())) {
        throw new IllegalArgumentException(
            "A collection cannot be its own parent (id=" + parentId + ")");
      }
      if (existingChildIds.contains(parentId)) {
        throw new IllegalArgumentException(
            "Cycle detected: collection "
                + parentId
                + " is already a child of "
                + current.getId()
                + " and cannot also be a parent");
      }
    }
  }

  /**
   * Find ContentCollectionEntity entries in the parent collection that match the provided IDs.
   * Accepts both content IDs (ContentCollectionEntity.id) and referenced collection IDs for
   * flexibility.
   *
   * @param parentCollection The parent collection to search in
   * @param idsToRemove IDs to match - can be either ContentCollectionEntity IDs or referenced
   *     collection IDs
   * @return List of ContentCollectionEntity entries that match, empty list if none found
   */
  private List<ContentCollectionEntity> findCurrentContentCollections(
      CollectionEntity parentCollection, List<Long> idsToRemove) {
    if (parentCollection == null || idsToRemove == null || idsToRemove.isEmpty()) {
      return Collections.emptyList();
    }

    List<ContentCollectionEntity> matchingContentCollections = new ArrayList<>();

    // Get all join table entries for this parent collection
    List<CollectionContentEntity> joinEntries =
        collectionRepository.findContentByCollectionIdOrderByOrderIndex(parentCollection.getId());

    for (CollectionContentEntity joinEntry : joinEntries) {
      Long contentId = joinEntry.getContentId();
      if (contentId == null) {
        continue;
      }

      // Load the content entity to check if it's a ContentCollectionEntity
      ContentCollectionEntity contentCollectionEntity =
          contentRepository.findCollectionContentById(contentId).orElse(null);
      if (contentCollectionEntity != null) {
        // Check if the ID matches either:
        // 1. The ContentCollectionEntity ID (content table ID) - matches API response
        // "id" field
        // 2. The referenced collection ID - matches API response
        // "referencedCollectionId" field
        Long contentCollectionId = contentCollectionEntity.getId();
        CollectionEntity referencedCollection = contentCollectionEntity.getReferencedCollection();
        Long referencedCollectionId =
            referencedCollection != null ? referencedCollection.getId() : null;

        boolean matchesContentId = idsToRemove.contains(contentCollectionId);
        boolean matchesReferencedId =
            referencedCollectionId != null && idsToRemove.contains(referencedCollectionId);

        if (matchesContentId || matchesReferencedId) {
          matchingContentCollections.add(contentCollectionEntity);
          log.debug(
              "Found matching ContentCollectionEntity {} (referencedCollectionId={}) for removal"
                  + " (matched by {})",
              contentCollectionId,
              referencedCollectionId,
              matchesContentId ? "contentId" : "referencedCollectionId");
        }
      }
    }

    if (matchingContentCollections.isEmpty()) {
      log.debug(
          "No matching ContentCollectionEntity entries found for removal in collection {}"
              + " (searched for IDs: {})",
          parentCollection.getId(),
          idsToRemove);
    } else {
      log.debug(
          "Found {} matching ContentCollectionEntity entries for removal in collection {}",
          matchingContentCollections.size(),
          parentCollection.getId());
    }

    return matchingContentCollections;
  }

  /**
   * Find or create a ContentCollectionEntity for a given referenced collection. Reuses existing
   * ContentCollectionEntity if one already exists for this collection.
   *
   * @param referencedCollection The collection to reference
   * @return The ContentCollectionEntity (existing or newly created)
   */
  private ContentCollectionEntity findOrCreateContentCollectionEntity(
      CollectionEntity referencedCollection) {
    // Search for existing ContentCollectionEntity that references this collection
    ContentCollectionEntity existing =
        findContentCollectionEntityByReferencedCollectionId(referencedCollection.getId());

    if (existing != null) {
      log.debug(
          "Found existing ContentCollectionEntity {} for collection {}",
          existing.getId(),
          referencedCollection.getId());
      return existing;
    }

    // Create new ContentCollectionEntity
    ContentCollectionEntity newContentCollection =
        ContentCollectionEntity.builder()
            .contentType(ContentType.COLLECTION)
            .referencedCollection(referencedCollection)
            .build();

    ContentCollectionEntity saved = contentRepository.saveCollectionContent(newContentCollection);
    log.info(
        "Created new ContentCollectionEntity {} for collection {}",
        saved.getId(),
        referencedCollection.getId());
    return saved;
  }

  /**
   * Find a ContentCollectionEntity that references a collection with the given ID.
   *
   * @param referencedCollectionId The ID of the referenced collection
   * @return The ContentCollectionEntity if found, null otherwise
   */
  private ContentCollectionEntity findContentCollectionEntityByReferencedCollectionId(
      Long referencedCollectionId) {
    return contentRepository
        .findCollectionContentByReferencedCollectionId(referencedCollectionId)
        .orElse(null);
  }

  @Transactional
  @CacheEvict(value = "collections", allEntries = true)
  public CollectionModel reorderContent(Long collectionId, CollectionRequests.Reorder request) {
    log.debug(
        "Reordering content in collection {} with {} reorder operations",
        collectionId,
        request.reorders().size());

    // 1. Verify collection exists
    CollectionEntity collection =
        collectionRepository
            .findById(collectionId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException("Collection not found with ID: " + collectionId));

    List<CollectionRequests.Reorder.ReorderItem> reorders = request.reorders();

    // 2. Validate all content IDs belong to this collection before updating
    List<Long> requestedContentIds =
        reorders.stream().map(CollectionRequests.Reorder.ReorderItem::contentId).toList();
    List<CollectionContentEntity> existingEntries =
        collectionRepository.findContentByCollectionIdOrderByOrderIndex(collectionId);
    Set<Long> validContentIds =
        existingEntries.stream()
            .map(CollectionContentEntity::getContentId)
            .collect(Collectors.toSet());

    for (Long contentId : requestedContentIds) {
      if (!validContentIds.contains(contentId)) {
        throw new IllegalArgumentException(
            "Content with ID " + contentId + " does not belong to collection " + collectionId);
      }
    }

    // 3. Build map and perform single bulk UPDATE with CASE statement
    Map<Long, Integer> contentIdToOrderIndex =
        reorders.stream()
            .collect(
                Collectors.toMap(
                    CollectionRequests.Reorder.ReorderItem::contentId,
                    CollectionRequests.Reorder.ReorderItem::newOrderIndex));

    int totalUpdated =
        collectionRepository.batchUpdateContentOrderIndexes(collectionId, contentIdToOrderIndex);
    log.info("Successfully reordered {} items in collection {}", totalUpdated, collectionId);

    // Return updated collection model
    List<CollectionContentEntity> updatedContent =
        collectionRepository.findContentByCollectionIdOrderByOrderIndex(collectionId);
    long totalElements = updatedContent.size();
    int pageSize = totalElements > 0 ? (int) totalElements : DEFAULT_PAGE_SIZE;
    CollectionModel model =
        collectionProcessingUtil.convertToModel(
            collection, updatedContent, 0, pageSize, totalElements);
    collectionProcessingUtil.populateCollectionsOnContent(model);
    return model;
  }

  /**
   * Enforce visibility on a collection for read endpoints.
   *
   * <ul>
   *   <li>HOME slug always passes (existing exception).
   *   <li>LISTED + UNLISTED both pass for direct slug access.
   *   <li>HIDDEN passes only when {@code isLocalEnvironment} is true; otherwise NotFound.
   * </ul>
   */
  private void enforceVisibility(CollectionEntity entity, String slug, boolean isLocalEnvironment) {
    if (HOME_SLUG.equals(slug)) {
      return;
    }
    CollectionVisibility v = entity.getVisibility();
    if (v == CollectionVisibility.HIDDEN && !isLocalEnvironment) {
      log.debug("Blocked HIDDEN collection {} from non-local request", slug);
      throw new ResourceNotFoundException("Collection not found with slug: " + slug);
    }
    // LISTED and UNLISTED both allow direct slug access.
  }

  private boolean isLocalEnvironment() {
    return springEnv.acceptsProfiles(Profiles.of("dev"));
  }

  /**
   * Remove child collection content items that reference children the viewer should not see in this
   * context. Default scope (e.g. PARENT-of-portfolios) drops UNLISTED + HIDDEN children so
   * directories don't leak unlisted work. Client-gallery context — viewing a CLIENT_GALLERY
   * directly, or a PARENT that contains at least one CLIENT_GALLERY child — drops only HIDDEN, so
   * UNLISTED client galleries (the typical visibility for password-protected work) remain visible
   * to viewers who have already navigated into the parent. Authentication is enforced upstream;
   * this method runs only for already-authorized responses.
   */
  private void filterNonListedChildCollections(CollectionModel model) {
    if (model == null || model.getContent() == null || model.getContent().isEmpty()) {
      return;
    }

    // Collect referenced collection IDs from collection content items
    List<Long> referencedIds =
        model.getContent().stream()
            .filter(ContentModels.Collection.class::isInstance)
            .map(ContentModels.Collection.class::cast)
            .map(ContentModels.Collection::referencedCollectionId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

    if (referencedIds.isEmpty()) {
      return;
    }

    // Batch-load referenced children once — used for both context detection and visibility filter
    List<CollectionEntity> children = collectionRepository.findByIds(referencedIds);

    boolean isClientGalleryContext =
        model.getType() == CollectionType.CLIENT_GALLERY
            || (model.getType() == CollectionType.PARENT
                && children.stream().anyMatch(c -> c.getType() == CollectionType.CLIENT_GALLERY));

    Set<Long> excludedIds =
        children.stream()
            .filter(
                c ->
                    isClientGalleryContext
                        ? c.getVisibility() == CollectionVisibility.HIDDEN
                        : !c.getVisibility().appearsInLists())
            .map(CollectionEntity::getId)
            .collect(Collectors.toSet());

    if (excludedIds.isEmpty()) {
      return;
    }

    List<ContentModel> filtered =
        model.getContent().stream()
            .filter(
                content -> {
                  if (content instanceof ContentModels.Collection col) {
                    return !excludedIds.contains(col.referencedCollectionId());
                  }
                  return true;
                })
            .collect(Collectors.toList());

    model.setContent(filtered);
    log.debug(
        "Filtered {} child collections from response (parent={}, clientGalleryContext={})",
        excludedIds.size(),
        model.getSlug(),
        isClientGalleryContext);
  }

  /**
   * Persists gallery password and recipient list, then sends emails when requested.
   *
   * <p>Three modes, driven by the request:
   *
   * <ul>
   *   <li>password null: clear password and recipients
   *   <li>password set, emails empty: set password, no email
   *   <li>password set, emails non-empty: set password and send one email per recipient
   * </ul>
   *
   * <p>Accepted target types are {@link CollectionType#CLIENT_GALLERY} and {@link
   * CollectionType#PARENT}. For PARENT targets, when {@link
   * GalleryAccessRequest#propagateToChildren()} is {@code true}, the same password is batch-written
   * to every {@link CollectionType#CLIENT_GALLERY} child referenced by the PARENT (other child
   * types are skipped). Recipient emails are NOT propagated. Returns {@code
   * GalleryAccessResponse(saved=false, reason="not-eligible-type")} for any other type.
   */
  @Transactional
  public GalleryAccessResponse updateGalleryAccess(Long id, GalleryAccessRequest request) {
    CollectionEntity entity = findEntityById(id);

    if (entity.getType() != CollectionType.CLIENT_GALLERY
        && entity.getType() != CollectionType.PARENT) {
      log.warn(
          "Refusing gallery-access update on ineligible collection (id={}, type={})",
          id,
          entity.getType());
      return new GalleryAccessResponse(false, false, "not-eligible-type", null, List.of());
    }

    List<String> emails =
        request.emails() != null && !request.emails().isEmpty() ? request.emails() : List.of();

    if (request.password() == null) {
      collectionRepository.saveGalleryAccess(id, null, List.of());
      log.info("Cleared gallery password and recipients (id={}, slug={})", id, entity.getSlug());
      return new GalleryAccessResponse(true, false, null, null, List.of());
    }

    collectionRepository.saveGalleryAccess(id, request.password(), emails);
    log.info(
        "Set gallery password (id={}, slug={}, recipients={})",
        id,
        entity.getSlug(),
        emails.size());

    propagatePasswordToChildrenIfRequested(entity, request);

    if (emails.isEmpty()) {
      return new GalleryAccessResponse(true, false, null, request.password(), List.of());
    }

    boolean allSent = true;
    String firstFailureReason = null;
    for (String email : emails) {
      EmailService.SendResult result =
          emailService.sendGalleryPasswordEmail(
              email, entity.getTitle(), entity.getSlug(), request.password());
      if (!result.sent() && allSent) {
        allSent = false;
        firstFailureReason = result.reason();
      }
    }

    return new GalleryAccessResponse(
        true, allSent, allSent ? null : firstFailureReason, request.password(), emails);
  }

  /**
   * When {@code request.propagateToChildren()} is {@code true} AND {@code parent} is of type {@link
   * CollectionType#PARENT}, batch-update the same password on every {@link
   * CollectionType#CLIENT_GALLERY} child referenced by that PARENT. Other child types (other
   * PARENTs, PORTFOLIOs, BLOGs, etc.) are skipped.
   */
  private void propagatePasswordToChildrenIfRequested(
      CollectionEntity parent, GalleryAccessRequest request) {
    if (!Boolean.TRUE.equals(request.propagateToChildren())
        || parent.getType() != CollectionType.PARENT) {
      log.debug(
          "Skipping password propagation (parentId={}, propagate={}, type={})",
          parent.getId(),
          request.propagateToChildren(),
          parent.getType());
      return;
    }
    List<CollectionEntity> children =
        collectionRepository.findAllReferencedCollectionsByParentId(parent.getId());
    long clientGalleryCount =
        children.stream().filter(c -> c.getType() == CollectionType.CLIENT_GALLERY).count();
    log.info(
        "Propagating password from parent (id={}, slug={}): {} children found, {} are CLIENT_GALLERY",
        parent.getId(),
        parent.getSlug(),
        children.size(),
        clientGalleryCount);
    for (CollectionEntity child : children) {
      if (child.getType() == CollectionType.CLIENT_GALLERY) {
        collectionRepository.updateGalleryPassword(child.getId(), request.password());
        log.info(
            "Propagated parent (id={}) gallery password to CLIENT_GALLERY child (id={}, slug={})",
            parent.getId(),
            child.getId(),
            child.getSlug());
      }
    }
  }
}
