package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.entity.UserInviteEntity;
import edens.zac.portfolio.backend.types.Role;
import edens.zac.portfolio.backend.types.UserStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class UserInviteRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private UserInviteRepository inviteRepository;
  @Autowired private AppUserRepository userRepository;

  private Long seedUser(String email) {
    return userRepository.insert(
        AppUserEntity.builder()
            .email(email)
            .role(Role.CLIENT)
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
  void markUsedSetsUsedAt() {
    Long userId = seedUser("invite-used@example.com");
    Long id = inviteRepository.insert(newInvite(userId, "hash-mark", "invite-used@example.com"));
    LocalDateTime usedAt = LocalDateTime.now();
    inviteRepository.markUsed(id, usedAt);

    UserInviteEntity after = inviteRepository.findByTokenHash("hash-mark").orElseThrow();
    assertThat(after.getUsedAt()).isNotNull();
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

    org.springframework.jdbc.core.JdbcTemplate jdbc =
        (org.springframework.jdbc.core.JdbcTemplate)
            org.springframework.test.util.ReflectionTestUtils.getField(
                userRepository, "jdbcTemplate");
    jdbc.update("DELETE FROM app_user WHERE id = ?", userId);

    assertThat(inviteRepository.findByTokenHash("hash-cascade")).isEmpty();
  }
}
