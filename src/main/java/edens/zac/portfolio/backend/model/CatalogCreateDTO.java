package edens.zac.portfolio.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class CatalogCreateDTO {
    private String title;
    private String location; // Can be null
    private Integer priority; // 1 | 2 | 3 | 4 - 1 being 'best', 4 worst
    private String coverImageUrl;
    private List<String> people;
    private List<String> tags;
    private Boolean createHomeCard; // indicate if create homeCard or not

    // handle actual image files separately
    private List<Long> existingImageIds;
}


//"{"title":"Enchantments 2021","location":"Enchantments Zone, Washington USA","priority":"2","createHomeCard":"true"}"