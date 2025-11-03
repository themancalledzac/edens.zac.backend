package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.ContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import edens.zac.portfolio.backend.entity.ContentTagEntity;
import edens.zac.portfolio.backend.entity.ContentPersonEntity;
import edens.zac.portfolio.backend.model.*;
import edens.zac.portfolio.backend.repository.ContentRepository;
import edens.zac.portfolio.backend.repository.CollectionRepository;
import edens.zac.portfolio.backend.repository.CollectionContentRepository;
import edens.zac.portfolio.backend.types.CollectionType;
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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

        // Use default page size if not specified or invalid
        if (size <= 0) {
            size = DEFAULT_PAGE_SIZE;
        }

        // Create pageable (convert to 0-based page index)
        Pageable pageable = PageRequest.of(Math.max(0, page), size);

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

        // Save updated entity
        CollectionEntity savedEntity = collectionRepository.save(entity);

        // Update total blocks count from join table
        long totalBlocks = collectionContentRepository.countByCollectionId(savedEntity.getId());
        savedEntity.setTotalContent((int) totalBlocks);
        collectionRepository.save(savedEntity);

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
                .map(cc -> contentProcessingUtil.convertToModel(cc.getContent(), cc))
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

        // Get all collections as CollectionListModel
        List<CollectionListModel> collections = collectionRepository.findAll().stream()
                .map(entity -> CollectionListModel.builder()
                        .id(entity.getId())
                        .name(entity.getTitle())
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
     * Mirrors the logic from ContentServiceImpl.updateImageTags().
     *
     * @param collection The collection to update
     * @param tagUpdate The tag update containing remove/prev/newValue operations
     */
    private void updateCollectionTags(CollectionEntity collection, TagUpdate tagUpdate) {
        Set<ContentTagEntity> tags = new HashSet<>(collection.getTags());

        // Remove tags if specified
        if (tagUpdate.getRemove() != null && !tagUpdate.getRemove().isEmpty()) {
            tags.removeIf(tag -> tagUpdate.getRemove().contains(tag.getId()));
            log.info("Removed {} tags from collection {}", tagUpdate.getRemove().size(), collection.getId());
        }

        // Add existing tags by ID (prev)
        if (tagUpdate.getPrev() != null && !tagUpdate.getPrev().isEmpty()) {
            Set<ContentTagEntity> existingTags = tagUpdate.getPrev().stream()
                    .map(tagId -> contentService.getAllTags().stream()
                            .filter(t -> t.getId().equals(tagId))
                            .findFirst()
                            .orElseThrow(() -> new EntityNotFoundException("Tag not found: " + tagId)))
                    .map(tagModel -> {
                        ContentTagEntity entity = new ContentTagEntity();
                        entity.setId(tagModel.getId());
                        entity.setTagName(tagModel.getName());
                        return entity;
                    })
                    .collect(Collectors.toSet());
            tags.addAll(existingTags);
            log.info("Added {} existing tags to collection {}", existingTags.size(), collection.getId());
        }

        // Create and add new tags by name (newValue)
        if (tagUpdate.getNewValue() != null && !tagUpdate.getNewValue().isEmpty()) {
            for (String tagName : tagUpdate.getNewValue()) {
                if (tagName != null && !tagName.trim().isEmpty()) {
                    String trimmedName = tagName.trim();
                    // Use the service to create the tag (handles duplicates)
                    Map<String, Object> result = contentService.createTag(trimmedName);
                    Long tagId = (Long) result.get("id");

                    // Add to collection's tags
                    ContentTagEntity newTag = new ContentTagEntity();
                    newTag.setId(tagId);
                    newTag.setTagName(trimmedName);
                    tags.add(newTag);
                    log.info("Created and added new tag '{}' to collection {}", trimmedName, collection.getId());
                }
            }
        }

        collection.setTags(tags);
    }

    /**
     * Update collection people using prev/new/remove pattern.
     * Mirrors the logic from ContentServiceImpl.updateImagePeople().
     *
     * @param collection The collection to update
     * @param personUpdate The person update containing remove/prev/newValue operations
     */
    private void updateCollectionPeople(CollectionEntity collection, PersonUpdate personUpdate) {
        Set<ContentPersonEntity> people = new HashSet<>(collection.getPeople());

        // Remove people if specified
        if (personUpdate.getRemove() != null && !personUpdate.getRemove().isEmpty()) {
            people.removeIf(person -> personUpdate.getRemove().contains(person.getId()));
            log.info("Removed {} people from collection {}", personUpdate.getRemove().size(), collection.getId());
        }

        // Add existing people by ID (prev)
        if (personUpdate.getPrev() != null && !personUpdate.getPrev().isEmpty()) {
            Set<ContentPersonEntity> existingPeople = personUpdate.getPrev().stream()
                    .map(personId -> contentService.getAllPeople().stream()
                            .filter(p -> p.getId().equals(personId))
                            .findFirst()
                            .orElseThrow(() -> new EntityNotFoundException("Person not found: " + personId)))
                    .map(personModel -> {
                        ContentPersonEntity entity = new ContentPersonEntity();
                        entity.setId(personModel.getId());
                        entity.setPersonName(personModel.getName());
                        return entity;
                    })
                    .collect(Collectors.toSet());
            people.addAll(existingPeople);
            log.info("Added {} existing people to collection {}", existingPeople.size(), collection.getId());
        }

        // Create and add new people by name (newValue)
        if (personUpdate.getNewValue() != null && !personUpdate.getNewValue().isEmpty()) {
            for (String personName : personUpdate.getNewValue()) {
                if (personName != null && !personName.trim().isEmpty()) {
                    String trimmedName = personName.trim();
                    // Use the service to create the person (handles duplicates)
                    Map<String, Object> result = contentService.createPerson(trimmedName);
                    Long personId = (Long) result.get("id");

                    // Add to collection's people
                    ContentPersonEntity newPerson = new ContentPersonEntity();
                    newPerson.setId(personId);
                    newPerson.setPersonName(trimmedName);
                    people.add(newPerson);
                    log.info("Created and added new person '{}' to collection {}", trimmedName, collection.getId());
                }
            }
        }

        collection.setPeople(people);
    }

    /**
     * Handle collection-to-collection relationship updates.
     * This manages which parent collections this collection belongs to.
     *
     * @param childCollection The collection being updated (main collection)
     * @param collectionUpdate The collection update containing remove/prev/newValue operations
     */
    private void handleCollectionToCollectionUpdates(CollectionEntity childCollection, CollectionUpdate collectionUpdate) {
        ContentCollectionEntity contentColEntity = findContentCollectionEntity(childCollection);
        if (contentColEntity == null) {
            log.warn("No ContentCollectionEntity found for collection {}. Cannot update collection relationships.",
                    childCollection.getId());
            return;
        }

        // Step 1: Remove - unassociate from parent collections
        if (collectionUpdate.getRemove() != null) {
            for (Long parentId : collectionUpdate.getRemove()) {
                collectionContentRepository.removeContentFromCollection(parentId, List.of(contentColEntity.getId()));
            }
        }

        // Step 2: New Value - add to new parent collections
        if (collectionUpdate.getNewValue() != null) {
            for (ChildCollection newParent : collectionUpdate.getNewValue()) {
                CollectionEntity parent = collectionRepository.findById(newParent.getCollectionId())
                        .orElseThrow(() -> new EntityNotFoundException("Parent collection not found: " + newParent.getCollectionId()));

                Integer orderIndex = newParent.getOrderIndex() != null ? newParent.getOrderIndex() :
                        collectionContentRepository.getMaxOrderIndexForCollection(parent.getId()) + 1;

                collectionContentRepository.save(CollectionContentEntity.builder()
                        .collection(parent)
                        .content(contentColEntity)
                        .orderIndex(orderIndex)
                        .visible(newParent.getVisible() != null ? newParent.getVisible() : false)
                        .build());
            }
        }

        // Step 3: Prev - update existing associations
        if (collectionUpdate.getPrev() != null) {
            for (ChildCollection prev : collectionUpdate.getPrev()) {
                CollectionContentEntity joinEntry = collectionContentRepository
                        .findByCollectionIdAndContentId(prev.getCollectionId(), contentColEntity.getId());

                if (joinEntry != null) {
                    if (prev.getOrderIndex() != null) {
                        collectionContentRepository.updateOrderIndex(joinEntry.getId(), prev.getOrderIndex());
                    }
                    if (prev.getVisible() != null) {
                        collectionContentRepository.updateVisible(joinEntry.getId(), prev.getVisible());
                    }
                }
            }
        }
    }

    /**
     * Find a ContentCollectionEntity that references the given collection.
     *
     * @param referencedCollection The collection to reference
     * @return The ContentCollectionEntity if found, null otherwise
     */
    private ContentCollectionEntity findContentCollectionEntity(CollectionEntity referencedCollection) {
        // Search for existing ContentCollectionEntity that references this collection
        List<ContentEntity> allContent = contentRepository.findAll();
        for (ContentEntity content : allContent) {
            if (content instanceof ContentCollectionEntity contentColEntity) {
                if (contentColEntity.getReferencedCollection().getId().equals(referencedCollection.getId())) {
                    log.info("Found existing ContentCollectionEntity {} for collection {}",
                            contentColEntity.getId(), referencedCollection.getId());
                    return contentColEntity;
                }
            }
        }

        // Not found - return null instead of creating
        log.info("No existing ContentCollectionEntity found for collection {}", referencedCollection.getId());
        return null;
    }
}
