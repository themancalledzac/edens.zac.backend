package edens.zac.portfolio.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model representing a location for API responses.
 * Contains the location's ID and name.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationModel {

    private Long id;
    private String name;
}
