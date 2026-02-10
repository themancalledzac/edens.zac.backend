package edens.zac.portfolio.backend.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for creating a new tag. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTagRequest {

  @NotBlank(message = "Tag name is required") @Size(min = 1, max = 50, message = "Tag name must be between 1 and 50 characters") private String tagName;
}
