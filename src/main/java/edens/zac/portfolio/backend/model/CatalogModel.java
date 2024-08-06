package edens.zac.portfolio.backend.model;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Catalog Model
 * <p>
 * Object returned to the frontend.
 */
@NoArgsConstructor
@Data
@Builder
public class CatalogModel {

    private Long id;
    private String name;
    private ImageModel imageMain;
    private String location;
    private List<String> people;
    private List<String> tags;
    private Long priority; //specifies order when used as a Main Catalog

    public CatalogModel(Long id, String name, ImageModel imageMain, String location, List<String> people, List<String> tags, Long priority) {
        this.id = id;
        this.name = name;
        this.imageMain = imageMain;
        this.location = location;
        this.people = people;
        this.tags = tags;
        this.priority = priority;
    }

    public CatalogModel(Long id, String name, ImageModel imageMain, Long priority) {
        this.id = id;
        this.name = name;
        this.imageMain = imageMain;
        this.priority = priority;
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
