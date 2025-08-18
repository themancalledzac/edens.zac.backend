package edens.zac.portfolio.backend.model;

import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;
import jakarta.validation.Valid;

/**
 * DTO for updating existing content collections - extends base with partial update support.
 * Supports partial updates - all fields are optional except for validation constraints.
 * Content block operations are handled separately in service layer.
 */
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)  // Add this line to include parent class fields in toString
@NoArgsConstructor
@AllArgsConstructor
@Data
@SuperBuilder
public class ContentCollectionUpdateDTO extends ContentCollectionBaseModel {

    // Password handling for client galleries
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password; // Raw password, will be hashed (null = no change)

    // Pagination settings
    @Min(value = 1, message = "Blocks per page must be 1 or greater")
    private Integer blocksPerPage;

    // Content block operations (processed separately in service layer)
    @Valid  // Add this annotation to enable nested validation
    private List<ContentBlockReorderOperation> reorderOperations;
    private List<Long> contentBlockIdsToRemove;
    private List<String> newTextBlocks;
    private List<String> newCodeBlocks;

    // Optional insertion index for newTextBlocks (if null, new text blocks are appended at the end)
    @Min(value = 0, message = "Insertion index must be 0 or greater")
    private Integer newTextBlocksInsertAt; // TODO: WHY is this needed? seems not needed

    /**
     * Inner class for content block reordering operations.
     */
    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContentBlockReorderOperation {
        /**
         * Identifier of the block to move. Optional:
         * - Positive ID: refers to an existing block in the collection.
         * - Negative ID: placeholder mapping for newly added text blocks in this request.
         *   Use -1 for the first newTextBlocks entry, -2 for the second, etc.
         * - Null: when null, the block will be resolved by oldOrderIndex.
         */
        private Long contentBlockId;

        /**
         * The original order index of the block prior to reordering. Used when contentBlockId is null
         * or to double-check position in conflict scenarios.
         */
        @Min(value = 0, message = "Old order index must be 0 or greater")
        private Integer oldOrderIndex;

        @Min(value = 0, message = "New order index must be 0 or greater")
        private Integer newOrderIndex;

        /**
         * Backward-compatible convenience constructor used by existing tests: only ID and new index.
         */
        public ContentBlockReorderOperation(Long contentBlockId, Integer newOrderIndex) {
            this.contentBlockId = contentBlockId;
            this.newOrderIndex = newOrderIndex;
            this.oldOrderIndex = null;
        }
    }
}