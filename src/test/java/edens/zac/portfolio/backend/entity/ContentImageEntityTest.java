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

class ContentImageEntityTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testValidContentImage() {
        // Create a valid content image
        ContentImageEntity imageBlock = ContentImageEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .contentType(ContentType.IMAGE)
                .title("Mountain Landscape")
                .imageUrlWeb("https://example.com/images/mountain.jpg")
                .imageWidth(1920)
                .imageHeight(1080)
                .iso(100)
                .author("John Doe")
                .rating(5)
                .fStop("f/2.8")
                .lens(new ContentLensEntity("24-70mm"))
                .blackAndWhite(false)
                .isFilm(false)
                .shutterSpeed("1/125")
                .camera(new ContentCameraEntity("Canon EOS R5"))
                .focalLength("50mm")
                .location("Yosemite National Park")
                .createDate("2023-05-15")
                .build();

        Set<ConstraintViolation<ContentImageEntity>> violations = validator.validate(imageBlock);
        assertTrue(violations.isEmpty());
        assertEquals(ContentType.IMAGE, imageBlock.getContentType());
    }

    @Test
    void testInvalidContentImageMissingRequiredField() {
        // Create an invalid content image (missing required imageUrlWeb)
        ContentImageEntity imageBlock = ContentImageEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .contentType(ContentType.IMAGE)
                .title("Mountain Landscape")
                // imageUrlWeb is missing
                .build();

        Set<ConstraintViolation<ContentImageEntity>> violations = validator.validate(imageBlock);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("imageUrlWeb")));
    }

    @Test
    void testGetContentTypeReturnsImage() {
        ContentImageEntity imageBlock = new ContentImageEntity();
        assertEquals(ContentType.IMAGE, imageBlock.getContentType());
    }

    @Test
    void testBuilderWithAllFields() {
        // Test the builder pattern with all fields
        ContentImageEntity imageBlock = ContentImageEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .contentType(ContentType.IMAGE)
                .caption("A beautiful mountain landscape")
                .title("Mountain Landscape")
                .imageUrlWeb("https://example.com/images/mountain.jpg")
                .imageWidth(1920)
                .imageHeight(1080)
                .iso(100)
                .author("John Doe")
                .rating(5)
                .fStop("f/2.8")
                .lens(new ContentLensEntity("24-70mm"))
                .blackAndWhite(false)
                .isFilm(false)
                .shutterSpeed("1/125")
                .camera(new ContentCameraEntity("Canon EOS R5"))
                .focalLength("50mm")
                .location("Yosemite National Park")
                .createDate("2023-05-15")
                .build();

        // Verify all fields were set correctly
        assertEquals(1L, imageBlock.getCollectionId());
        assertEquals(0, imageBlock.getOrderIndex());
        assertEquals(ContentType.IMAGE, imageBlock.getContentType());
        assertEquals("A beautiful mountain landscape", imageBlock.getCaption());
        assertEquals("Mountain Landscape", imageBlock.getTitle());
        assertEquals("https://example.com/images/mountain.jpg", imageBlock.getImageUrlWeb());
        assertEquals(1920, imageBlock.getImageWidth());
        assertEquals(1080, imageBlock.getImageHeight());
        assertEquals(100, imageBlock.getIso());
        assertEquals("John Doe", imageBlock.getAuthor());
        assertEquals(5, imageBlock.getRating());
        assertEquals("f/2.8", imageBlock.getFStop());
        assertEquals("24-70mm", imageBlock.getLens().getLensName());
        assertFalse(imageBlock.getBlackAndWhite());
        assertFalse(imageBlock.getIsFilm());
        assertEquals("1/125", imageBlock.getShutterSpeed());
        assertEquals("Canon EOS R5", imageBlock.getCamera().getCameraName());
        assertEquals("50mm", imageBlock.getFocalLength());
        assertEquals("Yosemite National Park", imageBlock.getLocation());
        assertEquals("2023-05-15", imageBlock.getCreateDate());
    }

    @Test
    void testEqualsAndHashCode() {
        // Create two identical image blocks
        ContentImageEntity imageBlock1 = ContentImageEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .contentType(ContentType.IMAGE)
                .imageUrlWeb("https://example.com/images/mountain.jpg")
                .build();

        ContentImageEntity imageBlock2 = ContentImageEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .contentType(ContentType.IMAGE)
                .imageUrlWeb("https://example.com/images/mountain.jpg")
                .build();

        // Test equals and hashCode
        assertEquals(imageBlock1, imageBlock2);
        assertEquals(imageBlock1.hashCode(), imageBlock2.hashCode());

        // Modify one field and test again
        imageBlock2.setTitle("Different Title");
        assertNotEquals(imageBlock1, imageBlock2);
    }
}