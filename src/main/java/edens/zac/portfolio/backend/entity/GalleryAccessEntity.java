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
public class GalleryAccessEntity {
  private Long id;
  private Long userId;
  private Long collectionId;
  private boolean canDownload;
  private boolean canTag;
  private Long grantedBy;
  private LocalDateTime grantedAt;
  private LocalDateTime expiresAt;
}
