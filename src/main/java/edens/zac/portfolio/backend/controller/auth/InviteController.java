package edens.zac.portfolio.backend.controller.auth;

import edens.zac.portfolio.backend.controller.auth.InviteRequests.AcceptInviteRequest;
import edens.zac.portfolio.backend.controller.auth.InviteRequests.InvitePreview;
import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.entity.UserInviteEntity;
import edens.zac.portfolio.backend.services.SessionService;
import edens.zac.portfolio.backend.services.UserInviteService;
import edens.zac.portfolio.backend.types.UserStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoints that allow an invited user to preview and complete their registration. These
 * routes are explicitly permitted in {@link edens.zac.portfolio.backend.config.SecurityConfig}.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth/invite")
public class InviteController {

  private final UserInviteService userInviteService;
  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final SessionService sessionService;

  /**
   * Preview an invite — returns the invitee email and any pre-filled display name. Read-only; does
   * not consume the token.
   *
   * @param token the raw invite token from the URL
   * @return {@code 200} with {@link InvitePreview}, or {@code 404} if the token is unknown,
   *     expired, or already used
   */
  @GetMapping("/{token}")
  public ResponseEntity<InvitePreview> preview(@PathVariable String token) {
    Optional<UserInviteEntity> maybeInvite = userInviteService.validate(token);
    if (maybeInvite.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    UserInviteEntity invite = maybeInvite.get();
    Optional<AppUserEntity> maybeUser = appUserRepository.findById(invite.getUserId());
    if (maybeUser.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    AppUserEntity user = maybeUser.get();
    return ResponseEntity.ok(new InvitePreview(invite.getEmail(), user.getName()));
  }

  /**
   * Accept an invite — atomically redeems the token, sets the password and display name, activates
   * the account, and auto-logs the user in by writing an {@code ezac_session} cookie.
   *
   * @param token the raw invite token from the URL
   * @param body the chosen display name and password (validated)
   * @return {@code 204 No Content} (+ Set-Cookie), {@code 410 Gone} if the token is already used or
   *     expired, or {@code 400 Bad Request} for validation failures
   */
  @PostMapping("/{token}/accept")
  @Transactional
  public ResponseEntity<Void> accept(
      @PathVariable String token,
      @Valid @RequestBody AcceptInviteRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {

    Optional<UserInviteEntity> maybeInvite = userInviteService.redeem(token);
    if (maybeInvite.isEmpty()) {
      log.warn("Invite accept rejected: token already used or expired");
      return ResponseEntity.status(HttpStatus.GONE).build();
    }

    UserInviteEntity invite = maybeInvite.get();
    Long userId = invite.getUserId();

    appUserRepository.updatePasswordHash(userId, passwordEncoder.encode(body.password()));
    appUserRepository.updateName(userId, body.displayName());
    appUserRepository.updateStatus(userId, UserStatus.ACTIVE);

    AppUserEntity user =
        appUserRepository
            .findById(userId)
            .orElseThrow(() -> new IllegalStateException("User disappeared after invite redeem"));

    sessionService.create(user, false, request, response);
    log.info("Invite accepted: userId={}", userId);
    return ResponseEntity.noContent().build();
  }
}
