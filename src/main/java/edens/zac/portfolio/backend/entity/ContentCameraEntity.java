package edens.zac.portfolio.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Entity representing a camera model.
 * Cameras can be associated with ContentImages.
 * New cameras are automatically created when an image is updated with a new camera name.
 */
@Entity
@Table(
        name = "content_cameras",
        indexes = {
                @Index(name = "idx_content_camera_name", columnList = "camera_name", unique = true)
        }
)
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(min = 1, max = 100)
    @Column(name = "camera_name", unique = true, nullable = false, length = 100)
    private String cameraName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // One-to-many relationship with ContentImages (mappedBy side)
    @OneToMany(mappedBy = "camera", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<ContentImageEntity> contentImages = new HashSet<>();

    /**
     * Constructor for creating a camera with just a name.
     * Useful for quick camera creation during image updates.
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
