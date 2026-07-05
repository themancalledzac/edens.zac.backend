package edens.zac.portfolio.backend.services;

import edens.zac.portfolio.backend.dao.UserInviteRepository;
import edens.zac.portfolio.backend.entity.UserInviteEntity;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
