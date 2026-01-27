package edens.zac.portfolio.backend.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a camera lens model. Lenses can be associated with ContentImages. New lenses
 * are automatically created when an image is updated with a new lens name.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentLensEntity {

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ContentLensEntity that)) return false;
    return lensName != null && lensName.equals(that.lensName);
  }

  @Override
  public int hashCode() {
    return lensName != null ? lensName.hashCode() : 0;
  }

  private Long id;

  @NotBlank
  @Size(min = 1, max = 100)
  private String lensName;

  /** Column: lens_serial_number (VARCHAR) - Lens serial number */
  private String lensSerialNumber;

  private LocalDateTime createdAt;

  // One-to-many relationship with ContentImages (mappedBy side)
  @Builder.Default private Set<ContentImageEntity> contentImages = new HashSet<>();

  /**
   * Constructor for creating a lens with just a name. Useful for quick lens creation during image
   * updates.
   *
   * @param lensName The name of the lens
   */
  public ContentLensEntity(String lensName) {
    this.lensName = lensName;
    this.contentImages = new HashSet<>();
  }

  /**
   * Get the number of images using this lens.
   *
   * @return The number of images associated with this lens
   */
  public int getImageCount() {
    return contentImages.size();
  }
}
