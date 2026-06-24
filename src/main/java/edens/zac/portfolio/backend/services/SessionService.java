package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.dao.UserSessionRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.entity.UserSessionEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * Owns the opaque, DB-backed session lifecycle. A 256-bit CSPRNG token is sent raw in the {@code
 * ezac_session} cookie; only its SHA-256 hash is persisted (a DB leak never yields a usable
 * cookie). Sessions slide a 60-day window and can be revoked instantly. Cookie construction lives
 * here so controllers stay focused on HTTP wiring.
 */
@Service
@Slf4j
public class SessionService {

  private static final String COOKIE_NAME = "ezac_session";

  private final UserSessionRepository sessionRepository;
  private final AppUserRepository appUserRepository;
  private final boolean cookieSecure;
  private final long ttlDays;
  private final long refreshThresholdHours;
  private final long maxLifetimeDays;

  /**
   * Spring constructor: binds config and the repositories.
   *
   * @param sessionRepository the repository for {@code user_session} rows
   * @param appUserRepository the repository for {@code app_user} rows
   * @param cookieSecure whether the {@code ezac_session} cookie should carry the {@code Secure}
   *     flag (false in dev)
   * @param ttlDays sliding idle TTL in days; the cookie {@code Max-Age} matches and each slide
   *     extends idle expiry to {@code now + ttlDays}
   * @param refreshThresholdHours how many hours of inactivity before the sliding window is bumped
   * @param maxLifetimeDays absolute session ceiling in days from creation; a session can never
   *     slide past {@code createdAt + maxLifetimeDays}, bounding total lifetime per OWASP
   */
  public SessionService(
      UserSessionRepository sessionRepository,
      AppUserRepository appUserRepository,
      @Value("${app.auth.cookie-secure:true}") boolean cookieSecure,
      @Value("${app.auth.session.ttl-days:60}") long ttlDays,
      @Value("${app.auth.session.refresh-threshold-hours:24}") long refreshThresholdHours,
      @Value("${app.auth.session.max-lifetime-days:90}") long maxLifetimeDays) {
    this.sessionRepository = sessionRepository;
    this.appUserRepository = appUserRepository;
    this.cookieSecure = cookieSecure;
    this.ttlDays = ttlDays;
    this.refreshThresholdHours = refreshThresholdHours;
    this.maxLifetimeDays = maxLifetimeDays;
  }

  /**
   * Mint a new session for {@code user}, persist its hashed token, and write the {@code
   * ezac_session} cookie onto {@code response}.
   *
   * @param user the authenticated principal
   * @param mfaSatisfied true for a user-verified passkey login, false for break-glass password
   * @param request used only to record the IP and User-Agent
   * @param response the Set-Cookie sink
   */
  public void create(
      AppUserEntity user,
      boolean mfaSatisfied,
      HttpServletRequest request,
      HttpServletResponse response) {
    String raw = TokenUtil.generateRawToken();

    UserSessionEntity session =
        UserSessionEntity.builder()
            .userId(user.getId())
            .tokenHash(TokenUtil.sha256Hex(raw))
            .mfaSatisfied(mfaSatisfied)
            .ip(request.getHeader("X-Real-IP"))
            .userAgent(truncate(request.getHeader("User-Agent"), 255))
            .expiresAt(LocalDateTime.now().plusDays(ttlDays))
            .build();
    sessionRepository.insert(session);

    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(COOKIE_NAME, raw)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Strict")
            .path("/")
            .maxAge(Duration.ofDays(ttlDays))
            .build()
            .toString());
  }

  /**
   * Resolve a raw cookie token to a principal. Returns empty if the session is unknown, revoked, or
   * expired. Two timeouts bound the session: an <em>idle</em> timeout of {@code ttl-days} of
   * inactivity (slid forward on each resolve once {@code last_seen_at} is older than the refresh
   * threshold), and an <em>absolute</em> ceiling of {@code createdAt + max-lifetime-days} that the
   * slide can never cross. Once the slid expiry reaches the absolute ceiling, the session stops
   * renewing and lapses, so an actively-used session cannot live forever.
   *
   * @param rawToken the raw value read from the {@code ezac_session} cookie
   * @return the principal, or empty when the session is not currently valid
   */
  public Optional<AuthPrincipal> resolve(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return Optional.empty();
    }
    Optional<UserSessionEntity> maybeSession =
        sessionRepository.findByTokenHash(TokenUtil.sha256Hex(rawToken));
    if (maybeSession.isEmpty()) {
      return Optional.empty();
    }
    UserSessionEntity session = maybeSession.get();
    LocalDateTime now = LocalDateTime.now();
    if (session.getRevokedAt() != null || session.getExpiresAt().isBefore(now)) {
      return Optional.empty();
    }

    if (session.getLastSeenAt().isBefore(now.minusHours(refreshThresholdHours))) {
      // Slide the idle window to now + ttlDays, but never past the absolute ceiling. The capped
      // expires_at then enforces the absolute timeout via the expires_at < now rejection above.
      LocalDateTime absoluteMax = session.getCreatedAt().plusDays(maxLifetimeDays);
      LocalDateTime slideTo = now.plusDays(ttlDays);
      LocalDateTime newExpiry = slideTo.isBefore(absoluteMax) ? slideTo : absoluteMax;
      sessionRepository.touch(session.getId(), now, newExpiry);
    }

    Optional<AppUserEntity> maybeUser = appUserRepository.findById(session.getUserId());
    if (maybeUser.isEmpty()) {
      return Optional.empty();
    }
    AppUserEntity user = maybeUser.get();
    return Optional.of(
        new AuthPrincipal(user.getId(), user.getEmail(), user.getRole(), session.isMfaSatisfied()));
  }

  /**
   * Revoke the session identified by {@code rawToken} and clear the cookie on {@code response}.
   *
   * @param rawToken the raw value read from the {@code ezac_session} cookie (may be null)
   * @param response the Set-Cookie sink for the cleared cookie
   */
  public void revoke(String rawToken, HttpServletResponse response) {
    if (rawToken != null && !rawToken.isBlank()) {
      sessionRepository.revokeByTokenHash(TokenUtil.sha256Hex(rawToken));
    }
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(COOKIE_NAME, "")
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Strict")
            .path("/")
            .maxAge(0)
            .build()
            .toString());
  }

  /**
   * Test-only hook exposing the at-rest hash so tests can look a session up by its stored key
   * without duplicating the hashing algorithm.
   *
   * @param rawToken the raw cookie value
   * @return the SHA-256 hex digest stored in {@code user_session.token_hash}
   */
  String sha256HexForTest(String rawToken) {
    return TokenUtil.sha256Hex(rawToken);
  }

  private static String truncate(String value, int max) {
    if (value == null) {
      return null;
    }
    return value.length() <= max ? value : value.substring(0, max);
  }
}
