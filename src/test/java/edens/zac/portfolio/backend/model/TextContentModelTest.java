package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.types.ContentType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TextContentModelTest {

    private Validator validator;
    private TextContentModel textContent;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        textContent = new TextContentModel();
    }

    @Test
    @DisplayName("Valid TextContentModel should pass validation")
    void validTextContentModel_shouldPassValidation() {
        // Arrange
        textContent.setCollectionId(1L);
        textContent.setOrderIndex(0);
        textContent.setContentType(ContentType.TEXT);
        textContent.setContent("This is some test content");
        textContent.setFormatType("markdown");
        textContent.setTitle("Test Title");

        // Act
        Set<ConstraintViolation<TextContentModel>> violations = validator.validate(textContent);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Blank content should fail validation")
    void blankContent_shouldFailValidation() {
        // Arrange
        textContent.setCollectionId(1L);
        textContent.setOrderIndex(0);
        textContent.setContentType(ContentType.TEXT);
        textContent.setContent(""); // Invalid - blank
        textContent.setFormatType("markdown");

        // Act
        Set<ConstraintViolation<TextContentModel>> violations = validator.validate(textContent);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<TextContentModel> violation = violations.iterator().next();
        assertEquals("content", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("must not be blank"));
    }

    @Test
    @DisplayName("Null content should fail validation")
    void nullContent_shouldFailValidation() {
        // Arrange
        textContent.setCollectionId(1L);
        textContent.setOrderIndex(0);
        textContent.setContentType(ContentType.TEXT);
        textContent.setContent(null); // Invalid - null
        textContent.setFormatType("markdown");

        // Act
        Set<ConstraintViolation<TextContentModel>> violations = validator.validate(textContent);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<TextContentModel> violation = violations.iterator().next();
        assertEquals("content", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("must not be blank"));
    }

    @Test
    @DisplayName("Content over 10000 characters should fail validation")
    void longContent_shouldFailValidation() {
        // Arrange
        String longContent = "A".repeat(10001); // 10001 characters
        textContent.setCollectionId(1L);
        textContent.setOrderIndex(0);
        textContent.setContentType(ContentType.TEXT);
        textContent.setContent(longContent); // Invalid - too long
        textContent.setFormatType("markdown");

        // Act
        Set<ConstraintViolation<TextContentModel>> violations = validator.validate(textContent);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<TextContentModel> violation = violations.iterator().next();
        assertEquals("content", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 10000"));
    }

    @Test
    @DisplayName("Content at max length should pass validation")
    void maxLengthContent_shouldPassValidation() {
        // Arrange
        String maxContent = "A".repeat(10000); // Exactly 10000 characters
        textContent.setCollectionId(1L);
        textContent.setOrderIndex(0);
        textContent.setContentType(ContentType.TEXT);
        textContent.setContent(maxContent);
        textContent.setFormatType("markdown");

        // Act
        Set<ConstraintViolation<TextContentModel>> violations = validator.validate(textContent);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Null formatType should fail validation")
    void nullFormatType_shouldFailValidation() {
        // Arrange
        textContent.setCollectionId(1L);
        textContent.setOrderIndex(0);
        textContent.setContentType(ContentType.TEXT);
        textContent.setContent("Test content");
        textContent.setFormatType(null); // Invalid - null

        // Act
        Set<ConstraintViolation<TextContentModel>> violations = validator.validate(textContent);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<TextContentModel> violation = violations.iterator().next();
        assertEquals("formatType", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("must not be null"));
    }

    @Test
    @DisplayName("FormatType over 20 characters should fail validation")
    void longFormatType_shouldFailValidation() {
        // Arrange
        String longFormatType = "A".repeat(21); // 21 characters
        textContent.setCollectionId(1L);
        textContent.setOrderIndex(0);
        textContent.setContentType(ContentType.TEXT);
        textContent.setContent("Test content");
        textContent.setFormatType(longFormatType); // Invalid - too long

        // Act
        Set<ConstraintViolation<TextContentModel>> violations = validator.validate(textContent);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<TextContentModel> violation = violations.iterator().next();
        assertEquals("formatType", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 20"));
    }

    @Test
    @DisplayName("FormatType at max length should pass validation")
    void maxLengthFormatType_shouldPassValidation() {
        // Arrange
        String maxFormatType = "A".repeat(20); // Exactly 20 characters
        textContent.setCollectionId(1L);
        textContent.setOrderIndex(0);
        textContent.setContentType(ContentType.TEXT);
        textContent.setContent("Test content");
        textContent.setFormatType(maxFormatType);

        // Act
        Set<ConstraintViolation<TextContentModel>> violations = validator.validate(textContent);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Valid format types should pass validation")
    void validFormatTypes_shouldPassValidation() {
        // Test common format types
        String[] validFormats = {"markdown", "html", "plain"};
        
        for (String format : validFormats) {
            // Arrange
            textContent.setCollectionId(1L);
            textContent.setOrderIndex(0);
            textContent.setContentType(ContentType.TEXT);
            textContent.setContent("Test content for " + format);
            textContent.setFormatType(format);

            // Act
            Set<ConstraintViolation<TextContentModel>> violations = validator.validate(textContent);

            // Assert
            assertTrue(violations.isEmpty(), "Format type '" + format + "' should be valid");
        }
    }

    @Test
    @DisplayName("Title over 250 characters should fail validation")
    void longTitle_shouldFailValidation() {
        // Arrange
        String longTitle = "A".repeat(251); // 251 characters
        textContent.setCollectionId(1L);
        textContent.setOrderIndex(0);
        textContent.setContentType(ContentType.TEXT);
        textContent.setContent("Test content");
        textContent.setFormatType("markdown");
        textContent.setTitle(longTitle); // Invalid - too long

        // Act
        Set<ConstraintViolation<TextContentModel>> violations = validator.validate(textContent);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<TextContentModel> violation = violations.iterator().next();
        assertEquals("title", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 250"));
    }

    @Test
    @DisplayName("Title at max length should pass validation")
    void maxLengthTitle_shouldPassValidation() {
        // Arrange
        String maxTitle = "A".repeat(250); // Exactly 250 characters
        textContent.setCollectionId(1L);
        textContent.setOrderIndex(0);
        textContent.setContentType(ContentType.TEXT);
        textContent.setContent("Test content");
        textContent.setFormatType("markdown");
        textContent.setTitle(maxTitle);

        // Act
        Set<ConstraintViolation<TextContentModel>> violations = validator.validate(textContent);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Title can be null")
    void nullTitle_shouldPassValidation() {
        // Arrange
        textContent.setCollectionId(1L);
        textContent.setOrderIndex(0);
        textContent.setContentType(ContentType.TEXT);
        textContent.setContent("Test content");
        textContent.setFormatType("markdown");
        textContent.setTitle(null); // Should be valid - title is optional

        // Act
        Set<ConstraintViolation<TextContentModel>> violations = validator.validate(textContent);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Lombok inheritance works correctly")
    void lombokInheritance_worksCorrectly() {
        // Arrange
        TextContentModel block1 = new TextContentModel();
        block1.setId(1L);
        block1.setCollectionId(1L);
        block1.setOrderIndex(0);
        block1.setContentType(ContentType.TEXT);
        block1.setContent("Test content");
        block1.setFormatType("markdown");
        block1.setTitle("Test Title");

        TextContentModel block2 = new TextContentModel();
        block2.setId(1L);
        block2.setCollectionId(1L);
        block2.setOrderIndex(0);
        block2.setContentType(ContentType.TEXT);
        block2.setContent("Test content");
        block2.setFormatType("markdown");
        block2.setTitle("Test Title");

        // Act & Assert
        assertEquals(block1, block2);
        assertEquals(block1.hashCode(), block2.hashCode());
        assertTrue(block1.toString().contains("TextContentModel"));
    }

    @Test
    @DisplayName("Different content creates different objects")
    void differentContent_createsDifferentObjects() {
        // Arrange
        TextContentModel block1 = new TextContentModel();
        block1.setCollectionId(1L);
        block1.setOrderIndex(0);
        block1.setContentType(ContentType.TEXT);
        block1.setContent("First content");
        block1.setFormatType("markdown");

        TextContentModel block2 = new TextContentModel();
        block2.setCollectionId(1L);
        block2.setOrderIndex(0);
        block2.setContentType(ContentType.TEXT);
        block2.setContent("Second content"); // Different content
        block2.setFormatType("markdown");

        // Act & Assert
        assertNotEquals(block1, block2);
    }

    @Test
    @DisplayName("Different formatType creates different objects")
    void differentFormatType_createsDifferentObjects() {
        // Arrange
        TextContentModel block1 = new TextContentModel();
        block1.setCollectionId(1L);
        block1.setOrderIndex(0);
        block1.setContentType(ContentType.TEXT);
        block1.setContent("Test content");
        block1.setFormatType("markdown");

        TextContentModel block2 = new TextContentModel();
        block2.setCollectionId(1L);
        block2.setOrderIndex(0);
        block2.setContentType(ContentType.TEXT);
        block2.setContent("Test content");
        block2.setFormatType("html"); // Different format

        // Act & Assert
        assertNotEquals(block1, block2);
    }

    @Test
    @DisplayName("Multiple validation errors are captured")
    void multipleValidationErrors_areCaptured() {
        // Arrange
        String longContent = "A".repeat(10001);
        String longFormatType = "A".repeat(21);
        String longTitle = "A".repeat(251);
        
        textContent.setCollectionId(null); // Error 1 - inherited
        textContent.setOrderIndex(-1); // Error 2 - inherited
        textContent.setContentType(null); // Error 3 - inherited
        textContent.setContent(""); // Error 4 - blank content
        textContent.setFormatType(longFormatType); // Error 5 - long format type
        textContent.setTitle(longTitle); // Error 6 - long title

        // Act
        Set<ConstraintViolation<TextContentModel>> violations = validator.validate(textContent);

        // Assert
        assertEquals(6, violations.size());
    }

    @Test
    @DisplayName("Inherits validation from ContentModel")
    void inheritsValidation_fromContentModel() {
        // Arrange - Test that inherited validation still works
        textContent.setCollectionId(null); // Invalid from parent
        textContent.setOrderIndex(0);
        textContent.setContentType(ContentType.TEXT);
        textContent.setContent("Test content");
        textContent.setFormatType("markdown");

        // Act
        Set<ConstraintViolation<TextContentModel>> violations = validator.validate(textContent);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<TextContentModel> violation = violations.iterator().next();
        assertEquals("collectionId", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("must not be null"));
    }
}