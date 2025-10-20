package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.types.FilmFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ImageContentBlockModel extends ContentBlockModel {

    @Size(max = 250)
    private String title;

    private Integer imageWidth;
    private Integer imageHeight;
    private Integer iso;

    @Size(max = 100)
    private String author;

    private Integer rating;

    @Size(max = 15)
    private String fStop;

    @Size(max = 100)
    private String lens;

    private Boolean blackAndWhite;
    private Boolean isFilm;

    // Film-specific metadata (only used when isFilm is true)
    // Film type for this image (display name)
    private String filmType;
    private FilmFormat filmFormat;

    @Size(max = 20)
    private String shutterSpeed;

    @Size(max = 25)
    private String camera;

    @Size(max = 20)
    private String focalLength;

    @Size(max = 250)
    private String location;

    private String imageUrlFullSize;

    @NotNull
    private String imageUrlWeb;

    private String createDate;

    // Tags associated with this image block
    private List<ContentTagModel> tags;

    // People tagged in this image block
    private List<ContentPersonModel> people;

    /**
     * All collections this image belongs to.
     * Since the same image (by fileIdentifier) can exist in multiple collections,
     * this array contains all collection relationships with their visibility and order settings.
     */
    private List<ImageCollection> collections;
}
