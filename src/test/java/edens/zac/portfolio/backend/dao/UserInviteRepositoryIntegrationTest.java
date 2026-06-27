package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.entity.UserInviteEntity;
import edens.zac.portfolio.backend.types.UserStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class UserInviteRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private UserInviteRepository inviteRepository;
  @Autowired private AppUserRepository userRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private Long seedUser(String email) {
    return userRepository.insert(
        AppUserEntity.builder()
            .email(email)
            .name(email)
            .webauthnUserHandle(UUID.randomUUID())
            .status(UserStatus.INVITED)
            .build());
  }

  private UserInviteEntity newInvite(Long userId, String tokenHash, String email) {
    return UserInviteEntity.builder()
        .userId(userId)
        .tokenHash(tokenHash)
        .email(email)
        .expiresAt(LocalDateTime.now().plusDays(7))
        .build();
  }

  @Test
  void insertThenFindByTokenHashRoundTrips() {
    Long userId = seedUser("invite-rt@example.com");
    Long id = inviteRepository.insert(newInvite(userId, "hash-aaa", "invite-rt@example.com"));
    assertThat(id).isNotNull();

    Optional<UserInviteEntity> found = inviteRepository.findByTokenHash("hash-aaa");
    assertThat(found).isPresent();
    assertThat(found.get().getUserId()).isEqualTo(userId);
    assertThat(found.get().getEmail()).isEqualTo("invite-rt@example.com");
    assertThat(found.get().getUsedAt()).isNull();
    assertThat(found.get().getExpiresAt()).isNotNull();
    assertThat(found.get().getCreatedAt()).isNotNull();
  }

  @Test
  void findByTokenHashReturnsEmptyForUnknownHash() {
    assertThat(inviteRepository.findByTokenHash("no-such-hash")).isEmpty();
  }

  @Test
  void markUsedIfUnusedFlipsOnceThenNoOps() {
    Long userId = seedUser("invite-used@example.com");
    Long id = inviteRepository.insert(newInvite(userId, "hash-mark", "invite-used@example.com"));

    int firstAffected = inviteRepository.markUsedIfUnused(id, LocalDateTime.now());
    assertThat(firstAffected).isEqualTo(1);

    UserInviteEntity after = inviteRepository.findByTokenHash("hash-mark").orElseThrow();
    assertThat(after.getUsedAt()).isNotNull();

    int secondAffected = inviteRepository.markUsedIfUnused(id, LocalDateTime.now());
    assertThat(secondAffected).isZero();
  }

  @Test
  void duplicateTokenHashViolatesUniqueConstraint() {
    Long userId = seedUser("invite-dup@example.com");
    inviteRepository.insert(newInvite(userId, "hash-dup", "invite-dup@example.com"));
    assertThatThrownBy(
            () -> inviteRepository.insert(newInvite(userId, "hash-dup", "invite-dup2@example.com")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void deletingUserCascadesInvite() {
    Long userId = seedUser("invite-cascade@example.com");
    inviteRepository.insert(newInvite(userId, "hash-cascade", "invite-cascade@example.com"));

    jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);

    assertThat(inviteRepository.findByTokenHash("hash-cascade")).isEmpty();
  }
}
