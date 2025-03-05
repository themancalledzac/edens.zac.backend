package edens.zac.portfolio.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Catalog Model
 * <p>
 * Object returned to the frontend.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class CatalogModel {
    private Long id;
    private String title;
    private String location;
    private Integer priority;
    private String coverImageUrl;
    private List<String> people;
    private List<String> tags;
    private String slug;
    private LocalDate date;
    private List<ImageModel> images;
}


