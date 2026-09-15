package edens.zac.portfolio.backend.config;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fail-closed guard for prod startup, beside {@link ProdSecretGuard}. Both {@code
 * application.properties} and {@code docker-compose.yml} default the relying-party id to {@code
 * localhost}; a prod container that boots on that default enrolls passkeys no browser on the live
 * site can ever verify, and nothing else reports it.
 */
@Component
@Profile("prod")
public class ProdWebAuthnGuard {

  private final String rpId;
  private final String allowedOrigins;

  ProdWebAuthnGuard(
      @Value("${app.auth.webauthn.rp-id:}") String rpId,
      @Value("${app.auth.webauthn.allowed-origins:}") String allowedOrigins) {
    this.rpId = rpId;
    this.allowedOrigins = allowedOrigins;
  }

  @PostConstruct
  void verify() {
    if (rpId == null || rpId.isBlank() || "localhost".equals(rpId)) {
      throw new IllegalStateException(
          "app.auth.webauthn.rp-id (WEBAUTHN_RP_ID) must be the production domain when prod"
              + " profile is active; passkeys enrolled against localhost never verify on the site");
    }
    boolean hasRealOrigin =
        Arrays.stream(allowedOrigins == null ? new String[0] : allowedOrigins.split(","))
            .map(String::trim)
            .anyMatch(origin -> !origin.isEmpty() && !origin.contains("localhost"));
    if (!hasRealOrigin) {
      throw new IllegalStateException(
          "app.auth.webauthn.allowed-origins (WEBAUTHN_ALLOWED_ORIGINS) must include the"
              + " production origin when prod profile is active");
    }
  }
}
