package edens.zac.portfolio.backend.entity;

import edens.zac.portfolio.backend.types.Role;
import edens.zac.portfolio.backend.types.UserStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppUserEntity {
  private Long id;
  private String email;
  private Role role;
  private String passwordHash;
  private UUID webauthnUserHandle;
  private String displayName;
  private UserStatus status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
