package edens.zac.portfolio.backend.entity;

import edens.zac.portfolio.backend.types.AccessLevel;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A user's share link. The raw token is never stored -- only {@code tokenHash} -- so a database
 * leak yields no usable link. There is at most one row per user (V56's {@code uq_share_link_user}),
 * and "reset link" rotates {@code tokenHash} in place rather than inserting a new row, which
 * preserves the owner's {@code share_link_collection} opt-ins across a reset.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareLinkEntity {
  private Long id;
  private Long userId;
  private String tokenHash;

  /**
   * The same token, encrypted (see TokenCipher). Exists only so the owner can see their own live
   * link again to re-send it; never used for lookup, since AES-GCM output differs per call. Null
   * for rows minted before V58, which the owner's page reports as needing a reset.
   */
  private String tokenCipher;

  /**
   * The ceiling a holder of this link resolves to inside the share's scope. Always {@code GENERAL}
   * today; the column admits {@code CLIENT} so a future download toggle needs no migration.
   */
  private AccessLevel level;

  private LocalDateTime createdAt;
  private LocalDateTime rotatedAt;
  private LocalDateTime lastUsedAt;
}
