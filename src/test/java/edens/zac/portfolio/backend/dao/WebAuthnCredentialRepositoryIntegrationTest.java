package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.entity.WebAuthnCredentialEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Round-trips WebAuthnCredentialRepository against a real Postgres container. */
class WebAuthnCredentialRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private WebAuthnCredentialRepository repository;
  @Autowired private JdbcTemplate jdbcTemplate;

  // Auth tables are truncated after each test by
  // AbstractPostgresIntegrationTest.truncateAuthTables.

  private Long seedUser(String email) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO app_user (email, role, webauthn_user_handle, status) "
            + "VALUES (?, 'ADMIN', gen_random_uuid(), 'ACTIVE') RETURNING id",
        Long.class,
        email);
  }

  @Test
  void insertThenFindByUserIdRoundTrips() {
    Long userId = seedUser("wac-insert@example.com");
    WebAuthnCredentialEntity cred =
        WebAuthnCredentialEntity.builder()
            .userId(userId)
            .credentialId(new byte[] {10, 20, 30})
            .publicKey(new byte[] {40, 50, 60})
            .signCount(0L)
            .transports("internal")
            .label("Test Passkey")
            .build();

    Long id = repository.insert(cred);
    assertThat(id).isNotNull();

    List<WebAuthnCredentialEntity> found = repository.findByUserId(userId);
    assertThat(found).hasSize(1);
    assertThat(found.get(0).getCredentialId()).containsExactly(10, 20, 30);
    assertThat(found.get(0).getPublicKey()).containsExactly(40, 50, 60);
    assertThat(found.get(0).getSignCount()).isZero();
    assertThat(found.get(0).getTransports()).isEqualTo("internal");
    assertThat(found.get(0).getLabel()).isEqualTo("Test Passkey");
    assertThat(found.get(0).getCreatedAt()).isNotNull();
  }

  @Test
  void findByCredentialIdMatchesExactBytes() {
    Long userId = seedUser("wac-bycred@example.com");
    byte[] credId = new byte[] {1, 2, 3, 4, 5};
    repository.insert(
        WebAuthnCredentialEntity.builder()
            .userId(userId)
            .credentialId(credId)
            .publicKey(new byte[] {9})
            .signCount(7L)
            .build());

    Optional<WebAuthnCredentialEntity> hit = repository.findByCredentialId(credId);
    assertThat(hit).isPresent();
    assertThat(hit.get().getSignCount()).isEqualTo(7L);

    Optional<WebAuthnCredentialEntity> miss =
        repository.findByCredentialId(new byte[] {99, 98, 97});
    assertThat(miss).isEmpty();
  }

  @Test
  void updateSignCountAndLastUsedPersists() {
    Long userId = seedUser("wac-update@example.com");
    Long id =
        repository.insert(
            WebAuthnCredentialEntity.builder()
                .userId(userId)
                .credentialId(new byte[] {7, 7, 7})
                .publicKey(new byte[] {8})
                .signCount(0L)
                .build());

    LocalDateTime now = LocalDateTime.now();
    repository.updateSignCountAndLastUsed(id, 42L, now);

    WebAuthnCredentialEntity reloaded = repository.findByUserId(userId).get(0);
    assertThat(reloaded.getSignCount()).isEqualTo(42L);
    assertThat(reloaded.getLastUsedAt()).isNotNull();
  }

  @Test
  void duplicateCredentialIdIsRejected() {
    Long userId = seedUser("wac-dupe@example.com");
    byte[] credId = new byte[] {3, 1, 4, 1, 5};
    repository.insert(
        WebAuthnCredentialEntity.builder()
            .userId(userId)
            .credentialId(credId)
            .publicKey(new byte[] {1})
            .signCount(0L)
            .build());

    assertThatThrownBy(
            () ->
                repository.insert(
                    WebAuthnCredentialEntity.builder()
                        .userId(userId)
                        .credentialId(credId)
                        .publicKey(new byte[] {2})
                        .signCount(0L)
                        .build()))
        .isInstanceOf(DuplicateKeyException.class);
  }
}
