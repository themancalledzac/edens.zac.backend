package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.types.TextFormType;
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
}
