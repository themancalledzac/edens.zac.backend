package edens.zac.portfolio.backend.services;

import static edens.zac.portfolio.backend.config.DefaultValues.default_content_per_page;

import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.CollectionRepository;
import edens.zac.portfolio.backend.dao.ContentRepository;
import edens.zac.portfolio.backend.dao.LocationRepository;
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
import edens.zac.portfolio.backend.model.ContentFilmTypeModel;
import edens.zac.portfolio.backend.model.ContentModel;
import edens.zac.portfolio.backend.model.ContentModels;
import edens.zac.portfolio.backend.model.GeneralMetadataDTO;
import edens.zac.portfolio.backend.model.LocationPageResponse;
import edens.zac.portfolio.backend.model.Records;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.ContentType;
import edens.zac.portfolio.backend.types.FilmFormat;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for managing Collection entities with pagination and client gallery access. */
@Service
@Slf4j
@RequiredArgsConstructor
public class CollectionService {

  private final CollectionRepository collectionRepository;
  private final ContentRepository contentRepository;
  private final LocationRepository locationRepository;
  private final TagRepository tagRepository;
  private final ContentProcessingUtil contentProcessingUtil;
  private final CollectionProcessingUtil collectionProcessingUtil;
  private final MetadataService metadataService;

  @Value("${app.access-token.secret}")
  private String accessTokenSecret;

  private static final int DEFAULT_PAGE_SIZE = default_content_per_page;

  @Transactional(readOnly = true)
  public CollectionModel getCollectionWithPagination(String slug, int page, int size) {
    log.debug("Getting collection with slug: {} (page: {}, size: {})", slug, page, size);

    // Get collection metadata
    CollectionEntity collection =
        collectionRepository
            .findBySlug(slug)
            .orElseThrow(
                () -> new ResourceNotFoundException("Collection not found with slug: " + slug));

    // Normalize pagination parameters
    int normalizedPage = Math.max(0, page);
    int normalizedSize = size <= 0 ? DEFAULT_PAGE_SIZE : size;
    int offset = normalizedPage * normalizedSize;

    // Get total count for pagination
    long totalElements = collectionRepository.countContentByCollectionId(collection.getId());

    // Get paginated join table entries (collection-content associations)
    List<CollectionContentEntity> collectionContentList =
        collectionRepository.findContentByCollectionId(collection.getId(), normalizedSize, offset);

    // Convert to model (now using join table data)
    CollectionModel model =
        collectionProcessingUtil.convertToModel(
            collection, collectionContentList, normalizedPage, normalizedSize, totalElements);

    // Populate collections on content items
    populateCollectionsOnContent(model);

    return model;
  }

  @Transactional(readOnly = true)
  public boolean validateClientGalleryAccess(String slug, String password) {
    log.debug("Validating access to client gallery: {}", slug);

    CollectionEntity collection =
        collectionRepository
            .findBySlug(slug)
            .orElseThrow(
                () -> new ResourceNotFoundException("Collection not found with slug: " + slug));

    // Not password-protected — allow access
    if (collection.getPasswordHash() == null) {
      return true;
    }

    // Password required but not provided
    if (password == null || password.isEmpty()) {
      return false;
    }

    // Compare submitted password against stored hash
    return CollectionProcessingUtil.passwordMatches(password, collection.getPasswordHash());
  }

  /**
   * Generate a time-limited HMAC access token for a client gallery.
   *
   * @param slug Collection slug
   * @return HMAC token with embedded expiry
   */
  public String generateAccessToken(String slug) {
    long expiry = Instant.now().plus(Duration.ofHours(24)).getEpochSecond();
    String payload = slug + "|" + expiry;
    String hmac = computeHmac(payload, accessTokenSecret);
    return hmac + "|" + expiry;
  }

