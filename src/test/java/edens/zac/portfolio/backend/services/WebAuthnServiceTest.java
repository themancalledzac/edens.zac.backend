package edens.zac.portfolio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.dao.WebAuthnCredentialRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import edens.zac.portfolio.backend.types.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialCreationOptionsRequest;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialRequestOptionsRequest;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;

/**
 * Wiring/contract tests for {@link WebAuthnService}. The {@link WebAuthnRelyingPartyOperations}
 * engine is mocked (a virtual authenticator is impractical in a unit test), so these assert the
 * contract: register-start keys the challenge by the principal's user handle; register-finish is
 * gated on a stored challenge; login-start scopes the assertion to the email (the email is passed
 * as the operations Authentication name) and still returns options for an unknown email
 * (anti-enumeration); login-finish resolves the user and creates an mfa=true session; a sign-count
 * regression surfaced by the ops call propagates and creates no session.
 */
@ExtendWith(MockitoExtension.class)
class WebAuthnServiceTest {

  @Mock private WebAuthnRelyingPartyOperations operations;
  @Mock private WebAuthnChallengeStore challengeStore;
  @Mock private AppUserRepository appUserRepository;
  @Mock private WebAuthnCredentialRepository credentialRepository;
  @Mock private SessionService sessionService;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;

  private WebAuthnService service;

  private final UUID handle = UUID.randomUUID();
  private AppUserEntity admin;

  @BeforeEach
  void setUp() {
    service =
        new WebAuthnService(
            operations, challengeStore, appUserRepository, credentialRepository, sessionService);
    admin =
        AppUserEntity.builder()
            .id(1L)
            .email("admin@example.com")
            .role(Role.ADMIN)
            .webauthnUserHandle(handle)
            .build();
  }

  private AuthPrincipal principal() {
    return new AuthPrincipal(1L, "admin@example.com", Role.ADMIN, false);
  }

  @Test
  void startRegistrationStoresOptionsKeyedByUserHandle() {
    when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
    PublicKeyCredentialCreationOptions opts =
        org.mockito.Mockito.mock(PublicKeyCredentialCreationOptions.class);
    when(operations.createPublicKeyCredentialCreationOptions(any())).thenReturn(opts);

    PublicKeyCredentialCreationOptions result = service.startRegistration(principal());

    assertThat(result).isSameAs(opts);
    verify(challengeStore).put(eq(handle.toString()), eq(opts));
  }

  @Test
  void startRegistrationPassesPrincipalEmailAsAuthenticationName() {
    when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
    when(operations.createPublicKeyCredentialCreationOptions(any()))
        .thenReturn(org.mockito.Mockito.mock(PublicKeyCredentialCreationOptions.class));

    service.startRegistration(principal());

    // The operations engine scopes registration by authentication.getName(); it must be the email.
    verify(operations)
        .createPublicKeyCredentialCreationOptions(
            argThat(
                (PublicKeyCredentialCreationOptionsRequest req) ->
                    "admin@example.com".equals(req.getAuthentication().getName())));
  }

  @Test
  void finishRegistrationPersistsCredentialForPrincipal() {
    when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
    PublicKeyCredentialCreationOptions saved =
        org.mockito.Mockito.mock(PublicKeyCredentialCreationOptions.class);
    when(challengeStore.take(handle.toString())).thenReturn(Optional.of(saved));

    service.finishRegistration(principal(), validAttestationJson());

    // registerCredential is invoked (the UserCredentialRepository.save persists); the ops call is
    // the boundary we assert.
    verify(operations).registerCredential(any());
  }

  @Test
  void finishRegistrationWithoutStoredChallengeIsRejected() {
    when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
    when(challengeStore.take(handle.toString())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.finishRegistration(principal(), validAttestationJson()))
        .isInstanceOf(IllegalStateException.class);
    verify(operations, never()).registerCredential(any());
  }

