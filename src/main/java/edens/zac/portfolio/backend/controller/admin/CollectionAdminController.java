package edens.zac.portfolio.backend.controller.admin;

import edens.zac.portfolio.backend.controller.admin.RoleRequests.CollectionRoleGrantRow;
import edens.zac.portfolio.backend.dao.RoleRepository;
import edens.zac.portfolio.backend.model.CollectionRequests.GalleryAccessRequest;
import edens.zac.portfolio.backend.model.CollectionRequests.GalleryAccessResponse;
import edens.zac.portfolio.backend.services.CollectionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoints for managing client galleries (password, recipient emails, future operations).
 *
 * <p>Delegates gallery-access logic to {@link CollectionService}; role reads go straight to {@link
 * RoleRepository}, mirroring {@link AdminRoleController}.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/collections")
public class CollectionAdminController {

  private final CollectionService collectionService;
  private final RoleRepository roleRepository;

  /**
   * List the roles granting this collection (the inverse of the role-detail view), for the
   * collection-edit access panel. Bare array to match the sibling RBAC list endpoints.
   *
   * @param id the collection id
   * @return the granting roles, SHARED before PERSONAL, then by name
   */
  @GetMapping("/{id}/roles")
  public List<CollectionRoleGrantRow> collectionRoles(@PathVariable Long id) {
    return roleRepository.rolesGrantingCollection(id).stream()
        .map(g -> new CollectionRoleGrantRow(g.roleId(), g.name(), g.kind(), g.level()))
        .toList();
  }

  @PostMapping("/{id}/gallery-access")
  public ResponseEntity<GalleryAccessResponse> updateGalleryAccess(
      @PathVariable Long id, @Valid @RequestBody GalleryAccessRequest request) {
    GalleryAccessResponse response = collectionService.updateGalleryAccess(id, request);
    return response.saved()
        ? ResponseEntity.ok(response)
        : ResponseEntity.badRequest().body(response);
  }
}
