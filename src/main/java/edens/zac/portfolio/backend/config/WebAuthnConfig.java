package edens.zac.portfolio.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.jackson.WebauthnJackson2Module;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;

/**
 * Wires the programmatic Spring Security WebAuthn engine (NOT the {@code http.webAuthn()} DSL): the
 * relying-party entity (from {@code app.auth.webauthn.rp-id}/{@code rp-name}), the allowed-origins
 * set (from {@code app.auth.webauthn.allowed-origins}), the {@link WebAuthnRelyingPartyOperations}
 * bean, and a WebAuthn-aware {@link ObjectMapper} for (de)serializing the W3C ceremony JSON bodies.
 */
@Configuration
public class WebAuthnConfig {

  /**
   * The relying-party entity (rp-id + display name) used by the operations engine.
   *
   * @param rpId the relying-party id (effective domain, e.g. {@code localhost} in dev)
   * @param rpName the human-readable relying-party display name
   * @return the immutable RP entity
   */
  @Bean
  public PublicKeyCredentialRpEntity webAuthnRpEntity(
      @Value("${app.auth.webauthn.rp-id}") String rpId,
      @Value("${app.auth.webauthn.rp-name}") String rpName) {
    return PublicKeyCredentialRpEntity.builder().id(rpId).name(rpName).build();
  }

  /**
   * The set of allowed origins the operations engine validates assertions/attestations against.
   *
   * @param allowedOriginsCsv comma-separated list of allowed origins
   * @return an ordered set of trimmed, non-empty origins
   */
  @Bean("webAuthnAllowedOrigins")
  public Set<String> webAuthnAllowedOrigins(
      @Value("${app.auth.webauthn.allowed-origins}") String allowedOriginsCsv) {
    Set<String> origins = new LinkedHashSet<>();
    Arrays.stream(allowedOriginsCsv.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .forEach(origins::add);
    return origins;
  }

  /**
   * The WebAuthn relying-party operations engine, backed by our two JDBC SPI adapters.
   *
   * @param userEntities the {@code app_user}-backed user-entity repository
   * @param userCredentials the {@code webauthn_credential}-backed credential repository
   * @param rpEntity the relying-party entity
   * @param allowedOrigins the allowed-origins set
   * @return the configured operations engine
   */
  @Bean
  public WebAuthnRelyingPartyOperations webAuthnRelyingPartyOperations(
      PublicKeyCredentialUserEntityRepository userEntities,
      UserCredentialRepository userCredentials,
      PublicKeyCredentialRpEntity rpEntity,
      @Qualifier("webAuthnAllowedOrigins") Set<String> allowedOrigins) {
    return new Webauthn4JRelyingPartyOperations(
        userEntities, userCredentials, rpEntity, allowedOrigins);
  }

  /**
   * An {@link ObjectMapper} with the WebAuthn Jackson module registered, for (de)serializing the
   * W3C {@code PublicKeyCredential<...>} ceremony JSON bodies.
   *
   * @return the WebAuthn-aware object mapper
   */
  @Bean("webAuthnObjectMapper")
  public ObjectMapper webAuthnObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new WebauthnJackson2Module());
    return mapper;
  }
}
