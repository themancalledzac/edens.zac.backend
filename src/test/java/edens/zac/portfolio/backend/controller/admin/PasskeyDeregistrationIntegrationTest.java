package edens.zac.portfolio.backend.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import edens.zac.portfolio.backend.config.ResourceNotFoundException;
import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.dao.WebAuthnCredentialRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.entity.WebAuthnCredentialEntity;
import edens.zac.portfolio.backend.services.SessionService;
import edens.zac.portfolio.backend.types.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * S-26, end to end against a real Postgres: deregistering a passkey must stop the sessions that
 * credential minted from resolving.
 *
 * <p>The mock-level twins live in {@code AdminUserControllerTest.Passkeys} and prove the controller
 * makes the call. These prove the call does the thing -- {@link SessionService#resolve} tests
 * revoked, expired and {@code mayHoldSession} and never reads {@code webauthn_credential}, so
 * deleting the credential row cannot evict a session on its own, and no mock of {@code
 * SessionService} can show that.
 */
class PasskeyDeregistrationIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private AdminUserController adminUserController;
  @Autowired private AppUserRepository userRepository;
  @Autowired private WebAuthnCredentialRepository credentialRepository;
  @Autowired private SessionService sessionService;

  private AppUserEntity seedUser(String email) {
    Long id =
        userRepository.insert(
            AppUserEntity.builder()
                .email(email)
                .name(email)
                .webauthnUserHandle(UUID.randomUUID())
                .status(UserStatus.ACTIVE)
                .build());
    return userRepository.findById(id).orElseThrow();
  }

  private Long seedCredential(Long userId, byte marker) {
    return credentialRepository.insert(
        WebAuthnCredentialEntity.builder()
            .userId(userId)
            .credentialId(new byte[] {marker, 2, 3})
            .publicKey(new byte[] {marker, 5, 6})
            .signCount(0L)
            .transports("internal")
            .label("Passkey " + marker)
            .build());
  }

  private String mintSession(AppUserEntity user) {
    MockHttpServletResponse response = new MockHttpServletResponse();
    sessionService.create(user, true, new MockHttpServletRequest(), response);
    String setCookie = response.getHeader("Set-Cookie");
    assertThat(setCookie).isNotNull().contains("ezac_session=");
    String afterName =
        setCookie.substring(setCookie.indexOf("ezac_session=") + "ezac_session=".length());
    int semi = afterName.indexOf(';');
    return semi >= 0 ? afterName.substring(0, semi) : afterName;
  }

  /**
   * The finding itself. The account stays ACTIVE -- that is the whole point of the endpoint -- so
   * before the fix {@code resolve} kept returning the principal for up to the 60-day sliding TTL.
   * Mutation this catches: drop the {@code revokeAllForUser} call and the last assertion goes back
   * to finding a live principal.
   */
  @Test
  void deregisteringAPasskeyStopsItsSessionResolving() {
    AppUserEntity user = seedUser("s26-deregistered@example.com");
    Long credentialId = seedCredential(user.getId(), (byte) 1);
    String raw = mintSession(user);
    assertThat(sessionService.resolve(raw)).isPresent();

    adminUserController.deregisterPasskey(user.getId(), credentialId);

    assertThat(sessionService.resolve(raw)).isEmpty();
    assertThat(userRepository.findById(user.getId()).orElseThrow().getStatus())
        .isEqualTo(UserStatus.ACTIVE);
  }

  /**
   * The eviction is account-wide and deliberately so -- {@code user_session} records no credential
   * id, which is the trade S-15 accepted. Pinned rather than left implicit: someone narrowing the
   * revoke to "sessions this credential minted" needs a schema change first, and this test is where
   * they find that out.
   */
  @Test
  void deregisteringOnePasskeyEvictsSessionsMintedByTheOtherToo() {
    AppUserEntity user = seedUser("s26-both@example.com");
    Long kept = seedCredential(user.getId(), (byte) 1);
    Long removed = seedCredential(user.getId(), (byte) 2);
    String raw = mintSession(user);

    adminUserController.deregisterPasskey(user.getId(), removed);

    assertThat(sessionService.resolve(raw)).isEmpty();
    assertThat(credentialRepository.findByUserId(user.getId()))
        .singleElement()
        .satisfies(c -> assertThat(c.getId()).isEqualTo(kept));
  }

  /**
   * Bystanders are untouched: the revoke is scoped to the path variable, not to every account.
   * Mutation this catches: drop the {@code user_id} predicate from the revoke and one
   * deregistration signs out the whole site.
   */
  @Test
  void deregisteringAPasskeyLeavesAnotherAccountsSessionAlone() {
    AppUserEntity target = seedUser("s26-target@example.com");
    AppUserEntity bystander = seedUser("s26-bystander@example.com");
    Long credentialId = seedCredential(target.getId(), (byte) 1);
    String bystanderRaw = mintSession(bystander);

    adminUserController.deregisterPasskey(target.getId(), credentialId);

    assertThat(sessionService.resolve(bystanderRaw)).isPresent();
  }

  /**
   * The revoke sits below the delete guard. Mutation this catches: hoist {@code revokeAllForUser}
   * above the {@code deleteByIdAndUserId} check and a 404 signs the account out.
   */
  @Test
  void aFailedDeregistrationLeavesTheAccountsSessionResolving() {
    AppUserEntity user = seedUser("s26-notfound@example.com");
    seedCredential(user.getId(), (byte) 1);
    String raw = mintSession(user);

    assertThatThrownBy(() -> adminUserController.deregisterPasskey(user.getId(), 999_999L))
        .isInstanceOf(ResourceNotFoundException.class);

    assertThat(sessionService.resolve(raw)).isPresent();
  }
}
