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
 * Collection Model - extends base with pagination and content.
 * Object returned to the frontend for collections.
 * Includes pagination metadata and content.
 */
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)  // Add this line
@NoArgsConstructor
@AllArgsConstructor
@Data
@SuperBuilder
public class CollectionModel extends CollectionBaseModel {

    // Pagination metadata (specific to this model)
    @Min(value = DefaultValues.default_content_per_page, message = "Content per page must be 30 or greater")
    private Integer contentPerPage;

    @Min(value = 0, message = "Total content must be 0 or greater")
    private Integer contentCount;

    @Min(value = 1, message = "Current page must be 1 or greater")
    private Integer currentPage;

    @Min(value = 0, message = "Total pages must be 0 or greater")
    private Integer totalPages;

    // Cover image for the collection (full ContentImageModel when set)
    @Valid
    private ContentImageModel coverImage;

    // Tags associated with this collection
    private List<String> tags;

    // Content (paginated)
    @Valid
    private List<ContentModel> content;
}