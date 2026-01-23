package edens.zac.portfolio.backend.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new film type. Used when updating an image and the user wants to
 * create a new film type on the fly.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewFilmTypeRequest {

  /**
   * The human-readable display name (e.g., "Kodak Portra 400") The technical name will be
   * auto-generated from this (e.g., "KODAK_PORTRA_400")
   */
  @NotBlank(message = "Film type name is required")
  @Size(min = 1, max = 100, message = "Film type name must be between 1 and 100 characters")
  private String filmTypeName;

  /** The default ISO value for this film stock */
  @NotNull(message = "Default ISO is required") @Positive(message = "Default ISO must be a positive integer") private Integer defaultIso;
}
