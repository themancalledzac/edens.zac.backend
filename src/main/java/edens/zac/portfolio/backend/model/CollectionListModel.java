package edens.zac.portfolio.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Model representing a collection for API responses. Contains the collection's ID and name. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionListModel {

  private Long id;
  private String name;
}
