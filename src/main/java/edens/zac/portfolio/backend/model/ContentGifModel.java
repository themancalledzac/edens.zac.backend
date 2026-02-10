package edens.zac.portfolio.backend.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ContentGifModel extends ContentModel {

  // Note: title is inherited from ContentModel

  @NotNull private String gifUrl;

  @Size(max = 255) private String thumbnailUrl;

  private Integer width;
  private Integer height;

  @Size(max = 100) private String author;

  private String createDate;

  // Tags associated with this gif content
  private List<Records.Tag> tags;
}
