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

class ContentGifEntityTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testValidGifContentBlock() {
        // Create a valid gif content block
        ContentGifEntity gifBlock = ContentGifEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .contentType(ContentType.GIF)
                .title("Funny Cat")
                .gifUrl("https://example.com/gifs/funny-cat.gif")
                .thumbnailUrl("https://example.com/gifs/thumbnails/funny-cat.jpg")
                .width(480)
                .height(320)
                .author("Jane Smith")
                .createDate("2023-06-10")
                .build();

        Set<ConstraintViolation<ContentGifEntity>> violations = validator.validate(gifBlock);
        assertTrue(violations.isEmpty());
        assertEquals(ContentType.GIF, gifBlock.getContentType());
    }

    @Test
    void testInvalidGifContentBlockMissingRequiredField() {
        // Create an invalid gif content block (missing required gifUrl)
        ContentGifEntity gifBlock = ContentGifEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .contentType(ContentType.GIF)
                .title("Funny Cat")
                // gifUrl is missing
                .build();

        Set<ConstraintViolation<ContentGifEntity>> violations = validator.validate(gifBlock);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("gifUrl")));
    }

    @Test
    void testGetContentTypeReturnsGif() {
        ContentGifEntity gifBlock = new ContentGifEntity();
        assertEquals(ContentType.GIF, gifBlock.getContentType());
    }

    @Test
    void testBuilderWithAllFields() {
        // Test the builder pattern with all fields
        ContentGifEntity gifBlock = ContentGifEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .contentType(ContentType.GIF)
                .caption("A funny cat gif")
                .title("Funny Cat")
                .gifUrl("https://example.com/gifs/funny-cat.gif")
                .thumbnailUrl("https://example.com/gifs/thumbnails/funny-cat.jpg")
                .width(480)
                .height(320)
                .author("Jane Smith")
                .createDate("2023-06-10")
                .build();

        // Verify all fields were set correctly
        assertEquals(1L, gifBlock.getCollectionId());
        assertEquals(0, gifBlock.getOrderIndex());
        assertEquals(ContentType.GIF, gifBlock.getContentType());
        assertEquals("A funny cat gif", gifBlock.getCaption());
        assertEquals("Funny Cat", gifBlock.getTitle());
        assertEquals("https://example.com/gifs/funny-cat.gif", gifBlock.getGifUrl());
        assertEquals("https://example.com/gifs/thumbnails/funny-cat.jpg", gifBlock.getThumbnailUrl());
        assertEquals(480, gifBlock.getWidth());
        assertEquals(320, gifBlock.getHeight());
        assertEquals("Jane Smith", gifBlock.getAuthor());
        assertEquals("2023-06-10", gifBlock.getCreateDate());
    }

    @Test
    void testOptionalFields() {
        // Test with only required fields
        ContentGifEntity minimalGifBlock = ContentGifEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .contentType(ContentType.GIF)
                .gifUrl("https://example.com/gifs/minimal.gif")
                .build();
        
        // All optional fields should be null
        assertNull(minimalGifBlock.getTitle());
        assertNull(minimalGifBlock.getThumbnailUrl());
        assertNull(minimalGifBlock.getWidth());
        assertNull(minimalGifBlock.getHeight());
        assertNull(minimalGifBlock.getAuthor());
        assertNull(minimalGifBlock.getCreateDate());
        
        // But the required field should be set
        assertEquals("https://example.com/gifs/minimal.gif", minimalGifBlock.getGifUrl());
    }

    @Test
    void testEqualsAndHashCode() {
        // Create two identical gif blocks
        ContentGifEntity gifBlock1 = ContentGifEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .contentType(ContentType.GIF)
                .title("Funny Cat")
                .gifUrl("https://example.com/gifs/funny-cat.gif")
                .build();

        ContentGifEntity gifBlock2 = ContentGifEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .contentType(ContentType.GIF)
                .title("Funny Cat")
                .gifUrl("https://example.com/gifs/funny-cat.gif")
                .build();

        // Test equals and hashCode
        assertEquals(gifBlock1, gifBlock2);
        assertEquals(gifBlock1.hashCode(), gifBlock2.hashCode());

        // Modify one field and test again
        gifBlock2.setTitle("Different Title");
        assertNotEquals(gifBlock1, gifBlock2);
    }
}