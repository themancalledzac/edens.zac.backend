package edens.zac.portfolio.backend.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class GifContentBlockModel extends ContentBlockModel {
    
    @Size(max = 255)
    private String title;
    
    @NotNull
    private String gifUrl;
    
    @Size(max = 255)
    private String thumbnailUrl;
    
    private Integer width;
    private Integer height;
    
    @Size(max = 100)
    private String author;

    private String createDate;

    // Tags associated with this gif block
    private List<String> tags;
}