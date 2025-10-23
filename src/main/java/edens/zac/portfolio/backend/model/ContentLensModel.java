package edens.zac.portfolio.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model representing a lens for API responses.
 * Contains the lens's ID and name.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentLensModel {

    private Long id;
    private String name;
}
