package edens.zac.portfolio.backend.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for creating a new person. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePersonRequest {

  @NotBlank(message = "Person name is required") @Size(min = 1, max = 100, message = "Person name must be between 1 and 100 characters") private String personName;
}
