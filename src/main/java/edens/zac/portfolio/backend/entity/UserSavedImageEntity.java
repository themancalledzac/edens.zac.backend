package edens.zac.portfolio.backend.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One row of {@code user_saved_image}: a single (user, content image) bookmark. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSavedImageEntity {

  private Long userId;
  private Long imageId;
  private LocalDateTime createdAt;
}
