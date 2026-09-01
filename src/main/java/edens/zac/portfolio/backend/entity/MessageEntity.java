package edens.zac.portfolio.backend.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageEntity {

  private Long id;
  private String email;
  private String message;
  private LocalDateTime createdAt;

  /** When an admin first marked this message read; {@code null} means unread. */
  private LocalDateTime readAt;
}
