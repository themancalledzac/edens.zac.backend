package edens.zac.portfolio.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.model.MeResponse;
import edens.zac.portfolio.backend.types.Role;
import edens.zac.portfolio.backend.types.UserStatus;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * End-to-end integration test for the password login flow. Exercises the full stack:
 * SecurityFilterChain → SessionAuthenticationFilter → SessionService → real Postgres. The {@code
 * prod}-profile {@link edens.zac.portfolio.backend.config.InternalSecretFilter} is inactive in the
 * {@code test} profile so no BFF secret header is needed.
 *
 * <p>Note: the WebAuthn (passkey) round-trip requires a software authenticator to generate a valid
 * assertion and is intentionally out of scope for this test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthFlowEndToEndTest extends AbstractPostgresIntegrationTest {

  private static final String KNOWN_PASSWORD = "correct-horse";
  private static final String ADMIN_EMAIL = "e2e-admin@example.com";

  @LocalServerPort private int port;

  @Autowired private AppUserRepository appUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private TestRestTemplate restTemplate;

  @BeforeEach
  void seedAdminUser() {
    // AbstractPostgresIntegrationTest.truncateAuthTables() already cleared tables before we seed.
    restTemplate = new TestRestTemplate();
    String hash = passwordEncoder.encode(KNOWN_PASSWORD);
    appUserRepository.insert(
        AppUserEntity.builder()
            .email(ADMIN_EMAIL)
            .role(Role.ADMIN)
            .passwordHash(hash)
            .webauthnUserHandle(UUID.randomUUID())
            .status(UserStatus.ACTIVE)
            .build());
  }

  private String baseUrl() {
    return "http://localhost:" + port;
  }

  @Test
  void loginWithCorrectCredentials_returns204WithSessionCookie() {
    ResponseEntity<Void> response = postLogin(ADMIN_EMAIL, KNOWN_PASSWORD);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(response.getHeaders().get("Set-Cookie"))
        .isNotNull()
        .anySatisfy(
            cookie -> {
              assertThat(cookie).contains("ezac_session=");
              assertThat(cookie).containsIgnoringCase("HttpOnly");
            });
  }

  @Test
  void meWithSessionCookie_returns200WithAdminPrincipal() {
    ResponseEntity<Void> loginResponse = postLogin(ADMIN_EMAIL, KNOWN_PASSWORD);
    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    String sessionCookie = extractSessionCookie(loginResponse);
    assertThat(sessionCookie).isNotBlank();

    ResponseEntity<MeResponse> meResponse = getMe(sessionCookie);
    assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(meResponse.getBody()).isNotNull();
    assertThat(meResponse.getBody().email()).isEqualTo(ADMIN_EMAIL);
    assertThat(meResponse.getBody().role()).isEqualTo(Role.ADMIN);
    assertThat(meResponse.getBody().mfaSatisfied()).isFalse();
  }

  @Test
  void meWithoutCookie_returns401() {
    ResponseEntity<Void> response =
        restTemplate.exchange(
            baseUrl() + "/api/auth/me", HttpMethod.GET, HttpEntity.EMPTY, Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void loginWithWrongPassword_returns401() {
    ResponseEntity<Void> response = postLogin(ADMIN_EMAIL, "wrong-password");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // ---- helpers ----

  private ResponseEntity<Void> postLogin(String email, String password) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");
    Map<String, String> body = Map.of("email", email, "password", password);
    return restTemplate.exchange(
        baseUrl() + "/api/auth/login",
        HttpMethod.POST,
        new HttpEntity<>(body, headers),
        Void.class);
  }

  private String extractSessionCookie(ResponseEntity<?> response) {
    return response.getHeaders().getOrEmpty("Set-Cookie").stream()
        .filter(c -> c.startsWith("ezac_session="))
        .findFirst()
        .map(
            c -> {
              String after = c.substring("ezac_session=".length());
              int semi = after.indexOf(';');
              return semi >= 0 ? after.substring(0, semi) : after;
            })
        .orElse("");
  }

  private ResponseEntity<MeResponse> getMe(String rawToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Cookie", "ezac_session=" + rawToken);
    return restTemplate.exchange(
        baseUrl() + "/api/auth/me", HttpMethod.GET, new HttpEntity<>(headers), MeResponse.class);
  }
}
