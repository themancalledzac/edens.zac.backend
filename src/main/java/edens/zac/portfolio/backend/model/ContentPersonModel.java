package edens.zac.portfolio.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Model representing a person for API responses. Contains the person's ID and name. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentPersonModel {

  private Long id;
  private String name;
}
