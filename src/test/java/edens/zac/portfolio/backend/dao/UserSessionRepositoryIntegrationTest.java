package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.entity.UserSessionEntity;
import edens.zac.portfolio.backend.types.UserStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class UserSessionRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private UserSessionRepository sessionRepository;
  @Autowired private AppUserRepository userRepository;

  private Long seedUser(String email) {
    return userRepository.insert(
        AppUserEntity.builder()
            .email(email)
            .name(email)
            .webauthnUserHandle(UUID.randomUUID())
            .status(UserStatus.ACTIVE)
            .build());
  }

  private UserSessionEntity newSession(Long userId, String tokenHash) {
    return UserSessionEntity.builder()
        .userId(userId)
        .tokenHash(tokenHash)
        .mfaSatisfied(false)
        .ip("203.0.113.1")
        .userAgent("JUnit")
        .expiresAt(LocalDateTime.now().plusDays(60))
        .build();
  }

  @Test
  void insertThenFindByTokenHashRoundTrips() {
    Long userId = seedUser("sess@example.com");
    Long id = sessionRepository.insert(newSession(userId, "hash-aaa"));
    assertThat(id).isNotNull();

    Optional<UserSessionEntity> found = sessionRepository.findByTokenHash("hash-aaa");
    assertThat(found).isPresent();
    assertThat(found.get().getUserId()).isEqualTo(userId);
    assertThat(found.get().isMfaSatisfied()).isFalse();
    assertThat(found.get().getRevokedAt()).isNull();
    assertThat(found.get().getExpiresAt()).isNotNull();
    assertThat(found.get().getLastSeenAt()).isNotNull();
  }

  @Test
  void touchUpdatesLastSeenAndExpiry() {
    Long userId = seedUser("touch@example.com");
    sessionRepository.insert(newSession(userId, "hash-touch"));
    UserSessionEntity before = sessionRepository.findByTokenHash("hash-touch").orElseThrow();

    LocalDateTime newSeen = LocalDateTime.now().plusMinutes(5);
    LocalDateTime newExpiry = before.getExpiresAt().plusDays(1);
    sessionRepository.touch(before.getId(), newSeen, newExpiry);

    UserSessionEntity after = sessionRepository.findByTokenHash("hash-touch").orElseThrow();
    assertThat(after.getLastSeenAt()).isAfter(before.getLastSeenAt());
    assertThat(after.getExpiresAt()).isAfter(before.getExpiresAt());
  }

  @Test
  void revokeByTokenHashSetsRevokedAt() {
    Long userId = seedUser("revoke@example.com");
    sessionRepository.insert(newSession(userId, "hash-rev"));
    sessionRepository.revokeByTokenHash("hash-rev");

    UserSessionEntity after = sessionRepository.findByTokenHash("hash-rev").orElseThrow();
    assertThat(after.getRevokedAt()).isNotNull();
  }

  @Test
  void duplicateTokenHashViolatesUniqueConstraint() {
    Long userId = seedUser("dup-token@example.com");
    sessionRepository.insert(newSession(userId, "hash-dup"));
    assertThatThrownBy(() -> sessionRepository.insert(newSession(userId, "hash-dup")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void deletingUserCascadesSessions() {
    Long userId = seedUser("cascade-sess@example.com");
    sessionRepository.insert(newSession(userId, "hash-cascade"));
    userRepository.findById(userId).orElseThrow();

    // delete via the user repo path is not exposed; use a direct cascade check through session
    // lookup
    // after removing the parent. We rely on the FK ON DELETE CASCADE proven in the migration test;
    // here we assert the row is gone once the parent is removed.
    org.springframework.jdbc.core.JdbcTemplate jdbc =
        (org.springframework.jdbc.core.JdbcTemplate)
            org.springframework.test.util.ReflectionTestUtils.getField(
                userRepository, "jdbcTemplate");
    jdbc.update("DELETE FROM users WHERE id = ?", userId);

    assertThat(sessionRepository.findByTokenHash("hash-cascade")).isEmpty();
  }
}
