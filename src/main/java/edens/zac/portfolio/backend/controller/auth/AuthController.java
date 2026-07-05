package edens.zac.portfolio.backend.controller.auth;

import edens.zac.portfolio.backend.config.AuthLoginLimiter;
import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.dao.UserCollectionRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.model.GalleryMembership;
import edens.zac.portfolio.backend.model.LoginRequest;
import edens.zac.portfolio.backend.model.MeResponse;
import edens.zac.portfolio.backend.services.SessionService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
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
   * Precomputed BCrypt hash used to equalize the response time of the unknown-email branch with the
   * wrong-password branch. Without this, an attacker could distinguish "no such user" (fast) from
   * "wrong password" (slow BCrypt) via timing — a user-enumeration oracle. We always call {@code
   * passwordEncoder.matches} and discard the result so both branches pay the same BCrypt cost.
   */
  private static final String DUMMY_HASH =
      "{bcrypt}$2a$10$7EqJtq98hPqEX7fNZaFWoOe4LqswmsWnGKD.QZEWMbwIQfRoZxNfy";

  private final SessionService sessionService;
  private final AuthLoginLimiter loginLimiter;
  private final AppUserRepository appUserRepository;
  private final UserCollectionRepository userCollectionRepository;
  private final PasswordEncoder passwordEncoder;

  @PostMapping("/login")
  public ResponseEntity<Void> login(
      @Valid @RequestBody LoginRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    String ip = resolveClientIp(request);
    // Normalize to lowercase so login matches the lowercased email stored at account-creation time.
    String email = body.email().toLowerCase();

    if (loginLimiter.isBlocked(ip, email)) {
      log.warn("Auth login rate-limited for email={} ip={}", email, ip);
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
    }

    Optional<AppUserEntity> maybeUser = appUserRepository.findByEmail(email);
    if (maybeUser.isEmpty() || maybeUser.get().getPasswordHash() == null) {
      // Perform a dummy BCrypt check so unknown-email and wrong-password branches take the same
      // time — prevents user-enumeration via timing side-channel. The result is discarded.
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
        || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    List<GalleryMembership> galleries =
        userCollectionRepository.findByUserId(principal.userId()).stream()
            .map(m -> new GalleryMembership(m.getCollectionId(), m.getRole()))
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

  private static String resolveClientIp(HttpServletRequest request) {
    String realIp = request.getHeader("X-Real-IP");
    if (realIp != null && !realIp.isBlank()) {
      return realIp.trim();
    }
    return request.getRemoteAddr();
  }
}
