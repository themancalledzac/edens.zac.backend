package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.ContentEntity;
import edens.zac.portfolio.backend.entity.ContentImageEntity;
import edens.zac.portfolio.backend.entity.CollectionEntity;
import edens.zac.portfolio.backend.entity.CollectionContentEntity;
import edens.zac.portfolio.backend.model.*;
import edens.zac.portfolio.backend.repository.CollectionRepository;
import edens.zac.portfolio.backend.repository.ContentRepository;
import edens.zac.portfolio.backend.repository.CollectionContentRepository;
import edens.zac.portfolio.backend.types.CollectionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class CollectionProcessingUtil {

    private final CollectionRepository collectionRepository;
    private final ContentRepository contentRepository;
    private final CollectionContentRepository collectionContentRepository;
    private final ContentProcessingUtil contentProcessingUtil;
//    private final edens.zac.portfolio.backend.repository.ContentCollectionHomeCardRepository homeCardRepository;

    // =============================================================================
    // ERROR HANDLING
    // =============================================================================


    // =============================================================================
    // ENTITY-TO-MODEL CONVERSION
    // =============================================================================

    /**
     * Helper method to populate coverImage on a model from an entity.
     * Loads the full ContentImageModel if coverImageId is set.
     * Only accepts images as cover images.
     */
    private void populateCoverImage(CollectionModel model, CollectionEntity entity) {
        if (entity.getCoverImageId() != null) {
            ContentEntity content = contentRepository.findById(entity.getCoverImageId())
                    .orElse(null);
            if (content instanceof ContentImageEntity) {
                ContentModel contentModel = contentProcessingUtil.convertRegularContentEntityToModel(content);
                if (contentModel instanceof ContentImageModel imageModel) {
                    model.setCoverImage(imageModel);
                } else {
                    log.warn("Cover image {} converted to non-ContentImageModel: {}",
                            entity.getCoverImageId(), contentModel.getClass().getSimpleName());
                }
            } else if (content != null) {
                log.warn("Cover image {} is not a ContentImageEntity: {}",
                        entity.getCoverImageId(), content.getClass().getSimpleName());
            }
        }
    }

    /**
     * Convert a CollectionEntity to a CollectionModel with basic information.
     * This does not include content.
     *
     * @param entity The entity to convert
     * @return The converted model
     */
    public CollectionModel convertToBasicModel(CollectionEntity entity) {
        if (entity == null) {
            return null;
        }

        CollectionModel model = new CollectionModel();
        model.setId(entity.getId());
        model.setType(entity.getType());
        model.setTitle(entity.getTitle());
        model.setSlug(entity.getSlug());
        model.setDescription(entity.getDescription());
        model.setLocation(entity.getLocation());
        model.setCollectionDate(entity.getCollectionDate());
        model.setVisible(entity.getVisible());

        // Populate coverImage using helper method
        populateCoverImage(model, entity);

        // TODO: Re-implement password protection after migration
//        model.setIsPasswordProtected(entity.isPasswordProtected());
//        model.setHasAccess(!entity.isPasswordProtected()); // Default access for non-protected collections
        model.setCreatedAt(entity.getCreatedAt());
        model.setUpdatedAt(entity.getUpdatedAt());
        // Basic display mode: BLOG default chronological, others default ordered
        CollectionBaseModel.DisplayMode mode =
                entity.getType() == CollectionType.BLOG
                        ? CollectionBaseModel.DisplayMode.CHRONOLOGICAL
                        : CollectionBaseModel.DisplayMode.ORDERED;
        model.setDisplayMode(mode);

        // Set pagination metadata
        model.setContentCount(entity.getTotalContent());
        model.setContentPerPage(entity.getContentPerPage());
        model.setTotalPages(entity.getTotalPages());
        model.setCurrentPage(0);

        return model;
    }

    /**
     * Convert a CollectionEntity to a CollectionModel with all content.
     * Uses bulk loading of ContentEntity instances to avoid proxy issues and improve performance.
     *
     * @param entity The entity to convert
     * @return The converted model
     */
    public CollectionModel convertToFullModel(CollectionEntity entity) {
        if (entity == null) {
            return null;
        }

        CollectionModel model = convertToBasicModel(entity);

        // Fetch join table entries explicitly to get content with collection-specific metadata
        List<CollectionContentEntity> joinEntries = collectionContentRepository
                .findByCollectionIdOrderByOrderIndex(entity.getId());

        // Extract content IDs from join table entries
        List<Long> contentIds = joinEntries.stream()
                .map(cc -> cc.getContent().getId())
                .filter(Objects::nonNull)
                .toList();

        // Bulk fetch all ContentEntity instances in one query (properly loads all subclasses)
        final Map<Long, ContentEntity> contentMap;
        if (!contentIds.isEmpty()) {
            List<ContentEntity> contentEntities = contentRepository.findAllByIds(contentIds);
            contentMap = contentEntities.stream()
                    .collect(Collectors.toMap(ContentEntity::getId, ce -> ce));
        } else {
            contentMap = new HashMap<>();
        }

        // Convert join table entries to content models with collection-specific metadata
        // Use the bulk-loaded content entities instead of lazy-loaded ones
        List<ContentModel> contents = joinEntries.stream()
                .filter(Objects::nonNull)
                .map(cc -> {
                    ContentEntity content = contentMap.get(cc.getContent().getId());
                    if (content == null) {
                        log.warn("Content entity {} not found in bulk load for collection {}", 
                                cc.getContent().getId(), entity.getId());
                        return null;
                    }
                    // Create a temporary CollectionContentEntity with the bulk-loaded content
                    // to avoid proxy issues
                    CollectionContentEntity tempCc = CollectionContentEntity.builder()
                            .id(cc.getId())
                            .collection(cc.getCollection())
                            .content(content)  // Use bulk-loaded, properly typed entity
                            .orderIndex(cc.getOrderIndex())
                            .imageUrl(cc.getImageUrl())
                            .visible(cc.getVisible())
                            .createdAt(cc.getCreatedAt())
                            .updatedAt(cc.getUpdatedAt())
                            .build();
                    return contentProcessingUtil.convertEntityToModel(tempCc);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        model.setContent(contents);
        return model;
    }

    /**
     * Convert a CollectionEntity and a Page of CollectionContentEntity to a CollectionModel.
     * Uses bulk loading of ContentEntity instances to avoid proxy issues and improve performance.
     *
     * @param entity                The entity to convert
     * @param collectionContentPage The page of join table entries (collection-content associations)
     * @return The converted model
     */
    public CollectionModel convertToModel(CollectionEntity entity, Page<CollectionContentEntity> collectionContentPage) {
        if (entity == null) {
            return null;
        }

        CollectionModel model = convertToBasicModel(entity);

        // Extract content IDs from join table entries
        List<Long> contentIds = collectionContentPage.getContent().stream()
                .map(cc -> cc.getContent().getId())
                .filter(Objects::nonNull)
                .toList();

        // Bulk fetch all ContentEntity instances in one query (properly loads all subclasses)
        final Map<Long, ContentEntity> contentMap;
        if (!contentIds.isEmpty()) {
            List<ContentEntity> contentEntities = contentRepository.findAllByIds(contentIds);
            contentMap = contentEntities.stream()
                    .collect(Collectors.toMap(ContentEntity::getId, ce -> ce));
        } else {
            contentMap = new HashMap<>();
        }

        // Convert join table entries to content models with collection-specific metadata
        // Use the bulk-loaded content entities instead of lazy-loaded ones
        List<ContentModel> contents = collectionContentPage.getContent().stream()
                .filter(Objects::nonNull)
                .map(cc -> {
                    ContentEntity content = contentMap.get(cc.getContent().getId());
                    if (content == null) {
                        log.warn("Content entity {} not found in bulk load for collection {}", 
                                cc.getContent().getId(), entity.getId());
                        return null;
                    }
                    // Create a temporary CollectionContentEntity with the bulk-loaded content
                    // to avoid proxy issues
                    CollectionContentEntity tempCc = CollectionContentEntity.builder()
                            .id(cc.getId())
                            .collection(cc.getCollection())
                            .content(content)  // Use bulk-loaded, properly typed entity
                            .orderIndex(cc.getOrderIndex())
                            .imageUrl(cc.getImageUrl())
                            .visible(cc.getVisible())
                            .createdAt(cc.getCreatedAt())
                            .updatedAt(cc.getUpdatedAt())
                            .build();
                    return contentProcessingUtil.convertEntityToModel(tempCc);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        model.setContent(contents);

        // Set pagination metadata
        model.setCurrentPage(collectionContentPage.getNumber());
        model.setTotalPages(collectionContentPage.getTotalPages());
        model.setContentCount((int) collectionContentPage.getTotalElements());
        model.setContentPerPage(collectionContentPage.getSize());
        return model;
    }

    // =============================================================================
    // DTO-TO-ENTITY CONVERSION
    // =============================================================================


    /**
     * Minimal create: from CollectionCreateRequest (type, title only), apply defaults for the rest.
     */
    public CollectionEntity toEntity(CollectionCreateRequest request, int defaultPageSize) {
        if (request == null) {
            throw new IllegalArgumentException("Create request cannot be null");
        }
        CollectionEntity entity = new CollectionEntity();
        entity.setType(request.getType());
        entity.setTitle(request.getTitle());
        String baseSlug = generateSlug(request.getTitle());
        String uniqueSlug = validateAndEnsureUniqueSlug(baseSlug, null);
        entity.setSlug(uniqueSlug);
        entity.setDescription("");
        entity.setLocation("");
        entity.setCollectionDate(LocalDate.now());
        entity.setVisible(false);
        entity.setContentPerPage(defaultPageSize);
        entity.setTotalContent(0);
        // TODO: Re-implement password protection after migration
//        entity.setPasswordProtected(false);
//        entity.setPasswordHash(null);
        // Apply type-specific defaults (may adjust visibility etc.)
        return applyTypeSpecificDefaults(entity);
    }

    /**
     * Update imageUrl in all collection_content entries where the given collection
     * is referenced as a child collection (via ContentCollectionEntity).
     * This is called when a collection's cover_image_id is updated.
     *
     * @param collectionId The ID of the collection whose cover image changed
     * @param newImageUrl  The new cover image URL
     */
    private void updateChildCollectionImageUrls(Long collectionId, String newImageUrl) {
        List<CollectionContentEntity> childEntries = collectionContentRepository
                .findByReferencedCollectionId(collectionId);

        for (CollectionContentEntity entry : childEntries) {
            collectionContentRepository.updateImageUrl(entry.getId(), newImageUrl);
            log.debug("Updated imageUrl for collection_content entry {} to {}", entry.getId(), newImageUrl);
        }

        if (!childEntries.isEmpty()) {
            log.info("Updated imageUrl for {} collection_content entries referencing collection {}", 
                    childEntries.size(), collectionId);
        }
    }

    // =============================================================================
    // UPDATE HELPERS FOR SERVICE LAYER (split from updateContent)
    // =============================================================================

    /**
     * Apply basic property updates from updateDTO to the given entity.
     * This mirrors the simple field updates and slug/password logic from the service.
     * - title, description, location, collectionDate, visible, priority, coverImageUrl
     * - slug uniqueness handling (keeps same entity allowed)
     * - configJson
     * - blocksPerPage (>=1)
     * - client gallery password updates via provided password hasher
     */
    public void applyBasicUpdates(CollectionEntity entity,
                                  CollectionUpdateRequest updateDTO) {
        if (updateDTO.getTitle() != null) {
            entity.setTitle(updateDTO.getTitle());
        }
        if (updateDTO.getDescription() != null) {
            entity.setDescription(updateDTO.getDescription());
        }
        if (updateDTO.getType() != null) {
            entity.setType(updateDTO.getType());
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
        if (updateDTO.getSlug() != null && !updateDTO.getSlug().isBlank()) {
            String uniqueSlug = validateAndEnsureUniqueSlug(updateDTO.getSlug().trim(), entity.getId());
            entity.setSlug(uniqueSlug);
        }
        if (updateDTO.getContentPerPage() != null && updateDTO.getContentPerPage() >= 1) {
            entity.setContentPerPage(updateDTO.getContentPerPage());
        }

        // Handle coverImage updates
        if (updateDTO.getCoverImageId() != null) {
            // Validate that the cover image ID references an actual image block
            ContentEntity coverBlock = contentRepository.findById(updateDTO.getCoverImageId())
                    .orElse(null);
            if (coverBlock instanceof ContentImageEntity imageEntity) {
                Long oldCoverImageId = entity.getCoverImageId();
                entity.setCoverImageId(updateDTO.getCoverImageId());
                
                // If cover image changed, update imageUrl in all collection_content entries
                // where this collection is referenced as a child collection
                if (!updateDTO.getCoverImageId().equals(oldCoverImageId)) {
                    String newImageUrl = imageEntity.getImageUrlWeb();
                    updateChildCollectionImageUrls(entity.getId(), newImageUrl);
                }
            } else if (coverBlock != null) {
                throw new IllegalArgumentException("Cover image ID " + updateDTO.getCoverImageId()
                        + " does not reference an image block (found: " + coverBlock.getClass().getSimpleName() + ")");
            } else {
                throw new IllegalArgumentException("Cover image ID " + updateDTO.getCoverImageId()
                        + " does not exist");
            }
        }

        // TODO: Re-implement password protection after migration
        // Handle password updates for client galleries
//        if (entity.getType() == CollectionType.CLIENT_GALLERY) {
//            if (updateDTO.getHasAccess() != null && updateDTO.getHasAccess()) {
//                entity.setPasswordProtected(false);
//                entity.setPasswordHash(null);
//            }
//            if (hasPasswordUpdate(updateDTO)) {
//                entity.setPasswordProtected(true);
//                entity.setPasswordHash(hashPassword(updateDTO.getPassword()));
//            }
//        }
    }

//    /**
//     * Handle adding new text blocks, either appending to the end or inserting at a specific index.
//     * Behavior matches the original service implementation.
//     */
//    public void handleNewTextBlocks(Long collectionId, CollectionUpdateRequest updateDTO) {
//        if (updateDTO.getNewTextContent() == null || updateDTO.getNewTextContent().isEmpty()) {
//            return;
//        }
//        // Always append new text blocks to the end
//        Integer startOrderIndex = collectionContentRepository.getMaxOrderIndexForCollection(collectionId);
//        int currentIndex = (startOrderIndex != null) ? startOrderIndex + 1 : 0;
//        for (String text : updateDTO.getNewTextContent()) {
//            contentProcessingUtil.processTextContent(text, collectionId);
//            currentIndex++;
//        }
//    }

//    /**
//     * Variant of handleNewTextBlocks that returns the IDs of newly created text blocks
//     * in the same order as provided in updateDTO.getNewTextBlocks(). This enables
//     * deterministic placeholder mapping during subsequent reordering.
//     */
//    public List<Long> handleNewTextContentReturnIds(Long collectionId, CollectionUpdateRequest updateDTO) {
//        List<Long> createdIds = new ArrayList<>();
//        if (updateDTO.getNewTextContent() == null || updateDTO.getNewTextContent().isEmpty()) {
//            return createdIds;
//        }
//        for (String text : updateDTO.getNewTextContent()) {
//            ContentEntity created = contentProcessingUtil.processTextContent(text, collectionId);
//            if (created != null && created.getId() != null) {
//                createdIds.add(created.getId());
//            }
//            currentIndex++;
//        }
//        return createdIds;
//    }

//    /**
//     * Handle content block reordering operations. Supports reference by ID, placeholder for newly
//     * added text blocks (negative IDs: -1 for first new text, etc.), or by old order index.
//     */
//    public void handleContentReordering(Long collectionId, CollectionUpdateRequest updateDTO) {
//        if (updateDTO.getReorderOperations() == null || updateDTO.getReorderOperations().isEmpty()) {
//            return;
//        }
//        // Backward-compatible behavior: try to infer newTextIds by taking the last N blocks
//        List<Long> inferredNewTextIds = new ArrayList<>();
//        if (updateDTO.getNewTextContent() != null && !updateDTO.getNewTextContent().isEmpty()) {
//            int n = updateDTO.getNewTextContent().size();
//            // Get all join table entries and extract content entities
//            List<ContentEntity> allBlocks = collectionContentRepository
//                    .findByCollectionIdOrderByOrderIndex(collectionId)
//                    .stream()
//                    .map(CollectionContentEntity::getContent)
//                    .toList();
//            int total = allBlocks.size();
//            for (int i = Math.max(0, total - n); i < total; i++) {
//                inferredNewTextIds.add(allBlocks.get(i).getId());
//            }
//        }
//        handleContentReordering(collectionId, updateDTO, inferredNewTextIds);
//    }

    /**
     * Overloaded reordering that accepts explicit newTextIds mapping. This ensures
     * correct placeholder resolution regardless of insert position.
     *
     * TODO: This method is deprecated as reordering is now handled via individual content update endpoints
     */
//    public void handleContentReordering(Long collectionId, CollectionUpdateRequest updateDTO, List<Long> newTextIds) {
//        if (updateDTO.getReorderOperations() == null || updateDTO.getReorderOperations().isEmpty()) {
//            return;
//        }
//        List<Long> mapping = (newTextIds != null) ? newTextIds : new ArrayList<>();
//        for (CollectionUpdateRequest.ContentReorderOperation op : updateDTO.getReorderOperations()) {
//            Long targetId = null;
//            Long providedId = op.getContentId();
//            if (providedId != null) {
//                if (providedId > 0) {
//                    targetId = providedId;
//                } else if (providedId < 0) {
//                    int idx = (int) (-providedId) - 1; // -1 -> 0, -2 -> 1, ...
//                    if (idx >= 0 && idx < mapping.size()) {
//                        targetId = mapping.get(idx);
//                    } else {
//                        throw new IllegalArgumentException("Reorder operation references a new text block placeholder that does not exist: " + providedId);
//                    }
//                }
//            }
//            if (targetId == null) {
//                Integer oldIdx = op.getOldOrderIndex();
//                if (oldIdx == null) {
//                    throw new IllegalArgumentException("Reorder operation must include either contentId or oldOrderIndex");
//                }
//                // Find join table entry by collection and old orderIndex
//                CollectionContentEntity byIndex = collectionContentRepository.findByCollectionIdAndOrderIndex(collectionId, oldIdx);
//                if (byIndex == null) {
//                    throw new IllegalArgumentException("No content found at oldOrderIndex=" + oldIdx);
//                }
//                targetId = byIndex.getContent().getId();
//            }
//
//            // Find the join table entry for this content in this collection and update its orderIndex
//            CollectionContentEntity ccToUpdate = collectionContentRepository.findByCollectionIdAndContentId(collectionId, targetId);
//            if (ccToUpdate == null) {
//                throw new IllegalArgumentException("Content with ID " + targetId + " is not associated with collection " + collectionId);
//            }
//            collectionContentRepository.updateOrderIndex(ccToUpdate.getId(), op.getNewOrderIndex());
//        }
//    }

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
        if (title == null || title.isEmpty()) {
            return "";
        }

        return title.toLowerCase()
                .replaceAll("[^a-zA-Z0-9\\s-]", "") // Remove all non-alphanumeric chars except space and -
                .replaceAll("\\s+", "-") // Replace spaces with hyphens
                .replaceAll("-+", "-") // Replace multiple hyphens with single hyphen
                .replaceAll("^-|-$", ""); // Remove leading and trailing hyphens
    }

    /**
     * Validate and ensure a slug is unique.
     * If the slug already exists, append a number to make it unique.
     *
     * @param slug       The slug to validate
     * @param existingId The ID of the existing entity (null for new entities)
     * @return A unique slug
     */
    public String validateAndEnsureUniqueSlug(String slug, Long existingId) {
        if (slug == null || slug.isEmpty()) {
            throw new IllegalArgumentException("Slug cannot be empty");
        }

        // Check if slug already exists
        boolean exists = collectionRepository.findBySlug(slug)
                .map(entity -> !entity.getId().equals(existingId))
                .orElse(false);

        if (!exists) {
            return slug; // Slug is unique
        }

        // Slug exists, append a number to make it unique
        int counter = 1;
        String newSlug;
        do {
            newSlug = slug + "-" + counter++;
            exists = collectionRepository.findBySlug(newSlug).isPresent();
        } while (exists && counter < 100); // Limit to prevent infinite loop

        if (exists) {
            throw new RuntimeException("Could not generate a unique slug after 100 attempts");
        }

        return newSlug;
    }

    // =============================================================================
    // TYPE-SPECIFIC PROCESSING
    // =============================================================================


    /**
     * Update entity with type-specific defaults.
     *
     * @param entity The entity to update
     * @return The updated entity
     */
    public CollectionEntity applyTypeSpecificDefaults(CollectionEntity entity) {
        if (entity == null || entity.getType() == null) {
            return entity;
        }


        // Set default blocks per page if not set
        if (entity.getContentPerPage() == null || entity.getContentPerPage() <= 0) {
            entity.setContentPerPage(edens.zac.portfolio.backend.config.DefaultValues.default_content_per_page); // Default page size
        }

        // Set type-specific visibility defaults
        if (entity.getVisible() == null) {
            // Client galleries are private by default
            entity.setVisible(entity.getType() != CollectionType.CLIENT_GALLERY);
        }

        return entity;
    }

    // =============================================================================
    // VALIDATION METHODS
    // =============================================================================


    // =============================================================================
    // PASSWORD PROTECTION HELPERS
    // =============================================================================

    // TODO: Re-implement password protection after migration
//    /**
//     * Check if a collection (any type) is password-protected.
//     */
//    public static boolean isPasswordProtected(CollectionBaseModel model) {
//        return model.getIsPasswordProtected() != null && model.getIsPasswordProtected();
//    }


    /**
     * Check if an UpdateDTO includes password changes.
     */
    public static boolean hasPasswordUpdate(CollectionUpdateRequest dto) {
        return dto.getPassword() != null && !dto.getPassword().trim().isEmpty();
    }

    /**
     * Hash a password using SHA-256.
     * Note: For production client gallery secrets, prefer BCrypt or Argon2.
     */
    public static String hashPassword(String password) {
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
     * Check if a password matches a stored hash.
     */
    public static boolean passwordMatches(String password, String hash) {
        return hashPassword(password).equals(hash);
    }


    // =============================================================================
    // VISIBILITY HELPERS
    // =============================================================================

    /**
     * Check if a collection is publicly visible.
     */
    public static boolean isVisible(CollectionBaseModel model) {
        return model.getVisible() != null && model.getVisible();
    }

    // =============================================================================
    // PAGINATION HELPERS
    // =============================================================================

    /**
     * Check if a collection model is empty.
     */
    public static boolean isEmpty(CollectionModel model) {
        return model.getContentCount() == null || model.getContentCount() == 0;
    }
}
