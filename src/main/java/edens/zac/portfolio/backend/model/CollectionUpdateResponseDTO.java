package edens.zac.portfolio.backend.model;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for the collection update/manage endpoint. Contains the collection along with all
 * metadata needed for the update UI. Uses composition with GeneralMetadataDTO to avoid field
 * duplication.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionUpdateResponseDTO {

  /** The content collection with all its data */
  private CollectionModel collection;

  /**
   * General metadata including tags, people, cameras, lenses, film types, film formats, and
   * collections. This is unwrapped during JSON serialization to maintain backwards compatibility
   * with the API.
   */
  @JsonUnwrapped private GeneralMetadataDTO metadata;
}
