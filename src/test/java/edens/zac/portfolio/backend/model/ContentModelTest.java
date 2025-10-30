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

class ContentModelTest {

    private Validator validator;
    private ContentModel contentBlock;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        
        // Create ContentBlockModel directly since it's not abstract
        contentBlock = new ContentModel();
    }

    @Test
    @DisplayName("Valid ContentBlockModel should pass validation")
    void validContentBlockModel_shouldPassValidation() {
        // Arrange
        contentBlock.setCollectionId(1L);
        contentBlock.setOrderIndex(0);
        contentBlock.setContentType(ContentType.IMAGE);
        contentBlock.setCaption("Test caption");

        // Act
        Set<ConstraintViolation<ContentModel>> violations = validator.validate(contentBlock);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Null collectionId should fail validation")
    void nullCollectionId_shouldFailValidation() {
        // Arrange
        contentBlock.setCollectionId(null); // Invalid
        contentBlock.setOrderIndex(0);
        contentBlock.setContentType(ContentType.IMAGE);

        // Act
        Set<ConstraintViolation<ContentModel>> violations = validator.validate(contentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ContentModel> violation = violations.iterator().next();
        assertEquals("collectionId", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("must not be null"));
    }

    @Test
    @DisplayName("Negative orderIndex should fail validation")
    void negativeOrderIndex_shouldFailValidation() {
        // Arrange
        contentBlock.setCollectionId(1L);
        contentBlock.setOrderIndex(-1); // Invalid
        contentBlock.setContentType(ContentType.IMAGE);

        // Act
        Set<ConstraintViolation<ContentModel>> violations = validator.validate(contentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ContentModel> violation = violations.iterator().next();
        assertEquals("orderIndex", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("must be greater than or equal to 0"));
    }

    @Test
    @DisplayName("Null blockType should fail validation")
    void nullBlockType_shouldFailValidation() {
        // Arrange
        contentBlock.setCollectionId(1L);
        contentBlock.setOrderIndex(0);
        contentBlock.setContentType(null); // Invalid

        // Act
        Set<ConstraintViolation<ContentModel>> violations = validator.validate(contentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ContentModel> violation = violations.iterator().next();
        assertEquals("blockType", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("must not be null"));
    }

    @Test
    @DisplayName("Caption over 500 characters should fail validation")
    void longCaption_shouldFailValidation() {
        // Arrange
        String longCaption = "A".repeat(501); // 501 characters
        contentBlock.setCollectionId(1L);
        contentBlock.setOrderIndex(0);
        contentBlock.setContentType(ContentType.IMAGE);
        contentBlock.setCaption(longCaption); // Invalid

        // Act
        Set<ConstraintViolation<ContentModel>> violations = validator.validate(contentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ContentModel> violation = violations.iterator().next();
        assertEquals("caption", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 500"));
    }

    @Test
    @DisplayName("Valid caption at max length should pass validation")
    void maxLengthCaption_shouldPassValidation() {
        // Arrange
        String maxCaption = "A".repeat(500); // Exactly 500 characters
        contentBlock.setCollectionId(1L);
        contentBlock.setOrderIndex(0);
        contentBlock.setContentType(ContentType.IMAGE);
        contentBlock.setCaption(maxCaption);

        // Act
        Set<ConstraintViolation<ContentModel>> violations = validator.validate(contentBlock);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Optional fields can be null")
    void optionalFields_canBeNull() {
        // Arrange
        contentBlock.setCollectionId(1L);
        contentBlock.setOrderIndex(0);
        contentBlock.setContentType(ContentType.IMAGE);
        // Leave id, caption, createdAt, updatedAt as null

        // Act
        Set<ConstraintViolation<ContentModel>> violations = validator.validate(contentBlock);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Lombok equality and hashCode work correctly")
    void lombokMethods_workCorrectly() {
        // Arrange
        ContentModel block1 = new ContentModel();
        block1.setId(1L);
        block1.setCollectionId(1L);
        block1.setOrderIndex(0);
        block1.setContentType(ContentType.IMAGE);

        ContentModel block2 = new ContentModel();
        block2.setId(1L);
        block2.setCollectionId(1L);
        block2.setOrderIndex(0);
        block2.setContentType(ContentType.IMAGE);

        // Act & Assert
        assertEquals(block1, block2);
        assertEquals(block1.hashCode(), block2.hashCode());
        assertTrue(block1.toString().contains("ContentBlockModel"));
    }

    @Test
    @DisplayName("Different orderIndex creates different objects")
    void differentOrderIndex_createsDifferentObjects() {
        // Arrange
        ContentModel block1 = new ContentModel();
        block1.setCollectionId(1L);
        block1.setOrderIndex(0);
        block1.setContentType(ContentType.IMAGE);

        ContentModel block2 = new ContentModel();
        block2.setCollectionId(1L);
        block2.setOrderIndex(1); // Different order
        block2.setContentType(ContentType.IMAGE);

        // Act & Assert
        assertNotEquals(block1, block2);
    }

    @Test
    @DisplayName("Multiple validation errors are captured")
    void multipleValidationErrors_areCaptured() {
        // Arrange
        String longCaption = "A".repeat(501);
        contentBlock.setCollectionId(null); // Error 1
        contentBlock.setOrderIndex(-1); // Error 2
        contentBlock.setContentType(null); // Error 3
        contentBlock.setCaption(longCaption); // Error 4

        // Act
        Set<ConstraintViolation<ContentModel>> violations = validator.validate(contentBlock);

        // Assert
        assertEquals(4, violations.size());
    }
}