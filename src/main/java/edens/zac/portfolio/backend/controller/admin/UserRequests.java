package edens.zac.portfolio.backend.controller.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request and response records for the admin user-management endpoints. */
public final class UserRequests {

  private UserRequests() {}

  /**
   * Body for {@code POST /api/admin/users} — creates a new user account and returns an invite URL.
   *
   * @param email the invitee's email address (normalized to lowercase by the controller)
   * @param displayName optional display name pre-filled on the account; the invitee may override it
   *     at accept time
   * @param role reserved for future use; currently ignored by the controller (always CLIENT)
   */
  public record CreateUserRequest(@NotBlank @Email String email, String displayName, String role) {}

  /**
   * Response for {@code POST /api/admin/users}.
   *
   * @param userId the newly-created {@code app_user.id}
   * @param inviteUrl the full invite URL the admin should send to the invitee
   */
  public record CreateUserResponse(Long userId, String inviteUrl) {}
}
