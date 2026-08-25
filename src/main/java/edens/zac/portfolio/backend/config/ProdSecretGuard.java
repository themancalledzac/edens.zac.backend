package edens.zac.portfolio.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fail-closed guard for prod startup. Refuses to start when {@code internal.api.secret} or {@code
 * app.access-token.secret} is unset or still set to the default development value, or when {@code
 * app.admin.enforce-authz} is false.
 *
 * <p>The authz toggle exists so local dev stays login-free. In prod it is the only thing standing
 * between an anonymous visitor and the whole write surface: {@link SecurityConfig} falls through to
 * {@code permitAll} and {@link EditAccessWebConfig} skips its interceptor when it is off. One wrong
 * env var would open {@code /api/admin/**} and {@code /api/edit/**}, so prod refuses to boot.
 *
 * <p>{@code app.access-token.secret} is here because {@code docker-compose.yml} defaults it to a
 * value printed in this public repo. It is the AES-256 key {@link
 * edens.zac.portfolio.backend.services.TokenCipher} derives for {@code share_link.token_cipher},
 * and the HMAC key {@link edens.zac.portfolio.backend.services.ClientGalleryAuthService} signs
 * gallery access tokens and password fingerprints with. Both claim confidentiality properties that
 * a known key voids, so the compose default must never reach prod.
 */
@Component
@Profile("prod")
public class ProdSecretGuard {

  private final String secret;
  private final String accessTokenSecret;
  private final boolean enforceAuthz;

  ProdSecretGuard(
      @Value("${internal.api.secret:}") String secret,
      @Value("${app.access-token.secret:}") String accessTokenSecret,
      @Value("${app.admin.enforce-authz:true}") boolean enforceAuthz) {
    this.secret = secret;
    this.accessTokenSecret = accessTokenSecret;
    this.enforceAuthz = enforceAuthz;
  }

  @PostConstruct
  void verify() {
    if (secret == null || secret.isBlank() || "dev-internal-secret".equals(secret)) {
      throw new IllegalStateException(
          "internal.api.secret must be set to a non-default value when prod profile is active");
    }
    if (accessTokenSecret == null
        || accessTokenSecret.isBlank()
        || "dev-access-token-secret".equals(accessTokenSecret)) {
      throw new IllegalStateException(
          "app.access-token.secret must be set to a non-default value when prod profile is active:"
              + " it is the encryption key for share_link.token_cipher and the signing key for"
              + " client gallery access tokens");
    }
    if (!enforceAuthz) {
      throw new IllegalStateException(
          "app.admin.enforce-authz must not be false when prod profile is active: it removes the"
              + " authorization gate on /api/admin/** and /api/edit/**");
    }
  }
}
