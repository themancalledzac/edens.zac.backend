package edens.zac.portfolio.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class AdventureModalDTO {

    private Long id;
    private String name;
    private String imageMainTitle;
    private Boolean mainAdventure;

    // Constructor that matches the SELECT new statement in the repository query
    public AdventureModalDTO(Long id, String name, String imageMainTitle, Boolean mainAdventure) {
        this.id = id;
        this.name = name;
        this.imageMainTitle = imageMainTitle;
        this.mainAdventure = mainAdventure;
    }
}
