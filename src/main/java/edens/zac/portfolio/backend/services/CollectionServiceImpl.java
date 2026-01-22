package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import edens.zac.portfolio.backend.entity.ContentEntity;
import edens.zac.portfolio.backend.entity.ContentTagEntity;
import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import edens.zac.portfolio.backend.model.*;
import edens.zac.portfolio.backend.dao.CollectionDao;
import edens.zac.portfolio.backend.dao.CollectionContentDao;
import edens.zac.portfolio.backend.dao.ContentDao;
import edens.zac.portfolio.backend.dao.ContentCollectionDao;
import edens.zac.portfolio.backend.dao.TagDao;
import edens.zac.portfolio.backend.types.CollectionType;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import edens.zac.portfolio.backend.types.ContentType;
import edens.zac.portfolio.backend.types.FilmFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static edens.zac.portfolio.backend.config.DefaultValues.default_content_per_page;

/**
 * Implementation of ContentCollectionService that provides methods for
 * managing ContentCollection entities with pagination and client gallery
 * access.
 */
@Service
@Slf4j
@RequiredArgsConstructor
class CollectionServiceImpl implements CollectionService {

    private final CollectionDao collectionDao;
    private final CollectionContentDao collectionContentDao;
    private final ContentDao contentDao;
    private final ContentCollectionDao contentCollectionDao;
    private final TagDao tagDao;
    private final ContentProcessingUtil contentProcessingUtil;
    private final CollectionProcessingUtil collectionProcessingUtil;
    private final ContentService contentService;

    private static final int DEFAULT_PAGE_SIZE = default_content_per_page;

