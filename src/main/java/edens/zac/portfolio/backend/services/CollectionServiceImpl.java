package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.ContentEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.model.*;
import edens.zac.portfolio.backend.repository.ContentBlockRepository;
import edens.zac.portfolio.backend.repository.ContentCollectionRepository;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static edens.zac.portfolio.backend.config.DefaultValues.default_blocks_per_page;

/**
 * Implementation of ContentCollectionService that provides methods for
 * managing ContentCollection entities with pagination and client gallery access.
 */
@Service
@Slf4j
@RequiredArgsConstructor
class CollectionServiceImpl implements CollectionService {

    private final ContentCollectionRepository contentCollectionRepository;
    private final ContentBlockRepository contentBlockRepository;
    private final ContentBlockProcessingUtil contentBlockProcessingUtil;
    private final ContentCollectionProcessingUtil contentCollectionProcessingUtil;
    private final HomeService homeService;
    private final ContentService contentService;

    private static final int DEFAULT_PAGE_SIZE = default_blocks_per_page;


    @Override
    @Transactional(readOnly = true)
    public CollectionModel getCollectionWithPagination(String slug, int page, int size) {
        log.debug("Getting collection with slug: {} (page: {}, size: {})", slug, page, size);

        // Get collection metadata
        CollectionEntity collection = contentCollectionRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Collection not found with slug: " + slug));

        // Use default page size if not specified or invalid
        if (size <= 0) {
            size = DEFAULT_PAGE_SIZE;
        }

        // Create pageable (convert to 0-based page index)
        Pageable pageable = PageRequest.of(Math.max(0, page), size);

        // Get paginated content blocks
        Page<ContentEntity> contentPage = contentBlockRepository
                .findByCollectionId(collection.getId(), pageable);

        // Convert to model
        return convertToModel(collection, contentPage);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean validateClientGalleryAccess(String slug, String password) {
        log.debug("Validating access to client gallery: {}", slug);

        // Get collection metadata
        CollectionEntity collection = contentCollectionRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Collection not found with slug: " + slug));

        // Check if collection is password-protected
        if (!collection.isPasswordProtected()) {
            return true; // No password required
        }

        // Validate password
        if (password == null || password.isEmpty()) {
            return false; // Password required but not provided
        }

        // Check if password matches
        return ContentCollectionProcessingUtil.passwordMatches(password, collection.getPasswordHash());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CollectionModel> findByType(CollectionType type, Pageable pageable) {
        log.debug("Finding collections by type: {}", type);

        // Get collections by type with pagination
        Page<CollectionEntity> collectionsPage;

        if (type == CollectionType.BLOG) {
            // Blogs are ordered by date descending
            collectionsPage = contentCollectionRepository.findAll(pageable);
        } else {
            // Other types are ordered by priority
            collectionsPage = contentCollectionRepository.findAll(pageable);
        }

        // Convert to models
        List<CollectionModel> models = collectionsPage.getContent().stream()
                .map(this::convertToBasicModel)
                .collect(Collectors.toList());

        return new PageImpl<>(models, pageable, collectionsPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public List<HomeCardModel> findVisibleByTypeOrderByDate(CollectionType type) {
        log.debug("Finding visible collections by type ordered by date: {}", type);

        // Get visible collections by type, ordered by collection date descending (newest first)
        List<CollectionEntity> collections = contentCollectionRepository
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

        // Get collection with content blocks
        return contentCollectionRepository.findBySlugWithContentBlocks(slug)
                .map(this::convertToFullModel);
    }

    @Override
    @Transactional
    public CollectionUpdateResponseDTO createCollection(CollectionCreateRequest createRequest) {
        log.debug("Creating new collection: {}", createRequest.getTitle());

        // Create entity using utility converter
        CollectionEntity entity = contentCollectionProcessingUtil.toEntity(
                createRequest,
                DEFAULT_PAGE_SIZE
        );

        // Save entity
        CollectionEntity savedEntity = contentCollectionRepository.save(entity);

        // Return full update response with all metadata (tags, people, cameras, etc.)
        return getUpdateCollectionData(savedEntity.getSlug());
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionModel findById(Long id) {
        log.debug("Finding collection by ID: {}", id);

        // Get collection entity with content blocks
        CollectionEntity entity = contentCollectionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Collection not found with ID: " + id));

        // Convert to full model (includes content blocks)
        return convertToFullModel(entity);
    }


    @Override
    @Transactional
    public CollectionModel updateContent(Long id, CollectionUpdateDTO updateDTO) {
        log.debug("Updating collection with ID: {}", id);

        // Get existing entity
        CollectionEntity entity = contentCollectionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Collection not found with ID: " + id));

        // Update basic properties via utility helper
        contentCollectionProcessingUtil.applyBasicUpdates(entity, updateDTO);

        // Handle content block removals - dissociate blocks from this collection instead of deleting
        if (updateDTO.getContentIdsToRemove() != null && !updateDTO.getContentIdsToRemove().isEmpty()) {
            contentBlockRepository.dissociateFromCollection(id, updateDTO.getContentIdsToRemove());
            if (entity.getCoverImageBlockId() != null && updateDTO.getContentIdsToRemove().contains(entity.getCoverImageBlockId())) {
                // Removed the current cover image; choose the next available image as new cover if any
                entity.setCoverImageBlockId(null);
                List<ContentEntity> remaining = contentBlockRepository.findByCollectionIdOrderByOrderIndex(id);
                for (ContentEntity b : remaining) {
                    if (b instanceof ContentImageEntity img) {
                        entity.setCoverImageBlockId(img.getId());
                        break;
                    }
                }
            }
        }

        // Handle adding new text blocks via utility helper, capturing created IDs for deterministic mapping
        List<Long> newTextIds = contentCollectionProcessingUtil.handleNewTextBlocksReturnIds(id, updateDTO);

        // Handle content block reordering via utility helper with explicit mapping for new text placeholders
        contentCollectionProcessingUtil.handleContentBlockReordering(id, updateDTO, newTextIds);

        // Save updated entity
        CollectionEntity savedEntity = contentCollectionRepository.save(entity);

        applyHomeCardOptions(
                savedEntity,
                updateDTO.getHomeCardEnabled(),
                updateDTO.getPriority(),
                updateDTO.getHomeCardText()
        );

        // Update total blocks count
        long totalBlocks = contentBlockRepository.countByCollectionId(savedEntity.getId());
        savedEntity.setTotalBlocks((int) totalBlocks);
        contentCollectionRepository.save(savedEntity);

        return convertToFullModel(savedEntity);
    }


    @Override
    @Transactional
    public CollectionModel addContent(Long id, List<MultipartFile> files) {
        log.debug("Adding content blocks (files only) to collection ID: {}", id);

        // Ensure collection exists and fetch entity/title
        CollectionEntity entity = contentCollectionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Collection not found with ID: " + id));

        if (files == null || files.isEmpty()) {
            return convertToFullModel(entity);
        }

        List<ContentEntity> contentBlocks = new ArrayList<>();

        // Get the current highest order index for this collection
        Integer startOrderIndex = contentBlockRepository.getMaxOrderIndexForCollection(id);
        Integer orderIndex = (startOrderIndex != null) ? startOrderIndex + 1 : 0;

        // Track the first non-GIF image URL and blockId for potential cover usage
        String firstImageUrlWeb = null;
        Long firstImageBlockId = null;

        for (MultipartFile file : files) {
            try {
                // First check if this image already exists in the database (duplicate detection)
                if (file.getContentType() != null && file.getContentType().startsWith("image/") && !file.getContentType().equals("image/gif")) {
                    // Generate file identifier to check for duplicates
                    String originalFilename = file.getOriginalFilename();
                    if (originalFilename != null) {
                        String date = java.time.LocalDate.now().toString();
                        String fileIdentifier = date + "/" + originalFilename;

                        // Check if image already exists
                        if (contentBlockRepository.existsByFileIdentifier(fileIdentifier)) {
                            log.info("Skipping duplicate image: {}", originalFilename);
                            continue; // Skip this file and move to the next
                        }
                    }
                }

                // Process file based on content type
                if (file.getContentType() != null && file.getContentType().startsWith("image/")) {
                    if (file.getContentType().equals("image/gif")) {
                        // Process as GIF
                        ContentEntity gifBlock = contentBlockProcessingUtil.processGifContentBlock(
                                file, id, orderIndex, entity.getTitle(), null);
                        if (gifBlock != null && gifBlock.getId() != null) {
                            contentBlocks.add(gifBlock);
                        }
                    } else {
                        // Process as image
                        ContentImageEntity img = contentBlockProcessingUtil.processImageContentBlock(
                                file, id, orderIndex, entity.getTitle(), null);
                        if (img != null && img.getId() != null) {
                            contentBlocks.add(img);

                            // Capture the first non-GIF image URL and block id for cover if needed
                            if (firstImageUrlWeb == null) {
                                firstImageUrlWeb = img.getImageUrlWeb();
                                firstImageBlockId = img.getId();
                            }
                        }
                    }
                    orderIndex++;
                }
            } catch (Exception e) {
                log.error("Error processing file: {}", e.getMessage(), e);
            }
        }

        // If no cover yet and we uploaded at least one non-GIF image, set it now and sync HomeCard
        if (entity.getCoverImageBlockId() == null && firstImageBlockId != null) {
            entity.setCoverImageBlockId(firstImageBlockId);
            contentCollectionRepository.save(entity);
            homeService.syncHomeCardOnCollectionUpdate(entity);
        }

        // Update total blocks count if any blocks were added
        if (!contentBlocks.isEmpty()) {
            long totalBlocks = contentBlockRepository.countByCollectionId(id);
            entity.setTotalBlocks((int) totalBlocks);
            contentCollectionRepository.save(entity);
        }

        // Return fresh model
        return convertToFullModel(entity);
    }

    @Override
    @Transactional
    public void deleteCollection(Long id) {
        log.debug("Deleting collection with ID: {}", id);

        // Check if collection exists
        if (!contentCollectionRepository.existsById(id)) {
            throw new EntityNotFoundException("Collection not found with ID: " + id);
        }

        // Delete all content blocks first
        contentBlockRepository.deleteByCollectionId(id);

        // Delete collection
        contentCollectionRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CollectionModel> getAllCollections(Pageable pageable) {
        log.debug("Getting all collections with pagination");

        // Get all collections with pagination
        Page<CollectionEntity> collectionsPage = contentCollectionRepository.findAll(pageable);

        // Convert to models
        List<CollectionModel> models = collectionsPage.getContent().stream()
                .map(this::convertToBasicModel)
                .collect(Collectors.toList());

        return new PageImpl<>(models, pageable, collectionsPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CollectionModel> getAllCollectionsOrderedByDate() {
        log.debug("Getting all collections ordered by collection date");

        // Get all collections ordered by collection date descending
        List<CollectionEntity> collections = contentCollectionRepository.findAllByOrderByCollectionDateDesc();

        // Convert to basic models (no content blocks)
        return collections.stream()
                .map(this::convertToBasicModel)
                .collect(Collectors.toList());
    }

    /**
     * Convert a ContentCollectionEntity to a ContentCollectionModel with basic information.
     * This does not include content blocks.
     *
     * @param entity The entity to convert
     * @return The converted model
     */
    private CollectionModel convertToBasicModel(CollectionEntity entity) {
        return contentCollectionProcessingUtil.convertToBasicModel(entity);
    }

    /**
     * Convert a ContentCollectionEntity to a ContentCollectionModel with all content blocks.
     *
     * @param entity The entity to convert
     * @return The converted model
     */
    private CollectionModel convertToFullModel(CollectionEntity entity) {
        CollectionModel model = convertToBasicModel(entity);
        if (model == null) {
            // Defensive: mocked util may return null in tests; ensure non-null model to avoid NPE
            model = new CollectionModel();
        }

        // Fetch content blocks explicitly to avoid LAZY polymorphic initializer issues
        List<ContentModel> contentBlocks = contentBlockRepository
                .findByCollectionIdOrderByOrderIndex(entity.getId())
                .stream()
                .map(contentBlockProcessingUtil::convertToModel)
                .collect(Collectors.toList());

        model.setContentBlocks(contentBlocks);
        return model;
    }

    /**
     * Convert a ContentCollectionEntity and a Page of ContentBlockEntity to a ContentCollectionModel.
     *
     * @param entity      The entity to convert
     * @param contentPage The page of content blocks
     * @return The converted model
     */
    private CollectionModel convertToModel(CollectionEntity entity, Page<ContentEntity> contentPage) {
        // Delegate to the shared ProcessingUtil to ensure consistent enrichment (coverImage, etc.)
        return contentCollectionProcessingUtil.convertToModel(entity, contentPage);
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
                .priority(entity.getPriority())
                .coverImageUrl(getCoverImageUrl(entity))
                .slug(entity.getSlug())
                .text(null) // Collections don't have text field
                .build();
    }

    /**
     * Helper method to get cover image URL from collection's coverImageBlockId
     */
    private String getCoverImageUrl(CollectionEntity collection) {
        if (collection.getCoverImageBlockId() == null) {
            return null;
        }

        return contentBlockRepository.findById(collection.getCoverImageBlockId())
                .filter(block -> block instanceof ContentImageEntity)
                .map(block -> ((ContentImageEntity) block).getImageUrlWeb())
                .orElse(null);
    }

    /**
     * Shared handler for applying Home Card options for a collection during create/update flows.
     * - If homeCardEnabled is non-null, upsert/deactivate accordingly via HomeService
     * - If null, keep the HomeCard in sync with the current collection state
     */
    private void applyHomeCardOptions(
            CollectionEntity entity,
            Boolean homeCardEnabled,
            Integer priority,
            String text
    ) {
        if (homeCardEnabled != null) {
            boolean enabled = homeCardEnabled;
            homeService.upsertHomeCardForCollection(
                    entity,
                    enabled,
                    priority,
                    text
            );
        } else {
            // No explicit toggle provided; keep in sync if a card exists
            homeService.syncHomeCardOnCollectionUpdate(entity);
        }
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
        List<CollectionListModel> collections = contentCollectionRepository.findAll().stream()
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
