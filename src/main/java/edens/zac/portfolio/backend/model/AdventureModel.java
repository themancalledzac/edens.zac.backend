package edens.zac.portfolio.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class AdventureModel {

    private Long id;
    private String name;
    private ImageModel imageMain;
}
