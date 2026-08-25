package edens.zac.portfolio.backend.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
   * Body of {@code POST /api/read/user/share/email}.
   *
   * @param toEmail where to send the link, supplied by the sender
   */
  public record SendShareLinkRequest(@NotBlank @Email String toEmail) {}

  /**
   * Outcome of a send. Delivery is best-effort: a failure is reported rather than thrown, so the
   * sender still has their link to copy when email is off or SES rejects the address.
   *
   * @param sent whether SES accepted the message
   * @param reason a short failure code when it did not, otherwise null
   */
  public record ShareEmailResult(boolean sent, String reason) {}

  /**
   * The owner's own view of their link.
   *
   * @param exists whether a link has ever been minted for this user
   * @param token the owner's live raw token, so the page can show and re-send the link that is
   *     already in circulation. Null when it cannot be recovered -- a row minted before V58, or one
   *     encrypted under a since-changed secret -- which the UI should present as "reset to get a
   *     new link" rather than as an error
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
