package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.types.CollectionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Minimal request for creating a new ContentCollection.
 * Only accepts the essentials: type and title.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class CollectionCreateRequest {

    @NotNull(message = "Type is required")
    private CollectionType type;

    @NotNull(message = "Title is required")
    @Size(min = 3, max = 100, message = "Title must be between 3 and 100 characters")
    private String title;
}
