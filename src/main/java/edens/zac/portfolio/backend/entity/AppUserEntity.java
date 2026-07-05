package edens.zac.portfolio.backend.entity;

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
  private String passwordHash;
  private UUID webauthnUserHandle;
  private String name;
  private String description;
  private UserStatus status;
  private boolean isAdmin;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
