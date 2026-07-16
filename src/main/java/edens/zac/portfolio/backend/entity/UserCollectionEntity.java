package edens.zac.portfolio.backend.entity;

import edens.zac.portfolio.backend.types.AccessLevel;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCollectionEntity {
  private Long userId;
  private Long collectionId;
  private AccessLevel role;
  private LocalDateTime grantedAt;
  private Long grantedBy;
}
