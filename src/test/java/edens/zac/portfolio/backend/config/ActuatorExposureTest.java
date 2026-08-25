package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the shipped actuator exposure, which nothing else asserted.
 *
 * <p>{@code InternalSecretFilter} 403s everything but the three health URIs, and the frontend has
 * now closed its own {@code /api/proxy/actuator/**} hole, so this is the third layer rather than
 * the only one. It exists because the first two are code and this is configuration -- a one-line
 * edit to a property file can widen the surface without touching anything a compiler or a filter
 * test would notice.
 *
 * <p>Reads src/main/resources directly rather than the classpath, per working rule 2:
 * src/test/resources/application.properties shadows the shipped file during tests, so a {@code
 * ClassPathResource} lookup would assert against the stub and pass vacuously.
 */
class ActuatorExposureTest {

  private static final Path SHIPPED = Path.of("src", "main", "resources", "application.properties");

  /**
   * Endpoints that must never be reachable. Each either dumps configuration, dumps process state,
   * or mutates the running application.
   */
  private static final List<String> MUST_BE_EXCLUDED =
      List.of(
          "env",
          "configprops",
          "beans",
          "mappings",
          "heapdump",
          "threaddump",
          "loggers",
          "shutdown");

  private static String shippedProperty(String key) throws IOException {
    Properties properties = new Properties();
    properties.load(new StringReader(Files.readString(SHIPPED)));
    return properties.getProperty(key);
  }

  private static List<String> shippedList(String key) throws IOException {
    String value = shippedProperty(key);
    return value == null ? List.of() : Arrays.stream(value.split(",")).map(String::trim).toList();
  }

  @Test
  @DisplayName("health is the only exposed actuator endpoint")
  void exposureInclude_isHealthOnly() throws IOException {
    assertThat(shippedList("management.endpoints.web.exposure.include")).containsExactly("health");
  }

  @Test
  @DisplayName("the exclude list covers every config-dumping and state-mutating endpoint")
  void exposureExclude_namesEverySensitiveEndpoint() throws IOException {
    // Boot applies exclude after include, so this survives a stray
    // MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=* injected into a deployed environment -- which
    // working rule 1 says would otherwise outrank the include property above.
    assertThat(shippedList("management.endpoints.web.exposure.exclude"))
        .containsExactlyInAnyOrderElementsOf(MUST_BE_EXCLUDED);
  }

  @Test
  @DisplayName("excluding health would silently break the load balancer's probe")
  void exposureExclude_doesNotContainHealth() throws IOException {
    assertThat(shippedList("management.endpoints.web.exposure.exclude")).doesNotContain("health");
  }

  @Test
  @DisplayName("health details stay hidden, since the probe is reachable without the secret")
  void healthShowDetails_isNever() throws IOException {
    assertThat(shippedProperty("management.endpoint.health.show-details")).isEqualTo("never");
  }
}
