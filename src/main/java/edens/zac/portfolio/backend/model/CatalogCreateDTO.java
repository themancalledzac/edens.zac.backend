package edens.zac.portfolio.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class CatalogCreateDTO {
    private String title;
    private String location; // Can be null
    private Integer priority; // 1, 2, or 3
    private String coverImageUrl;
    private List<String> people;
    private List<String> tags;
    private String slug; // likely null, will be auto generated from title if so.

    // handle actual image files separately
    private List<Long> existingImageIds;
}
