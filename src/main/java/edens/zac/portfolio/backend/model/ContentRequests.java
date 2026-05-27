package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.types.FilmFormat;
import edens.zac.portfolio.backend.types.TextFormType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Consolidated request DTOs for Content operations. Uses Java records for immutability. */
public final class ContentRequests {
  private ContentRequests() {}

  /** Request for creating a new tag. */
  public record CreateTag(
      @NotBlank(message = "Tag name is required") @Size(min = 1, max = 50, message = "Tag name must be between 1 and 50 characters") String tagName) {}

  /** Request for creating a new camera. */
  public record CreateCamera(
      @NotBlank(message = "Camera name is required") @Size(min = 1, max = 100, message = "Camera name must be between 1 and 100 characters") String cameraName,
      String bodySerialNumber,
      Boolean isFilm,
      FilmFormat defaultFilmFormat) {}

  /** Request for creating a new person. */
  public record CreatePerson(
      @NotBlank(message = "Person name is required") @Size(min = 1, max = 100, message = "Person name must be between 1 and 100 characters") String personName) {}

  /**
   * Request for creating a new film type. Used when updating an image and the user wants to create
   * a new film type on the fly.
   *
   * <p>The technical name will be auto-generated from filmTypeName (e.g., "Kodak Portra 400" ->
   * "KODAK_PORTRA_400").
   */
  public record NewFilmType(
      @NotBlank(message = "Film type name is required") @Size(min = 1, max = 100, message = "Film type name must be between 1 and 100 characters") String filmTypeName,
      @NotNull(message = "Default ISO is required") @Positive(message = "Default ISO must be a positive integer") Integer defaultIso) {}

  /** Request for creating text or code content within a collection. */
  public record CreateTextContent(
      @NotNull(message = "Collection ID is required") Long collectionId,
      String title,
      String description,
      @NotBlank(message = "Text content is required") String textContent,
      TextFormType formType) {}

  /**
   * Request for updating an existing GIF/MP4 content block. All fields optional — only non-null
   * fields are applied. Mirrors the slice of {@code ContentImageUpdateRequest} that makes sense for
   * animated content (no EXIF/equipment fields).
   *
   * <p>{@code tags} and {@code collections} use the prev/newValue/remove pattern from {@link
   * CollectionRequests} so a single request can add, remove, and re-order memberships in one shot.
   */
  public record UpdateGif(
      @Size(max = 200, message = "Title must be 200 characters or less") String title,
      @Min(value = 0, message = "Rating must be between 0 and 5") @Max(value = 5, message = "Rating must be between 0 and 5") Integer rating,
      CollectionRequests.TagUpdate tags,
      CollectionRequests.CollectionUpdate collections) {}
}
