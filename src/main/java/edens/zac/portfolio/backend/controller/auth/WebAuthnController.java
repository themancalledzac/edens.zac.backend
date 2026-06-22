package edens.zac.portfolio.backend.controller.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import edens.zac.portfolio.backend.config.AuthLoginLimiter;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.services.WebAuthnService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The four WebAuthn ceremony endpoints. Register endpoints require an authenticated principal
 * (enforced by SecurityConfig's {@code /register/**} authenticated matcher); login endpoints are
 * public (SecurityConfig's {@code /login/**} permitAll matcher). Options bodies are serialized with
 * the WebAuthn {@link ObjectMapper} so the W3C field shapes survive. The login per-attempt id
 * round-trips via a short-lived HttpOnly cookie ({@code ezac_webauthn_attempt}) so the frontend
 * never couples to a custom header.
 */
@RestController
@RequestMapping("/api/auth/webauthn")
@Slf4j
public class WebAuthnController {

  private static final String ATTEMPT_COOKIE = "ezac_webauthn_attempt";
  private static final String ATTEMPT_COOKIE_PATH = "/api/auth/webauthn";
  private static final Duration ATTEMPT_COOKIE_TTL = Duration.ofMinutes(5);

  private final WebAuthnService webAuthnService;
  private final ObjectMapper webAuthnObjectMapper;
  private final AuthLoginLimiter loginLimiter;

  /**
   * Whether the attempt cookie is set Secure. Mirrors SessionService's flag (true in prod; the dev
   * profile overrides it to false so the cookie survives plain {@code http://localhost}).
   */
  private final boolean cookieSecure;

  /**
   * Constructor.
   *
   * @param webAuthnService the ceremony orchestration service
   * @param objectMapper the primary application mapper (the WebAuthn Jackson module is auto-applied
   *     by Spring Boot via the {@link
   *     edens.zac.portfolio.backend.config.WebAuthnConfig#webauthnJackson2Module()} bean)
   * @param loginLimiter the shared login rate-limiter (mirrors {@code AuthController})
   * @param cookieSecure whether the attempt cookie is set Secure
   */
  public WebAuthnController(
      WebAuthnService webAuthnService,
      ObjectMapper objectMapper,
      AuthLoginLimiter loginLimiter,
      @Value("${app.auth.cookie-secure:true}") boolean cookieSecure) {
    this.webAuthnService = webAuthnService;
    this.webAuthnObjectMapper = objectMapper;
    this.loginLimiter = loginLimiter;
    this.cookieSecure = cookieSecure;
  }

  /**
   * Mint registration (attestation) options for the authenticated admin.
   *
   * @param principal the authenticated principal (null if unauthenticated)
   * @return 200 with the W3C creation options JSON, or 401 if unauthenticated
   * @throws Exception if the options cannot be serialized
   */
  @PostMapping(value = "/register/start", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> registerStart(@AuthenticationPrincipal AuthPrincipal principal)
      throws Exception {
    if (principal == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    PublicKeyCredentialCreationOptions options = webAuthnService.startRegistration(principal);
    return ResponseEntity.ok(webAuthnObjectMapper.writeValueAsString(options));
  }

  /**
   * Verify and persist the newly registered credential for the authenticated admin.
   *
   * @param principal the authenticated principal (null if unauthenticated)
   * @param credentialJson the raw W3C attestation credential JSON
   * @return 204 on success, or 401 if unauthenticated
   */
  @PostMapping(value = "/register/finish", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> registerFinish(
      @AuthenticationPrincipal AuthPrincipal principal, @RequestBody String credentialJson) {
    if (principal == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    webAuthnService.finishRegistration(principal, credentialJson);
    return ResponseEntity.noContent().build();
  }

  /**
   * Mint assertion (login) options for the typed email and set the per-attempt cookie. The body is
   * a clean W3C {@code PublicKeyCredentialRequestOptions} document; the per-attempt id rides the
   * short-lived HttpOnly {@code ezac_webauthn_attempt} cookie so the client returns it on {@code
   * /login/finish}.
   *
   * <p>Rate-limiting mirrors {@code AuthController.login}: if the IP+email pair is blocked, 429 is
   * returned immediately before minting any options or setting a cookie. A failure counter is
   * recorded on every non-blocked call so that repeated start attempts (without a matching
   * successful finish) eventually trip the limiter. The limiter is reset on a successful {@link
   * #loginFinish}.
   *
   * @param body the JSON body {@code {"email": "..."}}
   * @param request the servlet request (IP resolution)
   * @return 200 with the options JSON and a Set-Cookie for the attempt id, or 429 if rate-limited
   * @throws Exception if the options cannot be serialized
   */
  @PostMapping(
      value = "/login/start",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> loginStart(
      @RequestBody Map<String, String> body, HttpServletRequest request) throws Exception {
    String email = body.get("email");
    String ip = resolveClientIp(request);

    if (loginLimiter.isBlocked(ip, email)) {
      log.warn("WebAuthn login/start rate-limited for email={} ip={}", email, ip);
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
    }
    loginLimiter.recordFailure(ip, email);

    WebAuthnService.LoginStart start = webAuthnService.startLogin(email);
    String optionsJson = webAuthnObjectMapper.writeValueAsString(start.options());
    ResponseCookie cookie =
        ResponseCookie.from(ATTEMPT_COOKIE, start.attemptId())
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Strict")
            .path(ATTEMPT_COOKIE_PATH)
            .maxAge(ATTEMPT_COOKIE_TTL)
            .build();
    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(optionsJson);
  }

  /**
   * Verify the assertion and mint the session. Reads the per-attempt id from the {@code
   * ezac_webauthn_attempt} cookie (401 if absent), delegates to the service (which sets the session
   * cookie via {@code SessionService.create}), then clears the single-use attempt cookie.
   *
   * <p>The attempt cookie is cleared in a {@code finally} block so it is always expired — on both
   * success and assertion failure — preventing the cookie from lingering until its 5-minute TTL on
   * bad assertions. On success the limiter is reset so subsequent legitimate logins are not
   * blocked.
   *
   * @param attemptId the per-attempt id from the cookie (null if absent)
   * @param credentialJson the raw W3C assertion credential JSON
   * @param request the servlet request
   * @param response the Set-Cookie sink
   * @return 204 on success, or 401 if the attempt cookie is absent
   */
  @PostMapping(value = "/login/finish", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> loginFinish(
      @CookieValue(value = ATTEMPT_COOKIE, required = false) String attemptId,
      @RequestBody String credentialJson,
      HttpServletRequest request,
      HttpServletResponse response) {
    if (attemptId == null || attemptId.isBlank()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    String ip = resolveClientIp(request);
    try {
      String authenticatedEmail =
          webAuthnService.finishLogin(attemptId, credentialJson, request, response);
      loginLimiter.reset(ip, authenticatedEmail);
      return ResponseEntity.noContent().build();
    } finally {
      // Always clear the single-use attempt cookie — on success and on assertion failure.
      ResponseCookie cleared =
          ResponseCookie.from(ATTEMPT_COOKIE, "")
              .httpOnly(true)
              .secure(cookieSecure)
              .sameSite("Strict")
              .path(ATTEMPT_COOKIE_PATH)
              .maxAge(0)
              .build();
      response.addHeader(HttpHeaders.SET_COOKIE, cleared.toString());
    }
  }

  private static String resolveClientIp(HttpServletRequest request) {
    String realIp = request.getHeader("X-Real-IP");
    if (realIp != null && !realIp.isBlank()) {
      return realIp.trim();
    }
    return request.getRemoteAddr();
  }
}
