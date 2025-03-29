package edens.zac.portfolio.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// TODO: Update to include optional Date ( in 2022.02.22 format )
@Data
@NoArgsConstructor
public class BlogCreateDTO {
    private String title;
    private String location;
    private Integer priority; // 1 | 2 | 3 | 4 - 1 being 'best', 4 worst
    private String paragraph;
    private String author;
    private List<String> tags;
    private String coverImageUrl;
    private Boolean createHomeCard; // Indicate if we create associated home card

    // handle actual image files separately
    private List<Long> existingImageIds;
}


//{"title":"Film Drop 001","location":"Seattle, Wa","priority":"1","paragraph":"Our first film drop. What more is there to say.","author":"Zac Edens","createHomeCard":"true"};