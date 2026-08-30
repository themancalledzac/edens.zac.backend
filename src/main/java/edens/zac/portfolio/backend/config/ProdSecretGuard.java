package edens.zac.portfolio.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fail-closed guard for prod startup. Refuses to start when {@code internal.api.secret} or {@code
 * app.access-token.secret} is unset or still set to the default development value.
 *
 * <p>A third check once refused a prod boot with {@code app.admin.enforce-authz=false}. That toggle
 * was removed on 2026-08-30 and the authorization gate is now unconditional, so there is no longer
 * an env var that can open {@code /api/admin/**} and {@code /api/edit/**} to open the check
 * against.
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

  ProdSecretGuard(
      @Value("${internal.api.secret:}") String secret,
      @Value("${app.access-token.secret:}") String accessTokenSecret) {
    this.secret = secret;
    this.accessTokenSecret = accessTokenSecret;
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
  }
}
