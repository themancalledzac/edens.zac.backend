package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.model.*;
import edens.zac.portfolio.backend.types.CollectionType;
import org.springframework.stereotype.Component;

@Component
public class ContentCollectionProcessingUtil {
    
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
            default -> false;
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
            if (summary.length() > 0) summary.append(", ");
            summary.append(dto.getTextBlockCount()).append(" text blocks");
        }
        if (dto.getCodeBlockCount() != null && dto.getCodeBlockCount() > 0) {
            if (summary.length() > 0) summary.append(", ");
            summary.append(dto.getCodeBlockCount()).append(" code blocks");
        }
        if (dto.getGifBlockCount() != null && dto.getGifBlockCount() > 0) {
            if (summary.length() > 0) summary.append(", ");
            summary.append(dto.getGifBlockCount()).append(" gifs");
        }
        
        return summary.length() > 0 ? summary.toString() : "No content";
    }
}