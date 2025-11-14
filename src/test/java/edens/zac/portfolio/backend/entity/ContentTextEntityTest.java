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

class ContentTextEntityTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testValidTextContent() {
        // Create a valid text content
        ContentTextEntity textBlock = ContentTextEntity.builder()
                .contentType(ContentType.TEXT)
                .textContent("This is a sample text content with enough characters to be meaningful.")
                .formatType("markdown")
                .build();

        Set<ConstraintViolation<ContentTextEntity>> violations = validator.validate(textBlock);
        assertTrue(violations.isEmpty());
        assertEquals(ContentType.TEXT, textBlock.getContentType());
    }

    @Test
    void testInvalidTextContentMissingRequiredField() {
        // Create an invalid text content (missing required content)
        ContentTextEntity textBlock = ContentTextEntity.builder()
                .contentType(ContentType.TEXT)
                // content is missing
                .formatType("markdown")
                .build();

        Set<ConstraintViolation<ContentTextEntity>> violations = validator.validate(textBlock);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("textContent")));
    }

    @Test
    void testGetContentTypeReturnsText() {
        ContentTextEntity textBlock = new ContentTextEntity();
        assertEquals(ContentType.TEXT, textBlock.getContentType());
    }

    @Test
    void testBuilderWithAllFields() {
        // Test the builder pattern with all fields
        ContentTextEntity textBlock = ContentTextEntity.builder()
                .contentType(ContentType.TEXT)
                .textContent("This is a sample text content with enough characters to be meaningful.")
                .formatType("markdown")
                .build();

        // Verify all fields were set correctly
        assertEquals(ContentType.TEXT, textBlock.getContentType());
        assertEquals("This is a sample text content with enough characters to be meaningful.", textBlock.getTextContent());
        assertEquals("markdown", textBlock.getFormatType());
    }

    @Test
    void testFormatTypeValues() {
        // Test with different valid format types
        ContentTextEntity markdownBlock = ContentTextEntity.builder()
                .contentType(ContentType.TEXT)
                .textContent("Markdown content")
                .formatType("markdown")
                .build();
        
        ContentTextEntity htmlBlock = ContentTextEntity.builder()
                .contentType(ContentType.TEXT)
                .textContent("<p>HTML content</p>")
                .formatType("html")
                .build();
        
        ContentTextEntity plainBlock = ContentTextEntity.builder()
                .contentType(ContentType.TEXT)
                .textContent("Plain text content")
                .formatType("plain")
                .build();

        assertEquals("markdown", markdownBlock.getFormatType());
        assertEquals("html", htmlBlock.getFormatType());
        assertEquals("plain", plainBlock.getFormatType());
    }

    @Test
    void testEqualsAndHashCode() {
        // Create two identical text blocks
        ContentTextEntity textBlock1 = ContentTextEntity.builder()
                .contentType(ContentType.TEXT)
                .textContent("This is a sample text content.")
                .formatType("markdown")
                .build();

        ContentTextEntity textBlock2 = ContentTextEntity.builder()
                .contentType(ContentType.TEXT)
                .textContent("This is a sample text content.")
                .formatType("markdown")
                .build();

        // Test equals and hashCode
        assertEquals(textBlock1, textBlock2);
        assertEquals(textBlock1.hashCode(), textBlock2.hashCode());

        // Modify one field and test again
        textBlock2.setTextContent("Different content");
        assertNotEquals(textBlock1, textBlock2);
    }
}