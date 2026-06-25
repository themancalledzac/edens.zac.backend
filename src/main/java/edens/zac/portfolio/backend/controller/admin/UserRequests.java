package edens.zac.portfolio.backend.controller.admin;

import edens.zac.portfolio.backend.types.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request and response records for the admin user-management endpoints. */
public final class UserRequests {

  private UserRequests() {}

  /**
   * Row in the admin user list ({@code GET /api/admin/users}). Deliberately excludes the password
   * hash and WebAuthn handle — admin needs only identity and lifecycle state.
   *
   * @param id the {@code app_user.id}
   * @param email the account email
   * @param displayName the display name, may be {@code null}
   * @param status the account lifecycle status (INVITED / ACTIVE / DISABLED)
   */
  public record AdminUserSummary(Long id, String email, String displayName, UserStatus status) {}

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
