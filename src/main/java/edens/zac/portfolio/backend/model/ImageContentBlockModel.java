package edens.zac.portfolio.backend.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

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

    @Size(max = 20)
    private String shutterSpeed;

    @Size(max = 100)
    private String rawFileName;

    @Size(max = 25)
    private String camera;

    @Size(max = 20)
    private String focalLength;

    @Size(max = 250)
    private String location;

    @NotNull
    private String imageUrlWeb;

    private String createDate;
}
