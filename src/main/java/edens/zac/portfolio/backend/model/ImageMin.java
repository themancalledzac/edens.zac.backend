package edens.zac.portfolio.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class ImageMin {

    private Long id;
    private String title;
    private Integer imageWidth;
    private Integer imageHeight;
    // private String imageUrlLarge;
}
