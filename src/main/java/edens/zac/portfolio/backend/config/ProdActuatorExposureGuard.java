package edens.zac.portfolio.backend.config;

import jakarta.annotation.PostConstruct;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fail-closed guard for prod startup. Refuses to start when the resolved actuator web exposure is
 * anything other than {@code health}, which covers every endpoint Boot has now or adds later
 * without naming any of them.
 *
 * <p>Reads {@link WebEndpointProperties} rather than the raw property, because that is the object
 * actuator consults when deciding what to expose. Prod only, matching {@link ProdSecretGuard}: a
 * wider include is a reasonable thing to want locally.
 */
@Component
@Profile("prod")
public class ProdActuatorExposureGuard {

  private static final Set<String> ALLOWED_EXPOSURE = Set.of("health");

  private final Set<String> include;

  ProdActuatorExposureGuard(WebEndpointProperties properties) {
    this.include = properties.getExposure().getInclude();
  }

  @PostConstruct
  void verify() {
    Set<String> resolved =
        include.stream()
            .map(name -> name.trim().toLowerCase(Locale.ROOT))
            .filter(name -> !name.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    if (!ALLOWED_EXPOSURE.equals(resolved)) {
      throw new IllegalStateException(
          "management.endpoints.web.exposure.include must resolve to exactly [health] when the prod"
              + " profile is active, but resolved to "
              + resolved
              + ": a wider include exposes endpoints the exclude list does not name");
    }
  }
}
