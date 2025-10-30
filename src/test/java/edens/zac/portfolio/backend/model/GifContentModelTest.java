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

class GifContentModelTest {

    private Validator validator;
    private GifContentModel gifContentBlock;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        gifContentBlock = new GifContentModel();
    }

    @Test
    @DisplayName("Valid GifContentBlockModel should pass validation")
    void validGifContentBlockModel_shouldPassValidation() {
        // Arrange
        gifContentBlock.setCollectionId(1L);
        gifContentBlock.setOrderIndex(0);
        gifContentBlock.setContentType(ContentType.GIF);
        gifContentBlock.setGifUrl("https://example.com/gif.gif");
        gifContentBlock.setTitle("Test GIF");
        gifContentBlock.setThumbnailUrl("https://example.com/thumbnail.jpg");
        gifContentBlock.setWidth(400);
        gifContentBlock.setHeight(300);
        gifContentBlock.setAuthor("Test Author");
        gifContentBlock.setCreateDate("2024-01-01");

        // Act
        Set<ConstraintViolation<GifContentModel>> violations = validator.validate(gifContentBlock);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Null gifUrl should fail validation")
    void nullGifUrl_shouldFailValidation() {
        // Arrange
        gifContentBlock.setCollectionId(1L);
        gifContentBlock.setOrderIndex(0);
        gifContentBlock.setContentType(ContentType.GIF);
        gifContentBlock.setGifUrl(null); // Invalid - null

        // Act
        Set<ConstraintViolation<GifContentModel>> violations = validator.validate(gifContentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<GifContentModel> violation = violations.iterator().next();
        assertEquals("gifUrl", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("must not be null"));
    }

    @Test
    @DisplayName("Title over 255 characters should fail validation")
    void longTitle_shouldFailValidation() {
        // Arrange
        String longTitle = "A".repeat(256); // 256 characters
        gifContentBlock.setCollectionId(1L);
        gifContentBlock.setOrderIndex(0);
        gifContentBlock.setContentType(ContentType.GIF);
        gifContentBlock.setGifUrl("https://example.com/gif.gif");
        gifContentBlock.setTitle(longTitle); // Invalid - too long

        // Act
        Set<ConstraintViolation<GifContentModel>> violations = validator.validate(gifContentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<GifContentModel> violation = violations.iterator().next();
        assertEquals("title", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 255"));
    }

    @Test
    @DisplayName("Title at max length should pass validation")
    void maxLengthTitle_shouldPassValidation() {
        // Arrange
        String maxTitle = "A".repeat(255); // Exactly 255 characters
        gifContentBlock.setCollectionId(1L);
        gifContentBlock.setOrderIndex(0);
        gifContentBlock.setContentType(ContentType.GIF);
        gifContentBlock.setGifUrl("https://example.com/gif.gif");
        gifContentBlock.setTitle(maxTitle);

        // Act
        Set<ConstraintViolation<GifContentModel>> violations = validator.validate(gifContentBlock);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("ThumbnailUrl over 255 characters should fail validation")
    void longThumbnailUrl_shouldFailValidation() {
        // Arrange
        String longThumbnailUrl = "https://example.com/" + "A".repeat(240); // Over 255 chars
        gifContentBlock.setCollectionId(1L);
        gifContentBlock.setOrderIndex(0);
        gifContentBlock.setContentType(ContentType.GIF);
        gifContentBlock.setGifUrl("https://example.com/gif.gif");
        gifContentBlock.setThumbnailUrl(longThumbnailUrl); // Invalid - too long

        // Act
        Set<ConstraintViolation<GifContentModel>> violations = validator.validate(gifContentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<GifContentModel> violation = violations.iterator().next();
        assertEquals("thumbnailUrl", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 255"));
    }

    @Test
    @DisplayName("ThumbnailUrl at max length should pass validation")
    void maxLengthThumbnailUrl_shouldPassValidation() {
        // Arrange
        String maxThumbnailUrl = "A".repeat(255); // Exactly 255 characters
        gifContentBlock.setCollectionId(1L);
        gifContentBlock.setOrderIndex(0);
        gifContentBlock.setContentType(ContentType.GIF);
        gifContentBlock.setGifUrl("https://example.com/gif.gif");
        gifContentBlock.setThumbnailUrl(maxThumbnailUrl);

        // Act
        Set<ConstraintViolation<GifContentModel>> violations = validator.validate(gifContentBlock);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Author over 100 characters should fail validation")
    void longAuthor_shouldFailValidation() {
        // Arrange
        String longAuthor = "A".repeat(101); // 101 characters
        gifContentBlock.setCollectionId(1L);
        gifContentBlock.setOrderIndex(0);
        gifContentBlock.setContentType(ContentType.GIF);
        gifContentBlock.setGifUrl("https://example.com/gif.gif");
        gifContentBlock.setAuthor(longAuthor); // Invalid - too long

        // Act
        Set<ConstraintViolation<GifContentModel>> violations = validator.validate(gifContentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<GifContentModel> violation = violations.iterator().next();
        assertEquals("author", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 100"));
    }

    @Test
    @DisplayName("Author at max length should pass validation")
    void maxLengthAuthor_shouldPassValidation() {
        // Arrange
        String maxAuthor = "A".repeat(100); // Exactly 100 characters
        gifContentBlock.setCollectionId(1L);
        gifContentBlock.setOrderIndex(0);
        gifContentBlock.setContentType(ContentType.GIF);
        gifContentBlock.setGifUrl("https://example.com/gif.gif");
        gifContentBlock.setAuthor(maxAuthor);

        // Act
        Set<ConstraintViolation<GifContentModel>> violations = validator.validate(gifContentBlock);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Optional fields can be null")
    void optionalFields_canBeNull() {
        // Arrange
        gifContentBlock.setCollectionId(1L);
        gifContentBlock.setOrderIndex(0);
        gifContentBlock.setContentType(ContentType.GIF);
        gifContentBlock.setGifUrl("https://example.com/gif.gif");
        // Leave title, thumbnailUrl, width, height, author, createDate as null

        // Act
        Set<ConstraintViolation<GifContentModel>> violations = validator.validate(gifContentBlock);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Dimensions can be any integer values")
    void dimensions_canBeAnyIntegerValues() {
        // Arrange
        gifContentBlock.setCollectionId(1L);
        gifContentBlock.setOrderIndex(0);
        gifContentBlock.setContentType(ContentType.GIF);
        gifContentBlock.setGifUrl("https://example.com/gif.gif");
        gifContentBlock.setWidth(1920);
        gifContentBlock.setHeight(1080);

        // Act
        Set<ConstraintViolation<GifContentModel>> violations = validator.validate(gifContentBlock);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Zero dimensions should pass validation")
    void zeroDimensions_shouldPassValidation() {
        // Arrange
        gifContentBlock.setCollectionId(1L);
        gifContentBlock.setOrderIndex(0);
        gifContentBlock.setContentType(ContentType.GIF);
        gifContentBlock.setGifUrl("https://example.com/gif.gif");
        gifContentBlock.setWidth(0);
        gifContentBlock.setHeight(0);

        // Act
        Set<ConstraintViolation<GifContentModel>> violations = validator.validate(gifContentBlock);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Negative dimensions should pass validation")
    void negativeDimensions_shouldPassValidation() {
        // Arrange (no constraints on dimensions, so negatives should be allowed)
        gifContentBlock.setCollectionId(1L);
        gifContentBlock.setOrderIndex(0);
        gifContentBlock.setContentType(ContentType.GIF);
        gifContentBlock.setGifUrl("https://example.com/gif.gif");
        gifContentBlock.setWidth(-1);
        gifContentBlock.setHeight(-1);

        // Act
        Set<ConstraintViolation<GifContentModel>> violations = validator.validate(gifContentBlock);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Lombok inheritance works correctly")
    void lombokInheritance_worksCorrectly() {
        // Arrange
        GifContentModel block1 = new GifContentModel();
        block1.setId(1L);
        block1.setCollectionId(1L);
        block1.setOrderIndex(0);
        block1.setContentType(ContentType.GIF);
        block1.setGifUrl("https://example.com/gif.gif");
        block1.setTitle("Test GIF");

        GifContentModel block2 = new GifContentModel();
        block2.setId(1L);
        block2.setCollectionId(1L);
        block2.setOrderIndex(0);
        block2.setContentType(ContentType.GIF);
        block2.setGifUrl("https://example.com/gif.gif");
        block2.setTitle("Test GIF");

        // Act & Assert
        assertEquals(block1, block2);
        assertEquals(block1.hashCode(), block2.hashCode());
        assertTrue(block1.toString().contains("GifContentBlockModel"));
    }

    @Test
    @DisplayName("Different gifUrl creates different objects")
    void differentGifUrl_createsDifferentObjects() {
        // Arrange
        GifContentModel block1 = new GifContentModel();
        block1.setCollectionId(1L);
        block1.setOrderIndex(0);
        block1.setContentType(ContentType.GIF);
        block1.setGifUrl("https://example.com/gif1.gif");

        GifContentModel block2 = new GifContentModel();
        block2.setCollectionId(1L);
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
        GifContentModel block1 = new GifContentModel();
        block1.setCollectionId(1L);
        block1.setOrderIndex(0);
        block1.setContentType(ContentType.GIF);
        block1.setGifUrl("https://example.com/gif.gif");
        block1.setWidth(400);
        block1.setHeight(300);

        GifContentModel block2 = new GifContentModel();
        block2.setCollectionId(1L);
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
        String longTitle = "A".repeat(256);
        String longThumbnailUrl = "A".repeat(256);
        String longAuthor = "A".repeat(101);
        
        gifContentBlock.setCollectionId(null); // Error 1 - inherited
        gifContentBlock.setOrderIndex(-1); // Error 2 - inherited
        gifContentBlock.setContentType(null); // Error 3 - inherited
        gifContentBlock.setGifUrl(null); // Error 4 - null gif URL
        gifContentBlock.setTitle(longTitle); // Error 5 - long title
        gifContentBlock.setThumbnailUrl(longThumbnailUrl); // Error 6 - long thumbnail URL
        gifContentBlock.setAuthor(longAuthor); // Error 7 - long author

        // Act
        Set<ConstraintViolation<GifContentModel>> violations = validator.validate(gifContentBlock);

        // Assert
        assertEquals(7, violations.size());
    }

    @Test
    @DisplayName("Inherits validation from ContentBlockModel")
    void inheritsValidation_fromContentBlockModel() {
        // Arrange - Test that inherited validation still works
        gifContentBlock.setCollectionId(null); // Invalid from parent
        gifContentBlock.setOrderIndex(0);
        gifContentBlock.setContentType(ContentType.GIF);
        gifContentBlock.setGifUrl("https://example.com/gif.gif");

        // Act
        Set<ConstraintViolation<GifContentModel>> violations = validator.validate(gifContentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<GifContentModel> violation = violations.iterator().next();
        assertEquals("collectionId", violation.getPropertyPath().toString());
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
            gifContentBlock.setCollectionId(1L);
            gifContentBlock.setOrderIndex(0);
            gifContentBlock.setContentType(ContentType.GIF);
            gifContentBlock.setGifUrl(url);

            // Act
            Set<ConstraintViolation<GifContentModel>> violations = validator.validate(gifContentBlock);

            // Assert
            assertTrue(violations.isEmpty(), "URL '" + url + "' should be valid");
        }
    }

    @Test
    @DisplayName("CreateDate can be any string format")
    void createDate_canBeAnyStringFormat() {
        // Arrange
        gifContentBlock.setCollectionId(1L);
        gifContentBlock.setOrderIndex(0);
        gifContentBlock.setContentType(ContentType.GIF);
        gifContentBlock.setGifUrl("https://example.com/gif.gif");
        gifContentBlock.setCreateDate("2024-01-01T10:30:00Z"); // ISO format

        // Act
        Set<ConstraintViolation<GifContentModel>> violations = validator.validate(gifContentBlock);

        // Assert
        assertTrue(violations.isEmpty());
    }
}