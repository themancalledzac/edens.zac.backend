package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import edens.zac.portfolio.backend.entity.ContentEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.ContentTagEntity;
import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import edens.zac.portfolio.backend.model.*;
import edens.zac.portfolio.backend.repository.ContentRepository;
import edens.zac.portfolio.backend.repository.CollectionRepository;
import edens.zac.portfolio.backend.repository.CollectionContentRepository;
import edens.zac.portfolio.backend.types.CollectionType;
import edens.zac.portfolio.backend.types.ContentType;
import edens.zac.portfolio.backend.types.FilmFormat;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static edens.zac.portfolio.backend.config.DefaultValues.default_content_per_page;

/**
 * Implementation of ContentCollectionService that provides methods for
 * managing ContentCollection entities with pagination and client gallery access.
 */
@Service
@Slf4j
@RequiredArgsConstructor
class CollectionServiceImpl implements CollectionService {

    private final CollectionRepository collectionRepository;
    private final ContentRepository contentRepository;
    private final CollectionContentRepository collectionContentRepository;
    private final ContentProcessingUtil contentProcessingUtil;
    private final CollectionProcessingUtil collectionProcessingUtil;
    private final ContentService contentService;

    private static final int DEFAULT_PAGE_SIZE = default_content_per_page;


    @Override
    @Transactional(readOnly = true)
    public CollectionModel getCollectionWithPagination(String slug, int page, int size) {
        log.debug("Getting collection with slug: {} (page: {}, size: {})", slug, page, size);

        // Get collection metadata
        CollectionEntity collection = collectionRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Collection not found with slug: " + slug));

        // Normalize pagination parameters
        int normalizedPage = Math.max(0, page);
        int normalizedSize = size <= 0 ? DEFAULT_PAGE_SIZE : size;
        Pageable pageable = PageRequest.of(normalizedPage, normalizedSize);

        // Get paginated join table entries (collection-content associations)
        Page<CollectionContentEntity> collectionContentPage = collectionContentRepository
                .findByCollectionId(collection.getId(), pageable);

        // Convert to model (now using join table data)
        return collectionProcessingUtil.convertToModel(collection, collectionContentPage);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean validateClientGalleryAccess(String slug, String password) {
        log.debug("Validating access to client gallery: {}", slug);

        // TODO: Re-implement password protection after migration
        // Password protection temporarily disabled during refactoring

        // Get collection metadata
        CollectionEntity collection = collectionRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Collection not found with slug: " + slug));

        // For now, all galleries are accessible
        return true;

        // TODO: Uncomment when password protection is re-implemented
//        // Check if collection is password-protected
//        if (!collection.isPasswordProtected()) {
//            return true; // No password required
//        }
//
//        // Validate password
//        if (password == null || password.isEmpty()) {
//            return false; // Password required but not provided
//        }
//
//        // Check if password matches
//        return CollectionProcessingUtil.passwordMatches(password, collection.getPasswordHash());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CollectionModel> findByType(CollectionType type, Pageable pageable) {
        log.debug("Finding collections by type: {}", type);

        // Get collections by type with pagination
        Page<CollectionEntity> collectionsPage;

        if (type == CollectionType.BLOG) {
            // Blogs are ordered by date descending
            collectionsPage = collectionRepository.findAll(pageable);
        } else {
            // Other types are ordered by priority
            collectionsPage = collectionRepository.findAll(pageable);
        }

        // Convert to models
        List<CollectionModel> models = collectionsPage.getContent().stream()
                .map(collectionProcessingUtil::convertToBasicModel)
                .collect(Collectors.toList());

        return new PageImpl<>(models, pageable, collectionsPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CollectionModel> findVisibleByTypeOrderByDate(CollectionType type) {
        log.debug("Finding visible collections by type ordered by date: {}", type);

        // Get visible collections by type, ordered by collection date descending (newest first)
        List<CollectionEntity> collections = collectionRepository
                .findTop50ByTypeAndVisibleTrueOrderByCollectionDateDesc(type);

        // Convert to basic CollectionModel objects (no content blocks)
        return collections.stream()
                .map(collectionProcessingUtil::convertToBasicModel)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CollectionModel> findBySlug(String slug) {
        log.debug("Finding collection by slug: {}", slug);

        // Get collection metadata only - content is fetched via join table in convertToFullModel
        return collectionRepository.findBySlug(slug)
                .map(this::convertToFullModel);
    }

    @Override
    @Transactional
    public CollectionUpdateResponseDTO createCollection(CollectionCreateRequest createRequest) {
        log.debug("Creating new collection: {}", createRequest.getTitle());

        // Create entity using utility converter
        CollectionEntity entity = collectionProcessingUtil.toEntity(
                createRequest,
                DEFAULT_PAGE_SIZE
        );

        // Save entity
        CollectionEntity savedEntity = collectionRepository.save(entity);

        // Return full update response with all metadata (tags, people, cameras, etc.)
        return getUpdateCollectionData(savedEntity.getSlug());
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionModel findById(Long id) {
        log.debug("Finding collection by ID: {}", id);

        // Get collection entity with content blocks
        CollectionEntity entity = collectionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Collection not found with ID: " + id));

        // Convert to full model (includes content blocks)
        return convertToFullModel(entity);
    }


    @Override
    @Transactional
    public CollectionModel updateContent(Long id, CollectionUpdateRequest updateDTO) {
        log.debug("Updating collection with ID: {}", id);

        // Get existing entity
        CollectionEntity entity = collectionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Collection not found with ID: " + id));

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

//        // Handle adding new text blocks via utility helper, capturing created IDs for deterministic mapping
//        // TODO: don't handle creating new text blocks from 'updateContent', but rather it's own 'createContent' endpoint
//        List<Long> newTextIds = collectionProcessingUtil.handleNewTextContentReturnIds(id, updateDTO);

//        // Handle content block reordering via utility helper with explicit mapping for new text placeholders
//        collectionProcessingUtil.handleContentReordering(id, updateDTO, newTextIds);

        // Update total blocks count from join table before saving
        long totalBlocks = collectionContentRepository.countByCollectionId(entity.getId());
        entity.setTotalContent((int) totalBlocks);

        // Save updated entity
        CollectionEntity savedEntity = collectionRepository.save(entity);

        return convertToFullModel(savedEntity);
    }

    @Override
    @Transactional
    public void deleteCollection(Long id) {
        log.debug("Deleting collection with ID: {}", id);

        // Check if collection exists
        if (!collectionRepository.existsById(id)) {
            throw new EntityNotFoundException("Collection not found with ID: " + id);
        }

        // Delete all join table entries (dissociate content from collection)
        // This does NOT delete the content itself - content is reusable!
        collectionContentRepository.deleteByCollectionId(id);
        log.debug("Deleted all join table entries for collection ID: {}", id);

        // Delete collection
        collectionRepository.deleteById(id);
        log.info("Successfully deleted collection with ID: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CollectionModel> getAllCollections(Pageable pageable) {
        log.debug("Getting all collections with pagination");

        // Get all collections with pagination
        Page<CollectionEntity> collectionsPage = collectionRepository.findAll(pageable);

        // Convert to models
        List<CollectionModel> models = collectionsPage.getContent().stream()
                .map(collectionProcessingUtil::convertToBasicModel)
                .collect(Collectors.toList());

        return new PageImpl<>(models, pageable, collectionsPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CollectionModel> getAllCollectionsOrderedByDate() {
        log.debug("Getting all collections ordered by collection date");

        // Get all collections ordered by collection date descending
        List<CollectionEntity> collections = collectionRepository.findAllByOrderByCollectionDateDesc();

        // Convert to basic models (no content blocks)
        return collections.stream()
                .map(collectionProcessingUtil::convertToBasicModel)
                .collect(Collectors.toList());
    }


    /**
     * Convert a CollectionEntity to a CollectionModel with all content.
     *
     * @param entity The entity to convert
     * @return The converted model
     */
    private CollectionModel convertToFullModel(CollectionEntity entity) {
        CollectionModel model = collectionProcessingUtil.convertToBasicModel(entity);
        if (model == null) {
            // Defensive: mocked util may return null in tests; ensure non-null model to avoid NPE
            model = new CollectionModel();
        }

        // Fetch join table entries explicitly to get content with collection-specific metadata
        List<ContentModel> contents = collectionContentRepository
                .findByCollectionIdOrderByOrderIndex(entity.getId())
                .stream()
                .map(contentProcessingUtil::convertEntityToModel)
                .collect(Collectors.toList());

        model.setContent(contents);
        return model;
    }


    @Override
    @Transactional(readOnly = true)
    public CollectionUpdateResponseDTO getUpdateCollectionData(String slug) {
        log.debug("Getting update collection data for slug: {}", slug);

        // Get the collection
        CollectionModel collection = findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Collection not found with slug: " + slug));

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
    public GeneralMetadataDTO getGeneralMetadata() {
        log.debug("Getting general metadata");

        // Get all tags, people, cameras, lenses, and film types from ContentBlockService
        List<ContentTagModel> tags = contentService.getAllTags();
        List<ContentPersonModel> people = contentService.getAllPeople();
        List<ContentCameraModel> cameras = contentService.getAllCameras();
        List<ContentLensModel> lenses = contentService.getAllLenses();
        List<ContentFilmTypeModel> filmTypes = contentService.getAllFilmTypes();

        // Get all collections as CollectionListModel (using projection for efficiency)
        List<CollectionListModel> collections = collectionRepository.findIdAndTitleOnly().stream()
                .map(result -> CollectionListModel.builder()
                        .id((Long) result[0])
                        .name((String) result[1])
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
        Set<ContentTagEntity> updatedTags = contentProcessingUtil.updateTags(
                collection.getTags(),
                tagUpdate,
                null // No tracking needed for collection updates
        );
        collection.setTags(updatedTags);
        log.info("Updated tags for collection {}", collection.getId());
    }

    /**
     * Update collection people using prev/new/remove pattern.
     * Uses shared utility method from ContentProcessingUtil.
     *
     * @param collection   The collection to update
     * @param personUpdate The person update containing remove/prev/newValue operations
     */
    private void updateCollectionPeople(CollectionEntity collection, PersonUpdate personUpdate) {
        Set<ContentPersonEntity> updatedPeople = contentProcessingUtil.updatePeople(
                collection.getPeople(),
                personUpdate,
                null // No tracking needed for collection updates
        );
        collection.setPeople(updatedPeople);
        log.info("Updated people for collection {}", collection.getId());
    }

    /**
     * Handle collection-to-collection relationship updates.
     * This manages which child collections belong to this parent collection.
     *
     * @param parentCollection  The collection being updated (parent collection)
     * @param collectionUpdates The collection update containing remove/prev/newValue operations
     */
    private void handleCollectionToCollectionUpdates(CollectionEntity parentCollection, CollectionUpdate collectionUpdates) {
        log.debug("Handling collection-to-collection updates for collection {}", parentCollection.getId());

        // Step 1: Remove - unassociate child collections from parent collection
        if (collectionUpdates.getRemove() != null && !collectionUpdates.getRemove().isEmpty()) {
            List<ContentCollectionEntity> contentColEntities = findCurrentContentCollections(parentCollection, collectionUpdates.getRemove());
            
            // Continue even if no matching content collections are found
            if (contentColEntities != null && !contentColEntities.isEmpty()) {
                List<Long> contentIdsToRemove = contentColEntities.stream()
                        .map(ContentCollectionEntity::getId)
                        .toList();
                
                collectionContentRepository.removeContentFromCollection(parentCollection.getId(), contentIdsToRemove);
                log.info("Removed {} collection references from parent collection {}", contentIdsToRemove.size(), parentCollection.getId());
            } else {
                log.debug("No matching content collections found to remove from collection {}", parentCollection.getId());
            }
        }

        // Step 2: New Value - add new child collections to parent collection
        if (collectionUpdates.getNewValue() != null && !collectionUpdates.getNewValue().isEmpty()) {
            for (ChildCollection childCollection : collectionUpdates.getNewValue()) {
                // Find the child collection entity
                CollectionEntity childCollectionEntity = collectionRepository.findById(childCollection.getCollectionId())
                        .orElseThrow(() -> new EntityNotFoundException("Child collection not found: " + childCollection.getCollectionId()));

                // Check if ContentCollectionEntity already exists for this referenced collection
                ContentCollectionEntity existingContentCollection = findOrCreateContentCollectionEntity(childCollectionEntity);

                Integer orderIndex = childCollection.getOrderIndex() != null
                        ? childCollection.getOrderIndex()
                        : collectionContentRepository.getNextOrderIndexForCollection(parentCollection.getId());

                // Check if this content is already in the parent collection
                CollectionContentEntity existingJoinEntry = collectionContentRepository
                        .findByCollectionIdAndContentId(parentCollection.getId(), existingContentCollection.getId());

                // Fetch cover image URL from the referenced collection
                String coverImageUrl = getCoverImageUrl(childCollectionEntity);

                if (existingJoinEntry == null) {
                    // Create new join table entry
                    CollectionContentEntity newEntry = CollectionContentEntity.builder()
                            .collection(parentCollection)
                            .content(existingContentCollection)
                            .orderIndex(orderIndex)
                            .visible(childCollection.getVisible() != null ? childCollection.getVisible() : false)
                            .imageUrl(coverImageUrl)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();

                    collectionContentRepository.save(newEntry);
                    log.info("Added collection {} to parent collection {} at index {} with cover image URL", 
                            childCollectionEntity.getId(), parentCollection.getId(), orderIndex);
                } else {
                    // Update existing entry
                    if (childCollection.getOrderIndex() != null) {
                        collectionContentRepository.updateOrderIndex(existingJoinEntry.getId(), childCollection.getOrderIndex());
                    }
                    if (childCollection.getVisible() != null) {
                        collectionContentRepository.updateVisible(existingJoinEntry.getId(), childCollection.getVisible());
                    }
                    // Update image URL if cover image has changed
                    if (coverImageUrl != null && !coverImageUrl.equals(existingJoinEntry.getImageUrl())) {
                        collectionContentRepository.updateImageUrl(existingJoinEntry.getId(), coverImageUrl);
                    }
                    log.info("Updated existing collection reference in parent collection {}", parentCollection.getId());
                }
            }
        }

        // Step 3: Prev - update existing associations (orderIndex, visible)
        if (collectionUpdates.getPrev() != null && !collectionUpdates.getPrev().isEmpty()) {
            for (ChildCollection prev : collectionUpdates.getPrev()) {
                // Find ContentCollectionEntity that references this collection
                ContentCollectionEntity contentCollectionEntity = findContentCollectionEntityByReferencedCollectionId(prev.getCollectionId());
                
                if (contentCollectionEntity != null) {
                    CollectionContentEntity joinEntry = collectionContentRepository
                            .findByCollectionIdAndContentId(parentCollection.getId(), contentCollectionEntity.getId());

                    if (joinEntry != null) {
                        if (prev.getOrderIndex() != null) {
                            collectionContentRepository.updateOrderIndex(joinEntry.getId(), prev.getOrderIndex());
                        }
                        if (prev.getVisible() != null) {
                            collectionContentRepository.updateVisible(joinEntry.getId(), prev.getVisible());
                        }
                        log.debug("Updated existing collection reference {} in parent collection {}", 
                                contentCollectionEntity.getId(), parentCollection.getId());
                    }
                }
            }
        }
    }

    /**
     * Find ContentCollectionEntity entries in the parent collection that reference collections
     * matching the IDs in the remove list.
     *
     * @param parentCollection The parent collection to search in
     * @param collectionIdsToRemove The IDs of collections that should be removed
     * @return List of ContentCollectionEntity entries that match, empty list if none found
     */
    private List<ContentCollectionEntity> findCurrentContentCollections(CollectionEntity parentCollection, List<Long> collectionIdsToRemove) {
        if (parentCollection == null || collectionIdsToRemove == null || collectionIdsToRemove.isEmpty()) {
            return Collections.emptyList();
        }

        List<ContentCollectionEntity> matchingContentCollections = new ArrayList<>();
        
        // Get all join table entries for this parent collection
        List<CollectionContentEntity> joinEntries = collectionContentRepository
                .findByCollectionIdOrderByOrderIndex(parentCollection.getId());

        for (CollectionContentEntity joinEntry : joinEntries) {
            ContentEntity content = joinEntry.getContent();
            if (content instanceof ContentCollectionEntity contentCollectionEntity) {
                CollectionEntity referencedCollection = contentCollectionEntity.getReferencedCollection();
                if (referencedCollection != null && collectionIdsToRemove.contains(referencedCollection.getId())) {
                    matchingContentCollections.add(contentCollectionEntity);
                }
            }
        }

        if (matchingContentCollections.isEmpty()) {
            log.debug("No matching ContentCollectionEntity entries found for removal in collection {}", parentCollection.getId());
        }

        return matchingContentCollections;
    }

    /**
     * Find or create a ContentCollectionEntity for a given referenced collection.
     * Reuses existing ContentCollectionEntity if one already exists for this collection.
     *
     * @param referencedCollection The collection to reference
     * @return The ContentCollectionEntity (existing or newly created)
     */
    private ContentCollectionEntity findOrCreateContentCollectionEntity(CollectionEntity referencedCollection) {
        // Search for existing ContentCollectionEntity that references this collection
        ContentCollectionEntity existing = findContentCollectionEntityByReferencedCollectionId(referencedCollection.getId());
        
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

        ContentCollectionEntity saved = contentRepository.save(newContentCollection);
        log.info("Created new ContentCollectionEntity {} for collection {}", 
                saved.getId(), referencedCollection.getId());
        return saved;
    }

    /**
     * Find a ContentCollectionEntity that references a collection with the given ID.
     *
     * @param referencedCollectionId The ID of the referenced collection
     * @return The ContentCollectionEntity if found, null otherwise
     */
    private ContentCollectionEntity findContentCollectionEntityByReferencedCollectionId(Long referencedCollectionId) {
        return contentRepository.findContentCollectionByReferencedCollectionId(referencedCollectionId)
                .orElse(null);
    }

    /**
     * Get the cover image URL from a collection's cover_image_id.
     * Looks up the ContentImageEntity and returns its imageUrlWeb.
     *
     * @param collection The collection entity
     * @return The cover image URL, or null if no cover image is set or not found
     */
    private String getCoverImageUrl(CollectionEntity collection) {
        if (collection.getCoverImageId() == null) {
            return null;
        }

        try {
            ContentEntity content = contentRepository.findById(collection.getCoverImageId())
                    .orElse(null);
            
            if (content instanceof ContentImageEntity imageEntity) {
                return imageEntity.getImageUrlWeb();
            } else if (content != null) {
                log.warn("Collection {} has cover_image_id {} but it's not a ContentImageEntity: {}", 
                        collection.getId(), collection.getCoverImageId(), content.getClass().getSimpleName());
            }
        } catch (Exception e) {
            log.error("Error fetching cover image for collection {}: {}", collection.getId(), e.getMessage(), e);
        }

        return null;
    }
}
