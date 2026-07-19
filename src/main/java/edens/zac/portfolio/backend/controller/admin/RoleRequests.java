package edens.zac.portfolio.backend.controller.admin;

import edens.zac.portfolio.backend.types.AccessLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Request and response records for the admin role-management endpoints. */
public final class RoleRequests {

  private RoleRequests() {}

  /** A role in the admin list. */
  public record RoleSummary(Long id, String name) {}

  /** Body for {@code POST /api/admin/roles}. */
  public record CreateRoleRequest(@NotBlank @Size(max = 128) String name) {}

  /** One collection a role grants, for the role-detail view. */
  public record RoleCollectionRow(Long collectionId, String title, AccessLevel level) {}

  /** One member of a role, for the role-detail view. */
  public record RoleMemberRow(Long userId, String email, String name) {}

  /** Role detail: the role, its members, and its collection grants. */
  public record RoleDetail(
      Long id, String name, List<RoleMemberRow> members, List<RoleCollectionRow> collections) {}

  /** Body for {@code PUT /api/admin/roles/{roleId}/collections/{collectionId}}. */
  public record SetRoleGrantRequest(@NotNull AccessLevel level) {}

  /** One role a user belongs to, for the reshaped user-detail view. */
  public record UserRoleRow(Long roleId, String name) {}

  /**
   * One role granting a collection, for the collection-edit access panel. The provenance pair is
   * null for direct grants; inherited (waterfalled) rows carry the origin collection's id and title
   * so the UI can badge them.
   */
  public record CollectionRoleGrantRow(
      Long roleId,
      String name,
      AccessLevel level,
      Long inheritedFromCollectionId,
      String inheritedFromCollectionTitle) {}
}
