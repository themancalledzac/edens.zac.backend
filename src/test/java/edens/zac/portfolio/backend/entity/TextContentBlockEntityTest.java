package edens.zac.portfolio.backend.entity;

import edens.zac.portfolio.backend.types.ContentBlockType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TextContentBlockEntityTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testValidTextContentBlock() {
        // Create a valid text content block
        TextContentBlockEntity textBlock = TextContentBlockEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .blockType(ContentBlockType.TEXT)
                .content("This is a sample text content with enough characters to be meaningful.")
                .formatType("markdown")
                .build();

        Set<ConstraintViolation<TextContentBlockEntity>> violations = validator.validate(textBlock);
        assertTrue(violations.isEmpty());
        assertEquals(ContentBlockType.TEXT, textBlock.getBlockType());
    }

    @Test
    void testInvalidTextContentBlockMissingRequiredField() {
        // Create an invalid text content block (missing required content)
        TextContentBlockEntity textBlock = TextContentBlockEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .blockType(ContentBlockType.TEXT)
                // content is missing
                .formatType("markdown")
                .build();

        Set<ConstraintViolation<TextContentBlockEntity>> violations = validator.validate(textBlock);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("content")));
    }

    @Test
    void testGetBlockTypeReturnsText() {
        TextContentBlockEntity textBlock = new TextContentBlockEntity();
        assertEquals(ContentBlockType.TEXT, textBlock.getBlockType());
    }

    @Test
    void testBuilderWithAllFields() {
        // Test the builder pattern with all fields
        TextContentBlockEntity textBlock = TextContentBlockEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .blockType(ContentBlockType.TEXT)
                .caption("Text content caption")
                .content("This is a sample text content with enough characters to be meaningful.")
                .formatType("markdown")
                .build();

        // Verify all fields were set correctly
        assertEquals(1L, textBlock.getCollectionId());
        assertEquals(0, textBlock.getOrderIndex());
        assertEquals(ContentBlockType.TEXT, textBlock.getBlockType());
        assertEquals("Text content caption", textBlock.getCaption());
        assertEquals("This is a sample text content with enough characters to be meaningful.", textBlock.getContent());
        assertEquals("markdown", textBlock.getFormatType());
    }

    @Test
    void testFormatTypeValues() {
        // Test with different valid format types
        TextContentBlockEntity markdownBlock = TextContentBlockEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .blockType(ContentBlockType.TEXT)
                .content("Markdown content")
                .formatType("markdown")
                .build();
        
        TextContentBlockEntity htmlBlock = TextContentBlockEntity.builder()
                .collectionId(1L)
                .orderIndex(1)
                .blockType(ContentBlockType.TEXT)
                .content("<p>HTML content</p>")
                .formatType("html")
                .build();
        
        TextContentBlockEntity plainBlock = TextContentBlockEntity.builder()
                .collectionId(1L)
                .orderIndex(2)
                .blockType(ContentBlockType.TEXT)
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
        TextContentBlockEntity textBlock1 = TextContentBlockEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .blockType(ContentBlockType.TEXT)
                .content("This is a sample text content.")
                .formatType("markdown")
                .build();

        TextContentBlockEntity textBlock2 = TextContentBlockEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .blockType(ContentBlockType.TEXT)
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