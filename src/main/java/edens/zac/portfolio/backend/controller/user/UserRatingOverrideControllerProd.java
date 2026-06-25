package edens.zac.portfolio.backend.controller.user;

import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.UserRatingOverrideRequest;
import edens.zac.portfolio.backend.model.UserRatingOverrideResponse;
import edens.zac.portfolio.backend.services.UserRatingOverrideService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authed-user endpoints for per-user rating overrides. PUT upserts an override (authz: the caller
 * must hold a {@code gallery_access} grant whose {@code canTag} is true — enforced in the service);
 * GET lists the caller's overrides for a collection. Both require a non-null principal (401
 * otherwise). Admins use the canonical admin content path, not this endpoint.
 */
@RestController
@RequestMapping("/api/read/user/ratings")
@RequiredArgsConstructor
@Slf4j
public class UserRatingOverrideControllerProd {

  private final UserRatingOverrideService overrideService;

  /**
   * Upsert the caller's override for one image. 204 on success, 401 if unauthenticated, 403 if the
   * caller may not override in this collection.
   */
  @PutMapping
  public ResponseEntity<Void> upsert(
      @AuthenticationPrincipal AuthPrincipal principal,
      @Valid @RequestBody UserRatingOverrideRequest request) {
    if (principal == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    try {
      overrideService.upsert(
          principal.userId(), request.collectionId(), request.contentId(), request.rating());
    } catch (SecurityException e) {
      log.warn("Rejected rating override: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    return ResponseEntity.noContent().build();
  }

  /** The caller's overrides for a collection's view. 200 with the list, 401 if unauthenticated. */
  @GetMapping
  public ResponseEntity<List<UserRatingOverrideResponse>> list(
      @AuthenticationPrincipal AuthPrincipal principal,
      @RequestParam("collectionId") Long collectionId) {
    if (principal == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    List<UserRatingOverrideResponse> body =
        overrideService.listForUserInCollection(principal.userId(), collectionId).stream()
            .map(o -> new UserRatingOverrideResponse(o.getContentId(), o.getRating()))
            .toList();
    return ResponseEntity.ok(body);
  }
}
