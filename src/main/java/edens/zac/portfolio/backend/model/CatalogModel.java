package edens.zac.portfolio.backend.model;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@Builder
public class CatalogModel {

    private Long id;
    private String name;
    private ImageModel imageMain;

    public CatalogModel(Long id, String name, ImageModel imageMain) {
        this.id = id;
        this.name = name;
        this.imageMain = imageMain;
    }
}

// TODO:
//  1. Update CatalogModel to include the following:
//    a. Location
//    b. Date(s)
//    c. People
//    d. Tags
//  2. Tags Model / Entity / Table
//    a. Need Tags that are just 'general', such as;
//    b. `['Hiking', 'People', 'Music', 'Night', 'Bright', 'Moody', 'Washington', 'Europe']`
//    c. Can be any type of datapoint for the catalog.
