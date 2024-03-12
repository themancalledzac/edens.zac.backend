package edens.zac.portfolio.backend.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
@Data
public class Image {

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
    private String shutterSpeed;
    private String rawFileName;
    private String camera;
    private String focalLength;
    private String location;
    private String imageUrlLarge;
    private String imageUrlSmall;
    private String imageUrlRaw;
    private List<String> adventure;
    private String createDate;
    private LocalDateTime updateDate;
}
