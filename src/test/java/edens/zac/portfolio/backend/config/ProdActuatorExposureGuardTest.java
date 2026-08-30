package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.Application;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * S-23. Every case boots a real context, because the whole point of this guard is that it reads the
 * <em>resolved</em> exposure rather than a list someone typed. A test that constructed the guard by
 * hand and passed it a set would be asserting on its own enumeration, which is the failure S-18's
 * two tests already had.
 *
 * <p>The context finds the guard the way the application does, by scanning {@link Application}'s
 * package, so discovery is tested rather than assumed. Mutations this catches: delete
 * {@code @PostConstruct} and every refusal case goes green-but-dead -- caught, because the refusal
 * cases assert the context failed; delete {@code @Profile("prod")} and {@link
 * #guardIsNotRegisteredOutsideProd} reddens; delete {@code @Component}, or move the class out of
 * the scanned tree, and {@link #prodStartsOnTheShippedInclude} reddens on the missing bean.
 */
class ProdActuatorExposureGuardTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(ScanForTheGuard.class);

  /**
   * Stands in for the application's own component scan, excluding every bean but this guard so the
   * cases run without a datasource. {@link WebEndpointProperties} is bound from the runner's
   * properties, which is the same binding actuator performs.
   */
  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(WebEndpointProperties.class)
  @ComponentScan(
      basePackageClasses = Application.class,
      excludeFilters =
          @ComponentScan.Filter(
              type = FilterType.REGEX,
              pattern = "^(?!.*\\.ProdActuatorExposureGuard$).*$"))
  static class ScanForTheGuard {}

  private ApplicationContextRunner prodWithInclude(String include) {
    return runner.withPropertyValues(
        "spring.profiles.active=prod", "management.endpoints.web.exposure.include=" + include);
  }

  /**
   * The wildcard is the accident rule 34 named, and the two-name cases are S-23's own evidence:
   * {@code metrics} and {@code info} are in neither the shipped exclude list nor S-18's {@code
   * MUST_BE_EXCLUDED}, so nothing before this guard refused them.
   */
  @ParameterizedTest
  @ValueSource(strings = {"*", "health,metrics", "health,info", "metrics", "health,env"})
  void prodRefusesToStartOnAnyWiderInclude(String include) {
    prodWithInclude(include)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .rootCause()
                  .isInstanceOf(IllegalStateException.class)
                  .hasMessageContaining("management.endpoints.web.exposure.include");
            });
  }

  /**
   * The control. Without it, a context failing for an unrelated reason -- or one where the bean was
   * never registered at all -- would read as a working guard.
   */
  @Test
  void prodStartsOnTheShippedInclude() {
    prodWithInclude("health")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(ProdActuatorExposureGuard.class);
            });
  }

  /** Whitespace and casing are property-file accidents, not a wider exposure. */
  @ParameterizedTest
  @ValueSource(strings = {" health ", "HEALTH", "Health"})
  void prodStartsOnAnEquivalentSpelling(String include) {
    prodWithInclude(include).run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void guardIsNotRegisteredOutsideProd() {
    runner
        .withPropertyValues("management.endpoints.web.exposure.include=*")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(ProdActuatorExposureGuard.class);
            });
  }
}
