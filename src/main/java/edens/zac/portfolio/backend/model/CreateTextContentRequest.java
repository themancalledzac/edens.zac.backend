package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.types.TextFormType;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating text content.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CreateTextContentRequest extends CreateContentRequest {

    @NotBlank(message = "Text content is required")
    private String textContent;

    private TextFormType formType;
}
