package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.types.FilmFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating image content blocks. All fields except 'id' are optional to support
 * partial updates. Only fields included in the request will be updated. Uses a prev/new/remove
 * pattern for entity relationships: - prev: Reference to existing entity by ID - newValue: Create
 * new entity (by name or request object) - remove: Remove the association (for single entities) or
 * remove specific IDs (for collections)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentImageUpdateRequest {

  /** The ID of the image to update (required) */
  @NotNull(message = "Image ID is required for updates") private Long id;

  /** Image title */
  private String title;

  /** Image rating (1-5) */
  private Integer rating;

  /** Location where the image was taken */
  private String location;

  /** Author/photographer name */
  private String author;

  /** Whether the image was shot on film */
  private Boolean isFilm;

  /** Film format (35mm, 120, etc.) - required when isFilm is true */
  private FilmFormat filmFormat;

  /** Whether the image is black and white */
  private Boolean blackAndWhite;

  /** Focal length */
  private String focalLength;

  /** F-stop/aperture */
  private String fStop;

  /** Shutter speed */
  private String shutterSpeed;

  /** ISO value */
  private Integer iso;

  /** Date the image was created */
  private String createDate;

  /** Camera update using prev/new/remove pattern */
  private CameraUpdate camera;

  /** Lens update using prev/new/remove pattern */
  private LensUpdate lens;

  /** Film type update using prev/new/remove pattern */
  private FilmTypeUpdate filmType;

  /** Tag updates using prev/new/remove pattern */
  private TagUpdate tags;

  /** Person updates using prev/new/remove pattern */
  private PersonUpdate people;

  /** Collection updates using prev/new/remove pattern */
  private CollectionUpdate collections;

  // ========== Nested Update Classes ==========

  /**
   * Camera update wrapper (all fields optional) - prev: ID of existing camera to use - newValue:
   * Name of new camera to create - remove: true to remove camera association
   *
   * <p>Examples: - {prev: 5} = Use existing camera ID 5 - {newValue: "X100V"} = Create new camera
   * "X100V" - {remove: true} = Remove camera association
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CameraUpdate {
    private Long prev;
    private String newValue;
    private Boolean remove;
  }

  /**
   * Lens update wrapper (all fields optional) - prev: ID of existing lens to use - newValue: Name
   * of new lens to create - remove: true to remove lens association
   *
   * <p>Examples: - {prev: 3} = Use existing lens ID 3 - {newValue: "50mm f/1.8"} = Create new lens
   * "50mm f/1.8" - {remove: true} = Remove lens association
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class LensUpdate {
    private Long prev;
    private String newValue;
    private Boolean remove;
  }

  /**
   * Film type update wrapper (all fields optional) - prev: ID of existing film type to use -
   * newValue: Film type request to create new type - remove: true to remove film type association
   *
   * <p>Examples: - {prev: 2} = Use existing film type ID 2 - {newValue: {filmTypeName: "Portra
   * 400", defaultIso: 400}} = Create new film type - {remove: true} = Remove film type association
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FilmTypeUpdate {
    private Long prev;
    private NewFilmTypeRequest newValue;
    private Boolean remove;
  }
}
