package edens.zac.portfolio.backend.model;

import java.util.List;
import java.util.Objects;

/**
 * Response DTO for the general metadata endpoint. Contains all metadata without a specific
 * collection: tags, people, cameras, lenses, film types, film formats, and collections.
 */
public record GeneralMetadataDTO(
    /** All available tags that can be assigned to content blocks */
    List<Records.Tag> tags,
    /** All available people that can be tagged in content blocks */
    List<Records.Person> people,
    /** All available locations that can be assigned to content blocks and collections */
    List<Records.Location> locations,
    /** All available collections in the system */
    List<Records.CollectionList> collections,
    /** All available cameras for film photography metadata */
    List<Records.Camera> cameras,
    /** All available lenses for film photography metadata */
    List<Records.Lens> lenses,
    /** All available film types with their metadata (display name, default ISO) */
    List<ContentFilmTypeModel> filmTypes,
    /** All available film formats (35mm, 120, etc.) */
    List<Records.FilmFormat> filmFormats) {

  public GeneralMetadataDTO {
    tags = Objects.requireNonNullElse(tags, List.of());
    people = Objects.requireNonNullElse(people, List.of());
    locations = Objects.requireNonNullElse(locations, List.of());
    collections = Objects.requireNonNullElse(collections, List.of());
    cameras = Objects.requireNonNullElse(cameras, List.of());
    lenses = Objects.requireNonNullElse(lenses, List.of());
    filmTypes = Objects.requireNonNullElse(filmTypes, List.of());
    filmFormats = Objects.requireNonNullElse(filmFormats, List.of());
  }
}
