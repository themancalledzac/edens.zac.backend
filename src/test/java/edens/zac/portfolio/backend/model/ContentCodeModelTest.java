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

class ContentCodeModelTest {

    private Validator validator;
    private ContentCodeModel contentCode;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        contentCode = new ContentCodeModel();
    }

    @Test
    @DisplayName("Valid ContentCodeModel should pass validation")
    void validContentCodeaModel_shouldPassValidation() {
        // Arrange
        contentCode.setCollectionId(1L);
        contentCode.setOrderIndex(0);
        contentCode.setContentType(ContentType.CODE);
        contentCode.setCode("public class HelloWorld { public static void main(String[] args) { System.out.println(\"Hello World!\"); } }");
        contentCode.setLanguage("java");
        contentCode.setTitle("Hello World Example");
        contentCode.setFileName("HelloWorld.java");
        contentCode.setShowLineNumbers(true);
        contentCode.setDescription("A simple Hello World example in Java");

        // Act
        Set<ConstraintViolation<ContentCodeModel>> violations = validator.validate(contentCode);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Blank code should fail validation")
    void blankCode_shouldFailValidation() {
        // Arrange
        contentCode.setCollectionId(1L);
        contentCode.setOrderIndex(0);
        contentCode.setContentType(ContentType.CODE);
        contentCode.setCode(""); // Invalid - blank
        contentCode.setLanguage("java");

        // Act
        Set<ConstraintViolation<ContentCodeModel>> violations = validator.validate(contentCode);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ContentCodeModel> violation = violations.iterator().next();
        assertEquals("code", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("must not be blank"));
    }

    @Test
    @DisplayName("Null code should fail validation")
    void nullCode_shouldFailValidation() {
        // Arrange
        contentCode.setCollectionId(1L);
        contentCode.setOrderIndex(0);
        contentCode.setContentType(ContentType.CODE);
        contentCode.setCode(null); // Invalid - null
        contentCode.setLanguage("java");

        // Act
        Set<ConstraintViolation<ContentCodeModel>> violations = validator.validate(contentCode);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ContentCodeModel> violation = violations.iterator().next();
        assertEquals("code", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("must not be blank"));
    }

    @Test
    @DisplayName("Code over 50000 characters should fail validation")
    void longCode_shouldFailValidation() {
        // Arrange
        String longCode = "// This is a very long code file\n" + "A".repeat(50050); // Over 50000 chars
        contentCode.setCollectionId(1L);
        contentCode.setOrderIndex(0);
        contentCode.setContentType(ContentType.CODE);
        contentCode.setCode(longCode); // Invalid - too long
        contentCode.setLanguage("java");

        // Act
        Set<ConstraintViolation<ContentCodeModel>> violations = validator.validate(contentCode);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ContentCodeModel> violation = violations.iterator().next();
        assertEquals("code", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 50000"));
    }

    @Test
    @DisplayName("Code at max length should pass validation")
    void maxLengthCode_shouldPassValidation() {
        // Arrange
        String maxCode = "A".repeat(50000); // Exactly 50000 characters
        contentCode.setCollectionId(1L);
        contentCode.setOrderIndex(0);
        contentCode.setContentType(ContentType.CODE);
        contentCode.setCode(maxCode);
        contentCode.setLanguage("java");

        // Act
        Set<ConstraintViolation<ContentCodeModel>> violations = validator.validate(contentCode);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Null language should fail validation")
    void nullLanguage_shouldFailValidation() {
        // Arrange
        contentCode.setCollectionId(1L);
        contentCode.setOrderIndex(0);
        contentCode.setContentType(ContentType.CODE);
        contentCode.setCode("console.log('Hello World');");
        contentCode.setLanguage(null); // Invalid - null

        // Act
        Set<ConstraintViolation<ContentCodeModel>> violations = validator.validate(contentCode);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ContentCodeModel> violation = violations.iterator().next();
        assertEquals("language", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("must not be null"));
    }

    @Test
    @DisplayName("Language over 50 characters should fail validation")
    void longLanguage_shouldFailValidation() {
        // Arrange
        String longLanguage = "A".repeat(51); // 51 characters
        contentCode.setCollectionId(1L);
        contentCode.setOrderIndex(0);
        contentCode.setContentType(ContentType.CODE);
        contentCode.setCode("print('Hello World')");
        contentCode.setLanguage(longLanguage); // Invalid - too long

        // Act
        Set<ConstraintViolation<ContentCodeModel>> violations = validator.validate(contentCode);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ContentCodeModel> violation = violations.iterator().next();
        assertEquals("language", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 50"));
    }

    @Test
    @DisplayName("Language at max length should pass validation")
    void maxLengthLanguage_shouldPassValidation() {
        // Arrange
        String maxLanguage = "A".repeat(50); // Exactly 50 characters
        contentCode.setCollectionId(1L);
        contentCode.setOrderIndex(0);
        contentCode.setContentType(ContentType.CODE);
        contentCode.setCode("print('Hello World')");
        contentCode.setLanguage(maxLanguage);

        // Act
        Set<ConstraintViolation<ContentCodeModel>> violations = validator.validate(contentCode);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Valid programming languages should pass validation")
    void validProgrammingLanguages_shouldPassValidation() {
        // Test common programming languages
        String[] validLanguages = {"java", "javascript", "python", "sql", "typescript", "html", "css", "bash", "xml", "json"};
        
        for (String language : validLanguages) {
            // Arrange
            contentCode.setCollectionId(1L);
            contentCode.setOrderIndex(0);
            contentCode.setContentType(ContentType.CODE);
            contentCode.setCode("// Sample code for " + language);
            contentCode.setLanguage(language);

            // Act
            Set<ConstraintViolation<ContentCodeModel>> violations = validator.validate(contentCode);

            // Assert
            assertTrue(violations.isEmpty(), "Language '" + language + "' should be valid");
        }
    }

    @Test
    @DisplayName("Title over 255 characters should fail validation")
    void longTitle_shouldFailValidation() {
        // Arrange
        String longTitle = "A".repeat(256); // 256 characters
        contentCode.setCollectionId(1L);
        contentCode.setOrderIndex(0);
        contentCode.setContentType(ContentType.CODE);
        contentCode.setCode("print('Hello')");
        contentCode.setLanguage("python");
        contentCode.setTitle(longTitle); // Invalid - too long

        // Act
        Set<ConstraintViolation<ContentCodeModel>> violations = validator.validate(contentCode);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ContentCodeModel> violation = violations.iterator().next();
        assertEquals("title", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 255"));
    }

    @Test
    @DisplayName("Title at max length should pass validation")
    void maxLengthTitle_shouldPassValidation() {
        // Arrange
        String maxTitle = "A".repeat(255); // Exactly 255 characters
        contentCode.setCollectionId(1L);
        contentCode.setOrderIndex(0);
        contentCode.setContentType(ContentType.CODE);
        contentCode.setCode("print('Hello')");
        contentCode.setLanguage("python");
        contentCode.setTitle(maxTitle);

        // Act
        Set<ConstraintViolation<ContentCodeModel>> violations = validator.validate(contentCode);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("FileName over 255 characters should fail validation")
    void longFileName_shouldFailValidation() {
        // Arrange
        String longFileName = "A".repeat(255) + ".java"; // Over 255 characters
        contentCode.setCollectionId(1L);
        contentCode.setOrderIndex(0);
        contentCode.setContentType(ContentType.CODE);
        contentCode.setCode("public class Test {}");
        contentCode.setLanguage("java");
        contentCode.setFileName(longFileName); // Invalid - too long

        // Act
        Set<ConstraintViolation<ContentCodeModel>> violations = validator.validate(contentCode);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ContentCodeModel> violation = violations.iterator().next();
        assertEquals("fileName", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 255"));
    }

    @Test
    @DisplayName("FileName at max length should pass validation")
    void maxLengthFileName_shouldPassValidation() {
        // Arrange
        String maxFileName = "A".repeat(255); // Exactly 255 characters
        contentCode.setCollectionId(1L);
        contentCode.setOrderIndex(0);
        contentCode.setContentType(ContentType.CODE);
        contentCode.setCode("public class Test {}");
        contentCode.setLanguage("java");
        contentCode.setFileName(maxFileName);

        // Act
        Set<ConstraintViolation<ContentCodeModel>> violations = validator.validate(contentCode);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Description over 1000 characters should fail validation")
    void longDescription_shouldFailValidation() {
        // Arrange
        String longDescription = "A".repeat(1001); // 1001 characters
        contentCode.setCollectionId(1L);
        contentCode.setOrderIndex(0);
        contentCode.setContentType(ContentType.CODE);
        contentCode.setCode("console.log('test');");
        contentCode.setLanguage("javascript");
        contentCode.setDescription(longDescription); // Invalid - too long

        // Act
        Set<ConstraintViolation<ContentCodeModel>> violations = validator.validate(contentCode);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ContentCodeModel> violation = violations.iterator().next();
        assertEquals("description", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 1000"));
    }

    @Test
    @DisplayName("Description at max length should pass validation")
    void maxLengthDescription_shouldPassValidation() {
        // Arrange
        String maxDescription = "A".repeat(1000); // Exactly 1000 characters
        contentCode.setCollectionId(1L);
        contentCode.setOrderIndex(0);
        contentCode.setContentType(ContentType.CODE);
        contentCode.setCode("console.log('test');");
        contentCode.setLanguage("javascript");
        contentCode.setDescription(maxDescription);

        // Act
        Set<ConstraintViolation<ContentCodeModel>> violations = validator.validate(contentCode);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Optional fields can be null")
    void optionalFields_canBeNull() {
        // Arrange
        contentCode.setCollectionId(1L);
        contentCode.setOrderIndex(0);
        contentCode.setContentType(ContentType.CODE);
        contentCode.setCode("print('Hello World')");
        contentCode.setLanguage("python");
        // Leave title, fileName, showLineNumbers, description as null

        // Act
        Set<ConstraintViolation<ContentCodeModel>> violations = validator.validate(contentCode);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("ShowLineNumbers boolean values should pass validation")
    void showLineNumbers_booleanValues_shouldPassValidation() {
        // Test both true and false values
        Boolean[] values = {true, false, null};
        
        for (Boolean value : values) {
            // Arrange
            contentCode.setCollectionId(1L);
            contentCode.setOrderIndex(0);
            contentCode.setContentType(ContentType.CODE);
            contentCode.setCode("print('test')");
            contentCode.setLanguage("python");
            contentCode.setShowLineNumbers(value);

            // Act
            Set<ConstraintViolation<ContentCodeModel>> violations = validator.validate(contentCode);

            // Assert
            assertTrue(violations.isEmpty(), "ShowLineNumbers value '" + value + "' should be valid");
        }
    }

    @Test
    @DisplayName("Lombok inheritance works correctly")
    void lombokInheritance_worksCorrectly() {
        // Arrange
        ContentCodeModel content1 = new ContentCodeModel();
        content1.setId(1L);
        content1.setCollectionId(1L);
        content1.setOrderIndex(0);
        content1.setContentType(ContentType.CODE);
        content1.setCode("print('Hello')");
        content1.setLanguage("python");
        content1.setTitle("Test Code");

        ContentCodeModel content2 = new ContentCodeModel();
        content2.setId(1L);
        content2.setCollectionId(1L);
        content2.setOrderIndex(0);
        content2.setContentType(ContentType.CODE);
        content2.setCode("print('Hello')");
        content2.setLanguage("python");
        content2.setTitle("Test Code");

        // Act & Assert
        assertEquals(content1, content2);
        assertEquals(content1.hashCode(), content2.hashCode());
        assertTrue(content1.toString().contains("ContentCodeModel"));
    }

    @Test
    @DisplayName("Different code creates different objects")
    void differentCode_createsDifferentObjects() {
        // Arrange
        ContentCodeModel content1 = new ContentCodeModel();
        content1.setCollectionId(1L);
        content1.setOrderIndex(0);
        content1.setContentType(ContentType.CODE);
        content1.setCode("print('Hello')");
        content1.setLanguage("python");

        ContentCodeModel content2 = new ContentCodeModel();
        content2.setCollectionId(1L);
        content2.setOrderIndex(0);
        content2.setContentType(ContentType.CODE);
        content2.setCode("print('Goodbye')"); // Different code
        content2.setLanguage("python");

        // Act & Assert
        assertNotEquals(content1, content2);
    }

    @Test
    @DisplayName("Different language creates different objects")
    void differentLanguage_createsDifferentObjects() {
        // Arrange
        ContentCodeModel content1 = new ContentCodeModel();
        content1.setCollectionId(1L);
        content1.setOrderIndex(0);
        content1.setContentType(ContentType.CODE);
        content1.setCode("print('Hello')");
        content1.setLanguage("python");

        ContentCodeModel content2 = new ContentCodeModel();
        content2.setCollectionId(1L);
        content2.setOrderIndex(0);
        content2.setContentType(ContentType.CODE);
        content2.setCode("print('Hello')");
        content2.setLanguage("javascript"); // Different language

        // Act & Assert
        assertNotEquals(content1, content2);
    }

    @Test
    @DisplayName("Multiple validation errors are captured")
    void multipleValidationErrors_areCaptured() {
        // Arrange
        String longCode = "A".repeat(50001);
        String longLanguage = "A".repeat(51);
        String longTitle = "A".repeat(256);
        String longFileName = "A".repeat(256);
        String longDescription = "A".repeat(1001);
        
        contentCode.setCollectionId(null); // Error 1 - inherited
        contentCode.setOrderIndex(-1); // Error 2 - inherited
        contentCode.setContentType(null); // Error 3 - inherited
        contentCode.setCode(""); // Error 4 - blank code
        contentCode.setLanguage(longLanguage); // Error 5 - long language
        contentCode.setTitle(longTitle); // Error 6 - long title
        contentCode.setFileName(longFileName); // Error 7 - long filename
        contentCode.setDescription(longDescription); // Error 8 - long description

        // Act
        Set<ConstraintViolation<ContentCodeModel>> violations = validator.validate(contentCode);

        // Assert
        assertEquals(8, violations.size());
    }

    @Test
    @DisplayName("Inherits validation from ContentModel")
    void inheritsValidation_fromContentModel() {
        // Arrange - Test that inherited validation still works
        contentCode.setCollectionId(null); // Invalid from parent
        contentCode.setOrderIndex(0);
        contentCode.setContentType(ContentType.CODE);
        contentCode.setCode("print('Hello')");
        contentCode.setLanguage("python");

        // Act
        Set<ConstraintViolation<ContentCodeModel>> violations = validator.validate(contentCode);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<ContentCodeModel> violation = violations.iterator().next();
        assertEquals("collectionId", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("must not be null"));
    }

    @Test
    @DisplayName("Realistic code examples should pass validation")
    void realisticCodeExamples_shouldPassValidation() {
        // Test with realistic code snippets
        String[][] codeExamples = {
            {"java", "public class HelloWorld {\n    public static void main(String[] args) {\n        System.out.println(\"Hello, World!\");\n    }\n}", "HelloWorld.java"},
            {"javascript", "function greet(name) {\n    return `Hello, ${name}!`;\n}\n\nconsole.log(greet('World'));", "greet.js"},
            {"python", "def greet(name):\n    return f\"Hello, {name}!\"\n\nprint(greet('World'))", "greet.py"},
            {"sql", "SELECT users.name, COUNT(orders.id) as order_count\nFROM users\nLEFT JOIN orders ON users.id = orders.user_id\nGROUP BY users.id;", "user_orders.sql"},
            {"html", "<!DOCTYPE html>\n<html>\n<head>\n    <title>Hello World</title>\n</head>\n<body>\n    <h1>Hello, World!</h1>\n</body>\n</html>", "index.html"}
        };
        
        for (String[] example : codeExamples) {
            // Arrange
            contentCode.setCollectionId(1L);
            contentCode.setOrderIndex(0);
            contentCode.setContentType(ContentType.CODE);
            contentCode.setLanguage(example[0]);
            contentCode.setCode(example[1]);
            contentCode.setFileName(example[2]);
            contentCode.setShowLineNumbers(true);
            contentCode.setDescription("Example " + example[0] + " code snippet");

            // Act
            Set<ConstraintViolation<ContentCodeModel>> violations = validator.validate(contentCode);

            // Assert
            assertTrue(violations.isEmpty(), "Code example for '" + example[0] + "' should be valid");
        }
    }
}