    @Override
    @Transactional(readOnly = true)
    public CollectionModel getCollectionWithPagination(String slug, int page, int size) {
        log.debug("Getting collection with slug: {} (page: {}, size: {})", slug, page, size);

        // Get collection metadata
        CollectionEntity collection = collectionDao.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Collection not found with slug: " + slug));

        // Normalize pagination parameters
        int normalizedPage = Math.max(0, page);
        int normalizedSize = size <= 0 ? DEFAULT_PAGE_SIZE : size;
        int offset = normalizedPage * normalizedSize;

        // Get total count for pagination
        long totalElements = collectionContentDao.countByCollectionId(collection.getId());

        // Get paginated join table entries (collection-content associations)
        List<CollectionContentEntity> collectionContentList = collectionContentDao
                .findByCollectionId(collection.getId(), normalizedSize, offset);

        // Convert to model (now using join table data)
        CollectionModel model = collectionProcessingUtil.convertToModel(collection, collectionContentList,
                normalizedPage, normalizedSize, totalElements);

        // Populate collections on content items
        populateCollectionsOnContent(model);

        return model;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean validateClientGalleryAccess(String slug, String password) {
        log.debug("Validating access to client gallery: {}", slug);

        // TODO: Re-implement password protection after migration
        // Password protection temporarily disabled during refactoring

        // Verify collection exists (using existsBySlug for efficiency when we don't
        // need the entity)
        if (!collectionDao.existsBySlug(slug)) {
            throw new IllegalArgumentException("Collection not found with slug: " + slug);
        }

        // For now, all galleries are accessible
        return true;

        // TODO: Uncomment when password protection is re-implemented
        // // Check if collection is password-protected
        // if (!collection.isPasswordProtected()) {
        // return true; // No password required
        // }
        //
        // // Validate password
        // if (password == null || password.isEmpty()) {
        // return false; // Password required but not provided
        // }
        //
        // // Check if password matches
        // return CollectionProcessingUtil.passwordMatches(password,
        // collection.getPasswordHash());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CollectionModel> findByType(CollectionType type, Pageable pageable) {
        log.debug("Finding collections by type: {}", type);

        // Get total count
        long totalElements = collectionDao.countByType(type);

        // Get all collections of this type (no pagination in DAO yet, so we'll paginate
        // in memory)
        List<CollectionEntity> allCollections = collectionDao.findTop50ByTypeOrderByCollectionDateDesc(type);

        // Apply pagination manually
        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();
        int start = page * size;
        int end = Math.min(start + size, allCollections.size());

        List<CollectionEntity> paginatedCollections = start < allCollections.size()
                ? allCollections.subList(start, end)
                : Collections.emptyList();

        // Convert to models
        List<CollectionModel> models = paginatedCollections.stream()
                .map(collectionProcessingUtil::convertToBasicModel)
                .collect(Collectors.toList());

        return new PageImpl<>(models, pageable, totalElements);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CollectionModel> findVisibleByTypeOrderByDate(CollectionType type) {
        log.debug("Finding visible collections by type ordered by date: {}", type);

        // Get visible collections by type, ordered by collection date descending
        // (newest first)
        List<CollectionEntity> collections = collectionDao
                .findByTypeAndVisibleTrueOrderByCollectionDateDesc(type);

        // Convert to basic CollectionModel objects (no content blocks)
        return collections.stream()
                .map(collectionProcessingUtil::convertToBasicModel)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CollectionModel> findBySlug(String slug) {
        log.debug("Finding collection by slug: {}", slug);

        // Get collection metadata only - content is fetched via join table in
        // convertToFullModel
        return collectionDao.findBySlug(slug)
                .map(this::convertToFullModel);
    }

    @Override
    @Transactional
    @CacheEvict(value = "generalMetadata", allEntries = true)
    public CollectionUpdateResponseDTO createCollection(CollectionCreateRequest createRequest) {
        log.debug("Creating new collection: {}", createRequest.getTitle());

        // Create entity using utility converter
        CollectionEntity entity = collectionProcessingUtil.toEntity(
                createRequest,
                DEFAULT_PAGE_SIZE);

        // Save entity
        CollectionEntity savedEntity = collectionDao.save(entity);

        // Return full update response with all metadata (tags, people, cameras, etc.)
        return getUpdateCollectionData(savedEntity.getSlug());
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionModel findById(Long id) {
        log.debug("Finding collection by ID: {}", id);

        // Get collection entity with content blocks
        CollectionEntity entity = collectionDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Collection not found with ID: " + id));

        // Convert to full model (includes content blocks)
        return convertToFullModel(entity);
    }

    @Override
    @Transactional
    @CacheEvict(value = "generalMetadata", allEntries = true, condition = "#updateDTO != null && (#updateDTO.title != null || #updateDTO.slug != null)")
    public CollectionModel updateContent(Long id, CollectionUpdateRequest updateDTO) {
        log.debug("Updating collection with ID: {}", id);

        // Get existing entity
        CollectionEntity entity = collectionDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Collection not found with ID: " + id));

        // Update basic properties via utility helper
        collectionProcessingUtil.applyBasicUpdates(entity, updateDTO);

        // Handle tag updates using prev/new/remove pattern
        if (updateDTO.getTags() != null) {
            updateCollectionTags(entity, updateDTO.getTags());
        }

        // Handle people updates using prev/new/remove pattern
        if (updateDTO.getPeople() != null) {
            updateCollectionPeople(entity, updateDTO.getPeople());
        }

        // Handle collection updates using prev/new/remove pattern
        // This manages which parent collections this collection belongs to
        if (updateDTO.getCollections() != null) {
            handleCollectionToCollectionUpdates(entity, updateDTO.getCollections());
        }

        // Update total blocks count from join table before saving
        long totalBlocks = collectionContentDao.countByCollectionId(entity.getId());
        entity.setTotalContent((int) totalBlocks);

        // Save updated entity
        CollectionEntity savedEntity = collectionDao.save(entity);

        return convertToFullModel(savedEntity);
    }

    @Override
    @Transactional
    @CacheEvict(value = "generalMetadata", allEntries = true)
    public void deleteCollection(Long id) {
        log.debug("Deleting collection with ID: {}", id);

        // Check if collection exists
        if (!collectionDao.findById(id).isPresent()) {
            throw new IllegalArgumentException("Collection not found with ID: " + id);
        }

        // Delete all join table entries (dissociate content from collection)
        // This does NOT delete the content itself - content is reusable!
        collectionContentDao.deleteByCollectionId(id);
        log.debug("Deleted all join table entries for collection ID: {}", id);

        // Delete collection
        collectionDao.deleteById(id);
        log.info("Successfully deleted collection with ID: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CollectionModel> getAllCollections(Pageable pageable) {
        log.debug("Getting all collections with pagination");

        // Get all collections (no pagination in DAO yet, so we'll paginate in memory)
        List<CollectionEntity> allCollections = collectionDao.findAllByOrderByCollectionDateDesc();
        long totalElements = allCollections.size();

        // Apply pagination manually
        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();
        int start = page * size;
        int end = Math.min(start + size, allCollections.size());

        List<CollectionEntity> paginatedCollections = start < allCollections.size()
                ? allCollections.subList(start, end)
                : Collections.emptyList();

        // Convert to models
        List<CollectionModel> models = paginatedCollections.stream()
                .map(collectionProcessingUtil::convertToBasicModel)
                .collect(Collectors.toList());

        return new PageImpl<>(models, pageable, totalElements);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CollectionModel> getAllCollectionsOrderedByDate() {
        log.debug("Getting all collections ordered by collection date");

        // Get all collections ordered by collection date descending
        List<CollectionEntity> collections = collectionDao.findAllByOrderByCollectionDateDesc();

        // Convert to basic models (no content blocks)
        return collections.stream()
                .map(collectionProcessingUtil::convertToBasicModel)
                .collect(Collectors.toList());
    }

    /**
     * Convert a CollectionEntity to a CollectionModel with all content.
     * Efficiently batch-loads collections for all content items to avoid N+1
     * queries.
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
        List<CollectionContentEntity> joinEntries = collectionContentDao
                .findByCollectionIdOrderByOrderIndex(entity.getId());

        if (joinEntries.isEmpty()) {
            model.setContent(Collections.emptyList());
            return model;
        }

        // Extract content IDs and bulk load content entities
        List<Long> contentIds = joinEntries.stream()
                .map(CollectionContentEntity::getContentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // Bulk load all content entities
        final Map<Long, ContentEntity> contentMap;
        if (!contentIds.isEmpty()) {
            List<ContentEntity> contentEntities = contentDao.findAllByIds(contentIds);
            contentMap = contentEntities.stream()
                    .collect(Collectors.toMap(ContentEntity::getId, ce -> ce));
        } else {
            contentMap = new HashMap<>();
        }

        // Convert join table entries to content models using bulk-loaded entities
        List<ContentModel> contents = joinEntries.stream()
                .map(joinEntry -> {
                    ContentEntity content = contentMap.get(joinEntry.getContentId());
                    if (content == null) {
                        log.warn("Content entity {} not found for collection {}",
                                joinEntry.getContentId(), entity.getId());
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
     * Populate collections on content items in a CollectionModel.
     * Batch-loads all collections for all content items and populates the
     * collections field
     * on ContentImageModel instances. This avoids N+1 queries.
     *
     * @param model The CollectionModel with content items to populate
     */
    private void populateCollectionsOnContent(CollectionModel model) {
        if (model == null || model.getContent() == null || model.getContent().isEmpty()) {
            return;
        }

        // Extract all content IDs for batch loading
        List<Long> contentIds = model.getContent().stream()
                .map(ContentModel::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (contentIds.isEmpty()) {
            return;
        }

        // Batch-load all collections for all content items in one query
        List<CollectionContentEntity> allCollections = collectionContentDao.findByContentIdsIn(contentIds);
        Map<Long, List<CollectionContentEntity>> collectionsByContentId = allCollections.stream()
                .collect(Collectors.groupingBy(CollectionContentEntity::getContentId));

        // Populate collections for image content
        List<ContentModel> contents = model.getContent().stream()
                .peek(content -> {
                    if (content instanceof ContentImageModel imageModel) {
                        Long contentId = content.getId();
                        List<CollectionContentEntity> contentCollections = collectionsByContentId
                                .getOrDefault(contentId, Collections.emptyList());
                        List<ChildCollection> childCollections = contentCollections.stream()
                                .map(this::convertToChildCollection)
                                .collect(Collectors.toList());
                        imageModel.setCollections(childCollections);
                    }
                })
                .collect(Collectors.toList());

        model.setContent(contents);
    }

    /**
     * Convert a CollectionContentEntity to a ChildCollection model.
     * Used for populating the collections field in ContentImageModel.
     *
     * @param joinEntry The join table entry
     * @return The ChildCollection model
     */
    private ChildCollection convertToChildCollection(CollectionContentEntity joinEntry) {
        if (joinEntry == null || joinEntry.getCollectionId() == null) {
            return null;
        }

        CollectionEntity collection = collectionDao.findById(joinEntry.getCollectionId())
                .orElse(null);
        if (collection == null) {
            return null;
        }

        final String coverImageUrl = collection.getCoverImageId() != null
                ? contentDao.findImageById(collection.getCoverImageId())
                        .map(image -> image.getImageUrlWeb())
                        .orElse(null)
                : null;

        return ChildCollection.builder()
                .collectionId(collection.getId())
                .name(collection.getTitle())
                .slug(collection.getSlug())
                .coverImageUrl(coverImageUrl)
                .visible(joinEntry.getVisible())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionUpdateResponseDTO getUpdateCollectionData(String slug) {
        log.debug("Getting update collection data for slug: {}", slug);

        // Get the collection
        CollectionModel collection = findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Collection not found with slug: " + slug));

        // Get all general metadata using helper method
        GeneralMetadataDTO metadata = getGeneralMetadata();

        // Build and return response DTO with collection and metadata
        return CollectionUpdateResponseDTO.builder()
                .collection(collection)
                .metadata(metadata)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "generalMetadata", unless = "#result == null")
    public GeneralMetadataDTO getGeneralMetadata() {
        log.debug("Getting general metadata (cache miss)");

        // Get all tags, people, locations, cameras, lenses, and film types from
        // ContentService
        List<ContentTagModel> tags = contentService.getAllTags();
        List<ContentPersonModel> people = contentService.getAllPeople();
        List<LocationModel> locations = contentService.getAllLocations();
        List<ContentCameraModel> cameras = contentService.getAllCameras();
        List<ContentLensModel> lenses = contentService.getAllLenses();
        List<ContentFilmTypeModel> filmTypes = contentService.getAllFilmTypes();

        // Get all collections as CollectionListModel (using projection for efficiency)
        List<CollectionListModel> collections = collectionDao.findIdAndTitleOnly().stream()
                .map(summary -> CollectionListModel.builder()
                        .id(summary.id())
                        .name(summary.title())
                        .build())
                .collect(Collectors.toList());

        // Convert FilmFormat enums to DTOs
        List<FilmFormatDTO> filmFormats = java.util.Arrays.stream(FilmFormat.values())
                .map(this::convertToFilmFormatDTO)
                .collect(Collectors.toList());

        // Build and return metadata DTO
        return GeneralMetadataDTO.builder()
                .tags(tags)
                .people(people)
                .locations(locations)
                .collections(collections)
                .cameras(cameras)
                .lenses(lenses)
                .filmTypes(filmTypes)
                .filmFormats(filmFormats)
                .build();
    }

    /**
     * Convert FilmFormat enum to FilmFormatDTO
     */
    private FilmFormatDTO convertToFilmFormatDTO(FilmFormat filmFormat) {
        return FilmFormatDTO.builder()
                .name(filmFormat.name())
                .displayName(filmFormat.getDisplayName())
                .build();
    }

    /**
     * Update collection tags using prev/new/remove pattern.
     * Uses shared utility method from ContentProcessingUtil.
     *
     * @param collection The collection to update
     * @param tagUpdate  The tag update containing remove/prev/newValue operations
     */
    private void updateCollectionTags(CollectionEntity collection, TagUpdate tagUpdate) {
        // Load current tags
        List<Long> tagIds = tagDao.findCollectionTagIds(collection.getId());
        Set<ContentTagEntity> currentTags = tagIds.stream()
                .map(tagId -> {
                    // Create minimal tag entity with just ID - full loading not needed for update
                    ContentTagEntity tag = new ContentTagEntity();
                    tag.setId(tagId);
                    return tag;
                })
                .collect(Collectors.toSet());

        Set<ContentTagEntity> updatedTags = contentProcessingUtil.updateTags(
                currentTags,
                tagUpdate,
                null // No tracking needed for collection updates
        );

        // Save updated tags
        List<Long> updatedTagIds = updatedTags.stream()
                .map(ContentTagEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        tagDao.saveCollectionTags(collection.getId(), updatedTagIds);
        log.info("Updated tags for collection {}", collection.getId());
    }

    /**
     * Update collection people using prev/new/remove pattern.
     * Uses shared utility method from ContentProcessingUtil.
     *
     * @param collection   The collection to update
     * @param personUpdate The person update containing remove/prev/newValue
     *                     operations
     */
    private void updateCollectionPeople(CollectionEntity collection, PersonUpdate personUpdate) {
        // Load current people
        List<Long> personIds = collectionDao.findCollectionPersonIds(collection.getId());
        Set<ContentPersonEntity> currentPeople = personIds.stream()
                .map(personId -> {
                    // Create minimal person entity with just ID - full loading not needed for
                    // update
                    ContentPersonEntity person = new ContentPersonEntity();
                    person.setId(personId);
                    return person;
                })
                .collect(Collectors.toSet());

        Set<ContentPersonEntity> updatedPeople = contentProcessingUtil.updatePeople(
                currentPeople,
                personUpdate,
                null // No tracking needed for collection updates
        );

        // Save updated people
        List<Long> updatedPersonIds = updatedPeople.stream()
                .map(ContentPersonEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        collectionDao.saveCollectionPeople(collection.getId(), updatedPersonIds);
        log.info("Updated people for collection {}", collection.getId());
    }

    /**
     * Handle collection-to-collection relationship updates.
     * This manages which child collections belong to this parent collection.
     *
     * @param parentCollection  The collection being updated (parent collection)
     * @param collectionUpdates The collection update containing
     *                          remove/prev/newValue operations
     */
    private void handleCollectionToCollectionUpdates(CollectionEntity parentCollection,
            CollectionUpdate collectionUpdates) {
        log.debug("Handling collection-to-collection updates for collection {}", parentCollection.getId());

        // Step 1: Remove - unassociate child collections from parent collection
        if (collectionUpdates.getRemove() != null && !collectionUpdates.getRemove().isEmpty()) {
            List<ContentCollectionEntity> contentColEntities = findCurrentContentCollections(parentCollection,
                    collectionUpdates.getRemove());

            // Continue even if no matching content collections are found
            if (!contentColEntities.isEmpty()) {
                List<Long> contentIdsToRemove = contentColEntities.stream()
                        .map(ContentCollectionEntity::getId)
                        .toList();

                collectionContentDao.removeContentFromCollection(parentCollection.getId(), contentIdsToRemove);
                log.info("Removed {} collection references from parent collection {}", contentIdsToRemove.size(),
                        parentCollection.getId());
            } else {
                log.debug("No matching content collections found to remove from collection {}",
                        parentCollection.getId());
            }
        }

        // Step 2: New Value - add new child collections to parent collection
        if (collectionUpdates.getNewValue() != null && !collectionUpdates.getNewValue().isEmpty()) {
            for (ChildCollection childCollection : collectionUpdates.getNewValue()) {
                // Find the child collection entity
                CollectionEntity childCollectionEntity = collectionDao.findById(childCollection.getCollectionId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Child collection not found: " + childCollection.getCollectionId()));

                // Check if ContentCollectionEntity already exists for this referenced
                // collection
                ContentCollectionEntity existingContentCollection = findOrCreateContentCollectionEntity(
                        childCollectionEntity);

                Integer orderIndex = childCollection.getOrderIndex() != null
                        ? childCollection.getOrderIndex()
                        : (collectionContentDao.getMaxOrderIndexForCollection(parentCollection.getId()) != null
                                ? collectionContentDao.getMaxOrderIndexForCollection(parentCollection.getId()) + 1
                                : 0);

                // Check if this content is already in the parent collection
                CollectionContentEntity existingJoinEntry = collectionContentDao
                        .findByCollectionIdAndContentId(parentCollection.getId(), existingContentCollection.getId())
                        .orElse(null);

                if (existingJoinEntry == null) {
                    // Create new join table entry
                    CollectionContentEntity newEntry = CollectionContentEntity.builder()
                            .collectionId(parentCollection.getId())
                            .contentId(existingContentCollection.getId())
                            .orderIndex(orderIndex)
                            .visible(childCollection.getVisible() != null ? childCollection.getVisible() : false)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();

                    collectionContentDao.save(newEntry);
                    log.info("Added collection {} to parent collection {} at index {}",
                            childCollectionEntity.getId(), parentCollection.getId(), orderIndex);
                } else {
                    // Update existing entry
                    if (childCollection.getOrderIndex() != null) {
                        collectionContentDao.updateOrderIndex(existingJoinEntry.getId(),
                                childCollection.getOrderIndex());
                    }
                    if (childCollection.getVisible() != null) {
                        collectionContentDao.updateVisible(existingJoinEntry.getId(), childCollection.getVisible());
                    }
                    log.info("Updated existing collection reference in parent collection {}", parentCollection.getId());
                }
            }
        }

        // Step 3: Prev - update existing associations (orderIndex, visible)
        if (collectionUpdates.getPrev() != null && !collectionUpdates.getPrev().isEmpty()) {
            for (ChildCollection prev : collectionUpdates.getPrev()) {
                // Find ContentCollectionEntity that references this collection
                ContentCollectionEntity contentCollectionEntity = findContentCollectionEntityByReferencedCollectionId(
                        prev.getCollectionId());

                if (contentCollectionEntity == null) {
                    log.warn(
                            "No ContentCollectionEntity found for collection ID {} in prev update for parent collection {}",
                            prev.getCollectionId(), parentCollection.getId());
                    continue;
                }

                Optional<CollectionContentEntity> joinEntryOpt = collectionContentDao
                        .findByCollectionIdAndContentId(parentCollection.getId(), contentCollectionEntity.getId());

                if (joinEntryOpt.isPresent()) {
                    CollectionContentEntity joinEntry = joinEntryOpt.get();
                    if (prev.getOrderIndex() != null) {
                        collectionContentDao.updateOrderIndex(joinEntry.getId(), prev.getOrderIndex());
                    }
                    if (prev.getVisible() != null) {
                        collectionContentDao.updateVisible(joinEntry.getId(), prev.getVisible());
                    }
                    log.debug("Updated existing collection reference {} in parent collection {}",
                            contentCollectionEntity.getId(), parentCollection.getId());
                }
            }
        }
    }

    /**
     * Find ContentCollectionEntity entries in the parent collection that reference
     * collections
     * matching the IDs in the remove list.
     *
     * @param parentCollection      The parent collection to search in
     * @param collectionIdsToRemove The IDs of collections that should be removed
     * @return List of ContentCollectionEntity entries that match, empty list if
     *         none found
     */
    private List<ContentCollectionEntity> findCurrentContentCollections(CollectionEntity parentCollection,
            List<Long> collectionIdsToRemove) {
        if (parentCollection == null || collectionIdsToRemove == null || collectionIdsToRemove.isEmpty()) {
            return Collections.emptyList();
        }

        List<ContentCollectionEntity> matchingContentCollections = new ArrayList<>();

        // Get all join table entries for this parent collection
        List<CollectionContentEntity> joinEntries = collectionContentDao
                .findByCollectionIdOrderByOrderIndex(parentCollection.getId());

        for (CollectionContentEntity joinEntry : joinEntries) {
            Long contentId = joinEntry.getContentId();
            if (contentId == null) {
                continue;
            }

            // Load the content entity to check if it's a ContentCollectionEntity
            ContentCollectionEntity contentCollectionEntity = contentCollectionDao.findById(contentId).orElse(null);
            if (contentCollectionEntity != null) {
                CollectionEntity referencedCollection = contentCollectionEntity.getReferencedCollection();
                if (referencedCollection != null && collectionIdsToRemove.contains(referencedCollection.getId())) {
                    matchingContentCollections.add(contentCollectionEntity);
                    log.debug("Found matching ContentCollectionEntity {} referencing collection {} for removal",
                            contentCollectionEntity.getId(), referencedCollection.getId());
                }
            }
        }

        if (matchingContentCollections.isEmpty()) {
            log.debug(
                    "No matching ContentCollectionEntity entries found for removal in collection {} (searched for IDs: {})",
                    parentCollection.getId(), collectionIdsToRemove);
        } else {
            log.debug("Found {} matching ContentCollectionEntity entries for removal in collection {}",
                    matchingContentCollections.size(), parentCollection.getId());
        }

        return matchingContentCollections;
    }

    /**
     * Find or create a ContentCollectionEntity for a given referenced collection.
     * Reuses existing ContentCollectionEntity if one already exists for this
     * collection.
     *
     * @param referencedCollection The collection to reference
     * @return The ContentCollectionEntity (existing or newly created)
     */
    private ContentCollectionEntity findOrCreateContentCollectionEntity(CollectionEntity referencedCollection) {
        // Search for existing ContentCollectionEntity that references this collection
        ContentCollectionEntity existing = findContentCollectionEntityByReferencedCollectionId(
                referencedCollection.getId());

        if (existing != null) {
            log.debug("Found existing ContentCollectionEntity {} for collection {}",
                    existing.getId(), referencedCollection.getId());
            return existing;
        }

        // Create new ContentCollectionEntity
        ContentCollectionEntity newContentCollection = ContentCollectionEntity.builder()
                .contentType(ContentType.COLLECTION)
                .referencedCollection(referencedCollection)
                .build();

        ContentCollectionEntity saved = contentCollectionDao.save(newContentCollection);
        log.info("Created new ContentCollectionEntity {} for collection {}",
                saved.getId(), referencedCollection.getId());
        return saved;
    }

    /**
     * Find a ContentCollectionEntity that references a collection with the given
     * ID.
     *
     * @param referencedCollectionId The ID of the referenced collection
     * @return The ContentCollectionEntity if found, null otherwise
     */
    private ContentCollectionEntity findContentCollectionEntityByReferencedCollectionId(Long referencedCollectionId) {
        return contentCollectionDao.findByReferencedCollectionId(referencedCollectionId)
                .orElse(null);
    }

    @Override
    @Transactional
    @CacheEvict(value = "collections", allEntries = true)
    public CollectionModel reorderContent(Long collectionId, CollectionReorderRequest request) {
        log.debug("Reordering content in collection {} with {} reorder operations", collectionId,
                request.getReorders().size());

        // 1. Verify collection exists
        if (!collectionDao.findById(collectionId).isPresent()) {
            throw new IllegalArgumentException("Collection not found with ID: " + collectionId);
        }

        // 2 & 3. Update all order indices in a single transaction
        // If any content doesn't belong to the collection, the update returns 0 and we
        // throw an error
        int totalUpdated = 0;
        for (CollectionReorderRequest.ReorderItem item : request.getReorders()) {
            int updated = collectionContentDao.updateOrderIndexForContent(
                    collectionId, item.getContentId(), item.getNewOrderIndex());
            if (updated == 0) {
                throw new IllegalArgumentException(
                        "Content with ID " + item.getContentId() + " does not belong to collection " + collectionId);
            }
            totalUpdated += updated;
        }

        log.info("Successfully reordered {} items in collection {}", totalUpdated, collectionId);

        // Return updated collection model
        CollectionEntity collection = collectionDao.findById(collectionId)
                .orElseThrow(() -> new IllegalArgumentException("Collection not found with ID: " + collectionId));
        List<CollectionContentEntity> updatedContent = collectionContentDao
                .findByCollectionIdOrderByOrderIndex(collectionId);
        long totalElements = updatedContent.size();
        int pageSize = totalElements > 0 ? (int) totalElements : DEFAULT_PAGE_SIZE;
        CollectionModel model = collectionProcessingUtil.convertToModel(collection, updatedContent,
                0, pageSize, totalElements);
        populateCollectionsOnContent(model);
        return model;
    }

}
