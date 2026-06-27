package edens.zac.portfolio.backend.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One collection's selects for the {@code GET /api/read/user/selects} listing backing {@code
 * /user}. {@code contentIds} are newest-first.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSelectGroup {

  private Long collectionId;
  private List<Long> contentIds;
}
