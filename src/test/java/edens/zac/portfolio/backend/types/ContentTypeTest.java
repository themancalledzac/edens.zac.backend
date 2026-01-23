package edens.zac.portfolio.backend.types;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ContentTypeTest {

  @Test
  void enum_ShouldHaveCorrectValues() {
    // Test the existence and order of enum values
    ContentType[] values = ContentType.values();
    assertEquals(4, values.length);
    assertEquals(ContentType.IMAGE, values[0]);
    assertEquals(ContentType.TEXT, values[1]);
    assertEquals(ContentType.GIF, values[2]);
    assertEquals(ContentType.COLLECTION, values[3]);
  }

  @ParameterizedTest
  @MethodSource("provideEnumAndContentName")
  void contentName_ShouldBeCorrect(ContentType type, String expectedContentName) {
    assertEquals(expectedContentName, type.getContentName());
  }

  @ParameterizedTest
  @MethodSource("provideEnumAndName")
  void getValue_ShouldReturnEnumName(ContentType type, String expectedName) {
    assertEquals(expectedName, type.getValue());
  }

  @ParameterizedTest
  @MethodSource("provideNameAndEnum")
  void forValue_WithValidValue_ShouldReturnCorrectEnum(String value, ContentType expectedType) {
    assertEquals(expectedType, ContentType.forValue(value));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"invalid", "image", "IMAGES", "TEXT-BLOCK"})
  void forValue_WithInvalidValue_ShouldReturnText(String invalidValue) {
    assertEquals(ContentType.TEXT, ContentType.forValue(invalidValue));
  }

  // Test data providers
  static Stream<Arguments> provideEnumAndContentName() {
    return Stream.of(
        Arguments.of(ContentType.IMAGE, "IMAGE"),
        Arguments.of(ContentType.TEXT, "TEXT"),
        Arguments.of(ContentType.GIF, "GIF"));
  }

  static Stream<Arguments> provideEnumAndName() {
    return Stream.of(
        Arguments.of(ContentType.IMAGE, "IMAGE"),
        Arguments.of(ContentType.TEXT, "TEXT"),
        Arguments.of(ContentType.GIF, "GIF"));
  }

  static Stream<Arguments> provideNameAndEnum() {
    return Stream.of(
        Arguments.of("IMAGE", ContentType.IMAGE),
        Arguments.of("TEXT", ContentType.TEXT),
        Arguments.of("GIF", ContentType.GIF));
  }
}
