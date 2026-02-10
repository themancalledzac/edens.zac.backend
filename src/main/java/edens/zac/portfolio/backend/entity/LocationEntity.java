package edens.zac.portfolio.backend.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a location. Locations can be associated with Collections and ContentImages.
 *
 * <p>Database table: location Columns: - id (BIGINT, PRIMARY KEY, auto-generated) - location_name
 * (VARCHAR 255, NOT NULL, UNIQUE) - created_at (TIMESTAMP, NOT NULL)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationEntity {

  private Long id;

  @NotBlank @Size(min = 1, max = 255) private String locationName;

  private LocalDateTime createdAt;

  /**
   * Constructor for creating a location with just a name.
   *
   * @param locationName The name of the location
   */
  public LocationEntity(String locationName) {
    this.locationName = locationName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LocationEntity that)) return false;
    return locationName != null && locationName.equals(that.locationName);
  }

  @Override
  public int hashCode() {
    return locationName != null ? locationName.hashCode() : 0;
  }
}
