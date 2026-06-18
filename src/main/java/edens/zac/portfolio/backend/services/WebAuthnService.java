package edens.zac.portfolio.backend.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edens.zac.portfolio.backend.dao.AppUserRepository;
import edens.zac.portfolio.backend.dao.WebAuthnCredentialRepository;
import edens.zac.portfolio.backend.entity.AppUserEntity;
import edens.zac.portfolio.backend.model.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse;
import org.springframework.security.web.webauthn.api.AuthenticatorAttestationResponse;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.jackson.WebauthnJackson2Module;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialCreationOptionsRequest;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialRequestOptionsRequest;
import org.springframework.security.web.webauthn.management.ImmutableRelyingPartyRegistrationRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyPublicKey;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the four WebAuthn ceremonies on top of Spring Security's programmatic operations
 * engine. Registration is bound to the authenticated principal; login converges on the F1 {@code
 * SessionService} with {@code mfaSatisfied=true} (passkeys are user-verified).
 *
 * <p><strong>Email-scoped login (non-discoverable).</strong> Spring Security 6.4's operations
 * engine derives the assertion allow-list itself from {@code authentication.getName()} (it resolves
 * the user entity by that name, then looks up that user's stored credentials). So {@link
 * #startLogin(String)} passes the email as the operations {@code Authentication} name rather than
 * handing the engine an explicit descriptor list. Anti-enumeration falls out for free: for an
 * unknown email the engine mints a fresh challenge with an empty allow-list, so the response shape
 * and timing never reveal whether the account exists; the mismatch surfaces only at {@link
 * #finishLogin} (the assertion fails). No 404, no leak.
 */
@Service
@Slf4j
public class WebAuthnService {

  private final WebAuthnRelyingPartyOperations operations;
  private final WebAuthnChallengeStore challengeStore;
  private final AppUserRepository appUserRepository;
  private final WebAuthnCredentialRepository credentialRepository;
  private final SessionService sessionService;
  private final ObjectMapper objectMapper;

  /**
   * Primary constructor.
   *
   * @param operations the WebAuthn relying-party operations engine
   * @param challengeStore the short-TTL single-use challenge store
   * @param appUserRepository the app_user repository (principal/handle resolution)
   * @param credentialRepository the JDBC webauthn_credential repository
   * @param sessionService the F1 session service (login converges here)
   * @param objectMapper the primary application mapper (Spring Boot auto-registers the WebAuthn
   *     Jackson module onto it via the {@link
   *     edens.zac.portfolio.backend.config.WebAuthnConfig#webauthnJackson2Module()} bean)
   */
  @Autowired
  public WebAuthnService(
      WebAuthnRelyingPartyOperations operations,
      WebAuthnChallengeStore challengeStore,
      AppUserRepository appUserRepository,
      WebAuthnCredentialRepository credentialRepository,
      SessionService sessionService,
      ObjectMapper objectMapper) {
    this.operations = operations;
    this.challengeStore = challengeStore;
    this.appUserRepository = appUserRepository;
    this.credentialRepository = credentialRepository;
    this.sessionService = sessionService;
    this.objectMapper = objectMapper;
  }

  /**
   * Test-friendly constructor. Builds a WebAuthn-aware {@link ObjectMapper} (the {@code
   * WebauthnJackson2Module} is required to deserialize the finish-endpoint credential bodies) so
   * the ceremony JSON path is still exercised under unit tests.
   *
   * @param operations the WebAuthn relying-party operations engine
   * @param challengeStore the short-TTL single-use challenge store
   * @param appUserRepository the app_user repository
   * @param credentialRepository the JDBC webauthn_credential repository
   * @param sessionService the F1 session service
   */
  WebAuthnService(
      WebAuthnRelyingPartyOperations operations,
      WebAuthnChallengeStore challengeStore,
      AppUserRepository appUserRepository,
      WebAuthnCredentialRepository credentialRepository,
      SessionService sessionService) {
    this(
        operations,
        challengeStore,
        appUserRepository,
        credentialRepository,
        sessionService,
        defaultWebAuthnObjectMapper());
  }

  private static ObjectMapper defaultWebAuthnObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new WebauthnJackson2Module());
    return mapper;
  }

  /**
   * Registration options for the logged-in admin; the challenge is stored under their user handle.
   *
   * @param principal the authenticated principal
   * @return the W3C creation options to return to the browser
   */
  public PublicKeyCredentialCreationOptions startRegistration(AuthPrincipal principal) {
    AppUserEntity user = requireUser(principal.userId());
    Authentication auth = toAuthentication(principal);
    PublicKeyCredentialCreationOptions options =
        operations.createPublicKeyCredentialCreationOptions(
            new ImmutablePublicKeyCredentialCreationOptionsRequest(auth));
    challengeStore.put(user.getWebauthnUserHandle().toString(), options);
    return options;
  }

  /**
   * Verify and store the new credential, scoped to the authenticated principal's stored challenge.
   *
   * @param principal the authenticated principal
   * @param credentialJson the raw W3C attestation credential JSON from {@code register/finish}
   */
  public void finishRegistration(AuthPrincipal principal, String credentialJson) {
    AppUserEntity user = requireUser(principal.userId());
    PublicKeyCredentialCreationOptions saved =
        (PublicKeyCredentialCreationOptions)
            challengeStore
                .take(user.getWebauthnUserHandle().toString())
                .orElseThrow(
                    () ->
                        new IllegalStateException("No in-flight registration challenge for user"));
    PublicKeyCredential<AuthenticatorAttestationResponse> credential =
        deserializeAttestation(credentialJson);
    operations.registerCredential(
        new ImmutableRelyingPartyRegistrationRequest(
            saved, new RelyingPartyPublicKey(credential, user.getEmail())));
  }

  /**
   * Assertion request options for a login attempt, scoped to the email's registered credentials
   * (non-discoverable / allow-list flow, derived by the operations engine from the email). The
   * challenge is stored under a fresh per-attempt id.
   *
   * @param email the email the user typed
   * @return the per-attempt id + the W3C request options
   */
  public LoginStart startLogin(String email) {
    Authentication auth = usernameAuthentication(email);
    PublicKeyCredentialRequestOptions options =
        operations.createCredentialRequestOptions(
            new ImmutablePublicKeyCredentialRequestOptionsRequest(auth));
    String attemptId = UUID.randomUUID().toString();
    challengeStore.put(attemptId, options);
    return new LoginStart(attemptId, options);
  }

  /**
   * Verify the assertion (user-verification + sign_count regression enforced inside the operations
   * call) and mint an mfa=true session for the resolved user.
   *
   * @param attemptId the per-attempt id (carried by the ezac_webauthn_attempt cookie)
   * @param credentialJson the raw W3C assertion credential JSON from {@code login/finish}
   * @param request the servlet request (IP / User-Agent for the session row)
   * @param response the Set-Cookie sink for the session cookie
   */
  public void finishLogin(
      String attemptId,
      String credentialJson,
      HttpServletRequest request,
      HttpServletResponse response) {
    PublicKeyCredentialRequestOptions saved =
        (PublicKeyCredentialRequestOptions)
            challengeStore
                .take(attemptId)
                .orElseThrow(() -> new IllegalStateException("No in-flight login challenge"));
    PublicKeyCredential<AuthenticatorAssertionResponse> credential =
        deserializeAssertion(credentialJson);

    PublicKeyCredentialUserEntity authenticated =
        operations.authenticate(new RelyingPartyAuthenticationRequest(saved, credential));

    UUID handle =
        UUID.fromString(new String(authenticated.getId().getBytes(), StandardCharsets.UTF_8));
    AppUserEntity user =
        appUserRepository
            .findByWebauthnUserHandle(handle)
            .orElseThrow(
                () -> new IllegalStateException("Authenticated handle has no app_user: " + handle));

    sessionService.create(user, true, request, response);
  }

  private AppUserEntity requireUser(Long userId) {
    return appUserRepository
        .findById(userId)
        .orElseThrow(() -> new IllegalStateException("Principal has no app_user: " + userId));
  }

  private static Authentication toAuthentication(AuthPrincipal principal) {
    return new UsernamePasswordAuthenticationToken(
        principal.email(),
        principal,
        AuthorityUtils.createAuthorityList("ROLE_" + principal.role().name()));
  }

  /**
   * Build a bare {@link Authentication} whose name is the email. The operations engine reads only
   * {@code getName()} when minting login options, so this carries the email-scoping signal without
   * implying the user is actually authenticated.
   */
  private static Authentication usernameAuthentication(String email) {
    return new UsernamePasswordAuthenticationToken(email, null, AuthorityUtils.NO_AUTHORITIES);
  }

  private PublicKeyCredential<AuthenticatorAttestationResponse> deserializeAttestation(
      String json) {
    try {
      return objectMapper.readValue(
          json, new TypeReference<PublicKeyCredential<AuthenticatorAttestationResponse>>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid attestation credential JSON", e);
    }
  }

  private PublicKeyCredential<AuthenticatorAssertionResponse> deserializeAssertion(String json) {
    try {
      return objectMapper.readValue(
          json, new TypeReference<PublicKeyCredential<AuthenticatorAssertionResponse>>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid assertion credential JSON", e);
    }
  }

  /**
   * Result of {@link #startLogin(String)}: the per-attempt id (the controller sets it as the {@code
   * ezac_webauthn_attempt} cookie so the client returns it on {@code /login/finish}) plus the
   * request options.
   *
   * @param attemptId the per-attempt id
   * @param options the W3C request options
   */
  public record LoginStart(String attemptId, PublicKeyCredentialRequestOptions options) {}
}
