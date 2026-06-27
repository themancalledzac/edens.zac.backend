package edens.zac.portfolio.backend.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of {@code user_selects}: a single (user, content image) favorite scoped to a collection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSelectEntity {

  private Long userId;
  private Long contentId;
  private Long collectionId;
  private LocalDateTime createdAt;
}
