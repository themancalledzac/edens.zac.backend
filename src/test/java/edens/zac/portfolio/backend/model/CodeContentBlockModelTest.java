package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.types.ContentBlockType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CodeContentBlockModelTest {

    private Validator validator;
    private CodeContentBlockModel codeContentBlock;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        codeContentBlock = new CodeContentBlockModel();
    }

    @Test
    @DisplayName("Valid CodeContentBlockModel should pass validation")
    void validCodeContentBlockModel_shouldPassValidation() {
        // Arrange
        codeContentBlock.setCollectionId(1L);
        codeContentBlock.setOrderIndex(0);
        codeContentBlock.setBlockType(ContentBlockType.CODE);
        codeContentBlock.setCode("public class HelloWorld { public static void main(String[] args) { System.out.println(\"Hello World!\"); } }");
        codeContentBlock.setLanguage("java");
        codeContentBlock.setTitle("Hello World Example");
        codeContentBlock.setFileName("HelloWorld.java");
        codeContentBlock.setShowLineNumbers(true);
        codeContentBlock.setDescription("A simple Hello World example in Java");

        // Act
        Set<ConstraintViolation<CodeContentBlockModel>> violations = validator.validate(codeContentBlock);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Blank code should fail validation")
    void blankCode_shouldFailValidation() {
        // Arrange
        codeContentBlock.setCollectionId(1L);
        codeContentBlock.setOrderIndex(0);
        codeContentBlock.setBlockType(ContentBlockType.CODE);
        codeContentBlock.setCode(""); // Invalid - blank
        codeContentBlock.setLanguage("java");

        // Act
        Set<ConstraintViolation<CodeContentBlockModel>> violations = validator.validate(codeContentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<CodeContentBlockModel> violation = violations.iterator().next();
        assertEquals("code", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("must not be blank"));
    }

    @Test
    @DisplayName("Null code should fail validation")
    void nullCode_shouldFailValidation() {
        // Arrange
        codeContentBlock.setCollectionId(1L);
        codeContentBlock.setOrderIndex(0);
        codeContentBlock.setBlockType(ContentBlockType.CODE);
        codeContentBlock.setCode(null); // Invalid - null
        codeContentBlock.setLanguage("java");

        // Act
        Set<ConstraintViolation<CodeContentBlockModel>> violations = validator.validate(codeContentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<CodeContentBlockModel> violation = violations.iterator().next();
        assertEquals("code", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("must not be blank"));
    }

    @Test
    @DisplayName("Code over 50000 characters should fail validation")
    void longCode_shouldFailValidation() {
        // Arrange
        String longCode = "// This is a very long code file\n" + "A".repeat(50050); // Over 50000 chars
        codeContentBlock.setCollectionId(1L);
        codeContentBlock.setOrderIndex(0);
        codeContentBlock.setBlockType(ContentBlockType.CODE);
        codeContentBlock.setCode(longCode); // Invalid - too long
        codeContentBlock.setLanguage("java");

        // Act
        Set<ConstraintViolation<CodeContentBlockModel>> violations = validator.validate(codeContentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<CodeContentBlockModel> violation = violations.iterator().next();
        assertEquals("code", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 50000"));
    }

    @Test
    @DisplayName("Code at max length should pass validation")
    void maxLengthCode_shouldPassValidation() {
        // Arrange
        String maxCode = "A".repeat(50000); // Exactly 50000 characters
        codeContentBlock.setCollectionId(1L);
        codeContentBlock.setOrderIndex(0);
        codeContentBlock.setBlockType(ContentBlockType.CODE);
        codeContentBlock.setCode(maxCode);
        codeContentBlock.setLanguage("java");

        // Act
        Set<ConstraintViolation<CodeContentBlockModel>> violations = validator.validate(codeContentBlock);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Null language should fail validation")
    void nullLanguage_shouldFailValidation() {
        // Arrange
        codeContentBlock.setCollectionId(1L);
        codeContentBlock.setOrderIndex(0);
        codeContentBlock.setBlockType(ContentBlockType.CODE);
        codeContentBlock.setCode("console.log('Hello World');");
        codeContentBlock.setLanguage(null); // Invalid - null

        // Act
        Set<ConstraintViolation<CodeContentBlockModel>> violations = validator.validate(codeContentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<CodeContentBlockModel> violation = violations.iterator().next();
        assertEquals("language", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("must not be null"));
    }

    @Test
    @DisplayName("Language over 50 characters should fail validation")
    void longLanguage_shouldFailValidation() {
        // Arrange
        String longLanguage = "A".repeat(51); // 51 characters
        codeContentBlock.setCollectionId(1L);
        codeContentBlock.setOrderIndex(0);
        codeContentBlock.setBlockType(ContentBlockType.CODE);
        codeContentBlock.setCode("print('Hello World')");
        codeContentBlock.setLanguage(longLanguage); // Invalid - too long

        // Act
        Set<ConstraintViolation<CodeContentBlockModel>> violations = validator.validate(codeContentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<CodeContentBlockModel> violation = violations.iterator().next();
        assertEquals("language", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 50"));
    }

    @Test
    @DisplayName("Language at max length should pass validation")
    void maxLengthLanguage_shouldPassValidation() {
        // Arrange
        String maxLanguage = "A".repeat(50); // Exactly 50 characters
        codeContentBlock.setCollectionId(1L);
        codeContentBlock.setOrderIndex(0);
        codeContentBlock.setBlockType(ContentBlockType.CODE);
        codeContentBlock.setCode("print('Hello World')");
        codeContentBlock.setLanguage(maxLanguage);

        // Act
        Set<ConstraintViolation<CodeContentBlockModel>> violations = validator.validate(codeContentBlock);

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
            codeContentBlock.setCollectionId(1L);
            codeContentBlock.setOrderIndex(0);
            codeContentBlock.setBlockType(ContentBlockType.CODE);
            codeContentBlock.setCode("// Sample code for " + language);
            codeContentBlock.setLanguage(language);

            // Act
            Set<ConstraintViolation<CodeContentBlockModel>> violations = validator.validate(codeContentBlock);

            // Assert
            assertTrue(violations.isEmpty(), "Language '" + language + "' should be valid");
        }
    }

    @Test
    @DisplayName("Title over 255 characters should fail validation")
    void longTitle_shouldFailValidation() {
        // Arrange
        String longTitle = "A".repeat(256); // 256 characters
        codeContentBlock.setCollectionId(1L);
        codeContentBlock.setOrderIndex(0);
        codeContentBlock.setBlockType(ContentBlockType.CODE);
        codeContentBlock.setCode("print('Hello')");
        codeContentBlock.setLanguage("python");
        codeContentBlock.setTitle(longTitle); // Invalid - too long

        // Act
        Set<ConstraintViolation<CodeContentBlockModel>> violations = validator.validate(codeContentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<CodeContentBlockModel> violation = violations.iterator().next();
        assertEquals("title", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 255"));
    }

    @Test
    @DisplayName("Title at max length should pass validation")
    void maxLengthTitle_shouldPassValidation() {
        // Arrange
        String maxTitle = "A".repeat(255); // Exactly 255 characters
        codeContentBlock.setCollectionId(1L);
        codeContentBlock.setOrderIndex(0);
        codeContentBlock.setBlockType(ContentBlockType.CODE);
        codeContentBlock.setCode("print('Hello')");
        codeContentBlock.setLanguage("python");
        codeContentBlock.setTitle(maxTitle);

        // Act
        Set<ConstraintViolation<CodeContentBlockModel>> violations = validator.validate(codeContentBlock);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("FileName over 255 characters should fail validation")
    void longFileName_shouldFailValidation() {
        // Arrange
        String longFileName = "A".repeat(255) + ".java"; // Over 255 characters
        codeContentBlock.setCollectionId(1L);
        codeContentBlock.setOrderIndex(0);
        codeContentBlock.setBlockType(ContentBlockType.CODE);
        codeContentBlock.setCode("public class Test {}");
        codeContentBlock.setLanguage("java");
        codeContentBlock.setFileName(longFileName); // Invalid - too long

        // Act
        Set<ConstraintViolation<CodeContentBlockModel>> violations = validator.validate(codeContentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<CodeContentBlockModel> violation = violations.iterator().next();
        assertEquals("fileName", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 255"));
    }

    @Test
    @DisplayName("FileName at max length should pass validation")
    void maxLengthFileName_shouldPassValidation() {
        // Arrange
        String maxFileName = "A".repeat(255); // Exactly 255 characters
        codeContentBlock.setCollectionId(1L);
        codeContentBlock.setOrderIndex(0);
        codeContentBlock.setBlockType(ContentBlockType.CODE);
        codeContentBlock.setCode("public class Test {}");
        codeContentBlock.setLanguage("java");
        codeContentBlock.setFileName(maxFileName);

        // Act
        Set<ConstraintViolation<CodeContentBlockModel>> violations = validator.validate(codeContentBlock);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Description over 1000 characters should fail validation")
    void longDescription_shouldFailValidation() {
        // Arrange
        String longDescription = "A".repeat(1001); // 1001 characters
        codeContentBlock.setCollectionId(1L);
        codeContentBlock.setOrderIndex(0);
        codeContentBlock.setBlockType(ContentBlockType.CODE);
        codeContentBlock.setCode("console.log('test');");
        codeContentBlock.setLanguage("javascript");
        codeContentBlock.setDescription(longDescription); // Invalid - too long

        // Act
        Set<ConstraintViolation<CodeContentBlockModel>> violations = validator.validate(codeContentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<CodeContentBlockModel> violation = violations.iterator().next();
        assertEquals("description", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("size must be between 0 and 1000"));
    }

    @Test
    @DisplayName("Description at max length should pass validation")
    void maxLengthDescription_shouldPassValidation() {
        // Arrange
        String maxDescription = "A".repeat(1000); // Exactly 1000 characters
        codeContentBlock.setCollectionId(1L);
        codeContentBlock.setOrderIndex(0);
        codeContentBlock.setBlockType(ContentBlockType.CODE);
        codeContentBlock.setCode("console.log('test');");
        codeContentBlock.setLanguage("javascript");
        codeContentBlock.setDescription(maxDescription);

        // Act
        Set<ConstraintViolation<CodeContentBlockModel>> violations = validator.validate(codeContentBlock);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Optional fields can be null")
    void optionalFields_canBeNull() {
        // Arrange
        codeContentBlock.setCollectionId(1L);
        codeContentBlock.setOrderIndex(0);
        codeContentBlock.setBlockType(ContentBlockType.CODE);
        codeContentBlock.setCode("print('Hello World')");
        codeContentBlock.setLanguage("python");
        // Leave title, fileName, showLineNumbers, description as null

        // Act
        Set<ConstraintViolation<CodeContentBlockModel>> violations = validator.validate(codeContentBlock);

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
            codeContentBlock.setCollectionId(1L);
            codeContentBlock.setOrderIndex(0);
            codeContentBlock.setBlockType(ContentBlockType.CODE);
            codeContentBlock.setCode("print('test')");
            codeContentBlock.setLanguage("python");
            codeContentBlock.setShowLineNumbers(value);

            // Act
            Set<ConstraintViolation<CodeContentBlockModel>> violations = validator.validate(codeContentBlock);

            // Assert
            assertTrue(violations.isEmpty(), "ShowLineNumbers value '" + value + "' should be valid");
        }
    }

    @Test
    @DisplayName("Lombok inheritance works correctly")
    void lombokInheritance_worksCorrectly() {
        // Arrange
        CodeContentBlockModel block1 = new CodeContentBlockModel();
        block1.setId(1L);
        block1.setCollectionId(1L);
        block1.setOrderIndex(0);
        block1.setBlockType(ContentBlockType.CODE);
        block1.setCode("print('Hello')");
        block1.setLanguage("python");
        block1.setTitle("Test Code");

        CodeContentBlockModel block2 = new CodeContentBlockModel();
        block2.setId(1L);
        block2.setCollectionId(1L);
        block2.setOrderIndex(0);
        block2.setBlockType(ContentBlockType.CODE);
        block2.setCode("print('Hello')");
        block2.setLanguage("python");
        block2.setTitle("Test Code");

        // Act & Assert
        assertEquals(block1, block2);
        assertEquals(block1.hashCode(), block2.hashCode());
        assertTrue(block1.toString().contains("CodeContentBlockModel"));
    }

    @Test
    @DisplayName("Different code creates different objects")
    void differentCode_createsDifferentObjects() {
        // Arrange
        CodeContentBlockModel block1 = new CodeContentBlockModel();
        block1.setCollectionId(1L);
        block1.setOrderIndex(0);
        block1.setBlockType(ContentBlockType.CODE);
        block1.setCode("print('Hello')");
        block1.setLanguage("python");

        CodeContentBlockModel block2 = new CodeContentBlockModel();
        block2.setCollectionId(1L);
        block2.setOrderIndex(0);
        block2.setBlockType(ContentBlockType.CODE);
        block2.setCode("print('Goodbye')"); // Different code
        block2.setLanguage("python");

        // Act & Assert
        assertNotEquals(block1, block2);
    }

    @Test
    @DisplayName("Different language creates different objects")
    void differentLanguage_createsDifferentObjects() {
        // Arrange
        CodeContentBlockModel block1 = new CodeContentBlockModel();
        block1.setCollectionId(1L);
        block1.setOrderIndex(0);
        block1.setBlockType(ContentBlockType.CODE);
        block1.setCode("print('Hello')");
        block1.setLanguage("python");

        CodeContentBlockModel block2 = new CodeContentBlockModel();
        block2.setCollectionId(1L);
        block2.setOrderIndex(0);
        block2.setBlockType(ContentBlockType.CODE);
        block2.setCode("print('Hello')");
        block2.setLanguage("javascript"); // Different language

        // Act & Assert
        assertNotEquals(block1, block2);
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
        
        codeContentBlock.setCollectionId(null); // Error 1 - inherited
        codeContentBlock.setOrderIndex(-1); // Error 2 - inherited
        codeContentBlock.setBlockType(null); // Error 3 - inherited
        codeContentBlock.setCode(""); // Error 4 - blank code
        codeContentBlock.setLanguage(longLanguage); // Error 5 - long language
        codeContentBlock.setTitle(longTitle); // Error 6 - long title
        codeContentBlock.setFileName(longFileName); // Error 7 - long filename
        codeContentBlock.setDescription(longDescription); // Error 8 - long description

        // Act
        Set<ConstraintViolation<CodeContentBlockModel>> violations = validator.validate(codeContentBlock);

        // Assert
        assertEquals(8, violations.size());
    }

    @Test
    @DisplayName("Inherits validation from ContentBlockModel")
    void inheritsValidation_fromContentBlockModel() {
        // Arrange - Test that inherited validation still works
        codeContentBlock.setCollectionId(null); // Invalid from parent
        codeContentBlock.setOrderIndex(0);
        codeContentBlock.setBlockType(ContentBlockType.CODE);
        codeContentBlock.setCode("print('Hello')");
        codeContentBlock.setLanguage("python");

        // Act
        Set<ConstraintViolation<CodeContentBlockModel>> violations = validator.validate(codeContentBlock);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<CodeContentBlockModel> violation = violations.iterator().next();
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
            codeContentBlock.setCollectionId(1L);
            codeContentBlock.setOrderIndex(0);
            codeContentBlock.setBlockType(ContentBlockType.CODE);
            codeContentBlock.setLanguage(example[0]);
            codeContentBlock.setCode(example[1]);
            codeContentBlock.setFileName(example[2]);
            codeContentBlock.setShowLineNumbers(true);
            codeContentBlock.setDescription("Example " + example[0] + " code snippet");

            // Act
            Set<ConstraintViolation<CodeContentBlockModel>> violations = validator.validate(codeContentBlock);

            // Assert
            assertTrue(violations.isEmpty(), "Code example for '" + example[0] + "' should be valid");
        }
    }
}