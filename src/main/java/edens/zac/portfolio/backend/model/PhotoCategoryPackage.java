package edens.zac.portfolio.backend.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class PhotoCategoryPackage {

    private String title;
    private List<String> photoList;
}
