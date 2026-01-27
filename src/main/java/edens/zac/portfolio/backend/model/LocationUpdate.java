package edens.zac.portfolio.backend.model;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Location update wrapper using prev/new/remove pattern. All fields are optional to support partial
 * updates.
 *
 * <p>- prev: ID of existing location to use - newValue: Name of new location to create - remove:
 * true to remove location association
 *
 * <p>Examples: - {prev: 5} = Use existing location ID 5 - {newValue: "New York"} = Create new
 * location "New York" - {remove: true} = Remove location association
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationUpdate {
  private Long prev;

  @Size(max = 255, message = "Location cannot exceed 255 characters")
  private String newValue;

  private Boolean remove;
}
