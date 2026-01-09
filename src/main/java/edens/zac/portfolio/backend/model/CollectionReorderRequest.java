package edens.zac.portfolio.backend.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request for reordering content within a collection.
 * Allows multiple content items to be reordered in a single atomic operation.
 * Works with any content type: IMAGE, TEXT, GIF, COLLECTION.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class CollectionReorderRequest {

    @NotEmpty(message = "At least one reorder item is required")
    @Valid
    private List<ReorderItem> reorders;

    /**
     * Represents a single content reorder operation.
     */
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class ReorderItem {

        /**
         * The content ID (from content[].id in the collection response).
         * This is the content table ID, consistent across all content types.
         */
        @NotNull(message = "Content ID is required")
        private Long contentId;

        @NotNull(message = "New order index is required")
        private Integer newOrderIndex;
    }
}

