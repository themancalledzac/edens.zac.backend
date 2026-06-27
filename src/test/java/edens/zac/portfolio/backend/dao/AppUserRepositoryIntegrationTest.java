package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.types.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class AppUserRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private AppUserRepository repository;

  private AppUserEntity newUser(String email) {
    return AppUserEntity.builder()
        .email(email)
        .name(email)
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
            .passwordHash("{bcrypt}$2a$10$abc")
            .webauthnUserHandle(handle)
            .name("Round Trip")
            .status(UserStatus.ACTIVE)
            .build();

    Long id = repository.insert(user);
    assertThat(id).isNotNull();

    Optional<AppUserEntity> found = repository.findByEmail("round@example.com");
    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(id);
    assertThat(found.get().getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(found.get().getPasswordHash()).isEqualTo("{bcrypt}$2a$10$abc");
    assertThat(found.get().getWebauthnUserHandle()).isEqualTo(handle);
    assertThat(found.get().getName()).isEqualTo("Round Trip");
    assertThat(found.get().getCreatedAt()).isNotNull();
  }

  @Test
  void findByWebauthnUserHandleRoundTrips() {
    UUID handle = UUID.randomUUID();
    repository.insert(
        AppUserEntity.builder()
            .email("handle@example.com")
            .name("Handle")
            .webauthnUserHandle(handle)
            .status(UserStatus.ACTIVE)
            .build());

    assertThat(repository.findByWebauthnUserHandle(handle)).isPresent();
    assertThat(repository.findByWebauthnUserHandle(UUID.randomUUID())).isEmpty();
  }

  @Test
  void updatePasswordHashPersists() {
    Long id = repository.insert(newUser("pw@example.com"));
    repository.updatePasswordHash(id, "{bcrypt}$2a$10$newhash");
    assertThat(repository.findById(id)).isPresent();
    assertThat(repository.findById(id).get().getPasswordHash()).isEqualTo("{bcrypt}$2a$10$newhash");
  }

  @Test
  void duplicateEmailViolatesUniqueConstraint() {
    repository.insert(newUser("unique@example.com"));
    assertThatThrownBy(() -> repository.insert(newUser("unique@example.com")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void updateStatusPersists() {
    Long id =
        repository.insert(
            AppUserEntity.builder()
                .email("status@example.com")
                .name("Status")
                .webauthnUserHandle(UUID.randomUUID())
                .status(UserStatus.INVITED)
                .build());
    repository.updateStatus(id, UserStatus.ACTIVE);
    assertThat(repository.findById(id)).isPresent();
    assertThat(repository.findById(id).get().getStatus()).isEqualTo(UserStatus.ACTIVE);
  }

  @Test
  void updateNamePersists() {
    Long id = repository.insert(newUser("displayname@example.com"));
    repository.updateName(id, "Alice Smith");
    assertThat(repository.findById(id)).isPresent();
    assertThat(repository.findById(id).get().getName()).isEqualTo("Alice Smith");
  }

  @Test
  void updateDescriptionPersists() {
    Long id = repository.insert(newUser("desc@example.com"));
    // description is NULL on fresh insert
    assertThat(repository.findById(id).get().getDescription()).isNull();

    repository.updateDescription(id, "Photographer and traveller.");
    assertThat(repository.findById(id).get().getDescription())
        .isEqualTo("Photographer and traveller.");
  }

  @Test
  void updateDescriptionCanBeCleared() {
    Long id = repository.insert(newUser("desc-clear@example.com"));
    repository.updateDescription(id, "Some description");
    repository.updateDescription(id, null);
    assertThat(repository.findById(id).get().getDescription()).isNull();
  }
}
