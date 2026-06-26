package edens.zac.portfolio.backend.controller.admin;

import edens.zac.portfolio.backend.types.CollectionRole;
import edens.zac.portfolio.backend.types.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
   */
  public record CreateUserRequest(@NotBlank @Email String email, String displayName) {}

  /**
   * Response for {@code POST /api/admin/users}.
   *
   * @param userId the newly-created {@code app_user.id}
   * @param inviteUrl the full invite URL the admin should send to the invitee
   */
  public record CreateUserResponse(Long userId, String inviteUrl) {}

  /**
   * Body for {@code PATCH /api/admin/users/{id}} — updates the two admin-editable fields. Email is
   * deliberately immutable (it is the login identity and invite target). {@code displayName} may be
   * {@code null} to clear it; {@code status} is required.
   *
   * @param displayName the new display name, or {@code null} to clear
   * @param status the new lifecycle status (INVITED / ACTIVE / DISABLED)
   */
  public record UpdateUserRequest(String displayName, @NotNull UserStatus status) {}

  /**
   * One associated-collection row in the admin user detail. {@code role} is {@code null} when the
   * user is tagged via {@code collection_people} but holds no membership (tagged only, no access).
   *
   * @param collectionId the collection id
   * @param title the collection title
   * @param role the membership role, or {@code null} if tagged only
   */
  public record AdminUserCollection(Long collectionId, String title, String role) {}

  /**
   * Body for {@code PUT /api/admin/users/{id}/collections/{collectionId}} — set the membership
   * role.
   *
   * @param role the new membership role (GENERAL or CLIENT)
   */
  public record SetCollectionRoleRequest(@NotNull CollectionRole role) {}
}
