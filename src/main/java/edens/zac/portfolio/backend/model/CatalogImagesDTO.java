package edens.zac.portfolio.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@Data
public class CatalogImagesDTO {
    private Long id;
    private String catalog;
    //    private String headerImage;
    private List<ImageModel> images;

    // Constructors, getters, and setters
    public CatalogImagesDTO(String catalog, List<ImageModel> images) { // String headerImage //
        this.catalog = catalog;
//        this.headerImage = headerImage;
        this.images = images;
    }
}
