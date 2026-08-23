package edens.zac.portfolio.backend.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
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
   * An unknown discriminator must reach the caller as a 400, not become a TEXT block. This used to
   * coerce everything unrecognised to TEXT, so a typo silently produced valid-looking content.
   */
  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "invalid", "IMAGES", "TEXT-BLOCK"})
  void forValue_WithUnknownValue_Throws(String invalidValue) {
    assertThrows(IllegalArgumentException.class, () -> ContentType.forValue(invalidValue));
  }

  /**
   * Case variance is tolerated, matching CollectionVisibility.forValue. The serialized form is
   * uppercase, so a lowercase name is a client quirk rather than an unknown value.
   */
  @ParameterizedTest
  @ValueSource(strings = {"image", "Image", "iMaGe"})
  void forValue_IsCaseInsensitive(String value) {
    assertEquals(ContentType.IMAGE, ContentType.forValue(value));
  }

  @Test
  void forValue_UnknownValue_NamesTheValidOptions() {
    assertThat(
            assertThrows(IllegalArgumentException.class, () -> ContentType.forValue("TEXT-BLOCK"))
                .getMessage())
        .contains("TEXT-BLOCK")
        .contains("IMAGE, TEXT, GIF, COLLECTION");
  }

  static Stream<Arguments> provideNameAndEnum() {
    return Stream.of(
        Arguments.of("IMAGE", ContentType.IMAGE),
        Arguments.of("TEXT", ContentType.TEXT),
        Arguments.of("GIF", ContentType.GIF));
  }
}
