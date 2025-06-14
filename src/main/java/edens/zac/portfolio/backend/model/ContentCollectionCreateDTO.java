package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.types.CollectionType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * DTO for creating new content collections - extends base with creation-specific validations.
 * Includes required field validations and password handling for client galleries.
 */
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@SuperBuilder
public class ContentCollectionCreateDTO extends ContentCollectionBaseModel {

    // Override base fields with @NotNull for creation requirements
    @NotNull(message = "Collection type is required")
    private CollectionType type;

    @NotBlank(message = "Title is required")
    @Size(min = 3, max = 100, message = "Title must be between 3 and 100 characters")
    private String title;

    @NotNull(message = "Visibility is required")
    private Boolean visible;

    // Password field for client galleries (raw password, will be hashed)
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;

    // Pagination settings (optional, defaults will be applied in service)
    @Min(value = 1, message = "Blocks per page must be 1 or greater")
    private Integer blocksPerPage;

    // Initial content (processed separately in service layer)
    private List<String> initialTextBlocks;
    private List<String> initialCodeBlocks;
    // Note: Images/GIFs handled via multipart upload
}