package edens.zac.portfolio.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class BlogCreateDTO {
    private String title;
    private String date;
    private String location;
    private String paragraph;
    private String author;
    private List<String> tags;
    private String slug;
    private String coverImageUrl;

    // handle actual image files separately
    private List<Long> existingImageIds;
}
