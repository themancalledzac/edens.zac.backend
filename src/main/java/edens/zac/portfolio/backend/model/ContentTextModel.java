package edens.zac.portfolio.backend.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ContentTextModel extends ContentModel {

    // Note: title is inherited from ContentModel

    @NotBlank
    @Size(max = 10000)
    private String content;

    // TODO: Remove formatType, as text is text, and code would be markdown/html
    @NotNull
    @Size(max = 20)
    private String formatType; // "markdown", "html", "plain"
}
