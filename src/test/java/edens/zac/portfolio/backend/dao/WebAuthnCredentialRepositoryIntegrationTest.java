package edens.zac.portfolio.backend.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.config.JdbcUserCredentialRepository;
import edens.zac.portfolio.backend.entity.WebAuthnCredentialEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.webauthn.api.Bytes;

/** Round-trips WebAuthnCredentialRepository against a real Postgres container. */
class WebAuthnCredentialRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private WebAuthnCredentialRepository repository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JdbcUserCredentialRepository springSecurityCredentials;

  private Long seedUser(String email) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO users (name, email, webauthn_user_handle, status) "
            + "VALUES (?, ?, gen_random_uuid(), 'ACTIVE') RETURNING id",
        Long.class,
        email,
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

  @Test
  void deleteByIdAndUserIdRemovesTheCredential() {
    Long userId = seedUser("wac-delete@example.com");
    Long id =
        repository.insert(
            WebAuthnCredentialEntity.builder()
                .userId(userId)
                .credentialId(new byte[] {1, 2, 3})
                .publicKey(new byte[] {4})
                .signCount(0L)
                .build());

    assertThat(repository.deleteByIdAndUserId(id, userId)).isEqualTo(1);

    assertThat(repository.findByUserId(userId)).isEmpty();
    assertThat(repository.findByCredentialId(new byte[] {1, 2, 3})).isEmpty();
  }

  @Test
  void deleteByIdAndUserIdWillNotDeleteAnotherAccountsCredential() {
    Long owner = seedUser("wac-owner@example.com");
    Long other = seedUser("wac-other@example.com");
    Long id =
        repository.insert(
            WebAuthnCredentialEntity.builder()
                .userId(owner)
                .credentialId(new byte[] {9, 9, 9})
                .publicKey(new byte[] {4})
                .signCount(0L)
                .build());

    assertThat(repository.deleteByIdAndUserId(id, other)).isZero();

    assertThat(repository.findByUserId(owner)).hasSize(1);
  }

  /**
   * The lookup {@code WebAuthnService.finishLogin} reaches through {@code operations.authenticate}
   * is {@link JdbcUserCredentialRepository#findByCredentialId(Bytes)}. Once it returns null the
   * assertion cannot be verified, so this is the deregistration actually taking effect on login
   * rather than a mock of the layer under test.
   */
  @Test
  void aDeregisteredCredentialIsGoneFromTheLoginLookup() {
    Long userId = seedUser("wac-login@example.com");
    byte[] credentialId = new byte[] {5, 6, 7};
    Long id =
        repository.insert(
            WebAuthnCredentialEntity.builder()
                .userId(userId)
                .credentialId(credentialId)
                .publicKey(new byte[] {8})
                .signCount(0L)
                .build());

    assertThat(springSecurityCredentials.findByCredentialId(new Bytes(credentialId))).isNotNull();

    repository.deleteByIdAndUserId(id, userId);

    assertThat(springSecurityCredentials.findByCredentialId(new Bytes(credentialId))).isNull();
  }
}
