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
 * Pins the shipped actuator exposure, which nothing else asserts from the property file.
 *
 * <p>{@link ProdActuatorExposureGuard} is the real protection: it refuses prod startup unless the
 * resolved include is exactly {@code health}. This class pins the guard's premise -- that the
 * shipped include says {@code health} and nothing else -- so a widened property file reddens here
 * rather than only at deploy time.
 *
 * <p>Reads src/main/resources directly rather than the classpath, per working rule 2:
 * src/test/resources/application.properties shadows the shipped file during tests, so a {@code
 * ClassPathResource} lookup would assert against the stub and pass vacuously.
 */
class ActuatorExposureTest {

  private static final Path SHIPPED = Path.of("src", "main", "resources", "application.properties");

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
  @DisplayName("health details stay hidden, since the probe is reachable without the secret")
  void healthShowDetails_isNever() throws IOException {
    assertThat(shippedProperty("management.endpoint.health.show-details")).isEqualTo("never");
  }
}
