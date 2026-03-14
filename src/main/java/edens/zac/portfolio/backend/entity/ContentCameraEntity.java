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
 * Entity representing a camera model. Cameras can be associated with ContentImages. New cameras are
 * automatically created when an image is updated with a new camera name.
 *
 * <p>Database table: content_cameras Indexes: idx_content_camera_name (camera_name, unique)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentCameraEntity {

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ContentCameraEntity that)) return false;
    return cameraName != null && cameraName.equals(that.cameraName);
  }

  @Override
  public int hashCode() {
    return cameraName != null ? cameraName.hashCode() : 0;
  }

  /** Column: id (BIGINT, PRIMARY KEY, auto-generated) */
  private Long id;

  /** Column: camera_name (VARCHAR(100), NOT NULL, UNIQUE) */
  @NotBlank @Size(min = 1, max = 100) private String cameraName;

  /** Column: body_serial_number (VARCHAR) - Camera body serial number */
  private String bodySerialNumber;

  /** Column: created_at (TIMESTAMP, NOT NULL) */
  private LocalDateTime createdAt;

  // One-to-many relationship with ContentImages (mappedBy side)
  @Builder.Default private Set<ContentImageEntity> contentImages = new HashSet<>();

  /**
   * Constructor for creating a camera with just a name. Useful for quick camera creation during
   * image updates.
   *
   * @param cameraName The name of the camera
   */
  public ContentCameraEntity(String cameraName) {
    this.cameraName = cameraName;
    this.contentImages = new HashSet<>();
  }

  /**
   * Get the number of images using this camera.
   *
   * @return The number of images associated with this camera
   */
  public int getImageCount() {
    return contentImages.size();
  }
}
