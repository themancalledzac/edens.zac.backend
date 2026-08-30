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
 * anything other than {@code health}.
 *
 * <p>S-18 answered the same risk by naming endpoints on {@code
 * management.endpoints.web.exposure.exclude}, and S-23 is the finding that a name list cannot
 * close: {@code metrics} and {@code info} meet S-18's own criterion under an injected {@code
 * include=*}, sit in neither the exclude list nor the test's {@code MUST_BE_EXCLUDED}, and both
 * S-18 tests derive from the same hand enumeration that omitted them -- so neither test can see the
 * omission. Adding two more names would leave the next Boot release's endpoints in the same
 * position.
 *
 * <p>This checks the <em>resolved</em> include instead, which is one assertion covering every
 * endpoint that exists now or ships later. It reads {@link WebEndpointProperties} rather than the
 * raw property text because that object is what actuator itself consults when deciding what to
 * expose, so the check cannot drift from the exposure it is guarding.
 *
 * <p>Prod only, matching {@link ProdSecretGuard}. A wider include is a legitimate thing to want
 * locally, and the reachable exposure this closes is the prod one: {@code InternalSecretFilter}
 * admits only the three health URIs otherwise, so anything else reaching an endpoint is doing it
 * with an internal-secret bearer token.
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
