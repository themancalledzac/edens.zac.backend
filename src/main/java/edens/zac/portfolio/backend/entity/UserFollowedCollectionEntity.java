package edens.zac.portfolio.backend.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One row of {@code user_followed_collection}: a single (user, collection) follow. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFollowedCollectionEntity {

  private Long userId;
  private Long collectionId;
  private LocalDateTime createdAt;
}
