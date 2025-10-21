package edens.zac.portfolio.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Simplified person model for use within content blocks.
 * Contains only the person's ID and name, without related entity IDs.
 * This prevents over-fetching when people are nested inside content block arrays.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentBlockPersonModel {

    private Long id;
    private String personName;
}
