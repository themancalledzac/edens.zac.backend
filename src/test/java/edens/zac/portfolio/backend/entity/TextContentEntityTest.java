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

class TextContentEntityTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testValidTextContentBlock() {
        // Create a valid text content block
        TextContentEntity textBlock = TextContentEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .contentType(ContentType.TEXT)
                .content("This is a sample text content with enough characters to be meaningful.")
                .formatType("markdown")
                .build();

        Set<ConstraintViolation<TextContentEntity>> violations = validator.validate(textBlock);
        assertTrue(violations.isEmpty());
        assertEquals(ContentType.TEXT, textBlock.getContentType());
    }

    @Test
    void testInvalidTextContentBlockMissingRequiredField() {
        // Create an invalid text content block (missing required content)
        TextContentEntity textBlock = TextContentEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .contentType(ContentType.TEXT)
                // content is missing
                .formatType("markdown")
                .build();

        Set<ConstraintViolation<TextContentEntity>> violations = validator.validate(textBlock);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("content")));
    }

    @Test
    void testGetContentTypeReturnsText() {
        TextContentEntity textBlock = new TextContentEntity();
        assertEquals(ContentType.TEXT, textBlock.getContentType());
    }

    @Test
    void testBuilderWithAllFields() {
        // Test the builder pattern with all fields
        TextContentEntity textBlock = TextContentEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .contentType(ContentType.TEXT)
                .caption("Text content caption")
                .content("This is a sample text content with enough characters to be meaningful.")
                .formatType("markdown")
                .build();

        // Verify all fields were set correctly
        assertEquals(1L, textBlock.getCollectionId());
        assertEquals(0, textBlock.getOrderIndex());
        assertEquals(ContentType.TEXT, textBlock.getContentType());
        assertEquals("Text content caption", textBlock.getCaption());
        assertEquals("This is a sample text content with enough characters to be meaningful.", textBlock.getContent());
        assertEquals("markdown", textBlock.getFormatType());
    }

    @Test
    void testFormatTypeValues() {
        // Test with different valid format types
        TextContentEntity markdownBlock = TextContentEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .contentType(ContentType.TEXT)
                .content("Markdown content")
                .formatType("markdown")
                .build();
        
        TextContentEntity htmlBlock = TextContentEntity.builder()
                .collectionId(1L)
                .orderIndex(1)
                .contentType(ContentType.TEXT)
                .content("<p>HTML content</p>")
                .formatType("html")
                .build();
        
        TextContentEntity plainBlock = TextContentEntity.builder()
                .collectionId(1L)
                .orderIndex(2)
                .contentType(ContentType.TEXT)
                .content("Plain text content")
                .formatType("plain")
                .build();

        assertEquals("markdown", markdownBlock.getFormatType());
        assertEquals("html", htmlBlock.getFormatType());
        assertEquals("plain", plainBlock.getFormatType());
    }

    @Test
    void testEqualsAndHashCode() {
        // Create two identical text blocks
        TextContentEntity textBlock1 = TextContentEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .contentType(ContentType.TEXT)
                .content("This is a sample text content.")
                .formatType("markdown")
                .build();

        TextContentEntity textBlock2 = TextContentEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .contentType(ContentType.TEXT)
                .content("This is a sample text content.")
                .formatType("markdown")
                .build();

        // Test equals and hashCode
        assertEquals(textBlock1, textBlock2);
        assertEquals(textBlock1.hashCode(), textBlock2.hashCode());

        // Modify one field and test again
        textBlock2.setContent("Different content");
        assertNotEquals(textBlock1, textBlock2);
    }
}