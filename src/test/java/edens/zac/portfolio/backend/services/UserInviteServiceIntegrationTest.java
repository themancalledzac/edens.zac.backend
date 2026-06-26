package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.dao.UserInviteRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.entity.UserInviteEntity;
import edens.zac.portfolio.backend.types.Role;
import edens.zac.portfolio.backend.types.UserStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class UserInviteServiceIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private UserInviteService inviteService;
  @Autowired private UserInviteRepository inviteRepository;
  @Autowired private AppUserRepository userRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private Long seedUser(String email) {
    return userRepository.insert(
        AppUserEntity.builder()
            .email(email)
            .name(email)
            .role(Role.CLIENT)
            .webauthnUserHandle(UUID.randomUUID())
            .status(UserStatus.INVITED)
            .build());
  }

  @Test
  void createInviteReturnsRawTokenAndStoresHash() {
    Long userId = seedUser("create@example.com");
    String raw = inviteService.createInvite(userId, "create@example.com");

    assertThat(raw).isNotBlank();
    String hash = TokenUtil.sha256Hex(raw);
    Optional<UserInviteEntity> found = inviteRepository.findByTokenHash(hash);
    assertThat(found).isPresent();
    assertThat(found.get().getUserId()).isEqualTo(userId);
    assertThat(found.get().getEmail()).isEqualTo("create@example.com");
    assertThat(found.get().getUsedAt()).isNull();
    assertThat(found.get().getExpiresAt()).isAfter(LocalDateTime.now());
  }

  @Test
  void validateHappyPathReturnsInvite() {
    Long userId = seedUser("valid@example.com");
    String raw = inviteService.createInvite(userId, "valid@example.com");

    Optional<UserInviteEntity> result = inviteService.validate(raw);
    assertThat(result).isPresent();
    assertThat(result.get().getUserId()).isEqualTo(userId);
  }

  @Test
  void validateExpiredTokenReturnsEmpty() {
    Long userId = seedUser("expired@example.com");
    String raw = inviteService.createInvite(userId, "expired@example.com");

    // Force the invite to appear expired by updating expires_at in DB directly
    String hash = TokenUtil.sha256Hex(raw);
    jdbcTemplate.update(
        "UPDATE user_invite SET expires_at = now() - INTERVAL '1 day' WHERE token_hash = ?", hash);

    assertThat(inviteService.validate(raw)).isEmpty();
  }

  @Test
  void validateUsedTokenReturnsEmpty() {
    Long userId = seedUser("used@example.com");
    String raw = inviteService.createInvite(userId, "used@example.com");
    inviteService.redeem(raw);

    assertThat(inviteService.validate(raw)).isEmpty();
  }

  @Test
  void validateUnknownTokenReturnsEmpty() {
    assertThat(inviteService.validate("completely-unknown-token")).isEmpty();
  }

  @Test
  void redeemHappyPathMarksUsedAndReturnsInvite() {
    Long userId = seedUser("redeem@example.com");
    String raw = inviteService.createInvite(userId, "redeem@example.com");

    Optional<UserInviteEntity> result = inviteService.redeem(raw);
    assertThat(result).isPresent();
    assertThat(result.get().getUserId()).isEqualTo(userId);

    // Second redeem must fail (single-use)
    assertThat(inviteService.redeem(raw)).isEmpty();
  }

  @Test
  void redeemIsAtomicSingleUse() {
    Long userId = seedUser("redeem-atomic@example.com");
    String raw = inviteService.createInvite(userId, "redeem-atomic@example.com");
    Long inviteId =
        inviteRepository.findByTokenHash(TokenUtil.sha256Hex(raw)).orElseThrow().getId();

    assertThat(inviteService.redeem(raw)).isPresent();
    assertThat(inviteService.redeem(raw)).isEmpty();

    // The single-use gate lives in the DB write: a repeat conditional update affects no rows.
    assertThat(inviteRepository.markUsedIfUnused(inviteId, LocalDateTime.now())).isZero();
  }

  @Test
  void redeemExpiredTokenReturnsEmpty() {
    Long userId = seedUser("redeem-expired@example.com");
    String raw = inviteService.createInvite(userId, "redeem-expired@example.com");
    String hash = TokenUtil.sha256Hex(raw);
    jdbcTemplate.update(
        "UPDATE user_invite SET expires_at = now() - INTERVAL '1 day' WHERE token_hash = ?", hash);

    assertThat(inviteService.redeem(raw)).isEmpty();
  }

  @Test
  void regenerateInvalidatesPriorUnusedInvitesAndMintsFresh() {
    Long userId = seedUser("regen@example.com");
    String oldRaw = inviteService.createInvite(userId, "regen@example.com");

    String newRaw = inviteService.regenerateInvite(userId, "regen@example.com");

    // The old link is now dead; the freshly-minted link validates.
    assertThat(inviteService.validate(oldRaw)).isEmpty();
    assertThat(inviteService.validate(newRaw)).isPresent();
    assertThat(inviteService.validate(newRaw).orElseThrow().getUserId()).isEqualTo(userId);

    // Exactly one unused invite remains for the user.
    Integer unused =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM user_invite WHERE user_id = ? AND used_at IS NULL",
            Integer.class,
            userId);
    assertThat(unused).isEqualTo(1);
  }
}
