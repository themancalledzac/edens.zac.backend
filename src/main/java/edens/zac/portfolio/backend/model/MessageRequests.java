package edens.zac.portfolio.backend.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class MessageRequests {

  private MessageRequests() {}

  public record CreateMessage(
      @Email(message = "Invalid email address") @NotBlank(message = "Email is required") @Size(max = 320, message = "Email cannot exceed 320 characters") String email,
      @NotBlank(message = "Message is required") @Size(max = 5000, message = "Message cannot exceed 5000 characters") String message) {}

  public record CreatedResponse(Long id, LocalDateTime createdAt) {}
}
