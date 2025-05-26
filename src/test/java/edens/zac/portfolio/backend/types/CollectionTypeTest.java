package edens.zac.portfolio.backend.types;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CollectionTypeTest {

    @Test
    void enum_ShouldHaveCorrectValues() {
        // Test the existence and order of enum values
        CollectionType[] values = CollectionType.values();
        assertEquals(4, values.length);
        assertEquals(CollectionType.BLOG, values[0]);
        assertEquals(CollectionType.ART_GALLERY, values[1]);
        assertEquals(CollectionType.CLIENT_GALLERY, values[2]);
        assertEquals(CollectionType.PORTFOLIO, values[3]);
    }

    @ParameterizedTest
    @MethodSource("provideEnumAndDisplayName")
    void displayName_ShouldBeCorrect(CollectionType type, String expectedDisplayName) {
        assertEquals(expectedDisplayName, type.getDisplayName());
    }

    @ParameterizedTest
    @MethodSource("provideEnumAndName")
    void getValue_ShouldReturnEnumName(CollectionType type, String expectedName) {
        assertEquals(expectedName, type.getValue());
    }

    @ParameterizedTest
    @MethodSource("provideNameAndEnum")
    void forValue_WithValidValue_ShouldReturnCorrectEnum(String value, CollectionType expectedType) {
        assertEquals(expectedType, CollectionType.forValue(value));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalid", "blog", "BLOGS", "ART-GALLERY"})
    void forValue_WithInvalidValue_ShouldReturnPortfolio(String invalidValue) {
        assertEquals(CollectionType.PORTFOLIO, CollectionType.forValue(invalidValue));
    }

    // Test data providers
    static Stream<Arguments> provideEnumAndDisplayName() {
        return Stream.of(
                Arguments.of(CollectionType.BLOG, "Blog"),
                Arguments.of(CollectionType.ART_GALLERY, "Art Gallery"),
                Arguments.of(CollectionType.CLIENT_GALLERY, "Client Gallery"),
                Arguments.of(CollectionType.PORTFOLIO, "Portfolio")
        );
    }

    static Stream<Arguments> provideEnumAndName() {
        return Stream.of(
                Arguments.of(CollectionType.BLOG, "BLOG"),
                Arguments.of(CollectionType.ART_GALLERY, "ART_GALLERY"),
                Arguments.of(CollectionType.CLIENT_GALLERY, "CLIENT_GALLERY"),
                Arguments.of(CollectionType.PORTFOLIO, "PORTFOLIO")
        );
    }

    static Stream<Arguments> provideNameAndEnum() {
        return Stream.of(
                Arguments.of("BLOG", CollectionType.BLOG),
                Arguments.of("ART_GALLERY", CollectionType.ART_GALLERY),
                Arguments.of("CLIENT_GALLERY", CollectionType.CLIENT_GALLERY),
                Arguments.of("PORTFOLIO", CollectionType.PORTFOLIO)
        );
    }
}