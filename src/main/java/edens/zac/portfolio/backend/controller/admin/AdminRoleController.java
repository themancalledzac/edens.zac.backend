package edens.zac.portfolio.backend.controller.admin;

import edens.zac.portfolio.backend.controller.admin.RoleRequests.CreateRoleRequest;
import edens.zac.portfolio.backend.controller.admin.RoleRequests.RoleCollectionRow;
import edens.zac.portfolio.backend.controller.admin.RoleRequests.RoleDetail;
import edens.zac.portfolio.backend.controller.admin.RoleRequests.RoleMemberRow;
import edens.zac.portfolio.backend.controller.admin.RoleRequests.RoleSummary;
import edens.zac.portfolio.backend.controller.admin.RoleRequests.SetRoleGrantRequest;
import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.dao.RoleRepository;
import edens.zac.portfolio.backend.entity.RoleEntity;
import edens.zac.portfolio.backend.types.RoleKind;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
  private final AppUserRepository appUserRepository;

  @GetMapping
  public List<RoleSummary> listRoles() {
    return roleRepository.findAll().stream()
        .map(r -> new RoleSummary(r.getId(), r.getName(), r.getKind()))
        .toList();
  }

  @PostMapping
  public ResponseEntity<RoleSummary> createRole(@Valid @RequestBody CreateRoleRequest body) {
    RoleKind kind = body.kind() != null ? body.kind() : RoleKind.SHARED;
    Long id = roleRepository.createRole(body.name(), kind, null);
    return ResponseEntity.status(HttpStatus.CREATED).body(new RoleSummary(id, body.name(), kind));
  }

  @GetMapping("/{roleId}")
  public ResponseEntity<RoleDetail> getRole(@PathVariable Long roleId) {
    RoleEntity role = roleRepository.findById(roleId).orElse(null);
    if (role == null) {
      return ResponseEntity.notFound().build();
    }
    List<RoleMemberRow> members =
        roleRepository.memberUserIds(roleId).stream()
            .map(appUserRepository::findById)
            .flatMap(Optional::stream)
            .map(u -> new RoleMemberRow(u.getId(), u.getEmail(), u.getName()))
            .toList();
    List<RoleCollectionRow> collections =
        roleRepository.grantsForRole(roleId).stream()
            .map(g -> new RoleCollectionRow(g.collectionId(), g.title(), g.level()))
            .toList();
    return ResponseEntity.ok(
        new RoleDetail(role.getId(), role.getName(), role.getKind(), members, collections));
  }

  @DeleteMapping("/{roleId}")
  public ResponseEntity<Void> deleteRole(@PathVariable Long roleId) {
    roleRepository.deleteRole(roleId);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{roleId}/collections/{collectionId}")
  public ResponseEntity<Void> setGrant(
      @PathVariable Long roleId,
      @PathVariable Long collectionId,
      @Valid @RequestBody SetRoleGrantRequest body) {
    roleRepository.setCollectionGrant(roleId, collectionId, body.level(), null);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{roleId}/collections/{collectionId}")
  public ResponseEntity<Void> removeGrant(
      @PathVariable Long roleId, @PathVariable Long collectionId) {
    roleRepository.removeCollectionGrant(roleId, collectionId);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{roleId}/members/{userId}")
  public ResponseEntity<Void> addMember(@PathVariable Long roleId, @PathVariable Long userId) {
    roleRepository.addMember(roleId, userId, null);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{roleId}/members/{userId}")
  public ResponseEntity<Void> removeMember(@PathVariable Long roleId, @PathVariable Long userId) {
    roleRepository.removeMember(roleId, userId);
    return ResponseEntity.noContent().build();
  }
}
