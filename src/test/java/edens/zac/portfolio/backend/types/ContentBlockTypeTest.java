package edens.zac.portfolio.backend.types;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ContentBlockTypeTest {

    @Test
    void enum_ShouldHaveCorrectValues() {
        // Test the existence and order of enum values
        ContentBlockType[] values = ContentBlockType.values();
        assertEquals(4, values.length);
        assertEquals(ContentBlockType.IMAGE, values[0]);
        assertEquals(ContentBlockType.TEXT, values[1]);
        assertEquals(ContentBlockType.GIF, values[2]);
        assertEquals(ContentBlockType.CODE, values[3]);
    }

    @ParameterizedTest
    @MethodSource("provideEnumAndContentName")
    void contentName_ShouldBeCorrect(ContentBlockType type, String expectedContentName) {
        assertEquals(expectedContentName, type.getContentName());
    }

    @ParameterizedTest
    @MethodSource("provideEnumAndName")
    void getValue_ShouldReturnEnumName(ContentBlockType type, String expectedName) {
        assertEquals(expectedName, type.getValue());
    }

    @ParameterizedTest
    @MethodSource("provideNameAndEnum")
    void forValue_WithValidValue_ShouldReturnCorrectEnum(String value, ContentBlockType expectedType) {
        assertEquals(expectedType, ContentBlockType.forValue(value));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalid", "image", "IMAGES", "TEXT-BLOCK"})
    void forValue_WithInvalidValue_ShouldReturnText(String invalidValue) {
        assertEquals(ContentBlockType.TEXT, ContentBlockType.forValue(invalidValue));
    }

    // Test data providers
    static Stream<Arguments> provideEnumAndContentName() {
        return Stream.of(
                Arguments.of(ContentBlockType.IMAGE, "Image"),
                Arguments.of(ContentBlockType.TEXT, "Text"),
                Arguments.of(ContentBlockType.GIF, "Gif"),
                Arguments.of(ContentBlockType.CODE, "Code")
        );
    }

    static Stream<Arguments> provideEnumAndName() {
        return Stream.of(
                Arguments.of(ContentBlockType.IMAGE, "IMAGE"),
                Arguments.of(ContentBlockType.TEXT, "TEXT"),
                Arguments.of(ContentBlockType.GIF, "GIF"),
                Arguments.of(ContentBlockType.CODE, "CODE")
        );
    }

    static Stream<Arguments> provideNameAndEnum() {
        return Stream.of(
                Arguments.of("IMAGE", ContentBlockType.IMAGE),
                Arguments.of("TEXT", ContentBlockType.TEXT),
                Arguments.of("GIF", ContentBlockType.GIF),
                Arguments.of("CODE", ContentBlockType.CODE)
        );
    }
}