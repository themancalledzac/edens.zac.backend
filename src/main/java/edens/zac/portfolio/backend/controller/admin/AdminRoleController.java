package edens.zac.portfolio.backend.controller.admin;

import edens.zac.portfolio.backend.controller.admin.RoleRequests.CreateRoleRequest;
import edens.zac.portfolio.backend.controller.admin.RoleRequests.RoleCollectionRow;
import edens.zac.portfolio.backend.controller.admin.RoleRequests.RoleDetail;
import edens.zac.portfolio.backend.controller.admin.RoleRequests.RoleMemberRow;
import edens.zac.portfolio.backend.controller.admin.RoleRequests.RoleSummary;
import edens.zac.portfolio.backend.controller.admin.RoleRequests.SetRoleGrantRequest;
import edens.zac.portfolio.backend.dao.RoleRepository;
import edens.zac.portfolio.backend.entity.RoleEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.services.RoleGrantPropagationService;
import edens.zac.portfolio.backend.types.RoleKind;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoints for role management. Same two-layer authz as {@link AdminUserController} (prod
 * transport perimeter + {@code hasRole("ADMIN")}). Roles hold per-collection grants; users join
 * roles to inherit access.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
public class AdminRoleController {

  private final RoleRepository roleRepository;
  private final RoleGrantPropagationService roleGrantPropagationService;

  /**
   * List all roles (SHARED before PERSONAL, then by name) for the admin role-management view.
   *
   * @return the role summaries
   */
  @GetMapping
  public List<RoleSummary> listRoles() {
    return roleRepository.findAll().stream()
        .map(r -> new RoleSummary(r.getId(), r.getName(), r.getKind()))
        .toList();
  }

  /**
   * Create a new role. Kind defaults to {@code SHARED} when omitted.
   *
   * @param body the new role's name and optional kind
   * @return {@code 201 Created} with the created {@link RoleSummary}
   */
  @PostMapping
  public ResponseEntity<RoleSummary> createRole(@Valid @RequestBody CreateRoleRequest body) {
    RoleKind kind = body.kind() != null ? body.kind() : RoleKind.SHARED;
    Long id = roleRepository.createRole(body.name(), kind, currentUserId());
    return ResponseEntity.status(HttpStatus.CREATED).body(new RoleSummary(id, body.name(), kind));
  }

  /**
   * Role detail: the role, its members, and its collection grants, for the role-edit screen.
   *
   * @param roleId the role id
   * @return {@code 200} with {@link RoleDetail}, or {@code 404} if no such role
   */
  @GetMapping("/{roleId}")
  public ResponseEntity<RoleDetail> getRole(@PathVariable Long roleId) {
    RoleEntity role = roleRepository.findById(roleId).orElse(null);
    if (role == null) {
      return ResponseEntity.notFound().build();
    }
    List<RoleMemberRow> members =
        roleRepository.membersForRole(roleId).stream()
            .map(m -> new RoleMemberRow(m.userId(), m.email(), m.name()))
            .toList();
    List<RoleCollectionRow> collections =
        roleRepository.grantsForRole(roleId).stream()
            .map(g -> new RoleCollectionRow(g.collectionId(), g.title(), g.level()))
            .toList();
    return ResponseEntity.ok(
        new RoleDetail(role.getId(), role.getName(), role.getKind(), members, collections));
  }

  /**
   * Delete a role (cascades its memberships and grants).
   *
   * @param roleId the role id
   * @return {@code 204 No Content}, or {@code 404} if no such role
   */
  @DeleteMapping("/{roleId}")
  public ResponseEntity<Void> deleteRole(@PathVariable Long roleId) {
    return roleRepository.deleteRole(roleId) > 0
        ? ResponseEntity.noContent().build()
        : ResponseEntity.notFound().build();
  }

  /**
   * Grant a collection to a role at a level (GENERAL or CLIENT). Upserts: creates the grant if
   * absent, promotes/demotes if present. The grant waterfalls: it is also materialized onto every
   * visible descendant collection as an inherited copy.
   *
   * @param roleId the role id
   * @param collectionId the collection id
   * @param body the access level to grant
   * @return {@code 204 No Content}
   */
  @PutMapping("/{roleId}/collections/{collectionId}")
  public ResponseEntity<Void> setGrant(
      @PathVariable Long roleId,
      @PathVariable Long collectionId,
      @Valid @RequestBody SetRoleGrantRequest body) {
    roleGrantPropagationService.setGrant(roleId, collectionId, body.level(), currentUserId());
    return ResponseEntity.noContent().build();
  }

  /**
   * Revoke a role's grant on a collection, along with the inherited copies it waterfalled onto
   * descendants. Copies still inherited from a surviving ancestor grant are re-materialized.
   *
   * @param roleId the role id
   * @param collectionId the collection id
   * @return {@code 204 No Content}
   */
  @DeleteMapping("/{roleId}/collections/{collectionId}")
  public ResponseEntity<Void> removeGrant(
      @PathVariable Long roleId, @PathVariable Long collectionId) {
    roleGrantPropagationService.removeGrant(roleId, collectionId);
    return ResponseEntity.noContent().build();
  }

  /**
   * Add a user to a role (membership). Idempotent.
   *
   * @param roleId the role id
   * @param userId the {@code app_user.id} to add
   * @return {@code 204 No Content}
   */
  @PutMapping("/{roleId}/members/{userId}")
  public ResponseEntity<Void> addMember(@PathVariable Long roleId, @PathVariable Long userId) {
    roleRepository.addMember(roleId, userId, currentUserId());
    return ResponseEntity.noContent().build();
  }

  /**
   * Remove a user from a role.
   *
   * @param roleId the role id
   * @param userId the {@code app_user.id} to remove
   * @return {@code 204 No Content}
   */
  @DeleteMapping("/{roleId}/members/{userId}")
  public ResponseEntity<Void> removeMember(@PathVariable Long roleId, @PathVariable Long userId) {
    roleRepository.removeMember(roleId, userId);
    return ResponseEntity.noContent().build();
  }

  /** The acting admin's user id for audit columns, or null in dev where the gate is open. */
  private static Long currentUserId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return (auth != null && auth.getPrincipal() instanceof AuthPrincipal p) ? p.userId() : null;
  }
}
