package edens.zac.portfolio.backend.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a person who can be tagged in image content. This allows tracking which
 * people appear in photographs.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentPersonEntity {

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ContentPersonEntity that)) return false;
    return personName != null && personName.equals(that.personName);
  }

  @Override
  public int hashCode() {
    return personName != null ? personName.hashCode() : 0;
  }

  private Long id;

  @NotBlank @Size(min = 1, max = 100) private String personName;

  private LocalDateTime createdAt;

  /**
   * Constructor for creating a person with just a name. Useful for quick person creation.
   *
   * @param personName The name of the person
   */
  public ContentPersonEntity(String personName) {
    this.personName = personName;
  }
}
