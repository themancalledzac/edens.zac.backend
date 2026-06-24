package edens.zac.portfolio.backend.controller.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request and response records for the public invite-acceptance endpoints. */
public final class InviteRequests {

  private InviteRequests() {}

  /**
   * Body for {@code POST /api/auth/invite/{token}/accept}.
   *
   * @param displayName the name the new user wants displayed on their account
   * @param password the plaintext password chosen by the invitee; minimum 8 characters
   */
  public record AcceptInviteRequest(
      @NotBlank String displayName, @NotBlank @Size(min = 8) String password) {}

  /**
   * Response for {@code GET /api/auth/invite/{token}} — read-only preview of the invite so the
   * frontend can pre-fill the accept form.
   *
   * @param email the email address the invite was issued to
   * @param displayName the display name pre-filled by the admin, may be {@code null}
   */
  public record InvitePreview(String email, String displayName) {}
}
