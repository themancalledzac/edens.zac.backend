package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.dao.UserInviteRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.entity.UserInviteEntity;
import edens.zac.portfolio.backend.types.UserStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the lifecycle of single-use invite tokens. Raw tokens are never stored; only their
 * SHA-256 hash is persisted. Tokens expire after 7 days and become invalid after first use.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserInviteService {

  static final long INVITE_TTL_DAYS = 7;

  private final UserInviteRepository inviteRepository;
  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final SessionService sessionService;

  /** The outcome of {@link #accept}, which the controller maps to a status code. */
  public enum AcceptResult {
    /** The invite was redeemed, the account activated, and a session minted. */
    ACCEPTED,
    /** The token was unusable, or the account may not complete an accept. */
    REJECTED
  }

  /**
   * The statuses an account may hold and still complete an accept. {@code INVITED} is onboarding;
   * {@code ACTIVE} is the admin-issued password reset, which redeems through the same endpoint (see
   * {@link #regenerateInvite}). Every other status is refused, which is what stops a {@code
   * DISABLED} holder of an unexpired invite from re-activating their own account.
   *
   * <p>Stated as an allowlist rather than a {@code != DISABLED} denylist on purpose: {@link
   * UserStatus} also has {@code PERSON}, a tag-only identity with no login account, and a denylist
   * would let one become a real account.
   *
   * @param status the account's current status
   * @return whether an invite held against this account may still be redeemed
   */
  public static boolean mayAcceptInvite(UserStatus status) {
    return status == UserStatus.INVITED || status == UserStatus.ACTIVE;
  }

  /**
   * Create a new invite for an existing {@code app_user} row. Generates a 256-bit CSPRNG token,
   * stores its hash with a 7-day expiry, and returns the raw token for embedding in the invite URL.
   *
   * @param userId the id of the {@code app_user} record the invite is issued for
   * @param email the invite target email (matches {@code app_user.email} at issue time)
   * @return the raw token — caller must embed this in the invite link; it is never stored
   */
  @Transactional
  public String createInvite(Long userId, String email) {
    String raw = TokenUtil.generateRawToken();
    UserInviteEntity entity =
        UserInviteEntity.builder()
            .userId(userId)
            .tokenHash(TokenUtil.sha256Hex(raw))
            .email(email)
            .expiresAt(LocalDateTime.now().plusDays(INVITE_TTL_DAYS))
            .build();
    inviteRepository.insert(entity);
    return raw;
  }

  /**
   * Re-issue an invite for an existing user: invalidate any outstanding unused invites, then mint a
   * fresh one. Serves both resend (for an {@code INVITED} user) and password-reset (for an {@code
   * ACTIVE} user, who completes the same accept flow). The raw token is returned for the link; the
   * old link, if anyone still holds it, is dead. Does not change the user's status.
   *
   * @param userId the id of the {@code app_user} record to re-invite
   * @param email the user's current email, captured on the fresh invite
   * @return the raw token for the new invite link
   */
  @Transactional
  public String regenerateInvite(Long userId, String email) {
    inviteRepository.invalidateUnusedForUser(userId);
    return createInvite(userId, email);
  }

  /**
   * Kill any still-unused invites for a user without minting a replacement. Used when the account's
   * login email changes out from under an outstanding invite: the old link was bound to the prior
   * address and must not remain redeemable, so the admin has to issue a fresh invite to the new
   * address. Used invites are untouched.
   *
   * @param userId the id of the {@code app_user} record whose outstanding invites should be killed
   * @return the number of invites invalidated
   */
  @Transactional
  public int invalidateInvites(Long userId) {
    return inviteRepository.invalidateUnusedForUser(userId);
  }

  /**
   * Validate a raw invite token. Returns the invite entity if the token is found, unexpired, and
   * not yet redeemed. Returns empty for unknown, expired, or already-used tokens.
   *
   * @param rawToken the raw token from the invite URL
   * @return the invite entity if valid, or empty
   */
  @Transactional(readOnly = true)
  public Optional<UserInviteEntity> validate(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return Optional.empty();
    }
    Optional<UserInviteEntity> maybeInvite =
        inviteRepository.findByTokenHash(TokenUtil.sha256Hex(rawToken));
    if (maybeInvite.isEmpty()) {
      return Optional.empty();
    }
    UserInviteEntity invite = maybeInvite.get();
    if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
      return Optional.empty();
    }
    if (invite.getUsedAt() != null) {
      return Optional.empty();
    }
    return Optional.of(invite);
  }

  /**
   * Redeem an invite into a usable account: consume the token, set the password and display name,
   * activate the account, and mint a session.
   *
   * <p>The account's status is read and tested against {@link #mayAcceptInvite} <em>before</em> the
   * activating write, so a status the invite lifecycle does not sanction never reaches the flip to
   * {@code ACTIVE}. The redeem stands either way, so a token presented against such an account is
   * spent rather than left live.
   *
   * @param rawToken the raw token from the invite URL
   * @param displayName the chosen display name
   * @param rawPassword the chosen password, encoded here
   * @param request the servlet request (IP / User-Agent for the session row)
   * @param response the Set-Cookie sink for the session cookie
   * @return {@link AcceptResult#ACCEPTED}, or {@link AcceptResult#REJECTED} if the token is
   *     unknown, expired, already used, or the account may not complete an accept
   * @throws IllegalStateException if the redeemed invite points at no {@code app_user} row
   */
  @Transactional
  public AcceptResult accept(
      String rawToken,
      String displayName,
      String rawPassword,
      HttpServletRequest request,
      HttpServletResponse response) {
    Optional<UserInviteEntity> maybeInvite = redeem(rawToken);
    if (maybeInvite.isEmpty()) {
      log.warn("Invite accept rejected: token already used or expired");
      return AcceptResult.REJECTED;
    }

    Long userId = maybeInvite.get().getUserId();
    AppUserEntity user =
        appUserRepository
            .findById(userId)
            .orElseThrow(() -> new IllegalStateException("User disappeared after invite redeem"));

    if (!mayAcceptInvite(user.getStatus())) {
      log.warn("Invite accept rejected: userId={} status={}", userId, user.getStatus());
      return AcceptResult.REJECTED;
    }

    appUserRepository.updatePasswordHash(userId, passwordEncoder.encode(rawPassword));
    appUserRepository.updateName(userId, displayName);
    appUserRepository.updateStatus(userId, UserStatus.ACTIVE);

    sessionService.create(user, false, request, response);
    log.info("Invite accepted: userId={}", userId);
    return AcceptResult.ACCEPTED;
  }

  /**
   * Atomically redeem a raw invite token. The single-use guarantee lives in the DB write: after
   * confirming the token is known and unexpired, the conditional {@code markUsedIfUnused} update is
   * the gate. Only the first caller's update affects a row; a concurrent or repeat redeem affects
   * zero rows and yields empty. This holds regardless of how the controller sequences calls.
   *
   * @param rawToken the raw token from the invite URL
   * @return the redeemed invite entity, or empty if the token is unknown, expired, or already used
   */
  @Transactional
  public Optional<UserInviteEntity> redeem(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return Optional.empty();
    }
    Optional<UserInviteEntity> maybeInvite =
        inviteRepository.findByTokenHash(TokenUtil.sha256Hex(rawToken));
    if (maybeInvite.isEmpty()) {
      return Optional.empty();
    }
    UserInviteEntity invite = maybeInvite.get();
    if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
      return Optional.empty();
    }
    if (inviteRepository.markUsedIfUnused(invite.getId(), LocalDateTime.now()) == 0) {
      return Optional.empty();
    }
    return Optional.of(invite);
  }
}
