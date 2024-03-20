package edens.zac.portfolio.backend.model;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@Data
public class AdventureImagesDTO {
    private Long id;
    private String adventure;
    //    private String headerImage;
    private List<ImageModel> images;

    // Constructors, getters, and setters
    public AdventureImagesDTO(String adventure, List<ImageModel> images) { // String headerImage //
        this.adventure = adventure;
//        this.headerImage = headerImage;
        this.images = images;
    }
}
