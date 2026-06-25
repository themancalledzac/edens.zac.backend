package edens.zac.portfolio.backend.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of {@code user_rating_override}: a single user's rating for one image within a
 * collection's view. {@code (userId, contentId)} is the primary key; this never touches the
 * canonical {@code content.rating}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRatingOverrideEntity {

  private Long userId;
  private Long contentId;
  private Long collectionId;
  private int rating;
  private LocalDateTime updatedAt;
}
