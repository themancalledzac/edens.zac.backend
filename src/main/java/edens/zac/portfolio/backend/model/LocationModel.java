package edens.zac.portfolio.backend.model;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model representing a location for API responses. Contains the location's ID
 * and name.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationModel {

  private Long id;

  @Size(max = 255, message = "Location cannot exceed 255 characters")
  private String name;
}
