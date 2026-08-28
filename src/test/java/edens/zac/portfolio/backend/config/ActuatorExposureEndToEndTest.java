package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.AbstractPostgresIntegrationTest;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

/**
 * Proves the exclude list actually wins, rather than only that it is present.
 *
 * <p>{@link ActuatorExposureTest} pins what the shipped file says; this boots the app with the
 * worst realistic accident on top of it -- {@code MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=*}
 * injected into a deployed environment, which working rule 1 says outranks the shipped include --
 * and asserts the sensitive endpoints still do not exist. Without that, the whole hardening rests
 * on a documented ordering nobody had checked.
 *
 * <p>The exclude value here is a literal because {@code @SpringBootTest} properties are annotation
 * constants. {@link #excludeLiteralMatchesTheShippedFile()} keeps it honest.
 *
 * <p>The probe loop iterates {@link ActuatorExposureTest#MUST_BE_EXCLUDED}, not the exclude value
 * it is testing. Iterating the exclude value made an omission structurally invisible: drop a name
 * from the shipped file and the literal together and the loop simply stopped probing it, so {@code
 * caches} was reachable under {@code include=*} for as long as nobody thought to add it. The
 * expectation has to be written down somewhere the config cannot edit.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "management.endpoints.web.exposure.include=*",
      "management.endpoints.web.exposure.exclude="
          + ActuatorExposureEndToEndTest.SHIPPED_EXCLUDE_LITERAL
    })
class ActuatorExposureEndToEndTest extends AbstractPostgresIntegrationTest {

  static final String SHIPPED_EXCLUDE_LITERAL =
      "env,configprops,beans,mappings,heapdump,threaddump,loggers,shutdown,"
          + "caches,conditions,flyway,scheduledtasks";

  @LocalServerPort private int port;

  private final TestRestTemplate restTemplate = new TestRestTemplate();

  private HttpStatus statusOf(String path) {
    return HttpStatus.valueOf(
        restTemplate
            .getForEntity("http://localhost:" + port + path, String.class)
            .getStatusCode()
            .value());
  }

  @Test
  @DisplayName("exclude beats include=*, so the sensitive endpoints are not even registered")
  void sensitiveEndpoints_areNotRegistered_evenWhenIncludeIsWildcard() {
    for (String endpoint : ActuatorExposureTest.MUST_BE_EXCLUDED) {
      assertThat(statusOf("/actuator/" + endpoint))
          .as("/actuator/%s is reachable with include=*", endpoint)
          .isEqualTo(HttpStatus.NOT_FOUND);
    }
  }

  @Test
  @DisplayName("health survives the exclude list, so the deployment probe still works")
  void health_isStillReachable() {
    assertThat(statusOf("/actuator/health")).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("the literal above is still what the shipped file carries")
  void excludeLiteralMatchesTheShippedFile() throws IOException {
    Properties properties = new Properties();
    properties.load(
        new StringReader(
            Files.readString(Path.of("src", "main", "resources", "application.properties"))));
    assertThat(properties.getProperty("management.endpoints.web.exposure.exclude"))
        .isEqualTo(SHIPPED_EXCLUDE_LITERAL);
  }
}
