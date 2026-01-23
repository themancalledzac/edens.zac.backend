package edens.zac.portfolio.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Model representing a camera for API responses. Contains the camera's ID and name. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentCameraModel {

  private Long id;
  private String name;
}