  /**
   * Validate a time-limited HMAC access token for a client gallery.
   *
   * @param slug Collection slug
   * @param accessToken The token to validate
   * @return true if valid and not expired
   */
  @Transactional(readOnly = true)
  public boolean validateAccessToken(String slug, String accessToken) {
    if (accessToken == null || !accessToken.contains("|")) {
      return false;
    }
    // Look up the collection; if it doesn't exist, deny access
    Optional<CollectionEntity> optCollection = collectionRepository.findBySlug(slug);
    if (optCollection.isEmpty()) {
      return false;
    }
    // Non-protected collections are always accessible
    if (optCollection.get().getPasswordHash() == null) {
      return true;
    }

    String[] parts = accessToken.split("\\|");
    if (parts.length != 2) {
      return false;
    }
    try {
      long expiry = Long.parseLong(parts[1]);
      if (Instant.now().getEpochSecond() > expiry) {
        return false;
      }
      String expectedHmac = computeHmac(slug + "|" + expiry, accessTokenSecret);
      return MessageDigest.isEqual(
          expectedHmac.getBytes(StandardCharsets.UTF_8), parts[0].getBytes(StandardCharsets.UTF_8));
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private String computeHmac(String data, String secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
    } catch (Exception e) {
      throw new RuntimeException("HMAC computation failed", e);
    }
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

  @Transactional(readOnly = true)
  public List<CollectionModel> findVisibleByTypeOrderByDate(CollectionType type) {
    log.debug("Finding visible collections by type ordered by date: {}", type);

    // Get visible collections by type, ordered by collection date descending
    // (newest first)
    List<CollectionEntity> collections =
        collectionRepository.findByTypeAndVisibleTrueOrderByCollectionDateDesc(type);

    // Convert to basic CollectionModel objects (no content blocks) using batch loading
    return collectionProcessingUtil.batchConvertToBasicModels(collections);
  }

  @Transactional(readOnly = true)
  public LocationPageResponse getLocationPage(
      String locationName, int collectionPage, int collectionSize, int imagePage, int imageSize) {
    log.debug("Getting location page for: {}", locationName);

    // Get visible collections at this location
    long totalCollections = collectionRepository.countVisibleByLocationName(locationName);
    int collectionOffset = collectionPage * collectionSize;
    List<CollectionEntity> collectionEntities =
        collectionRepository.findVisibleByLocationName(
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
      allCollectionIds = collectionRepository.findVisibleIdsByLocationName(locationName);
    }

    // Get orphan images (at this location but not in any of those collections)
    int imageOffset = imagePage * imageSize;
    List<ContentImageEntity> orphanImageEntities =
        contentRepository.findOrphanImagesByLocationName(
            locationName, allCollectionIds, imageSize, imageOffset);
    long totalImages =
        contentRepository.countOrphanImagesByLocationName(locationName, allCollectionIds);

    List<ContentModels.Image> images =
        contentProcessingUtil.batchConvertImageEntitiesToModels(orphanImageEntities);

    // Resolve the location record from already-converted collections
    Records.Location location =
        collections.isEmpty()
            ? new Records.Location(null, locationName, SlugUtil.generateSlug(locationName))
            : collections.getFirst().getLocation();

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
    return collectionRepository
        .findBySlug(slug)
        .map(collectionProcessingUtil::convertToBasicModel)
        .orElseThrow(
            () -> new ResourceNotFoundException("Collection not found with slug: " + slug));
  }

  @Transactional(readOnly = true)
  public Optional<CollectionModel> findBySlug(String slug) {
    log.debug("Finding collection by slug: {}", slug);

    // Get collection metadata only - content is fetched via join table in
    // convertToFullModel
    return collectionRepository.findBySlug(slug).map(this::convertToFullModel);
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
    return convertToFullModel(entity);
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

  @Transactional
  @CacheEvict(value = "generalMetadata", allEntries = true)
  public void deleteCollection(Long id) {
    log.debug("Deleting collection with ID: {}", id);

    // Check if collection exists
    if (collectionRepository.findById(id).isEmpty()) {
      throw new ResourceNotFoundException("Collection not found with ID: " + id);
    }

    // Delete all join table entries (dissociate content from collection)
    // This does NOT delete the content itself - content is reusable!
    collectionRepository.deleteContentByCollectionId(id);
    log.debug("Deleted all join table entries for collection ID: {}", id);

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
  public List<CollectionModel> getAllCollectionsOrderedByDate() {
    log.debug("Getting all collections ordered by collection date");

    // Get all collections ordered by collection date descending
    List<CollectionEntity> collections = collectionRepository.findAllByOrderByCollectionDateDesc();

    // Convert to basic models (no content blocks) using batch loading
    return collectionProcessingUtil.batchConvertToBasicModels(collections);
  }

  /**
   * Convert a CollectionEntity to a CollectionModel with all content. Efficiently batch-loads
   * collections for all content items to avoid N+1 queries.
   *
   * @param entity The entity to convert
   * @return The converted model
   */
  private CollectionModel convertToFullModel(CollectionEntity entity) {
    CollectionModel model = collectionProcessingUtil.convertToBasicModel(entity);
    if (model == null) {
      // Defensive: mocked util may return null in tests; ensure non-null model to
      // avoid NPE
      model = new CollectionModel();
    }

    // Fetch join table entries explicitly to get content with collection-specific
    // metadata
    List<CollectionContentEntity> joinEntries =
        collectionRepository.findContentByCollectionIdOrderByOrderIndex(entity.getId());

    if (joinEntries.isEmpty()) {
      model.setContent(Collections.emptyList());
      return model;
    }

    // Extract content IDs and bulk load content entities
    List<Long> contentIds =
        joinEntries.stream()
            .map(CollectionContentEntity::getContentId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

    // Bulk load all content entities
    final Map<Long, ContentEntity> contentMap;
    if (!contentIds.isEmpty()) {
      List<ContentEntity> contentEntities = contentRepository.findAllByIds(contentIds);
      contentMap =
          contentEntities.stream().collect(Collectors.toMap(ContentEntity::getId, ce -> ce));
    } else {
      contentMap = new HashMap<>();
    }

    // Convert join table entries to content models using bulk-loaded entities
    List<ContentModel> contents =
        joinEntries.stream()
            .map(
                joinEntry -> {
                  ContentEntity content = contentMap.get(joinEntry.getContentId());
                  if (content == null) {
                    log.warn(
                        "Content entity {} not found for collection {}",
                        joinEntry.getContentId(),
                        entity.getId());
                    return null;
                  }
                  return contentProcessingUtil.convertBulkLoadedContentToModel(content, joinEntry);
                })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    model.setContent(contents);

    // Populate collections on content items
    populateCollectionsOnContent(model);

    return model;
  }

  /**
   * Populate collections on content items in a CollectionModel. Batch-loads all collections for all
   * content items and populates the collections field on ContentModels.Image instances. This avoids
   * N+1 queries by pre-loading all collections and cover images upfront.
   *
   * @param model The CollectionModel with content items to populate
   */
  private void populateCollectionsOnContent(CollectionModel model) {
    if (model == null || model.getContent() == null || model.getContent().isEmpty()) {
      return;
    }

    // Extract all content IDs for batch loading
    List<Long> contentIds =
        model.getContent().stream()
            .map(ContentModel::id)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

    if (contentIds.isEmpty()) {
      return;
    }

    // Batch-load all collections for all content items in one query
    List<CollectionContentEntity> allCollections =
        collectionRepository.findContentByContentIdsIn(contentIds);
    Map<Long, List<CollectionContentEntity>> collectionsByContentId =
        allCollections.stream()
            .collect(Collectors.groupingBy(CollectionContentEntity::getContentId));

    // Extract all unique collection IDs and batch-load them
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

    // Extract all unique cover image IDs and batch-load them
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

    // Populate collections for image content (records are immutable -- use withCollections)
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
   * Convert a CollectionContentEntity to a ChildCollection model using pre-loaded collections and
   * cover images. Used for populating the collections field in ContentModels.Image. This avoids N+1
   * queries by using maps instead of individual database lookups.
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

  @Transactional(readOnly = true)
  public CollectionRequests.UpdateResponse getUpdateCollectionData(String slug) {
    log.debug("Getting update collection data for slug: {}", slug);

    // Get the collection
    CollectionModel collection =
        findBySlug(slug)
            .orElseThrow(
                () -> new ResourceNotFoundException("Collection not found with slug: " + slug));

    // Get all general metadata using helper method
    GeneralMetadataDTO metadata = getGeneralMetadata();

    // Build and return response DTO with collection and metadata
    return new CollectionRequests.UpdateResponse(collection, metadata);
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
   * ContentProcessingUtil.
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
        contentProcessingUtil.updateTags(
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
   * ContentProcessingUtil.
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
        contentProcessingUtil.updatePeople(
            currentPeople, personUpdate, null // No tracking needed for collection updates
            );

    // Save updated people
    List<Long> updatedPersonIds =
        updatedPeople.stream()
            .map(ContentPersonEntity::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    collectionRepository.saveCollectionPeople(collection.getId(), updatedPersonIds);
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
                  .visible(childCollection.visible() != null ? childCollection.visible() : false)
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
    populateCollectionsOnContent(model);
    return model;
  }
}
