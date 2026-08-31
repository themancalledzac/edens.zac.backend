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
 * Every case boots a real context, found by scanning {@link Application}'s package the way the
 * application does -- a hand-built guard handed a set would only assert on the test's own
 * enumeration, and would not notice the bean failing to register.
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

  /** {@code metrics} and {@code info} are the two nothing before this guard refused. */
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

  /** The control: without it, an unrelated startup failure would read as a working guard. */
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