  @Test
  void startLoginScopesToEmailAndStoresOptions() {
    PublicKeyCredentialRequestOptions opts =
        org.mockito.Mockito.mock(PublicKeyCredentialRequestOptions.class);
    when(operations.createCredentialRequestOptions(any())).thenReturn(opts);

    WebAuthnService.LoginStart start = service.startLogin("admin@example.com");

    assertThat(start.options()).isSameAs(opts);
    assertThat(start.attemptId()).isNotBlank();
    verify(challengeStore).put(eq(start.attemptId()), eq(opts));
    // The email is handed to the operations engine as the Authentication name; the engine itself
    // builds the email-scoped allow-list from that user's stored credentials.
    verify(operations)
        .createCredentialRequestOptions(
            argThat(
                (PublicKeyCredentialRequestOptionsRequest req) ->
                    "admin@example.com".equals(req.getAuthentication().getName())));
  }

  @Test
  void startLoginWithUnknownEmailStillReturnsWellFormedOptions() {
    PublicKeyCredentialRequestOptions opts =
        org.mockito.Mockito.mock(PublicKeyCredentialRequestOptions.class);
    when(operations.createCredentialRequestOptions(any())).thenReturn(opts);

    WebAuthnService.LoginStart start = service.startLogin("ghost@example.com");

    // No 404, no leak: an unknown email still yields well-formed options (the operations engine
    // mints a fresh challenge + empty allow-list for an unknown username).
    assertThat(start.options()).isSameAs(opts);
    assertThat(start.attemptId()).isNotBlank();
    verify(challengeStore).put(eq(start.attemptId()), eq(opts));
  }

  @Test
  void finishLoginCreatesMfaTrueSession() {
    PublicKeyCredentialRequestOptions saved =
        org.mockito.Mockito.mock(PublicKeyCredentialRequestOptions.class);
    when(challengeStore.take("attempt-1")).thenReturn(Optional.of(saved));

    PublicKeyCredentialUserEntity authedEntity =
        org.mockito.Mockito.mock(PublicKeyCredentialUserEntity.class);
    when(authedEntity.getId())
        .thenReturn(new Bytes(handle.toString().getBytes(StandardCharsets.UTF_8)));
    when(operations.authenticate(any())).thenReturn(authedEntity);
    when(appUserRepository.findByWebauthnUserHandle(handle)).thenReturn(Optional.of(admin));

    service.finishLogin("attempt-1", validAssertionJson(), request, response);

    verify(sessionService).create(eq(admin), eq(true), eq(request), eq(response));
  }

  @Test
  void finishLoginPropagatesSignCountRegressionAndCreatesNoSession() {
    PublicKeyCredentialRequestOptions saved =
        org.mockito.Mockito.mock(PublicKeyCredentialRequestOptions.class);
    when(challengeStore.take("attempt-1")).thenReturn(Optional.of(saved));
    when(operations.authenticate(any()))
        .thenThrow(new IllegalStateException("Sign count regression detected"));

    assertThatThrownBy(
            () -> service.finishLogin("attempt-1", validAssertionJson(), request, response))
        .isInstanceOf(IllegalStateException.class);
    verify(sessionService, never())
        .create(any(), org.mockito.ArgumentMatchers.anyBoolean(), any(), any());
  }

  @Test
  void finishLoginWithoutStoredChallengeIsRejected() {
    when(challengeStore.take("attempt-1")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.finishLogin("attempt-1", validAssertionJson(), request, response))
        .isInstanceOf(IllegalStateException.class);
    verify(operations, never()).authenticate(any());
  }

  /**
   * A minimal but structurally valid W3C attestation credential JSON (registration finish body)
   * that the WebAuthn Jackson module can deserialize into a {@code
   * PublicKeyCredential<AuthenticatorAttestationResponse>}.
   */
  private static String validAttestationJson() {
    return """
        {
          "id": "AQIDBA",
          "rawId": "AQIDBA",
          "type": "public-key",
          "response": {
            "clientDataJSON": "AQIDBA",
            "attestationObject": "AQIDBA"
          },
          "clientExtensionResults": {}
        }
        """;
  }

  /**
   * A minimal but structurally valid W3C assertion credential JSON (login finish body) that the
   * WebAuthn Jackson module can deserialize into a {@code
   * PublicKeyCredential<AuthenticatorAssertionResponse>}.
   */
  private static String validAssertionJson() {
    return """
        {
          "id": "AQIDBA",
          "rawId": "AQIDBA",
          "type": "public-key",
          "response": {
            "clientDataJSON": "AQIDBA",
            "authenticatorData": "AQIDBA",
            "signature": "AQIDBA",
            "userHandle": "AQIDBA"
          },
          "clientExtensionResults": {}
        }
        """;
  }
}
