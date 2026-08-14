package edens.zac.portfolio.backend.model;

import java.time.LocalDateTime;
import java.util.List;

/** Request and response shapes for user share links. */
public final class ShareModels {

  private ShareModels() {}

  /**
   * What a share-link recipient receives: whose work they are looking at, and the page itself.
   *
   * @param ownerName the sharer's display name, for the "you are viewing X's work" banner and the
   *     home-page re-entry tile
   * @param page the recipient view -- the standard collection layout, scoped to the share allowlist
   */
  public record ShareView(String ownerName, CollectionModel page) {}

  /**
   * The owner's own view of their link.
   *
   * @param exists whether a link has ever been minted for this user
   * @param token the raw token, present ONLY in the response that just created or rotated it. Read
   *     requests return null here -- the raw value is unrecoverable once issued, since only its
   *     hash is stored, which is also why "reset" mints a new one rather than revealing the old
   * @param createdAt when the link was first minted
   * @param rotatedAt when it was last reset, or null if never
   * @param lastUsedAt when a recipient last opened it, or null if never
   * @param optedInCollectionIds collections the owner deliberately added to the share
   * @param candidateCollections collections the owner can add -- ones they hold a role grant on but
   *     are not tagged in, which are therefore excluded by default
   */
  public record ShareSettings(
      boolean exists,
      String token,
      LocalDateTime createdAt,
      LocalDateTime rotatedAt,
      LocalDateTime lastUsedAt,
      List<Long> optedInCollectionIds,
      List<CollectionModel> candidateCollections) {}
}
