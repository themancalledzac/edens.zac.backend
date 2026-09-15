package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edens.zac.portfolio.backend.Application;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

class ProdWebAuthnGuardTest {

  @Test
  void refusesTheLocalhostRelyingPartyId() {
    var guard = new ProdWebAuthnGuard("localhost", "https://zacedens.com");
    assertThrows(IllegalStateException.class, guard::verify);
  }

  @Test
  void refusesABlankRelyingPartyId() {
    var guard = new ProdWebAuthnGuard("", "https://zacedens.com");
    assertThrows(IllegalStateException.class, guard::verify);
  }

  @Test
  void refusesOriginsThatAreAllLocalhost() {
    var guard =
        new ProdWebAuthnGuard("zacedens.com", "http://localhost:3000,http://localhost:3001");
    assertThrows(IllegalStateException.class, guard::verify);
  }

  @Test
  void acceptsTheProductionDomainAndOrigins() {
    var guard =
        new ProdWebAuthnGuard("zacedens.com", "https://zacedens.com,https://www.zacedens.com");
    assertDoesNotThrow(guard::verify);
  }

  /**
   * The tests above call {@code verify()} directly on a hand-built guard, so none of them can see
   * {@code @PostConstruct} -- delete the annotation and the guard is dead at startup while they all
   * stay green. These boot a real context instead, so the container is what calls {@code verify()}.
   *
   * <p>The context finds the guard the way the application does, by scanning {@link Application}'s
   * package. Handing the runner {@code ProdWebAuthnGuard.class} directly would leave the discovery
   * half of the wiring untested: dropping {@code @Component}, or moving the class out of the
   * scanned tree, would keep every case green while prod booted unguarded.
   *
   * <p>Mutations this catches: delete {@code @PostConstruct} and {@link
   * #prodRefusesToStartOnTheLocalhostRelyingPartyId} reddens; delete {@code @Profile("prod")} and
   * {@link #guardIsNotRegisteredOutsideProd} reddens; delete {@code @Component}, or move the class
   * out of {@code edens.zac.portfolio.backend}, and {@link #prodStartsOnARealRpIdAndOrigins}
   * reddens on the missing bean, along with the refusal case.
   */
  @Nested
  class Wiring {

    private final ApplicationContextRunner runner =
        new ApplicationContextRunner().withUserConfiguration(ScanForTheGuard.class);

    /**
     * Stands in for the application's own component scan. {@code basePackageClasses} resolves to
     * {@link Application}'s package, the real scan root, so the guard has to be discoverable from
     * there rather than named by the test.
     *
     * <p>The exclude filter keeps every other bean out, which is what lets these cases run without
     * a datasource. It is a negative lookahead: it matches, and so excludes, every fully qualified
     * name that does not end in {@code .ProdWebAuthnGuard}. Default filters stay on, so a candidate
     * still has to carry a stereotype annotation to be registered at all.
     */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
        basePackageClasses = Application.class,
        excludeFilters =
            @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "^(?!.*\\.ProdWebAuthnGuard$).*$"))
    static class ScanForTheGuard {}

    private ApplicationContextRunner prodWith(String... properties) {
      return runner
          .withPropertyValues("spring.profiles.active=prod")
          .withPropertyValues(properties);
    }

    /** A prod context with no defect at all, for the control case. */
    private ApplicationContextRunner prodWithGoodConfig() {
      return prodWith(
          "app.auth.webauthn.rp-id=zacedens.com",
          "app.auth.webauthn.allowed-origins=https://zacedens.com");
    }

    @Test
    void prodRefusesToStartOnTheLocalhostRelyingPartyId() {
      prodWith(
              "app.auth.webauthn.rp-id=localhost",
              "app.auth.webauthn.allowed-origins=https://zacedens.com")
          .run(
              context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("WEBAUTHN_RP_ID");
              });
    }

    /**
     * The control for the case above: without it, a context that failed for an unrelated reason, or
     * one where the bean was never registered at all, would read as a passing guard.
     */
    @Test
    void prodStartsOnARealRpIdAndOrigins() {
      prodWithGoodConfig()
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(ProdWebAuthnGuard.class);
              });
    }

    @Test
    void guardIsNotRegisteredOutsideProd() {
      runner
          .withPropertyValues("app.auth.webauthn.rp-id=localhost")
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(ProdWebAuthnGuard.class);
              });
    }
  }
}
