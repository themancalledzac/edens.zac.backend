package edens.zac.portfolio.backend.controller.prod;

import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.services.UserFollowsService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Per-user followed collections. Every endpoint is session-required (401 when anonymous),
 * self-only.
 */
@RestController
@RequestMapping("/api/read/user/follows")
@RequiredArgsConstructor
public class UserFollowsControllerProd {

  private final UserFollowsService userFollowsService;

  /** Body of {@code POST /api/read/user/follows}. */
  public record AddFollowRequest(Long collectionId) {}

  /** Add a collection to the caller's follows. 201 on success, 401 when anonymous. */
  @PostMapping
  public ResponseEntity<Void> add(
      @AuthenticationPrincipal AuthPrincipal principal, @RequestBody AddFollowRequest body) {
    if (principal == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    userFollowsService.add(principal.userId(), body.collectionId());
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  /** Remove a collection from the caller's follows. 204 on success, 401 when anonymous. */
  @DeleteMapping("/{collectionId}")
  public ResponseEntity<Void> remove(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long collectionId) {
    if (principal == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    userFollowsService.remove(principal.userId(), collectionId);
    return ResponseEntity.noContent().build();
  }

  /** The caller's followed collection ids, newest-followed first. 401 when anonymous. */
  @GetMapping
  public ResponseEntity<List<Long>> list(@AuthenticationPrincipal AuthPrincipal principal) {
    if (principal == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    return ResponseEntity.ok(userFollowsService.listFollowedCollectionIds(principal.userId()));
  }
}
