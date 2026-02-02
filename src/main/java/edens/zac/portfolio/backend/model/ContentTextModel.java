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

  @NotBlank @Size(max = 10000) private String textContent;

  @NotNull @Size(max = 20) private String
      formatType; // "markdown", "html", "plain", "js", "py", "sql", "java", "ts", "tf", "yml"
}
