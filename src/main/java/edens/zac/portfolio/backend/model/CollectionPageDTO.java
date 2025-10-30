package edens.zac.portfolio.backend.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.ToString;

import java.util.List;

/**
 * DTO for paginated content collection responses - extends base with full pagination metadata.
 * Used specifically for API endpoints that return collections with pagination.
 * Optimized for frontend consumption with all necessary pagination metadata.
 */
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)  // Add this line to include parent class fields in toString
@NoArgsConstructor
@AllArgsConstructor
@Data
@SuperBuilder
public class CollectionPageDTO extends CollectionBaseModel {

    // Enhanced pagination metadata (more detailed than ContentCollectionModel)
    @NotNull(message = "Current page is required")
    @Min(value = 1, message = "Current page must be 1 or greater")
    private Integer currentPage;

    @NotNull(message = "Page size is required")
    @Min(value = 1, message = "Page size must be 1 or greater")
    @Max(value = 100, message = "Page size cannot exceed 100")
    private Integer pageSize;

    @NotNull(message = "Total elements is required")
    @Min(value = 0, message = "Total elements must be 0 or greater")
    private Integer totalElements;

    @NotNull(message = "Total pages is required")
    @Min(value = 0, message = "Total pages must be 0 or greater")
    private Integer totalPages;

    @NotNull(message = "Has previous page flag is required")
    private Boolean hasPrevious;

    @NotNull(message = "Has next page flag is required")
    private Boolean hasNext;

    @NotNull(message = "Is first page flag is required")
    private Boolean isFirst;

    @NotNull(message = "Is last page flag is required")
    private Boolean isLast;

    // Navigation helpers
    private Integer previousPage;
    private Integer nextPage;

    // Content summary for SEO/preview
    private Integer imageBlockCount;
    private Integer textBlockCount;
    private Integer codeBlockCount;
    private Integer gifBlockCount;

    // Content blocks for current page
    @Valid
    @NotNull(message = "Content blocks list is required")
    private List<ContentModel> contentBlocks;
}