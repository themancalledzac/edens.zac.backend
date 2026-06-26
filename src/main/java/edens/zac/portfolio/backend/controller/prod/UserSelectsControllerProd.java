package edens.zac.portfolio.backend.controller.prod;

import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.UserSelectGroup;
import edens.zac.portfolio.backend.services.UserSelectsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Per-user Selects. Every endpoint is session-required (401 when anonymous), self-only. */
@RestController
@RequestMapping("/api/read/user/selects")
@RequiredArgsConstructor
public class UserSelectsControllerProd {

  private final UserSelectsService userSelectsService;

  /** Body of {@code POST /api/read/user/selects}. */
  public record AddSelectRequest(Long collectionId, Long contentId) {}

  /** Add an image to the caller's selects. 201 on success, 401 when anonymous. */
  @PostMapping
  public ResponseEntity<Void> add(
      @AuthenticationPrincipal AuthPrincipal principal, @RequestBody AddSelectRequest body) {
    if (principal == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    userSelectsService.add(principal.userId(), body.collectionId(), body.contentId());
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  /** Remove an image from the caller's selects. 204 on success, 401 when anonymous. */
  @DeleteMapping("/{contentId}")
  public ResponseEntity<Void> remove(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long contentId) {
    if (principal == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    userSelectsService.remove(principal.userId(), contentId);
    return ResponseEntity.noContent().build();
  }

  /**
   * {@code GET /api/read/user/selects?collectionId=} → the caller's selected image ids in that
   * collection. {@code GET /api/read/user/selects} (no param) → all selects grouped by collection.
   * 401 when anonymous.
   */
  @GetMapping
  public ResponseEntity<?> list(
      @AuthenticationPrincipal AuthPrincipal principal,
      @RequestParam(name = "collectionId", required = false) Long collectionId) {
    if (principal == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    if (collectionId != null) {
      List<Long> ids = userSelectsService.listSelectIds(principal.userId(), collectionId);
      return ResponseEntity.ok(ids);
    }
    List<UserSelectGroup> groups = userSelectsService.listAll(principal.userId());
    return ResponseEntity.ok(groups);
  }
}
