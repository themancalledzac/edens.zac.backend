package edens.zac.portfolio.backend.controller.prod;

import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.CollectionModel;
import edens.zac.portfolio.backend.services.UserPageAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Session-required self-only aggregation of a user's galleries and tagged content. */
@RestController
@RequestMapping("/api/read/user")
@RequiredArgsConstructor
public class UserControllerProd {

  private final UserPageAssembler userPageAssembler;

  /** The signed-in user's synthetic collection; 401 when anonymous. Never accepts a client id. */
  @GetMapping("/me/page")
  public ResponseEntity<CollectionModel> myPage(@AuthenticationPrincipal AuthPrincipal principal) {
    if (principal == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    return ResponseEntity.ok(userPageAssembler.assembleForUser(principal.userId()));
  }
}
