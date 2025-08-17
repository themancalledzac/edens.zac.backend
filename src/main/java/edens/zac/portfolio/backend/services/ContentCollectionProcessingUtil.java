package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.entity.ContentBlockEntity;
import edens.zac.portfolio.backend.entity.ContentCollectionEntity;
import edens.zac.portfolio.backend.model.*;
import edens.zac.portfolio.backend.repository.ContentCollectionRepository;
import edens.zac.portfolio.backend.types.CollectionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class ContentCollectionProcessingUtil {

    private final ContentCollectionRepository contentCollectionRepository;
    private final ContentBlockProcessingUtil contentBlockProcessingUtil;
    private final ImageProcessingUtil imageProcessingUtil;

    // =============================================================================
    // ERROR HANDLING
    // =============================================================================


    // =============================================================================
    // ENTITY-TO-MODEL CONVERSION
    // =============================================================================

    /**
     * Convert a ContentCollectionEntity to a ContentCollectionModel with basic information.
     * This does not include content blocks.
     *
     * @param entity The entity to convert
     * @return The converted model
     */
    public ContentCollectionModel convertToBasicModel(ContentCollectionEntity entity) {
        if (entity == null) {
            return null;
        }

        ContentCollectionModel model = new ContentCollectionModel();
        model.setId(entity.getId());
        model.setType(entity.getType());
        model.setTitle(entity.getTitle());
        model.setSlug(entity.getSlug());
        model.setDescription(entity.getDescription());
        model.setLocation(entity.getLocation());
        model.setCollectionDate(entity.getCollectionDate());
        model.setVisible(entity.getVisible());
        model.setPriority(entity.getPriority());
        model.setCoverImageUrl(entity.getCoverImageUrl());
        model.setIsPasswordProtected(entity.isPasswordProtected());
        model.setHasAccess(!entity.isPasswordProtected()); // Default access for non-protected collections
        model.setCreatedAt(entity.getCreatedAt());
        model.setUpdatedAt(entity.getUpdatedAt());
        model.setConfigJson(entity.getConfigJson());

        // Set pagination metadata
        model.setTotalBlocks(entity.getTotalBlocks());
        model.setBlocksPerPage(entity.getBlocksPerPage());
        model.setTotalPages(entity.getTotalPages());
        model.setCurrentPage(0);

        return model;
    }

    /**
     * Convert a ContentCollectionEntity to a ContentCollectionModel with all content blocks.
     *
     * @param entity The entity to convert
     * @return The converted model
     */
    public ContentCollectionModel convertToFullModel(ContentCollectionEntity entity) {
        if (entity == null) {
            return null;
        }

        ContentCollectionModel model = convertToBasicModel(entity);

        // Convert content blocks
        List<ContentBlockModel> contentBlocks = new ArrayList<>();
        if (entity.getContentBlocks() != null) {
            contentBlocks = entity.getContentBlocks().stream()
                    .filter(Objects::nonNull)
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
    public ContentCollectionModel convertToModel(ContentCollectionEntity entity, Page<ContentBlockEntity> contentPage) {
        if (entity == null) {
            return null;
        }

        ContentCollectionModel model = convertToBasicModel(entity);

        // Convert content blocks
        List<ContentBlockModel> contentBlocks = contentPage.getContent().stream()
                .filter(Objects::nonNull)
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

    // =============================================================================
    // DTO-TO-ENTITY CONVERSION
    // =============================================================================

    /**
     * Convert a ContentCollectionCreateDTO into a new ContentCollectionEntity applying
     * defaults, slug handling, password protection and type-specific defaults.
     * Note: Password hashing is delegated via the provided passwordHasher to keep
     * security concerns in the service layer and make testing easier.
     *
     * @param dto The create DTO
     * @param defaultPageSize Fallback blocksPerPage when dto value is null/invalid
     * @param passwordHasher Function to hash plain-text password when needed (may be null if not required)
     * @return A new ContentCollectionEntity ready to be persisted
     */
    public ContentCollectionEntity toEntity(ContentCollectionCreateDTO dto, int defaultPageSize,
                                            Function<String, String> passwordHasher) {
        if (dto == null) {
            throw new IllegalArgumentException("Create DTO cannot be null");
        }

        ContentCollectionEntity entity = new ContentCollectionEntity();
        entity.setType(dto.getType());
        entity.setTitle(dto.getTitle());

        // Determine slug: use provided, otherwise generate from title; ensure uniqueness
        String baseSlug = (dto.getSlug() != null && !dto.getSlug().trim().isEmpty())
                ? dto.getSlug().trim()
                : generateSlug(dto.getTitle());
        String uniqueSlug = validateAndEnsureUniqueSlug(baseSlug, null);
        entity.setSlug(uniqueSlug);

        entity.setDescription(dto.getDescription());

        // Defaults
        entity.setLocation(dto.getLocation() != null ? dto.getLocation() : "");
        entity.setCollectionDate(dto.getCollectionDate() != null ? dto.getCollectionDate() : LocalDateTime.now());
        entity.setVisible(dto.getVisible() != null ? dto.getVisible() : false);
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : 4);
        entity.setCoverImageUrl(dto.getCoverImageUrl() != null ? dto.getCoverImageUrl() : "");

        Integer requestedBpp = dto.getBlocksPerPage();
        entity.setBlocksPerPage((requestedBpp != null && requestedBpp >= 1) ? requestedBpp : defaultPageSize);
        entity.setTotalBlocks(0);

        // Password protection for client galleries
        if (requiresPasswordProtection(dto)) {
            if (!hasPassword(dto)) {
                throw new IllegalArgumentException("Password is required for client galleries");
            }
            entity.setPasswordProtected(true);
            if (passwordHasher == null) {
                throw new IllegalStateException("Password hasher is required for password-protected collections");
            }
            entity.setPasswordHash(passwordHasher.apply(dto.getPassword()));
        } else {
            entity.setPasswordProtected(false);
            entity.setPasswordHash(null);
        }

        // Apply type-specific defaults
        entity = applyTypeSpecificDefaults(entity);

        return entity;
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
        return imageProcessingUtil.generateSlug(title);
    }

    /**
     * Validate and ensure a slug is unique.
     * If the slug already exists, append a number to make it unique.
     * 
     * @param slug The slug to validate
     * @param existingId The ID of the existing entity (null for new entities)
     * @return A unique slug
     */
    public String validateAndEnsureUniqueSlug(String slug, Long existingId) {
        if (slug == null || slug.isEmpty()) {
            throw new IllegalArgumentException("Slug cannot be empty");
        }

        // Check if slug already exists
        boolean exists = contentCollectionRepository.findTop50BySlug(slug)
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
            exists = contentCollectionRepository.findTop50BySlug(newSlug).isPresent();
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
     * Get type-specific configuration for a collection.
     * 
     * @param type The collection type
     * @return JSON configuration string
     */
    public String getDefaultConfigForType(CollectionType type) {
        return switch (type) {
            case BLOG -> "{\"displayMode\":\"chronological\",\"showDates\":true}";
            case ART_GALLERY -> "{\"displayMode\":\"grid\",\"gridColumns\":3}";
            case CLIENT_GALLERY -> "{\"downloadEnabled\":true,\"showMetadata\":false}";
            case PORTFOLIO -> "{\"displayMode\":\"showcase\",\"highlightCover\":true}";
        };
    }

    /**
     * Update entity with type-specific defaults.
     * 
     * @param entity The entity to update
     * @return The updated entity
     */
    public ContentCollectionEntity applyTypeSpecificDefaults(ContentCollectionEntity entity) {
        if (entity == null || entity.getType() == null) {
            return entity;
        }

        // Set type-specific defaults if not already set
        if (entity.getConfigJson() == null || entity.getConfigJson().isEmpty()) {
            entity.setConfigJson(getDefaultConfigForType(entity.getType()));
        }

        // Set default blocks per page if not set
        if (entity.getBlocksPerPage() == null || entity.getBlocksPerPage() <= 0) {
            entity.setBlocksPerPage(50); // Default page size
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

    /**
     * Validate if a ContentCollectionCreateDTO is valid for its collection type.
     */
    public static boolean isValidForType(ContentCollectionCreateDTO dto) {
        return switch (dto.getType()) {
            case CLIENT_GALLERY -> !isPasswordProtected(dto) ||
                    (dto.getPassword() != null && !dto.getPassword().trim().isEmpty());
            case BLOG, ART_GALLERY, PORTFOLIO -> true;
        };
    }

    /**
     * Check if DTO requires password protection.
     */
    public static boolean requiresPasswordProtection(ContentCollectionCreateDTO dto) {
        return dto.getType() == CollectionType.CLIENT_GALLERY && 
               isPasswordProtected(dto) && 
               dto.getPassword() != null && 
               !dto.getPassword().trim().isEmpty();
    }

    // =============================================================================
    // PASSWORD PROTECTION HELPERS
    // =============================================================================

    /**
     * Check if a collection (any type) is password-protected.
     */
    public static boolean isPasswordProtected(ContentCollectionBaseModel model) {
        return model.getIsPasswordProtected() != null && model.getIsPasswordProtected();
    }

    /**
     * Check if a CreateDTO will be password-protected.
     */
    public static boolean hasPassword(ContentCollectionCreateDTO dto) {
        return dto.getPassword() != null && !dto.getPassword().trim().isEmpty();
    }

    /**
     * Check if an UpdateDTO includes password changes.
     */
    public static boolean hasPasswordUpdate(ContentCollectionUpdateDTO dto) {
        return dto.getPassword() != null && !dto.getPassword().trim().isEmpty();
    }

    /**
     * Check if a CreateDTO is for a client gallery.
     */
    public static boolean isClientGallery(ContentCollectionCreateDTO dto) {
        return CollectionType.CLIENT_GALLERY.equals(dto.getType());
    }

    // =============================================================================
    // VISIBILITY HELPERS
    // =============================================================================

    /**
     * Check if a collection is publicly visible.
     */
    public static boolean isVisible(ContentCollectionBaseModel model) {
        return model.getVisible() != null && model.getVisible();
    }

    // =============================================================================
    // PRIORITY HELPERS
    // =============================================================================

    /**
     * Get priority as display text.
     */
    public static String getPriorityDisplay(ContentCollectionBaseModel model) {
        Integer priority = model.getPriority();
        if (priority == null) return "Not Set";
        return switch (priority) {
            case 1 -> "Highest (1)";
            case 2 -> "High (2)";
            case 3 -> "Medium (3)";
            case 4 -> "Low (4)";
            default -> "Unknown (" + priority + ")";
        };
    }

    // =============================================================================
    // PAGINATION HELPERS
    // =============================================================================

    /**
     * Check if a collection model supports pagination.
     */
    public static boolean isPaginated(ContentCollectionModel model) {
        return model.getTotalPages() != null && model.getTotalPages() > 1;
    }

    /**
     * Check if a collection model is empty.
     */
    public static boolean isEmpty(ContentCollectionModel model) {
        return model.getTotalBlocks() == null || model.getTotalBlocks() == 0;
    }

    /**
     * Check if a page DTO is empty.
     */
    public static boolean isEmpty(ContentCollectionPageDTO dto) {
        return dto.getTotalElements() == null || dto.getTotalElements() == 0;
    }

    /**
     * Check if a page DTO supports pagination.
     */
    public static boolean isPaginated(ContentCollectionPageDTO dto) {
        return dto.getTotalPages() != null && dto.getTotalPages() > 1;
    }

    /**
     * Get the range of items being displayed for a page DTO.
     * Example: "Showing 1-30 of 150 items"
     * 
     * @return array with [startItem, endItem] or null if empty
     */
    public static int[] getDisplayRange(ContentCollectionPageDTO dto) {
        if (isEmpty(dto)) {
            return null;
        }

        int startItem = ((dto.getCurrentPage() - 1) * dto.getPageSize()) + 1;
        int endItem = Math.min(dto.getCurrentPage() * dto.getPageSize(), dto.getTotalElements());

        return new int[]{startItem, endItem};
    }

    /**
     * Get display range as formatted string.
     */
    public static String getDisplayRangeText(ContentCollectionPageDTO dto) {
        int[] range = getDisplayRange(dto);
        if (range == null) {
            return "No items";
        }
        return String.format("Showing %d-%d of %d items", range[0], range[1], dto.getTotalElements());
    }

    // =============================================================================
    // CONTENT OPERATION HELPERS
    // =============================================================================

    /**
     * Check if an update DTO includes content operations.
     */
    public static boolean hasContentOperations(ContentCollectionUpdateDTO dto) {
        return (dto.getReorderOperations() != null && !dto.getReorderOperations().isEmpty()) ||
               (dto.getContentBlockIdsToRemove() != null && !dto.getContentBlockIdsToRemove().isEmpty()) ||
               (dto.getNewTextBlocks() != null && !dto.getNewTextBlocks().isEmpty()) ||
               (dto.getNewCodeBlocks() != null && !dto.getNewCodeBlocks().isEmpty());
    }

    // =============================================================================
    // CONTENT SUMMARY HELPERS
    // =============================================================================

    /**
     * Get total content blocks from page DTO content summary.
     */
    public static int getTotalContentBlocks(ContentCollectionPageDTO dto) {
        int total = 0;
        if (dto.getImageBlockCount() != null) total += dto.getImageBlockCount();
        if (dto.getTextBlockCount() != null) total += dto.getTextBlockCount();
        if (dto.getCodeBlockCount() != null) total += dto.getCodeBlockCount();
        if (dto.getGifBlockCount() != null) total += dto.getGifBlockCount();
        return total;
    }

    /**
     * Get content summary as formatted string.
     */
    public static String getContentSummary(ContentCollectionPageDTO dto) {
        StringBuilder summary = new StringBuilder();

        if (dto.getImageBlockCount() != null && dto.getImageBlockCount() > 0) {
            summary.append(dto.getImageBlockCount()).append(" images");
        }
        if (dto.getTextBlockCount() != null && dto.getTextBlockCount() > 0) {
            if (!summary.isEmpty()) summary.append(", ");
            summary.append(dto.getTextBlockCount()).append(" text blocks");
        }
        if (dto.getCodeBlockCount() != null && dto.getCodeBlockCount() > 0) {
            if (!summary.isEmpty()) summary.append(", ");
            summary.append(dto.getCodeBlockCount()).append(" code blocks");
        }
        if (dto.getGifBlockCount() != null && dto.getGifBlockCount() > 0) {
            if (!summary.isEmpty()) summary.append(", ");
            summary.append(dto.getGifBlockCount()).append(" gifs");
        }

        return !summary.isEmpty() ? summary.toString() : "No content";
    }
}
