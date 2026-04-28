package edens.zac.portfolio.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fail-closed guard: refuses to start the prod profile when {@code internal.api.secret} is unset or
 * still set to the default development value.
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProdSecretGuard {

  @Value("${internal.api.secret:}")
  private String secret;

  @PostConstruct
  void verify() {
    if (secret == null || secret.isBlank() || "dev-internal-secret".equals(secret)) {
      throw new IllegalStateException(
          "internal.api.secret must be set to a non-default value when prod profile is active");
    }
  }
}
