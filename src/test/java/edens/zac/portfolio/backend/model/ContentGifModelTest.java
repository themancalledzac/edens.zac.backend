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

class ContentGifModelTest {

    private Validator validator;
    private ContentGifModel contentGif;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        contentGif = new ContentGifModel();
    }

    @Test
    @DisplayName("Valid ContentGifModel should pass validation")
    void validContentGifModel_shouldPassValidation() {
        // Arrange
        contentGif.setOrderIndex(0);
        contentGif.setContentType(ContentType.GIF);
        contentGif.setGifUrl("https://example.com/gif.gif");
        contentGif.setTitle("Test GIF");
        contentGif.setThumbnailUrl("https://example.com/thumbnail.jpg");
        contentGif.setWidth(400);
        contentGif.setHeight(300);
        contentGif.setAuthor("Test Author");
        contentGif.setCreateDate("2024-01-01");

        // Act
        Set<ConstraintViolation<ContentGifModel>> violations = validator.validate(contentGif);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Null gifUrl should fail validation")
    void nullGifUrl_shouldFailValidation() {
        // Arrange
        contentGif.setOrderIndex(0);
        contentGif.setContentType(ContentType.GIF);
        contentGif.setGifUrl(null); // Invalid - null

        // Act
        Set<ConstraintViolation<ContentGifModel>> violations = validator.validate(contentGif);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ContentGifModel> violation = violations.iterator().next();
        assertEquals("gifUrl", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("must not be null"));
    }

    @Test
    @DisplayName("Title over 255 characters should fail validation")
    void longTitle_shouldFailValidation() {
        // Arrange
        String longTitle = "A".repeat(256); // 256 characters
        contentGif.setOrderIndex(0);
        contentGif.setContentType(ContentType.GIF);
        contentGif.setGifUrl("https://example.com/gif.gif");
        contentGif.setTitle(longTitle); // Invalid - too long

        // Act
        Set<ConstraintViolation<ContentGifModel>> violations = validator.validate(contentGif);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ContentGifModel> violation = violations.iterator().next();
        assertEquals("title", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 250"));
    }

    @Test
    @DisplayName("Title at max length should pass validation")
    void maxLengthTitle_shouldPassValidation() {
        // Arrange
        String maxTitle = "A".repeat(250); // Exactly 250 characters (max for title)
        contentGif.setOrderIndex(0);
        contentGif.setContentType(ContentType.GIF);
        contentGif.setGifUrl("https://example.com/gif.gif");
        contentGif.setTitle(maxTitle);

        // Act
        Set<ConstraintViolation<ContentGifModel>> violations = validator.validate(contentGif);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("ThumbnailUrl over 255 characters should fail validation")
    void longThumbnailUrl_shouldFailValidation() {
        // Arrange
        String longThumbnailUrl = "https://example.com/" + "A".repeat(240); // Over 255 chars
        contentGif.setOrderIndex(0);
        contentGif.setContentType(ContentType.GIF);
        contentGif.setGifUrl("https://example.com/gif.gif");
        contentGif.setThumbnailUrl(longThumbnailUrl); // Invalid - too long

        // Act
        Set<ConstraintViolation<ContentGifModel>> violations = validator.validate(contentGif);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ContentGifModel> violation = violations.iterator().next();
        assertEquals("thumbnailUrl", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 255"));
    }

    @Test
    @DisplayName("ThumbnailUrl at max length should pass validation")
    void maxLengthThumbnailUrl_shouldPassValidation() {
        // Arrange
        String maxThumbnailUrl = "A".repeat(255); // Exactly 255 characters
        contentGif.setOrderIndex(0);
        contentGif.setContentType(ContentType.GIF);
        contentGif.setGifUrl("https://example.com/gif.gif");
        contentGif.setThumbnailUrl(maxThumbnailUrl);

        // Act
        Set<ConstraintViolation<ContentGifModel>> violations = validator.validate(contentGif);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Author over 100 characters should fail validation")
    void longAuthor_shouldFailValidation() {
        // Arrange
        String longAuthor = "A".repeat(101); // 101 characters
        contentGif.setOrderIndex(0);
        contentGif.setContentType(ContentType.GIF);
        contentGif.setGifUrl("https://example.com/gif.gif");
        contentGif.setAuthor(longAuthor); // Invalid - too long

        // Act
        Set<ConstraintViolation<ContentGifModel>> violations = validator.validate(contentGif);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ContentGifModel> violation = violations.iterator().next();
        assertEquals("author", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 100"));
    }

    @Test
    @DisplayName("Author at max length should pass validation")
    void maxLengthAuthor_shouldPassValidation() {
        // Arrange
        String maxAuthor = "A".repeat(100); // Exactly 100 characters
        contentGif.setOrderIndex(0);
        contentGif.setContentType(ContentType.GIF);
        contentGif.setGifUrl("https://example.com/gif.gif");
        contentGif.setAuthor(maxAuthor);

        // Act
        Set<ConstraintViolation<ContentGifModel>> violations = validator.validate(contentGif);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Optional fields can be null")
    void optionalFields_canBeNull() {
        // Arrange
        contentGif.setOrderIndex(0);
        contentGif.setContentType(ContentType.GIF);
        contentGif.setGifUrl("https://example.com/gif.gif");
        // Leave title, thumbnailUrl, width, height, author, createDate as null

        // Act
        Set<ConstraintViolation<ContentGifModel>> violations = validator.validate(contentGif);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Dimensions can be any integer values")
    void dimensions_canBeAnyIntegerValues() {
        // Arrange
        contentGif.setOrderIndex(0);
        contentGif.setContentType(ContentType.GIF);
        contentGif.setGifUrl("https://example.com/gif.gif");
        contentGif.setWidth(1920);
        contentGif.setHeight(1080);

        // Act
        Set<ConstraintViolation<ContentGifModel>> violations = validator.validate(contentGif);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Zero dimensions should pass validation")
    void zeroDimensions_shouldPassValidation() {
        // Arrange
        contentGif.setOrderIndex(0);
        contentGif.setContentType(ContentType.GIF);
        contentGif.setGifUrl("https://example.com/gif.gif");
        contentGif.setWidth(0);
        contentGif.setHeight(0);

        // Act
        Set<ConstraintViolation<ContentGifModel>> violations = validator.validate(contentGif);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Negative dimensions should pass validation")
    void negativeDimensions_shouldPassValidation() {
        // Arrange (no constraints on dimensions, so negatives should be allowed)
        contentGif.setOrderIndex(0);
        contentGif.setContentType(ContentType.GIF);
        contentGif.setGifUrl("https://example.com/gif.gif");
        contentGif.setWidth(-1);
        contentGif.setHeight(-1);

        // Act
        Set<ConstraintViolation<ContentGifModel>> violations = validator.validate(contentGif);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Lombok inheritance works correctly")
    void lombokInheritance_worksCorrectly() {
        // Arrange
        ContentGifModel block1 = new ContentGifModel();
        block1.setId(1L);
        block1.setOrderIndex(0);
        block1.setContentType(ContentType.GIF);
        block1.setGifUrl("https://example.com/gif.gif");
        block1.setTitle("Test GIF");

        ContentGifModel block2 = new ContentGifModel();
        block2.setId(1L);
        block2.setOrderIndex(0);
        block2.setContentType(ContentType.GIF);
        block2.setGifUrl("https://example.com/gif.gif");
        block2.setTitle("Test GIF");

        // Act & Assert
        assertEquals(block1, block2);
        assertEquals(block1.hashCode(), block2.hashCode());
        assertTrue(block1.toString().contains("ContentGifModel"));
    }

    @Test
    @DisplayName("Different gifUrl creates different objects")
    void differentGifUrl_createsDifferentObjects() {
        // Arrange
        ContentGifModel block1 = new ContentGifModel();
        block1.setOrderIndex(0);
        block1.setContentType(ContentType.GIF);
        block1.setGifUrl("https://example.com/gif1.gif");

        ContentGifModel block2 = new ContentGifModel();
        block2.setOrderIndex(0);
        block2.setContentType(ContentType.GIF);
        block2.setGifUrl("https://example.com/gif2.gif"); // Different URL

        // Act & Assert
        assertNotEquals(block1, block2);
    }

    @Test
    @DisplayName("Different dimensions create different objects")
    void differentDimensions_createDifferentObjects() {
        // Arrange
        ContentGifModel block1 = new ContentGifModel();
        block1.setOrderIndex(0);
        block1.setContentType(ContentType.GIF);
        block1.setGifUrl("https://example.com/gif.gif");
        block1.setWidth(400);
        block1.setHeight(300);

        ContentGifModel block2 = new ContentGifModel();
        block2.setOrderIndex(0);
        block2.setContentType(ContentType.GIF);
        block2.setGifUrl("https://example.com/gif.gif");
        block2.setWidth(800); // Different width
        block2.setHeight(600); // Different height

        // Act & Assert
        assertNotEquals(block1, block2);
    }

    @Test
    @DisplayName("Multiple validation errors are captured")
    void multipleValidationErrors_areCaptured() {
        // Arrange
        String longTitle = "A".repeat(251); // Too long (max 250)
        String longThumbnailUrl = "A".repeat(256); // Too long (max 255)
        String longAuthor = "A".repeat(101); // Too long (max 100)
        
        contentGif.setOrderIndex(-1); // Error 1 - inherited (negative)
        contentGif.setContentType(null); // Error 2 - inherited (null)
        contentGif.setGifUrl(null); // Error 3 - null gif URL
        contentGif.setTitle(longTitle); // Error 4 - long title
        contentGif.setThumbnailUrl(longThumbnailUrl); // Error 5 - long thumbnail URL
        contentGif.setAuthor(longAuthor); // Error 6 - long author

        // Act
        Set<ConstraintViolation<ContentGifModel>> violations = validator.validate(contentGif);

        // Assert
        assertEquals(6, violations.size());
    }

    @Test
    @DisplayName("Inherits validation from ContentModel")
    void inheritsValidation_fromContentModel() {
        // Arrange - Test that inherited validation still works
        contentGif.setOrderIndex(0);
        // contentType is null (not set) - should fail validation
        contentGif.setGifUrl("https://example.com/gif.gif");

        // Act
        Set<ConstraintViolation<ContentGifModel>> violations = validator.validate(contentGif);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ContentGifModel> violation = violations.iterator().next();
        assertEquals("contentType", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("must not be null"));
    }

    @Test
    @DisplayName("Valid URL formats should pass validation")
    void validUrlFormats_shouldPassValidation() {
        // Test various valid URL formats
        String[] validUrls = {
            "https://example.com/gif.gif",
            "http://example.com/gif.gif",
            "https://cdn.example.com/path/to/gif.gif",
            "/relative/path/gif.gif",
            "gif.gif"
        };
        
        for (String url : validUrls) {
            // Arrange
            contentGif.setOrderIndex(0);
            contentGif.setContentType(ContentType.GIF);
            contentGif.setGifUrl(url);

            // Act
            Set<ConstraintViolation<ContentGifModel>> violations = validator.validate(contentGif);

            // Assert
            assertTrue(violations.isEmpty(), "URL '" + url + "' should be valid");
        }
    }

    @Test
    @DisplayName("CreateDate can be any string format")
    void createDate_canBeAnyStringFormat() {
        // Arrange
        contentGif.setOrderIndex(0);
        contentGif.setContentType(ContentType.GIF);
        contentGif.setGifUrl("https://example.com/gif.gif");
        contentGif.setCreateDate("2024-01-01T10:30:00Z"); // ISO format

        // Act
        Set<ConstraintViolation<ContentGifModel>> violations = validator.validate(contentGif);

        // Assert
        assertTrue(violations.isEmpty());
    }
}