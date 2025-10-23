package edens.zac.portfolio.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Simplified tag model for use within content blocks.
 * Contains only the tag's ID and name, without related entity IDs.
 * This prevents over-fetching when tags are nested inside content block arrays.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentBlockTagModel {

    private Long id;
    private String name;
}
