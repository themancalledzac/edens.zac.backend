package edens.zac.portfolio.backend.model;

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
public abstract class CreateContentRequest {
    private String title;
    private String description;
}
