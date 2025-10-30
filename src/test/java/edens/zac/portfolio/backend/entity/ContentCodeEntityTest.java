package edens.zac.portfolio.backend.entity;

import edens.zac.portfolio.backend.types.ContentType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ContentCodeEntityTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testValidCodeContentBlock() {
        // Create a valid code content block
        ContentCodeEntity codeBlock = ContentCodeEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .blockType(ContentType.CODE)
                .title("Hello World Example")
                .language("java")
                .code("public class HelloWorld {\n    public static void main(String[] args) {\n        System.out.println(\"Hello, World!\");\n    }\n}")
                .build();

        Set<ConstraintViolation<ContentCodeEntity>> violations = validator.validate(codeBlock);
        assertTrue(violations.isEmpty());
        assertEquals(ContentType.CODE, codeBlock.getContentType());
    }

    @Test
    void testInvalidCodeContentBlockMissingRequiredFields() {
        // Create an invalid code content block (missing required fields)
        ContentCodeEntity codeBlock = ContentCodeEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .blockType(ContentType.CODE)
                .title("Hello World Example")
                // language is missing
                // code is missing
                .build();

        Set<ConstraintViolation<ContentCodeEntity>> violations = validator.validate(codeBlock);
        assertFalse(violations.isEmpty());
        assertEquals(2, violations.size());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("language")));
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("code")));
    }

    @Test
    void testGetContentTypeReturnsCode() {
        ContentCodeEntity codeBlock = new ContentCodeEntity();
        assertEquals(ContentType.CODE, codeBlock.getContentType());
    }

    @Test
    void testBuilderWithAllFields() {
        // Test the builder pattern with all fields
        ContentCodeEntity codeBlock = ContentCodeEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .blockType(ContentType.CODE)
                .caption("A simple Java Hello World example")
                .title("Hello World Example")
                .language("java")
                .code("public class HelloWorld {\n    public static void main(String[] args) {\n        System.out.println(\"Hello, World!\");\n    }\n}")
                .build();

        // Verify all fields were set correctly
        assertEquals(1L, codeBlock.getCollectionId());
        assertEquals(0, codeBlock.getOrderIndex());
        assertEquals(ContentType.CODE, codeBlock.getContentType());
        assertEquals("A simple Java Hello World example", codeBlock.getCaption());
        assertEquals("Hello World Example", codeBlock.getTitle());
        assertEquals("java", codeBlock.getLanguage());
        assertEquals("public class HelloWorld {\n    public static void main(String[] args) {\n        System.out.println(\"Hello, World!\");\n    }\n}", codeBlock.getCode());
    }

    @Test
    void testDifferentProgrammingLanguages() {
        // Test with different programming languages
        ContentCodeEntity javaBlock = ContentCodeEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .blockType(ContentType.CODE)
                .title("Java Example")
                .language("java")
                .code("System.out.println(\"Hello\");")
                .build();
        
        ContentCodeEntity pythonBlock = ContentCodeEntity.builder()
                .collectionId(1L)
                .orderIndex(1)
                .blockType(ContentType.CODE)
                .title("Python Example")
                .language("python")
                .code("print('Hello')")
                .build();
        
        ContentCodeEntity javascriptBlock = ContentCodeEntity.builder()
                .collectionId(1L)
                .orderIndex(2)
                .blockType(ContentType.CODE)
                .title("JavaScript Example")
                .language("javascript")
                .code("console.log('Hello');")
                .build();

        assertEquals("java", javaBlock.getLanguage());
        assertEquals("python", pythonBlock.getLanguage());
        assertEquals("javascript", javascriptBlock.getLanguage());
    }

    @Test
    void testEqualsAndHashCode() {
        // Create two identical code blocks
        ContentCodeEntity codeBlock1 = ContentCodeEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .blockType(ContentType.CODE)
                .title("Example")
                .language("java")
                .code("System.out.println(\"Hello\");")
                .build();

        ContentCodeEntity codeBlock2 = ContentCodeEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .blockType(ContentType.CODE)
                .title("Example")
                .language("java")
                .code("System.out.println(\"Hello\");")
                .build();

        // Test equals and hashCode
        assertEquals(codeBlock1, codeBlock2);
        assertEquals(codeBlock1.hashCode(), codeBlock2.hashCode());

        // Modify one field and test again
        codeBlock2.setCode("System.out.println(\"Different\");");
        assertNotEquals(codeBlock1, codeBlock2);
    }
}