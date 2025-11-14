package edens.zac.portfolio.backend.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request for reordering images within a collection.
 * Allows multiple images to be reordered in a single atomic operation.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class CollectionReorderRequest {

    @NotEmpty(message = "At least one reorder item is required")
    @Valid
    private List<ReorderItem> reorders;

    /**
     * Represents a single image reorder operation.
     */
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class ReorderItem {

        @NotNull(message = "Image ID is required")
        private Long imageId;

        @NotNull(message = "New order index is required")
        private Integer newOrderIndex;
    }
}

