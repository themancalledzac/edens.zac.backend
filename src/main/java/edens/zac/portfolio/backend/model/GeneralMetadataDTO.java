package edens.zac.portfolio.backend.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for the general metadata endpoint. Contains all metadata without a specific
 * collection: tags, people, cameras, lenses, film types, film formats, and collections.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneralMetadataDTO {

  /** All available tags that can be assigned to content blocks */
  private List<Records.Tag> tags;

  /** All available people that can be tagged in content blocks */
  private List<Records.Person> people;

  /** All available locations that can be assigned to content blocks and collections */
  private List<Records.Location> locations;

  /** All available collections in the system */
  private List<Records.CollectionList> collections;

  /** All available cameras for film photography metadata */
  private List<Records.Camera> cameras;

  /** All available lenses for film photography metadata */
  private List<Records.Lens> lenses;

  /** All available film types with their metadata (display name, default ISO) */
  private List<ContentFilmTypeModel> filmTypes;

  /** All available film formats (35mm, 120, etc.) */
  private List<Records.FilmFormat> filmFormats;
}
