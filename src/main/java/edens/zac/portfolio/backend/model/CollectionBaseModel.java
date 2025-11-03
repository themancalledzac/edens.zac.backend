package edens.zac.portfolio.backend.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import edens.zac.portfolio.backend.types.CollectionType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Base model containing common fields shared across all Collection DTOs.
 * This eliminates duplication between CollectionModel, CollectionPageDTO,
 * and CollectionUpdateRequest.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@SuperBuilder // Lombok's @SuperBuilder enables inheritance-friendly builders
public abstract class CollectionBaseModel {

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

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate collectionDate;

    private Boolean visible;

    // Display mode for ordering content in the collection
    // chronological: by time; ordered: by explicit orderIndex
    public enum DisplayMode { CHRONOLOGICAL, ORDERED }
    private DisplayMode displayMode;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}