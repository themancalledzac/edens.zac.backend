package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.types.Role;
import edens.zac.portfolio.backend.types.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class AppUserRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private AppUserRepository repository;

  private AppUserEntity newUser(String email, Role role) {
    return AppUserEntity.builder()
        .email(email)
        .role(role)
        .webauthnUserHandle(UUID.randomUUID())
        .status(UserStatus.ACTIVE)
        .build();
  }

  @Test
  void insertThenFindByEmailRoundTrips() {
    UUID handle = UUID.randomUUID();
    AppUserEntity user =
        AppUserEntity.builder()
            .email("round@example.com")
            .role(Role.ADMIN)
            .passwordHash("{bcrypt}$2a$10$abc")
            .webauthnUserHandle(handle)
            .displayName("Round Trip")
            .status(UserStatus.ACTIVE)
            .build();

    Long id = repository.insert(user);
    assertThat(id).isNotNull();

    Optional<AppUserEntity> found = repository.findByEmail("round@example.com");
    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(id);
    assertThat(found.get().getRole()).isEqualTo(Role.ADMIN);
    assertThat(found.get().getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(found.get().getPasswordHash()).isEqualTo("{bcrypt}$2a$10$abc");
    assertThat(found.get().getWebauthnUserHandle()).isEqualTo(handle);
    assertThat(found.get().getDisplayName()).isEqualTo("Round Trip");
    assertThat(found.get().getCreatedAt()).isNotNull();
  }

  @Test
  void findByWebauthnUserHandleRoundTrips() {
    UUID handle = UUID.randomUUID();
    repository.insert(
        AppUserEntity.builder()
            .email("handle@example.com")
            .role(Role.CLIENT)
            .webauthnUserHandle(handle)
            .status(UserStatus.ACTIVE)
            .build());

    assertThat(repository.findByWebauthnUserHandle(handle)).isPresent();
    assertThat(repository.findByWebauthnUserHandle(UUID.randomUUID())).isEmpty();
  }

  @Test
  void existsByRoleReflectsInsertedAdmin() {
    assertThat(repository.existsByRole(Role.ADMIN)).isFalse();
    repository.insert(newUser("admin@example.com", Role.ADMIN));
    assertThat(repository.existsByRole(Role.ADMIN)).isTrue();
  }

  @Test
  void updatePasswordHashPersists() {
    Long id = repository.insert(newUser("pw@example.com", Role.ADMIN));
    repository.updatePasswordHash(id, "{bcrypt}$2a$10$newhash");
    assertThat(repository.findById(id)).isPresent();
    assertThat(repository.findById(id).get().getPasswordHash()).isEqualTo("{bcrypt}$2a$10$newhash");
  }

  @Test
  void duplicateEmailViolatesUniqueConstraint() {
    repository.insert(newUser("unique@example.com", Role.ADMIN));
    assertThatThrownBy(() -> repository.insert(newUser("unique@example.com", Role.CLIENT)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
