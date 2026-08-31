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
import java.util.Arrays;
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

  /** Extract the raw {@code ezac_session} value from the Set-Cookie header. */
  private String rawTokenFrom(MockHttpServletResponse response) {
    String setCookie = response.getHeader("Set-Cookie");
    assertThat(setCookie).isNotNull().contains("ezac_session=");
    String afterName =
        setCookie.substring(setCookie.indexOf("ezac_session=") + "ezac_session=".length());
    int semi = afterName.indexOf(';');
    return semi >= 0 ? afterName.substring(0, semi) : afterName;
  }

  /**
   * The raw token is not stored: looking the session up by the raw value as if it were the hash
   * must miss, while resolving with that same raw value succeeds.
   */
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
    assertThat(sessionRepository.findByTokenHash(raw)).isEmpty();
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

  /**
   * Disabling an account must kill the sessions it already holds, without anyone revoking them. The
   * session row here stays live -- unrevoked and unexpired -- so the only thing that can reject it
   * is the status test in {@code resolve}. Strip that test and this goes green with a principal in
   * hand, which is the mutation it exists to catch. It has to run against a real database: every
   * test that mocks {@code AppUserRepository} would return an ACTIVE entity regardless of what the
   * column says.
   */
  @Test
  void resolveRejectsSessionWhoseAccountWasDisabled() {
    AppUserEntity admin = seedAdmin("disabled@example.com");
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    sessionService.create(admin, false, request, response);
    String raw = rawTokenFrom(response);
    assertThat(sessionService.resolve(raw)).isPresent();

    userRepository.updateStatus(admin.getId(), UserStatus.DISABLED);

    assertThat(sessionService.resolve(raw)).isEmpty();
    UserSessionEntity session =
        sessionRepository.findByTokenHash(sessionService.sha256HexForTest(raw)).orElseThrow();
    assertThat(session.getRevokedAt()).isNull();
    assertThat(session.getExpiresAt()).isAfter(LocalDateTime.now());
  }

  /**
   * S-8: the admin status change revokes the session rows themselves, so the account stops
   * resolving without depending on {@code resolve} reading status. Asserted on the row, not on
   * {@code resolve}, because {@code resolve} already rejects a DISABLED account either way -- the
   * revoked timestamp is the only thing that distinguishes this fix from the S-1 guard. Mutation
   * this catches: drop the {@code revokeAllForUser} call and {@code revokedAt} stays null.
   */
  @Test
  void revokeAllForStatusRevokesTheSessionRowsOfADisabledAccount() {
    AppUserEntity admin = seedAdmin("revoke-status@example.com");
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    sessionService.create(admin, false, request, response);
    String raw = rawTokenFrom(response);

    assertThat(sessionService.revokeAllForStatus(admin.getId(), UserStatus.DISABLED)).isEqualTo(1);

    UserSessionEntity session =
        sessionRepository.findByTokenHash(sessionService.sha256HexForTest(raw)).orElseThrow();
    assertThat(session.getRevokedAt()).isNotNull();
    assertThat(sessionService.resolve(raw)).isEmpty();
  }

  /**
   * The sweep uses an ACTIVE-only allowlist rather than the {@code {INVITED, ACTIVE}} one that
   * governs invites: a demotion to INVITED leaves sessions that can never resolve again, so they
   * are revoked too. Mutation this catches: reuse {@code UserInviteService.mayAcceptInvite} here
   * and the demoted account's rows stay live forever.
   */
  @Test
  void revokeAllForStatusRevokesOnDemotionToInvited() {
    AppUserEntity admin = seedAdmin("revoke-invited@example.com");
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    sessionService.create(admin, false, request, response);
    String raw = rawTokenFrom(response);

    assertThat(sessionService.revokeAllForStatus(admin.getId(), UserStatus.INVITED)).isEqualTo(1);

    assertThat(
            sessionRepository
                .findByTokenHash(sessionService.sha256HexForTest(raw))
                .orElseThrow()
                .getRevokedAt())
        .isNotNull();
  }

  /**
   * The scope guard on the other side: re-enabling an account must not log out the session the
   * admin is holding. Mutation this catches: revoke on every status write and an admin who
   * re-activates a user kills any session that user just established.
   */
  @Test
  void revokeAllForStatusLeavesAnActiveAccountsSessionsAlone() {
    AppUserEntity admin = seedAdmin("revoke-active@example.com");
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    sessionService.create(admin, false, request, response);
    String raw = rawTokenFrom(response);

    assertThat(sessionService.revokeAllForStatus(admin.getId(), UserStatus.ACTIVE)).isZero();

    assertThat(sessionService.resolve(raw)).isPresent();
  }

  /** The status test is an ACTIVE allowlist, so a demotion to INVITED lapses the session too. */
  @Test
  void resolveRejectsSessionWhoseAccountWasReturnedToInvited() {
    AppUserEntity admin = seedAdmin("reinvited@example.com");
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    sessionService.create(admin, false, request, response);
    String raw = rawTokenFrom(response);
    assertThat(sessionService.resolve(raw)).isPresent();

    userRepository.updateStatus(admin.getId(), UserStatus.INVITED);

    assertThat(sessionService.resolve(raw)).isEmpty();
  }

  /**
   * Backdates {@code last_seen_at} past the 24h refresh threshold so the slide fires. Mutation this
   * catches: delete the slide block, or move it below the {@code return}, and {@code last_seen_at}
   * stays where the test put it.
   */
  @Test
  void resolveSlidesLastSeenWhenStale() {
    AppUserEntity admin = seedAdmin("slide@example.com");
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    sessionService.create(admin, false, request, response);
    String raw = rawTokenFrom(response);

    UserSessionEntity session =
        sessionRepository.findByTokenHash(sessionService.sha256HexForTest(raw)).orElseThrow();
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

  /**
   * U-4: the slide runs below the status test, so a request rejected for a non-ACTIVE account does
   * not extend the session it was rejected on. The session is given a stale {@code last_seen_at} so
   * the slide would fire if it were still reachable. Mutation this catches: move the slide block
   * back above the {@code findById} lookup or the {@code mayHoldSession} test, and both timestamps
   * move.
   */
  @Test
  void resolveDoesNotSlideTheWindowOfADisabledAccountsSession() {
    AppUserEntity admin = seedAdmin("no-slide-on-reject@example.com");
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    sessionService.create(admin, false, request, response);
    String raw = rawTokenFrom(response);
    String tokenHash = sessionService.sha256HexForTest(raw);

    UserSessionEntity session = sessionRepository.findByTokenHash(tokenHash).orElseThrow();
    LocalDateTime staleLastSeen = LocalDateTime.now().minusDays(2);
    sessionRepository.touch(session.getId(), staleLastSeen, session.getExpiresAt());
    UserSessionEntity before = sessionRepository.findByTokenHash(tokenHash).orElseThrow();

    userRepository.updateStatus(admin.getId(), UserStatus.DISABLED);

    assertThat(sessionService.resolve(raw)).isEmpty();

    UserSessionEntity after = sessionRepository.findByTokenHash(tokenHash).orElseThrow();
    assertThat(after.getLastSeenAt())
        .isCloseTo(before.getLastSeenAt(), within(1, java.time.temporal.ChronoUnit.SECONDS));
    assertThat(after.getExpiresAt())
        .isCloseTo(before.getExpiresAt(), within(1, java.time.temporal.ChronoUnit.SECONDS));
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

  /**
   * Creation is backdated to ~89 days ago with a stale {@code last_seen_at} so the slide fires,
   * while {@code expires_at} stays in the future so the session is still valid at resolve time. The
   * slide must land on {@code createdAt + 90d}, roughly a day from now, not on {@code now + 60d};
   * the second assertion is the sanity check that the uncapped slide would have blown past the
   * ceiling.
   */
  @Test
  void resolveCapsSlideAtAbsoluteLifetimeCeiling() {
    AppUserEntity admin = seedAdmin("absolute@example.com");
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    sessionService.create(admin, false, request, response);
    String raw = rawTokenFrom(response);

    String tokenHash = sessionService.sha256HexForTest(raw);
    Long sessionId = sessionRepository.findByTokenHash(tokenHash).orElseThrow().getId();

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
    LocalDateTime absoluteMax = createdAt.plusDays(90);
    assertThat(slidExpiry).isCloseTo(absoluteMax, within(5, java.time.temporal.ChronoUnit.SECONDS));
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

  /**
   * Pins the allowlist itself, written out, over the whole enum. The four call sites now route
   * through {@link SessionService#mayHoldSession}, and both parameterized login tests ({@code
   * AuthControllerTest}, {@code WebAuthnServiceTest}) build their cases by asking the predicate
   * which statuses it refuses. That coupling is the point of the refactor and it is also why those
   * two tests cannot police the predicate: widening {@code mayHoldSession} makes them emit fewer
   * cases rather than fail. Verified, not assumed -- mutating the predicate to {@code != DISABLED}
   * took AuthControllerTest from 13 cases to 11 and WebAuthnServiceTest from 12 to 10, both still
   * green.
   *
   * <p>So the guard against a silently-widened allowlist lives here, and only here, stated as a
   * literal. Adding a fifth {@code UserStatus} reddens this test, which is the intended cost:
   * session eligibility becomes a decision someone makes rather than a default they inherit.
   */
  @Test
  void mayHoldSessionAdmitsActiveAndNothingElse() {
    assertThat(Arrays.stream(UserStatus.values()).filter(SessionService::mayHoldSession))
        .containsExactly(UserStatus.ACTIVE);
    assertThat(Arrays.stream(UserStatus.values()).filter(s -> !SessionService.mayHoldSession(s)))
        .containsExactlyInAnyOrder(UserStatus.INVITED, UserStatus.DISABLED, UserStatus.PERSON);
  }
}
