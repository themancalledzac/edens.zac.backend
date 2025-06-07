package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.types.CollectionType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Base model containing common fields shared across all ContentCollection DTOs.
 * This eliminates duplication between ContentCollectionModel, ContentCollectionPageDTO,
 * ContentCollectionCreateDTO, and ContentCollectionUpdateDTO.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@SuperBuilder // Lombok's @SuperBuilder enables inheritance-friendly builders
public abstract class ContentCollectionBaseModel {

    private Long id;

    private CollectionType type;

    @Size(min = 3, max = 100, message = "Title must be between 3 and 100 characters")
    private String title;

    @Size(min = 3, max = 150, message = "Slug must be between 3 and 150 characters")
    private String slug;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    @Size(max = 255, message = "Location cannot exceed 255 characters")
    private String location;

    private LocalDateTime collectionDate;

    private Boolean visible;

    @Min(value = 1, message = "Priority must be between 1 and 4 (1 = best, 4 = worst)")
    @Max(value = 4, message = "Priority must be between 1 and 4 (1 = best, 4 = worst)")
    private Integer priority;

    // Cover image URL for the collection
    private String coverImageUrl;

    // Client gallery security fields
    private Boolean isPasswordProtected;
    private Boolean hasAccess;

    // Type-specific configuration (stored as JSON in database)
    private String configJson;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}