package edens.zac.portfolio.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
public class CatalogUpdateDTO {
    private Long id;
    private String title;
    private String location;
    private Integer priority;
    private String coverImageUrl;
    private List<String> people;
    private List<String> tags;
    private String slug;
    private LocalDate date;
    private Boolean updateHomeCard;

    // Image Handling
    private List<ImageModel> images;
    private List<Long> imagesToRemove;
}
