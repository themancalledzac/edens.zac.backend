package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.config.DefaultValues;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * ContentCollection Model - extends base with pagination and content blocks.
 * Object returned to the frontend for content collections.
 * Includes pagination metadata and content blocks.
 */
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)  // Add this line
@NoArgsConstructor
@AllArgsConstructor
@Data
@SuperBuilder
public class ContentCollectionModel extends ContentCollectionBaseModel {

    // Pagination metadata (specific to this model)
    @Min(value = DefaultValues.default_blocks_per_page, message = "Blocks per page must be 30 or greater")
    private Integer blocksPerPage;

    @Min(value = 1, message = "Total blocks must be 1 or greater")
    private Integer totalBlocks;

    @Min(value = 1, message = "Current page must be 1 or greater")
    private Integer currentPage;

    @Min(value = 1, message = "Total pages must be 1 or greater")
    private Integer totalPages;

    // Cover image for the collection (full ImageContentBlockModel when set)
    @Valid
    private ImageContentBlockModel coverImage;

    // Home page card settings
    private Boolean homeCardEnabled;
    private String homeCardText;

    // Content blocks (paginated)
    @Valid
    private List<ContentBlockModel> contentBlocks;
}