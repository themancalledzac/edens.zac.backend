package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.types.FilmFormat;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ContentImageModel extends ContentModel {

    private Integer imageWidth;
    private Integer imageHeight;
    private Integer iso;

    @Size(max = 100)
    private String author;
    private Integer rating;

    @Size(max = 15)
    private String fStop;
    private ContentLensModel lens;
    private Boolean blackAndWhite;
    private Boolean isFilm;
    private String filmType;
    private FilmFormat filmFormat;

    @Size(max = 20)
    private String shutterSpeed;

    private ContentCameraModel camera;

    @Size(max = 20)
    private String focalLength;

    @Size(max = 250)
    private String location;
    private String createDate;
    private List<ContentTagModel> tags;
    private List<ContentPersonModel> people;
    private List<ChildCollection> collections;
}
