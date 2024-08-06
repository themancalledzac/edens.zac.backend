package edens.zac.portfolio.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 *
 */
@NoArgsConstructor
@Data
public class CatalogModalDTO {

    private Long id;
    private String name;
    private String imageMainTitle;
    private Boolean mainCatalog;
    private Long priority;

    // Constructor that matches the SELECT new statement in the repository query
    public CatalogModalDTO(Long id, String name, String imageMainTitle, Boolean mainCatalog, Long priority) {
        this.id = id;
        this.name = name;
        this.imageMainTitle = imageMainTitle;
        this.mainCatalog = mainCatalog;
        this.priority = priority;
    }
}
