package edens.zac.portfolio.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model representing a content tag for API responses.
 * Contains the tag's ID and name.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentTagModel {

    private Long id;
    private String name;
}