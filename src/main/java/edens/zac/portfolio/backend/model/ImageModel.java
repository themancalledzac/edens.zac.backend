package edens.zac.portfolio.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@NoArgsConstructor
@Data
public class ImageModel {

    private Long id;
    private String title;
    private Integer imageWidth;
    private Integer imageHeight;
    private Integer iso;
    private String author;
    private Integer rating;
    private String fStop;
    private String lens;
    private Boolean blackAndWhite;
    private Boolean isFilm;
    private String shutterSpeed;
    private String rawFileName;
    private String camera;
    private String focalLength;
    private String location;
    private String imageUrlWeb;
    private String imageUrlSmall;
    private String imageUrlRaw;
    private List<String> catalog;
    private List<String> tags;
    private String createDate;
    private LocalDateTime updateDate;

    // Public getters and setters
    public void setTitle(String title) {
        this.title = title;
    }
}
