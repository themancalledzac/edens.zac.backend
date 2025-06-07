package edens.zac.portfolio.backend.model;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import edens.zac.portfolio.backend.types.ContentBlockType;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ImageContentBlockModelTest {

    private Validator validator;
    private ImageContentBlockModel imageContentBlock;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        imageContentBlock = new ImageContentBlockModel();
    }

    @Test
    @DisplayName("Valid ImageContentBlockModel should pass validation")
    void validImageContentBlockModel_shouldPassValidation() {
        // Arrange
        setupValidImageContentBlock();

        // Act
        Set<ConstraintViolation<ImageContentBlockModel>> violations = validator.validate(imageContentBlock);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Null imageUrlWeb should fail validation")
    void nullImageUrlWeb_shouldFailValidation() {
        // Arrange
        setupValidImageContentBlock();
        imageContentBlock.setImageUrlWeb(null); // Invalid

        // Act
        Set<ConstraintViolation<ImageContentBlockModel>> violations = validator.validate(imageContentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ImageContentBlockModel> violation = violations.iterator().next();
        assertEquals("imageUrlWeb", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("must not be null"));
    }

    @Test
    @DisplayName("Title over 250 characters should fail validation")
    void longTitle_shouldFailValidation() {
        // Arrange
        setupValidImageContentBlock();
        String longTitle = "A".repeat(251); // 251 characters
        imageContentBlock.setTitle(longTitle); // Invalid

        // Act
        Set<ConstraintViolation<ImageContentBlockModel>> violations = validator.validate(imageContentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ImageContentBlockModel> violation = violations.iterator().next();
        assertEquals("title", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 250"));
    }

    @Test
    @DisplayName("Author over 100 characters should fail validation")
    void longAuthor_shouldFailValidation() {
        // Arrange
        setupValidImageContentBlock();
        String longAuthor = "A".repeat(101); // 101 characters
        imageContentBlock.setAuthor(longAuthor); // Invalid

        // Act
        Set<ConstraintViolation<ImageContentBlockModel>> violations = validator.validate(imageContentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ImageContentBlockModel> violation = violations.iterator().next();
        assertEquals("author", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 100"));
    }

    @Test
    @DisplayName("fStop over 15 characters should fail validation")
    void longFStop_shouldFailValidation() {
        // Arrange
        setupValidImageContentBlock();
        String longFStop = "A".repeat(16); // 16 characters
        imageContentBlock.setFStop(longFStop); // Invalid

        // Act
        Set<ConstraintViolation<ImageContentBlockModel>> violations = validator.validate(imageContentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ImageContentBlockModel> violation = violations.iterator().next();
        assertEquals("fStop", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 15"));
    }

    @Test
    @DisplayName("Lens over 100 characters should fail validation")
    void longLens_shouldFailValidation() {
        // Arrange
        setupValidImageContentBlock();
        String longLens = "A".repeat(101); // 101 characters
        imageContentBlock.setLens(longLens); // Invalid

        // Act
        Set<ConstraintViolation<ImageContentBlockModel>> violations = validator.validate(imageContentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ImageContentBlockModel> violation = violations.iterator().next();
        assertEquals("lens", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 100"));
    }

    @Test
    @DisplayName("ShutterSpeed over 20 characters should fail validation")
    void longShutterSpeed_shouldFailValidation() {
        // Arrange
        setupValidImageContentBlock();
        String longShutterSpeed = "A".repeat(21); // 21 characters
        imageContentBlock.setShutterSpeed(longShutterSpeed); // Invalid

        // Act
        Set<ConstraintViolation<ImageContentBlockModel>> violations = validator.validate(imageContentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ImageContentBlockModel> violation = violations.iterator().next();
        assertEquals("shutterSpeed", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 20"));
    }

    @Test
    @DisplayName("RawFileName over 100 characters should fail validation")
    void longRawFileName_shouldFailValidation() {
        // Arrange
        setupValidImageContentBlock();
        String longRawFileName = "A".repeat(101); // 101 characters
        imageContentBlock.setRawFileName(longRawFileName); // Invalid

        // Act
        Set<ConstraintViolation<ImageContentBlockModel>> violations = validator.validate(imageContentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ImageContentBlockModel> violation = violations.iterator().next();
        assertEquals("rawFileName", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 100"));
    }

    @Test
    @DisplayName("Camera over 25 characters should fail validation")
    void longCamera_shouldFailValidation() {
        // Arrange
        setupValidImageContentBlock();
        String longCamera = "A".repeat(26); // 26 characters
        imageContentBlock.setCamera(longCamera); // Invalid

        // Act
        Set<ConstraintViolation<ImageContentBlockModel>> violations = validator.validate(imageContentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ImageContentBlockModel> violation = violations.iterator().next();
        assertEquals("camera", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 25"));
    }

    @Test
    @DisplayName("FocalLength over 20 characters should fail validation")
    void longFocalLength_shouldFailValidation() {
        // Arrange
        setupValidImageContentBlock();
        String longFocalLength = "A".repeat(21); // 21 characters
        imageContentBlock.setFocalLength(longFocalLength); // Invalid

        // Act
        Set<ConstraintViolation<ImageContentBlockModel>> violations = validator.validate(imageContentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ImageContentBlockModel> violation = violations.iterator().next();
        assertEquals("focalLength", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 20"));
    }

    @Test
    @DisplayName("Location over 250 characters should fail validation")
    void longLocation_shouldFailValidation() {
        // Arrange
        setupValidImageContentBlock();
        String longLocation = "A".repeat(251); // 251 characters
        imageContentBlock.setLocation(longLocation); // Invalid

        // Act
        Set<ConstraintViolation<ImageContentBlockModel>> violations = validator.validate(imageContentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ImageContentBlockModel> violation = violations.iterator().next();
        assertEquals("location", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 250"));
    }

    @Test
    @DisplayName("Valid fields at max length should pass validation")
    void maxLengthFields_shouldPassValidation() {
        // Arrange
        setupValidImageContentBlock();
        imageContentBlock.setTitle("A".repeat(250));
        imageContentBlock.setAuthor("A".repeat(100));
        imageContentBlock.setFStop("A".repeat(15));
        imageContentBlock.setLens("A".repeat(100));
        imageContentBlock.setShutterSpeed("A".repeat(20));
        imageContentBlock.setRawFileName("A".repeat(100));
        imageContentBlock.setCamera("A".repeat(25));
        imageContentBlock.setFocalLength("A".repeat(20));
        imageContentBlock.setLocation("A".repeat(250));

        // Act
        Set<ConstraintViolation<ImageContentBlockModel>> violations = validator.validate(imageContentBlock);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Optional fields can be null")
    void optionalFields_canBeNull() {
        // Arrange
        setupValidImageContentBlock();
        // Set optional fields to null
        imageContentBlock.setTitle(null);
        imageContentBlock.setImageWidth(null);
        imageContentBlock.setImageHeight(null);
        imageContentBlock.setIso(null);
        imageContentBlock.setAuthor(null);
        imageContentBlock.setRating(null);
        imageContentBlock.setFStop(null);
        imageContentBlock.setLens(null);
        imageContentBlock.setBlackAndWhite(null);
        imageContentBlock.setIsFilm(null);
        imageContentBlock.setShutterSpeed(null);
        imageContentBlock.setRawFileName(null);
        imageContentBlock.setCamera(null);
        imageContentBlock.setFocalLength(null);
        imageContentBlock.setLocation(null);
        imageContentBlock.setImageUrlRaw(null);
        imageContentBlock.setCreateDate(null);

        // Act
        Set<ConstraintViolation<ImageContentBlockModel>> violations = validator.validate(imageContentBlock);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Multiple validation errors are captured")
    void multipleValidationErrors_areCaptured() {
        // Arrange
        setupValidImageContentBlock();
        imageContentBlock.setImageUrlWeb(null); // Error 1
        imageContentBlock.setTitle("A".repeat(251)); // Error 2
        imageContentBlock.setAuthor("A".repeat(101)); // Error 3
        imageContentBlock.setLocation("A".repeat(251)); // Error 4

        // Act
        Set<ConstraintViolation<ImageContentBlockModel>> violations = validator.validate(imageContentBlock);

        // Assert
        assertEquals(4, violations.size());
    }

    @Test
    @DisplayName("Inherited ContentBlockModel validations work correctly")
    void inheritedValidations_workCorrectly() {
        // Arrange
        setupValidImageContentBlock();
        imageContentBlock.setCollectionId(null); // Invalid from parent
        imageContentBlock.setOrderIndex(-1); // Invalid from parent
        imageContentBlock.setBlockType(null); // Invalid from parent

        // Act
        Set<ConstraintViolation<ImageContentBlockModel>> violations = validator.validate(imageContentBlock);

        // Assert
        assertEquals(3, violations.size());
        
        // Check that we have violations for inherited fields
        boolean hasCollectionIdViolation = violations.stream()
                .anyMatch(v -> "collectionId".equals(v.getPropertyPath().toString()));
        boolean hasOrderIndexViolation = violations.stream()
                .anyMatch(v -> "orderIndex".equals(v.getPropertyPath().toString()));
        boolean hasBlockTypeViolation = violations.stream()
                .anyMatch(v -> "blockType".equals(v.getPropertyPath().toString()));

        assertTrue(hasCollectionIdViolation);
        assertTrue(hasOrderIndexViolation);
        assertTrue(hasBlockTypeViolation);
    }

    @Test
    @DisplayName("Lombok equality and hashCode work correctly")
    void lombokMethods_workCorrectly() {
        // Arrange
        ImageContentBlockModel image1 = new ImageContentBlockModel();
        setupValidImageContentBlock(image1);

        ImageContentBlockModel image2 = new ImageContentBlockModel();
        setupValidImageContentBlock(image2);

        // Act & Assert
        assertEquals(image1, image2);
        assertEquals(image1.hashCode(), image2.hashCode());
        assertTrue(image1.toString().contains("ImageContentBlockModel"));
    }

    @Test
    @DisplayName("Different image-specific fields create different objects")
    void differentImageFields_createsDifferentObjects() {
        // Arrange
        ImageContentBlockModel image1 = new ImageContentBlockModel();
        setupValidImageContentBlock(image1);

        ImageContentBlockModel image2 = new ImageContentBlockModel();
        setupValidImageContentBlock(image2);
        image2.setTitle("Different Title");

        // Act & Assert
        assertNotEquals(image1, image2);
    }

    @Test
    @DisplayName("Realistic image metadata values should pass validation")
    void realisticImageMetadata_shouldPassValidation() {
        // Arrange
        setupValidImageContentBlock();
        imageContentBlock.setTitle("Sunset at Arches National Park");
        imageContentBlock.setImageWidth(4000);
        imageContentBlock.setImageHeight(6000);
        imageContentBlock.setIso(100);
        imageContentBlock.setAuthor("Zac Eden");
        imageContentBlock.setRating(5);
        imageContentBlock.setFStop("f/8.0");
        imageContentBlock.setLens("Canon 24-70mm f/2.8L");
        imageContentBlock.setBlackAndWhite(false);
        imageContentBlock.setIsFilm(false);
        imageContentBlock.setShutterSpeed("1/125");
        imageContentBlock.setRawFileName("IMG_5432.CR3");
        imageContentBlock.setCamera("Canon EOS R5");
        imageContentBlock.setFocalLength("35mm");
        imageContentBlock.setLocation("Arches National Park, Utah");
        imageContentBlock.setImageUrlRaw("https://s3.amazonaws.com/portfolio/raw/IMG_5432.CR3");
        imageContentBlock.setCreateDate("2024-03-15");

        // Act
        Set<ConstraintViolation<ImageContentBlockModel>> violations = validator.validate(imageContentBlock);

        // Assert
        assertTrue(violations.isEmpty());
    }

    private void setupValidImageContentBlock() {
        setupValidImageContentBlock(imageContentBlock);
    }

    private void setupValidImageContentBlock(ImageContentBlockModel image) {
        // Set required fields from parent ContentBlockModel
        image.setCollectionId(1L);
        image.setOrderIndex(0);
        image.setBlockType(ContentBlockType.IMAGE);
        
        // Set required fields specific to ImageContentBlockModel
        image.setImageUrlWeb("https://s3.amazonaws.com/portfolio/web/test-image.jpg");
        
        // Set optional fields with valid values
        image.setTitle("Test Image");
        image.setAuthor("Test Author");
        image.setLocation("Test Location");
    }
}