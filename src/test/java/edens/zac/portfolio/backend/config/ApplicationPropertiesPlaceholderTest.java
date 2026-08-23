package edens.zac.portfolio.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.PropertyPlaceholderHelper;

/**
 * Guards the shipped property files against bash-style {@code ${VAR:-default}} placeholders.
 *
 * <p>Spring's value separator is {@code :} and it splits on the first one it finds, so {@code
 * ${POSTGRES_HOST:-localhost}} makes the default the literal {@code -localhost}. An unset variable
 * then yields a wrong value instead of the intended fallback. The same {@code :-} spelling is
 * correct in {@code docker-compose.yml} and the shell scripts, where bash does the interpreting, so
 * only the Spring-parsed files are covered here.
 *
 * <p>These read src/main/resources directly instead of the classpath, because
 * src/test/resources/application.properties shadows the shipped file during tests.
 */
class ApplicationPropertiesPlaceholderTest {

  private static final Path MAIN_RESOURCES = Path.of("src", "main", "resources");

  private static final Pattern BASH_STYLE_DEFAULT = Pattern.compile("\\$\\{[^}:]+:-");

  private static final PropertyPlaceholderHelper HELPER =
      new PropertyPlaceholderHelper("${", "}", ":", true);

  private static String rawText(String fileName) throws IOException {
    return Files.readString(MAIN_RESOURCES.resolve(fileName));
  }

  /**
   * Resolves one property with every environment variable absent, the only condition under which
   * the inline defaults are exercised.
   *
   * @param fileName property file under src/main/resources
   * @param key property whose placeholders should be resolved
   * @return the resolved value
   */
  private static String resolveWithNoEnvironment(String fileName, String key) throws IOException {
    Properties properties = new Properties();
    properties.load(new StringReader(rawText(fileName)));
    return HELPER.replacePlaceholders(properties.getProperty(key), placeholder -> null);
  }

  @ParameterizedTest
  @ValueSource(strings = {"application.properties", "application-dev.properties"})
  @DisplayName("property files use Spring's ':' separator, never bash's ':-'")
  void propertyFiles_containNoBashStyleDefaults(String fileName) throws IOException {
    assertThat(BASH_STYLE_DEFAULT.matcher(rawText(fileName)).find())
        .as("%s still contains a ${VAR:-default} placeholder", fileName)
        .isFalse();
  }

  @Test
  @DisplayName("datasource URL falls back to localhost when POSTGRES_* are unset")
  void datasourceUrl_withoutEnvironment_fallsBackToLocalhost() throws IOException {
    assertThat(resolveWithNoEnvironment("application.properties", "spring.datasource.url"))
        .isEqualTo("jdbc:postgresql://localhost:5432/edens_zac");
  }

  @Test
  @DisplayName("datasource username falls back to zedens when POSTGRES_USER is unset")
  void datasourceUsername_withoutEnvironment_fallsBackToZedens() throws IOException {
    assertThat(resolveWithNoEnvironment("application.properties", "spring.datasource.username"))
        .isEqualTo("zedens");
  }

  @Test
  @DisplayName("active profile falls back to 'default' when SPRING_PROFILES_ACTIVE is unset")
  void activeProfile_withoutEnvironment_fallsBackToDefault() throws IOException {
    assertThat(resolveWithNoEnvironment("application.properties", "spring.profiles.active"))
        .isEqualTo("default");
  }
}
