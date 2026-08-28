package edens.zac.portfolio.backend.controller.auth;

import edens.zac.portfolio.backend.config.AuthLoginLimiter;
import edens.zac.portfolio.backend.config.ClientIp;
import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.GalleryMembership;
import edens.zac.portfolio.backend.model.LoginRequest;
import edens.zac.portfolio.backend.model.MeResponse;
import edens.zac.portfolio.backend.services.CollectionAccessService;
import edens.zac.portfolio.backend.services.SessionService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private static final String COOKIE_NAME = "ezac_session";

  /**
   * Precomputed BCrypt hash used to equalize the response time of the branches that reject before
   * the real password check -- unknown email, no password hash, and an account that fails {@link
   * SessionService#mayHoldSession} -- with the wrong-password branch. Without this, an attacker
   * could distinguish "no such user" (fast) from "wrong password" (slow BCrypt) via timing — a
   * user-enumeration oracle. We always call {@code passwordEncoder.matches} and discard the result
   * so every branch pays the same BCrypt cost.
   */
  private static final String DUMMY_HASH =
      "{bcrypt}$2a$10$7EqJtq98hPqEX7fNZaFWoOe4LqswmsWnGKD.QZEWMbwIQfRoZxNfy";

  private final SessionService sessionService;
  private final AuthLoginLimiter loginLimiter;
  private final AppUserRepository appUserRepository;
  private final CollectionAccessService collectionAccessService;
  private final PasswordEncoder passwordEncoder;

  /**
   * Password login. The submitted email is lowercased with {@link Locale#ROOT} so it matches both
   * the lowercased email stored at account-creation time and the key {@link AuthLoginLimiter}
   * builds for the same address. A locale-sensitive lowercase would diverge from the limiter key
   * under a Turkish default locale, where {@code I} lowercases to a dotless {@code i}.
   */
  @PostMapping("/login")
  public ResponseEntity<Void> login(
      @Valid @RequestBody LoginRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    String ip = ClientIp.resolve(request);
    String email = body.email().toLowerCase(Locale.ROOT);

    if (loginLimiter.isBlocked(ip, email)) {
      log.warn("Auth login rate-limited for email={} ip={}", email, ip);
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
    }

    Optional<AppUserEntity> maybeUser = appUserRepository.findByEmail(email);
    if (maybeUser.isEmpty()
        || maybeUser.get().getPasswordHash() == null
        || !SessionService.mayHoldSession(maybeUser.get().getStatus())) {
      passwordEncoder.matches(body.password(), DUMMY_HASH);
      loginLimiter.recordFailure(ip, email);
      log.warn("Failed auth login for email={} ip={}", email, ip);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    if (!passwordEncoder.matches(body.password(), maybeUser.get().getPasswordHash())) {
      loginLimiter.recordFailure(ip, email);
      log.warn("Failed auth login for email={} ip={}", email, ip);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    loginLimiter.reset(ip, email);
    sessionService.create(maybeUser.get(), false, request, response);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    sessionService.revoke(readCookie(request), response);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  public ResponseEntity<MeResponse> me() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal() instanceof AuthPrincipal principal)
        // A share-link holder is unreachable here anyway -- /api/auth/me requires ROLE_USER and a
        // flyby carries no authorities -- but effectiveGrants(null) below has no business being
        // called at all, so the identity requirement is stated rather than inferred from routing.
        || !AuthPrincipal.isRealUser(principal)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    List<GalleryMembership> galleries =
        collectionAccessService.effectiveGrants(principal.userId()).stream()
            .map(g -> new GalleryMembership(g.collectionId(), g.level()))
            .toList();
    return ResponseEntity.ok(
        new MeResponse(
            principal.email(), principal.isAdmin(), principal.mfaSatisfied(), galleries));
  }

  private static String readCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (COOKIE_NAME.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }
}
