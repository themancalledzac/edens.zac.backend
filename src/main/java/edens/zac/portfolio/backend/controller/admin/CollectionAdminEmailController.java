package edens.zac.portfolio.backend.controller.admin;

import edens.zac.portfolio.backend.model.CollectionRequests.GalleryAccessRequest;
import edens.zac.portfolio.backend.model.CollectionRequests.GalleryAccessResponse;
import edens.zac.portfolio.backend.services.CollectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoint for managing client gallery password and recipient emails.
 *
 * <p>Delegates all business logic to {@link CollectionService#updateGalleryAccess}.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/collections")
public class CollectionAdminEmailController {

  private final CollectionService collectionService;

  @PostMapping("/{id}/gallery-access")
  public ResponseEntity<GalleryAccessResponse> updateGalleryAccess(
      @PathVariable Long id, @Valid @RequestBody GalleryAccessRequest request) {
    GalleryAccessResponse response = collectionService.updateGalleryAccess(id, request);
    return response.saved()
        ? ResponseEntity.ok(response)
        : ResponseEntity.badRequest().body(response);
  }
}
