package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.types.FilmFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for updating image content blocks.
 * All fields except 'id' are optional to support partial updates.
 * Only fields included in the request will be updated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageUpdateRequest {

    /**
     * The ID of the image to update (required)
     */
    @NotNull(message = "Image ID is required for updates")
    private Long id;

    /**
     * Image title
     */
    private String title;

    /**
     * Image rating (1-5)
     */
    private Integer rating;

    /**
     * Location where the image was taken
     */
    private String location;

    /**
     * Author/photographer name
     */
    private String author;

    /**
     * Whether the image was shot on film
     */
    private Boolean isFilm;

    /**
     * Film type ID to associate with this image (only applicable when isFilm is true)
     * This is used to select an existing film type from the database.
     */
    private Long filmTypeId;

    /**
     * New film type to create and associate with this image.
     * If both filmTypeId and newFilmType are provided, newFilmType takes precedence.
     */
    private NewFilmTypeRequest newFilmType;

    /**
     * Film format (35mm, 120, etc.) - required when isFilm is true
     */
    private FilmFormat filmFormat;

    /**
     * Whether the image is black and white
     */
    private Boolean blackAndWhite;

    /**
     * Camera name - will find existing or create new camera entity
     */
    private String cameraName;

    /**
     * Lens used
     */
    private String lens;

    /**
     * Focal length
     */
    private String focalLength;

    /**
     * F-stop/aperture
     */
    private String fStop;

    /**
     * Shutter speed
     */
    private String shutterSpeed;

    /**
     * ISO value
     */
    private Integer iso;

    /**
     * Date the image was created
     */
    private String createDate;

    /**
     * List of tag IDs to associate with this image (existing tags)
     */
    private List<Long> tagIds;

    /**
     * List of new tag names to create and associate with this image
     */
    private List<String> newTags;

    /**
     * List of person IDs to associate with this image (existing people)
     */
    private List<Long> personIds;

    /**
     * List of new person names to create and associate with this image
     */
    private List<String> newPeople;

    /**
     * List of collection updates - manages visibility of this image in different collections
     */
    private List<ImageCollection> collections;
}