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
   * Redeem a raw invite token, marking it used. Callers must first call {@link #validate} and act
   * on its result before calling {@code redeem}. Returns the invite entity so the caller can look
   * up the associated user.
   *
   * @param rawToken the raw token from the invite URL
   * @return the redeemed invite entity, or empty if the token is invalid/already used/expired
   */
  @Transactional
  public Optional<UserInviteEntity> redeem(String rawToken) {
    Optional<UserInviteEntity> maybeInvite = validate(rawToken);
    if (maybeInvite.isEmpty()) {
      return Optional.empty();
    }
    UserInviteEntity invite = maybeInvite.get();
    inviteRepository.markUsed(invite.getId(), LocalDateTime.now());
    return Optional.of(invite);
  }
}
