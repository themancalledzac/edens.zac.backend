package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.types.TextFormType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Base class for content creation requests.
 * Extended by specific content type request classes (text, code, etc.).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class CreateTextContentRequest {

    @NotNull(message = "Collection ID is required")
    private Long collectionId;

    private String title;
    private String description;

    @NotBlank(message = "Text content is required")
    private String textContent;
    private TextFormType formType;
}
