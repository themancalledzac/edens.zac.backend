package edens.zac.portfolio.backend.model;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@Builder
public class AdventureModel {

    private Long id;
    private String name;
    private ImageModel imageMain;

    public AdventureModel(Long id, String name, ImageModel imageMain) {
        this.id = id;
        this.name = name;
        this.imageMain = imageMain;
    }
}
