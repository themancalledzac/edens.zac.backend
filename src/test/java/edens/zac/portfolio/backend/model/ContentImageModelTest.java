package edens.zac.portfolio.backend.model;

import static org.junit.jupiter.api.Assertions.*;

import edens.zac.portfolio.backend.types.ContentType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContentImageModelTest {

  private Validator validator;
  private ContentImageModel contentImage;

  @BeforeEach
  void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
    contentImage = new ContentImageModel();
  }

  @Test
  @DisplayName("Valid ContentImageModel should pass validation")
  void validContentImageModel_shouldPassValidation() {
    // Arrange
    setupValidContentImage();

    // Act
    Set<ConstraintViolation<ContentImageModel>> violations = validator.validate(contentImage);

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("Null orderIndex should fail validation")
  void nullOrderIndex_shouldFailValidation() {
    // Arrange
    setupValidContentImage();
    contentImage.setOrderIndex(null);

    // Act
    Set<ConstraintViolation<ContentImageModel>> violations = validator.validate(contentImage);

    // Assert
    assertEquals(1, violations.size());
    ConstraintViolation<ContentImageModel> violation = violations.iterator().next();
    assertEquals("orderIndex", violation.getPropertyPath().toString());
    assertTrue(violation.getMessage().contains("must not be null"));
  }

  @Test
  @DisplayName("Title over 250 characters should fail validation")
  void longTitle_shouldFailValidation() {
    // Arrange
    setupValidContentImage();
    String longTitle = "A".repeat(251); // 251 characters
    contentImage.setTitle(longTitle); // Invalid

    // Act
    Set<ConstraintViolation<ContentImageModel>> violations = validator.validate(contentImage);

    // Assert
    assertEquals(1, violations.size());
    ConstraintViolation<ContentImageModel> violation = violations.iterator().next();
    assertEquals("title", violation.getPropertyPath().toString());
    assertTrue(violation.getMessage().contains("size must be between 0 and 250"));
  }

  @Test
  @DisplayName("Author over 100 characters should fail validation")
  void longAuthor_shouldFailValidation() {
    // Arrange
    setupValidContentImage();
    String longAuthor = "A".repeat(101); // 101 characters
    contentImage.setAuthor(longAuthor); // Invalid

    // Act
    Set<ConstraintViolation<ContentImageModel>> violations = validator.validate(contentImage);

    // Assert
    assertEquals(1, violations.size());
    ConstraintViolation<ContentImageModel> violation = violations.iterator().next();
    assertEquals("author", violation.getPropertyPath().toString());
    assertTrue(violation.getMessage().contains("size must be between 0 and 100"));
  }

  @Test
  @DisplayName("fStop over 15 characters should fail validation")
  void longFStop_shouldFailValidation() {
    // Arrange
    setupValidContentImage();
    String longFStop = "A".repeat(16); // 16 characters
    contentImage.setFStop(longFStop); // Invalid

    // Act
    Set<ConstraintViolation<ContentImageModel>> violations = validator.validate(contentImage);

    // Assert
    assertEquals(1, violations.size());
    ConstraintViolation<ContentImageModel> violation = violations.iterator().next();
    assertEquals("fStop", violation.getPropertyPath().toString());
    assertTrue(violation.getMessage().contains("size must be between 0 and 15"));
  }

  // Note: Lens validation test removed - lens is now a ContentLensModel object,
  // not a String field. Validation is handled at the entity level (ContentLensEntity).

  @Test
  @DisplayName("ShutterSpeed over 20 characters should fail validation")
  void longShutterSpeed_shouldFailValidation() {
    // Arrange
    setupValidContentImage();
    String longShutterSpeed = "A".repeat(21); // 21 characters
    contentImage.setShutterSpeed(longShutterSpeed); // Invalid

    // Act
    Set<ConstraintViolation<ContentImageModel>> violations = validator.validate(contentImage);

    // Assert
    assertEquals(1, violations.size());
    ConstraintViolation<ContentImageModel> violation = violations.iterator().next();
    assertEquals("shutterSpeed", violation.getPropertyPath().toString());
    assertTrue(violation.getMessage().contains("size must be between 0 and 20"));
  }

  @Test
  @DisplayName("FocalLength over 20 characters should fail validation")
  void longFocalLength_shouldFailValidation() {
    // Arrange
    setupValidContentImage();
    String longFocalLength = "A".repeat(21); // 21 characters
    contentImage.setFocalLength(longFocalLength); // Invalid

    // Act
    Set<ConstraintViolation<ContentImageModel>> violations = validator.validate(contentImage);

    // Assert
    assertEquals(1, violations.size());
    ConstraintViolation<ContentImageModel> violation = violations.iterator().next();
    assertEquals("focalLength", violation.getPropertyPath().toString());
    assertTrue(violation.getMessage().contains("size must be between 0 and 20"));
  }

  @Test
  @DisplayName("Location over 255 characters should fail validation")
  void longLocation_shouldFailValidation() {
    // Arrange
    setupValidContentImage();
    String longLocation = "A".repeat(256); // 256 characters
    contentImage.setLocation(LocationModel.builder().id(1L).name(longLocation).build()); // Invalid

    // Act
    Set<ConstraintViolation<ContentImageModel>> violations = validator.validate(contentImage);

    // Assert
    assertEquals(1, violations.size());
    ConstraintViolation<ContentImageModel> violation = violations.iterator().next();
    assertEquals("location.name", violation.getPropertyPath().toString());
    assertTrue(violation.getMessage().contains("Location cannot exceed 255 characters"));
  }

  @Test
  @DisplayName("Valid fields at max length should pass validation")
  void maxLengthFields_shouldPassValidation() {
    // Arrange
    setupValidContentImage();
    contentImage.setTitle("A".repeat(250));
    contentImage.setAuthor("A".repeat(100));
    contentImage.setFStop("A".repeat(15));
    // Note: lens is now a ContentLensModel object, not a validated String
    contentImage.setShutterSpeed("A".repeat(20));
    contentImage.setFocalLength("A".repeat(20));
    contentImage.setLocation(LocationModel.builder().id(1L).name("A".repeat(255)).build());

    // Act
    Set<ConstraintViolation<ContentImageModel>> violations = validator.validate(contentImage);

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("Optional fields can be null")
  void optionalFields_canBeNull() {
    // Arrange
    setupValidContentImage();
    // Set optional fields to null
    contentImage.setTitle(null);
    contentImage.setImageWidth(null);
    contentImage.setImageHeight(null);
    contentImage.setIso(null);
    contentImage.setAuthor(null);
    contentImage.setRating(null);
    contentImage.setFStop(null);
    contentImage.setLens(null);
    contentImage.setBlackAndWhite(null);
    contentImage.setIsFilm(null);
    contentImage.setShutterSpeed(null);
    contentImage.setCamera(null);
    contentImage.setFocalLength(null);
    contentImage.setLocation(null);
    contentImage.setCreateDate(null);

    // Act
    Set<ConstraintViolation<ContentImageModel>> violations = validator.validate(contentImage);

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("Multiple validation errors are captured")
  void multipleValidationErrors_areCaptured() {
    // Arrange
    setupValidContentImage();
    contentImage.setTitle("A".repeat(251)); // Error 1: title too long
    contentImage.setAuthor("A".repeat(101)); // Error 2: author too long
    contentImage.setLocation(LocationModel.builder().id(1L).name("A".repeat(256)).build()); // Error 3: location too long
    contentImage.setOrderIndex(-1); // Error 4: negative orderIndex

    // Act
    Set<ConstraintViolation<ContentImageModel>> violations = validator.validate(contentImage);

    // Assert
    assertEquals(4, violations.size());
  }

  @Test
  @DisplayName("Inherited ContentModel validations work correctly")
  void inheritedValidations_workCorrectly() {
    // Arrange
    setupValidContentImage();
    contentImage.setOrderIndex(-1); // Invalid from parent
    contentImage.setContentType(null); // Invalid from parent
    contentImage.setTitle("A".repeat(251)); // Invalid from parent

    // Act
    Set<ConstraintViolation<ContentImageModel>> violations = validator.validate(contentImage);

    // Assert
    assertEquals(3, violations.size());

    // Check that we have violations for inherited fields
    boolean hasOrderIndexViolation =
        violations.stream().anyMatch(v -> "orderIndex".equals(v.getPropertyPath().toString()));
    boolean hasContentTypeViolation =
        violations.stream().anyMatch(v -> "contentType".equals(v.getPropertyPath().toString()));
    boolean hasTitleViolation =
        violations.stream().anyMatch(v -> "title".equals(v.getPropertyPath().toString()));

    assertTrue(hasOrderIndexViolation);
    assertTrue(hasContentTypeViolation);
    assertTrue(hasTitleViolation);
  }

  @Test
  @DisplayName("Lombok equality and hashCode work correctly")
  void lombokMethods_workCorrectly() {
    // Arrange
    ContentImageModel image1 = new ContentImageModel();
    setupValidContentImage(image1);

    ContentImageModel image2 = new ContentImageModel();
    setupValidContentImage(image2);

    // Act & Assert
    assertEquals(image1, image2);
    assertEquals(image1.hashCode(), image2.hashCode());
    assertTrue(image1.toString().contains("ContentImageModel"));
  }

  @Test
  @DisplayName("Different image-specific fields create different objects")
  void differentImageFields_createsDifferentObjects() {
    // Arrange
    ContentImageModel image1 = new ContentImageModel();
    setupValidContentImage(image1);

    ContentImageModel image2 = new ContentImageModel();
    setupValidContentImage(image2);
    image2.setTitle("Different Title");

    // Act & Assert
    assertNotEquals(image1, image2);
  }

  @Test
  @DisplayName("Realistic image metadata values should pass validation")
  void realisticImageMetadata_shouldPassValidation() {
    // Arrange
    setupValidContentImage();
    contentImage.setTitle("Sunset at Arches National Park");
    contentImage.setImageWidth(4000);
    contentImage.setImageHeight(6000);
    contentImage.setIso(100);
    contentImage.setAuthor("Zac Eden");
    contentImage.setRating(5);
    contentImage.setFStop("f/8.0");
    contentImage.setLens(ContentLensModel.builder().id(1L).name("Canon 24-70mm f/2.8L").build());
    contentImage.setBlackAndWhite(false);
    contentImage.setIsFilm(false);
    contentImage.setShutterSpeed("1/125");
    contentImage.setCamera(new ContentCameraModel());
    contentImage.setFocalLength("35mm");
    contentImage.setLocation(LocationModel.builder().id(1L).name("Arches National Park, Utah").build());
    contentImage.setCreateDate("2024-03-15");

    // Act
    Set<ConstraintViolation<ContentImageModel>> violations = validator.validate(contentImage);

    // Assert
    assertTrue(violations.isEmpty());
  }

  private void setupValidContentImage() {
    setupValidContentImage(contentImage);
  }

  private void setupValidContentImage(ContentImageModel image) {
    // Set required fields from parent ContentModel
    image.setOrderIndex(0);
    image.setContentType(ContentType.IMAGE);

    // Set required fields specific to ContentImageModel
    image.setImageUrl("https://s3.amazonaws.com/portfolio/web/test-image.jpg");

    // Set optional fields with valid values
    image.setTitle("Test Image");
    image.setAuthor("Test Author");
    image.setLocation(LocationModel.builder().id(1L).name("Test Location").build());
  }
}
