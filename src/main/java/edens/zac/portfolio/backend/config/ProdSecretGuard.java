package edens.zac.portfolio.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fail-closed guard for prod startup. Refuses to start when {@code internal.api.secret} is unset or
 * still set to the default development value, or when {@code app.admin.enforce-authz} is false.
 *
 * <p>The authz toggle exists so local dev stays login-free. In prod it is the only thing standing
 * between an anonymous visitor and the whole write surface: {@link SecurityConfig} falls through to
 * {@code permitAll} and {@link EditAccessWebConfig} skips its interceptor when it is off. One wrong
 * env var would open {@code /api/admin/**} and {@code /api/edit/**}, so prod refuses to boot.
 */
@Component
@Profile("prod")
public class ProdSecretGuard {

  private final String secret;
  private final boolean enforceAuthz;

  ProdSecretGuard(
      @Value("${internal.api.secret:}") String secret,
      @Value("${app.admin.enforce-authz:true}") boolean enforceAuthz) {
    this.secret = secret;
    this.enforceAuthz = enforceAuthz;
  }

  @PostConstruct
  void verify() {
    if (secret == null || secret.isBlank() || "dev-internal-secret".equals(secret)) {
      throw new IllegalStateException(
          "internal.api.secret must be set to a non-default value when prod profile is active");
    }
    if (!enforceAuthz) {
      throw new IllegalStateException(
          "app.admin.enforce-authz must not be false when prod profile is active: it removes the"
              + " authorization gate on /api/admin/** and /api/edit/**");
    }
  }
}
