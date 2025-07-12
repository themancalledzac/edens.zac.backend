package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.ContentBlockEntity;
import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import edens.zac.portfolio.backend.model.ContentBlockModel;
import edens.zac.portfolio.backend.model.ContentCollectionCreateDTO;
import edens.zac.portfolio.backend.model.ContentCollectionModel;
import edens.zac.portfolio.backend.model.ContentCollectionUpdateDTO;
import edens.zac.portfolio.backend.repository.ContentBlockRepository;
import edens.zac.portfolio.backend.repository.ContentCollectionRepository;
import edens.zac.portfolio.backend.types.CollectionType;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of ContentCollectionService that provides methods for
 * managing ContentCollection entities with pagination and client gallery access.
 */
@Service
@Slf4j
@RequiredArgsConstructor
class ContentCollectionServiceImpl implements ContentCollectionService {

    private final ContentCollectionRepository contentCollectionRepository;
    private final ContentBlockRepository contentBlockRepository;
    private final ContentBlockProcessingUtil contentBlockProcessingUtil;
    private final ContentCollectionProcessingUtil contentCollectionProcessingUtil;

    private static final int DEFAULT_PAGE_SIZE = 30;

    /**
     * Hash a password using SHA-256.
     *
     * @param password The password to hash
     * @return The hashed password
     */
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }

    /**
     * Check if a password matches a hash.
     *
     * @param password The password to check
     * @param hash The hash to check against
     * @return True if the password matches the hash
     */
    private boolean passwordMatches(String password, String hash) {
        return hashPassword(password).equals(hash);
    }

    @Override
    @Transactional(readOnly = true)
    public ContentCollectionModel getCollectionWithPagination(String slug, int page, int size) {
        log.debug("Getting collection with slug: {} (page: {}, size: {})", slug, page, size);

        // Get collection metadata
        ContentCollectionEntity collection = contentCollectionRepository.findTop50BySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Collection not found with slug: " + slug));

        // Use default page size if not specified or invalid
        if (size <= 0) {
            size = DEFAULT_PAGE_SIZE;
        }

        // Create pageable (convert to 0-based page index)
        Pageable pageable = PageRequest.of(Math.max(0, page), size);

        // Get paginated content blocks
        Page<ContentBlockEntity> contentPage = contentBlockRepository
                .findByCollectionId(collection.getId(), pageable);

        // Convert to model
        return convertToModel(collection, contentPage);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean validateClientGalleryAccess(String slug, String password) {
        log.debug("Validating access to client gallery: {}", slug);

        // Get collection metadata
        ContentCollectionEntity collection = contentCollectionRepository.findTop50BySlug(slug)
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
        return passwordMatches(password, collection.getPasswordHash());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ContentCollectionModel> findByType(CollectionType type, Pageable pageable) {
        log.debug("Finding collections by type: {}", type);

        // Get collections by type with pagination
        Page<ContentCollectionEntity> collectionsPage;

        if (type == CollectionType.BLOG) {
            // Blogs are ordered by date descending
            collectionsPage = contentCollectionRepository.findAll(pageable);
        } else {
            // Other types are ordered by priority
            collectionsPage = contentCollectionRepository.findAll(pageable);
        }

        // Convert to models
        List<ContentCollectionModel> models = collectionsPage.getContent().stream()
                .map(this::convertToBasicModel)
                .collect(Collectors.toList());

        return new PageImpl<>(models, pageable, collectionsPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ContentCollectionModel> findBySlug(String slug) {
        log.debug("Finding collection by slug: {}", slug);

        // Get collection with content blocks
        return contentCollectionRepository.findTop50BySlugWithContentBlocks(slug)
                .map(this::convertToFullModel);
    }

    @Override
    @Transactional
    public ContentCollectionModel createWithContent(ContentCollectionCreateDTO createDTO) {
        log.debug("Creating new collection: {}", createDTO.getTitle());

        // Validate DTO
        if (!contentCollectionProcessingUtil.isValidForType(createDTO)) {
            throw new IllegalArgumentException("Invalid collection data for type: " + createDTO.getType());
        }

        // Create entity
        ContentCollectionEntity entity = new ContentCollectionEntity();
        entity.setType(createDTO.getType());
        entity.setTitle(createDTO.getTitle());
        entity.setSlug(createDTO.getSlug());
        entity.setDescription(createDTO.getDescription());
        entity.setLocation(createDTO.getLocation());
        entity.setCollectionDate(createDTO.getCollectionDate());
        entity.setVisible(createDTO.getVisible());
        entity.setPriority(createDTO.getPriority());
        entity.setCoverImageUrl(createDTO.getCoverImageUrl());
        entity.setBlocksPerPage(DEFAULT_PAGE_SIZE);
        entity.setTotalBlocks(0);

        // Handle password protection for client galleries
        if (contentCollectionProcessingUtil.requiresPasswordProtection(createDTO)) {
            if (!contentCollectionProcessingUtil.hasPassword(createDTO)) {
                throw new IllegalArgumentException("Password is required for client galleries");
            }

            entity.setPasswordProtected(true);
            entity.setPasswordHash(hashPassword(createDTO.getPassword()));
        } else {
            entity.setPasswordProtected(false);
            entity.setPasswordHash(null);
        }

        // Save entity
        ContentCollectionEntity savedEntity = contentCollectionRepository.save(entity);

        return convertToFullModel(savedEntity);
    }


    @Override
    @Transactional
    public ContentCollectionModel updateContent(Long id, ContentCollectionUpdateDTO updateDTO) {
        log.debug("Updating collection with ID: {}", id);

        // Get existing entity
        ContentCollectionEntity entity = contentCollectionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Collection not found with ID: " + id));

        // Update basic properties if provided
        if (updateDTO.getTitle() != null) {
            entity.setTitle(updateDTO.getTitle());
        }

        if (updateDTO.getDescription() != null) {
            entity.setDescription(updateDTO.getDescription());
        }

        if (updateDTO.getLocation() != null) {
            entity.setLocation(updateDTO.getLocation());
        }

        if (updateDTO.getCollectionDate() != null) {
            entity.setCollectionDate(updateDTO.getCollectionDate());
        }

        if (updateDTO.getVisible() != null) {
            entity.setVisible(updateDTO.getVisible());
        }

        if (updateDTO.getPriority() != null) {
            entity.setPriority(updateDTO.getPriority());
        }

        if (updateDTO.getCoverImageUrl() != null) {
            entity.setCoverImageUrl(updateDTO.getCoverImageUrl());
        }

        // Handle password updates for client galleries
        if (entity.getType() == CollectionType.CLIENT_GALLERY && 
                contentCollectionProcessingUtil.hasPasswordUpdate(updateDTO)) {
            entity.setPasswordProtected(true);
            entity.setPasswordHash(hashPassword(updateDTO.getPassword()));
        }

        // Handle content block reordering if provided
        if (contentCollectionProcessingUtil.hasContentOperations(updateDTO) && 
                updateDTO.getReorderOperations() != null && !updateDTO.getReorderOperations().isEmpty()) {
            // Process each reorder operation
            updateDTO.getReorderOperations().forEach(op -> {
                contentBlockRepository.updateOrderIndex(op.getContentBlockId(), op.getNewOrderIndex());
            });
        }

        // Save updated entity
        ContentCollectionEntity savedEntity = contentCollectionRepository.save(entity);

        // Update total blocks count
        long totalBlocks = contentBlockRepository.countByCollectionId(savedEntity.getId());
        savedEntity.setTotalBlocks((int) totalBlocks);
        contentCollectionRepository.save(savedEntity);

        return convertToFullModel(savedEntity);
    }

    @Override
    @Transactional
    public ContentCollectionModel updateContentWithFiles(Long id, ContentCollectionUpdateDTO updateDTO, List<MultipartFile> files) {
        log.debug("Updating collection with ID: {} and processing files", id);

        // First update the collection with the provided DTO
        ContentCollectionModel updatedCollection = updateContent(id, updateDTO);

        // Process files if provided
        if (files != null && !files.isEmpty()) {
            List<ContentBlockEntity> contentBlocks = new ArrayList<>();

            // Get the current highest order index for this collection
            Integer startOrderIndex = contentBlockRepository.getMaxOrderIndexForCollection(id);
            Integer orderIndex = (startOrderIndex != null) ? startOrderIndex + 1 : 0;

            for (MultipartFile file : files) {
                try {
                    // Process file based on content type
                    if (file.getContentType() != null && file.getContentType().startsWith("image/")) {
                        if (file.getContentType().equals("image/gif")) {
                            // Process as GIF
                            ContentBlockEntity gifBlock = contentBlockProcessingUtil.processGifContentBlock(
                                    file, id, orderIndex, updatedCollection.getTitle(), null);
                            contentBlocks.add(gifBlock);
                        } else {
                            // Process as image
                            ContentBlockEntity imageBlock = contentBlockProcessingUtil.processImageContentBlock(
                                    file, id, orderIndex, updatedCollection.getTitle(), null);
                            contentBlocks.add(imageBlock);
                        }
                        orderIndex++;
                    }
                } catch (Exception e) {
                    log.error("Error processing file: {}", e.getMessage(), e);
                }
            }

            // Update total blocks count if any blocks were added
            if (!contentBlocks.isEmpty()) {
                ContentCollectionEntity entity = contentCollectionRepository.findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Collection not found with ID: " + id));

                // Update total blocks count
                long totalBlocks = contentBlockRepository.countByCollectionId(id);
                entity.setTotalBlocks((int) totalBlocks);
                contentCollectionRepository.save(entity);

                // Refresh the model with the updated entity
                return convertToFullModel(entity);
            }
        }

        return updatedCollection;
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
    public Page<ContentCollectionModel> getAllCollections(Pageable pageable) {
        log.debug("Getting all collections with pagination");

        // Get all collections with pagination
        Page<ContentCollectionEntity> collectionsPage = contentCollectionRepository.findAll(pageable);

        // Convert to models
        List<ContentCollectionModel> models = collectionsPage.getContent().stream()
                .map(this::convertToBasicModel)
                .collect(Collectors.toList());

        return new PageImpl<>(models, pageable, collectionsPage.getTotalElements());
    }

    /**
     * Convert a ContentCollectionEntity to a ContentCollectionModel with basic information.
     * This does not include content blocks.
     *
     * @param entity The entity to convert
     * @return The converted model
     */
    private ContentCollectionModel convertToBasicModel(ContentCollectionEntity entity) {
        return contentCollectionProcessingUtil.convertToBasicModel(entity);
    }

    /**
     * Convert a ContentCollectionEntity to a ContentCollectionModel with all content blocks.
     *
     * @param entity The entity to convert
     * @return The converted model
     */
    private ContentCollectionModel convertToFullModel(ContentCollectionEntity entity) {
        ContentCollectionModel model = convertToBasicModel(entity);

        // Convert content blocks
        List<ContentBlockModel> contentBlocks = new ArrayList<>();
        if (entity.getContentBlocks() != null) {
            contentBlocks = entity.getContentBlocks().stream()
                    .map(contentBlockProcessingUtil::convertToModel)
                    .collect(Collectors.toList());
        }

        model.setContentBlocks(contentBlocks);
        return model;
    }

    /**
     * Convert a ContentCollectionEntity and a Page of ContentBlockEntity to a ContentCollectionModel.
     *
     * @param entity The entity to convert
     * @param contentPage The page of content blocks
     * @return The converted model
     */
    private ContentCollectionModel convertToModel(ContentCollectionEntity entity, Page<ContentBlockEntity> contentPage) {
        ContentCollectionModel model = convertToBasicModel(entity);

        // Convert content blocks
        List<ContentBlockModel> contentBlocks = contentPage.getContent().stream()
                .map(contentBlockProcessingUtil::convertToModel)
                .collect(Collectors.toList());

        model.setContentBlocks(contentBlocks);

        // Set pagination metadata
        model.setCurrentPage(contentPage.getNumber());
        model.setTotalPages(contentPage.getTotalPages());
        model.setTotalBlocks((int) contentPage.getTotalElements());
        model.setBlocksPerPage(contentPage.getSize());

        return model;
    }
}
