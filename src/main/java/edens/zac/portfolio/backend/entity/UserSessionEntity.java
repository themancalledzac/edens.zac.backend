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
public class UserSessionEntity {
  private Long id;
  private Long userId;
  private String tokenHash;
  private boolean mfaSatisfied;
  private String ip;
  private String userAgent;
  private LocalDateTime createdAt;
  private LocalDateTime lastSeenAt;
  private LocalDateTime expiresAt;
  private LocalDateTime revokedAt;
}
