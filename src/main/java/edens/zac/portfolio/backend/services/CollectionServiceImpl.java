package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.ContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
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

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
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
    public List<HomeCardModel> findVisibleByTypeOrderByDate(CollectionType type) {
        log.debug("Finding visible collections by type ordered by date: {}", type);

        // Get visible collections by type, ordered by collection date descending (newest first)
        List<CollectionEntity> collections = collectionRepository
                .findTop50ByTypeAndVisibleTrueOrderByCollectionDateDesc(type);


        // Convert to HomeCardModel objects
        return collections.stream()
                .map(this::convertToHomeCardModel)
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
    public CollectionModel updateContent(Long id, CollectionUpdateDTO updateDTO) {
        log.debug("Updating collection with ID: {}", id);

        // Get existing entity
        CollectionEntity entity = collectionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Collection not found with ID: " + id));

        // Update basic properties via utility helper
        collectionProcessingUtil.applyBasicUpdates(entity, updateDTO);

        // Handle content block removals - dissociate blocks from this collection instead of deleting
        if (updateDTO.getContentIdsToRemove() != null && !updateDTO.getContentIdsToRemove().isEmpty()) {
            // Use join table repository to remove content associations
            collectionContentRepository.removeContentFromCollection(id, updateDTO.getContentIdsToRemove());
            if (entity.getCoverImageId() != null && updateDTO.getContentIdsToRemove().contains(entity.getCoverImageId())) {
                // Removed the current cover image; choose the next available image as new cover if any
                entity.setCoverImageId(null);
                // Get remaining content via join table and extract entities
                List<ContentEntity> remaining = collectionContentRepository
                        .findByCollectionIdOrderByOrderIndex(id)
                        .stream()
                        .map(CollectionContentEntity::getContent)
                        .toList();
                for (ContentEntity b : remaining) {
                    if (b instanceof ContentImageEntity img) {
                        entity.setCoverImageId(img.getId());
                        break;
                    }
                }
            }
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


    /**
     * Convert a ContentCollectionEntity to a HomeCardModel.
     *
     * @param entity The entity to convert
     * @return The converted HomeCardModel
     */
    private HomeCardModel convertToHomeCardModel(CollectionEntity entity) {
        return HomeCardModel.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .cardType(entity.getType() != null ? entity.getType().name() : null)
                .location(entity.getLocation())
                .date(entity.getCollectionDate() != null
                        ? entity.getCollectionDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                        : null)
                .coverImageUrl(getCoverImageUrl(entity))
                .slug(entity.getSlug())
                .text(null) // Collections don't have text field
                .build();
    }

    /**
     * Helper method to get cover image URL from collection's coverImageBlockId
     */
    private String getCoverImageUrl(CollectionEntity collection) {
        if (collection.getCoverImageId() == null) {
            return null;
        }

        return contentRepository.findById(collection.getCoverImageId())
                .filter(block -> block instanceof ContentImageEntity)
                .map(block -> ((ContentImageEntity) block).getImageUrlWeb())
                .orElse(null);
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
}
