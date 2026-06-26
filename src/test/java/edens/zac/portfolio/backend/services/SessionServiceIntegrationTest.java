package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.dao.UserSessionRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.entity.UserSessionEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.types.UserStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SessionServiceIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private SessionService sessionService;
  @Autowired private AppUserRepository userRepository;
  @Autowired private UserSessionRepository sessionRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private AppUserEntity seedAdmin(String email) {
    Long id =
        userRepository.insert(
            AppUserEntity.builder()
                .email(email)
                .name(email)
                .webauthnUserHandle(UUID.randomUUID())
                .status(UserStatus.ACTIVE)
                .build());
    return userRepository.findById(id).orElseThrow();
  }

  // Extract the raw ezac_session value from the Set-Cookie header.
  private String rawTokenFrom(MockHttpServletResponse response) {
    String setCookie = response.getHeader("Set-Cookie");
    assertThat(setCookie).isNotNull().contains("ezac_session=");
    String afterName =
        setCookie.substring(setCookie.indexOf("ezac_session=") + "ezac_session=".length());
    int semi = afterName.indexOf(';');
    return semi >= 0 ? afterName.substring(0, semi) : afterName;
  }

  @Test
  void createIssuesCookieAndPersistsHashedToken() {
    AppUserEntity admin = seedAdmin("create@example.com");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("User-Agent", "JUnit-UA");
    MockHttpServletResponse response = new MockHttpServletResponse();

    sessionService.create(admin, false, request, response);

    String setCookie = response.getHeader("Set-Cookie");
    assertThat(setCookie).contains("ezac_session=");
    assertThat(setCookie).contains("HttpOnly");
    assertThat(setCookie).contains("SameSite=Strict");
    assertThat(setCookie).contains("Path=/");

    String raw = rawTokenFrom(response);
    // Raw token is NOT stored: looking it up by its own value as the hash must miss.
    assertThat(sessionRepository.findByTokenHash(raw)).isEmpty();
    // Resolving with the raw token, however, succeeds.
    Optional<AuthPrincipal> principal = sessionService.resolve(raw);
    assertThat(principal).isPresent();
    assertThat(principal.get().email()).isEqualTo("create@example.com");
    assertThat(principal.get().mfaSatisfied()).isFalse();
  }

  @Test
  void resolveRejectsUnknownToken() {
    assertThat(sessionService.resolve("not-a-real-token")).isEmpty();
  }

  @Test
  void resolveRejectsExpiredSession() {
    AppUserEntity admin = seedAdmin("expired@example.com");
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    sessionService.create(admin, false, request, response);
    String raw = rawTokenFrom(response);

    // Force expiry into the past.
    UserSessionEntity session =
        sessionRepository.findByTokenHash(sessionService.sha256HexForTest(raw)).orElseThrow();
    sessionRepository.touch(
        session.getId(), session.getLastSeenAt(), LocalDateTime.now().minusMinutes(1));

    assertThat(sessionService.resolve(raw)).isEmpty();
  }

  @Test
  void resolveRejectsRevokedSession() {
    AppUserEntity admin = seedAdmin("revoked@example.com");
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    sessionService.create(admin, false, request, response);
    String raw = rawTokenFrom(response);

    sessionService.revoke(raw, new MockHttpServletResponse());
    assertThat(sessionService.resolve(raw)).isEmpty();
  }

  @Test
  void resolveSlidesLastSeenWhenStale() {
    AppUserEntity admin = seedAdmin("slide@example.com");
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    sessionService.create(admin, false, request, response);
    String raw = rawTokenFrom(response);

    UserSessionEntity session =
        sessionRepository.findByTokenHash(sessionService.sha256HexForTest(raw)).orElseThrow();
    // Make last_seen_at stale (older than the 24h refresh threshold).
    sessionRepository.touch(
        session.getId(), LocalDateTime.now().minusDays(2), session.getExpiresAt());

    LocalDateTime before =
        sessionRepository
            .findByTokenHash(sessionService.sha256HexForTest(raw))
            .orElseThrow()
            .getLastSeenAt();

    assertThat(sessionService.resolve(raw)).isPresent();

    LocalDateTime after =
        sessionRepository
            .findByTokenHash(sessionService.sha256HexForTest(raw))
            .orElseThrow()
            .getLastSeenAt();
    assertThat(after).isAfter(before);
  }

  @Test
  void revokeClearsCookie() {
    AppUserEntity admin = seedAdmin("clear@example.com");
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse createResponse = new MockHttpServletResponse();
    sessionService.create(admin, false, request, createResponse);
    String raw = rawTokenFrom(createResponse);

    MockHttpServletResponse logoutResponse = new MockHttpServletResponse();
    sessionService.revoke(raw, logoutResponse);
    String cleared = logoutResponse.getHeader("Set-Cookie");
    assertThat(cleared).contains("ezac_session=");
    assertThat(cleared).contains("Max-Age=0");
  }

  @Test
  void resolveCapsSlideAtAbsoluteLifetimeCeiling() {
    AppUserEntity admin = seedAdmin("absolute@example.com");
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    sessionService.create(admin, false, request, response);
    String raw = rawTokenFrom(response);

    String tokenHash = sessionService.sha256HexForTest(raw);
    Long sessionId = sessionRepository.findByTokenHash(tokenHash).orElseThrow().getId();

    // Backdate creation to ~89 days ago and make last_seen_at stale so resolve() slides.
    // expires_at stays in the future so the session is still valid at resolve time.
    LocalDateTime createdAt = LocalDateTime.now().minusDays(89);
    jdbcTemplate.update(
        "UPDATE user_session SET created_at = ?, last_seen_at = ?, expires_at = ? WHERE id = ?",
        createdAt,
        LocalDateTime.now().minusDays(2),
        LocalDateTime.now().plusDays(1),
        sessionId);

    assertThat(sessionService.resolve(raw)).isPresent();

    LocalDateTime slidExpiry =
        sessionRepository.findByTokenHash(tokenHash).orElseThrow().getExpiresAt();
    // The slide must be capped at createdAt + 90d (~1 day from now), NOT now + 60d.
    LocalDateTime absoluteMax = createdAt.plusDays(90);
    assertThat(slidExpiry).isCloseTo(absoluteMax, within(5, java.time.temporal.ChronoUnit.SECONDS));
    // Sanity: the uncapped slide (now + 60d) is far past the absolute ceiling — confirm we capped.
    assertThat(slidExpiry).isBefore(LocalDateTime.now().plusDays(2));
  }

  @Test
  void resolvePreservesMfaSatisfiedTrue() {
    AppUserEntity admin = seedAdmin("mfa@example.com");
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    sessionService.create(admin, true, request, response);
    String raw = rawTokenFrom(response);

    Optional<AuthPrincipal> principal = sessionService.resolve(raw);
    assertThat(principal).isPresent();
    assertThat(principal.get().mfaSatisfied()).isTrue();
  }
}
