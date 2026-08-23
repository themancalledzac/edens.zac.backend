package edens.zac.portfolio.backend.types;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Covers ContentType.forValue, the Jackson @JsonCreator for every contentType on the wire. */
class ContentTypeTest {

  @ParameterizedTest
  @MethodSource("provideNameAndEnum")
  void forValue_WithValidValue_ShouldReturnCorrectEnum(String value, ContentType expectedType) {
    assertEquals(expectedType, ContentType.forValue(value));
  }

  /**
   * Pins the current lenient behavior. Tracker bug #13 changes forValue to throw on an unknown
   * discriminator; this test is expected to be rewritten with that fix.
   */
  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"invalid", "image", "IMAGES", "TEXT-BLOCK"})
  void forValue_WithInvalidValue_ShouldReturnText(String invalidValue) {
    assertEquals(ContentType.TEXT, ContentType.forValue(invalidValue));
  }

  static Stream<Arguments> provideNameAndEnum() {
    return Stream.of(
        Arguments.of("IMAGE", ContentType.IMAGE),
        Arguments.of("TEXT", ContentType.TEXT),
        Arguments.of("GIF", ContentType.GIF));
  }
}
