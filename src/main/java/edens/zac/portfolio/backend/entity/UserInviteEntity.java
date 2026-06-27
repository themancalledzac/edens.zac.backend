package edens.zac.portfolio.backend.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInviteEntity {
  private Long id;
  private Long userId;
  private String tokenHash;
  private String email;
  private LocalDateTime expiresAt;
  private LocalDateTime usedAt;
  private Long createdBy;
  private LocalDateTime createdAt;
}
