package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.types.FilmFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ContentImageModel extends ContentModel {

  private Integer imageWidth;
  private Integer imageHeight;
  private Integer iso;

  @Size(max = 100) private String author;

  private Integer rating;

  @Size(max = 15) private String fStop;

  private Records.Lens lens;
  private Boolean blackAndWhite;
  private Boolean isFilm;
  private String filmType;
  private FilmFormat filmFormat;

  @Size(max = 20) private String shutterSpeed;

  private Records.Camera camera;

  @Size(max = 20) private String focalLength;

  @Valid private Records.Location location;

  private String createDate;
  private List<Records.Tag> tags;
  private List<Records.Person> people;
  private List<Records.ChildCollection> collections;
}
