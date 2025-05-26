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

class ImageContentBlockEntityTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testValidImageContentBlock() {
        // Create a valid image content block
        ImageContentBlockEntity imageBlock = ImageContentBlockEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .blockType(ContentBlockType.IMAGE)
                .title("Mountain Landscape")
                .imageUrlWeb("https://example.com/images/mountain.jpg")
                .imageWidth(1920)
                .imageHeight(1080)
                .iso(100)
                .author("John Doe")
                .rating(5)
                .fStop("f/2.8")
                .lens("24-70mm")
                .blackAndWhite(false)
                .isFilm(false)
                .shutterSpeed("1/125")
                .rawFileName("IMG_1234.CR2")
                .camera("Canon EOS R5")
                .focalLength("50mm")
                .location("Yosemite National Park")
                .imageUrlRaw("https://example.com/images/raw/mountain.cr2")
                .createDate("2023-05-15")
                .build();

        Set<ConstraintViolation<ImageContentBlockEntity>> violations = validator.validate(imageBlock);
        assertTrue(violations.isEmpty());
        assertEquals(ContentBlockType.IMAGE, imageBlock.getBlockType());
    }

    @Test
    void testInvalidImageContentBlockMissingRequiredField() {
        // Create an invalid image content block (missing required imageUrlWeb)
        ImageContentBlockEntity imageBlock = ImageContentBlockEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .blockType(ContentBlockType.IMAGE)
                .title("Mountain Landscape")
                // imageUrlWeb is missing
                .build();

        Set<ConstraintViolation<ImageContentBlockEntity>> violations = validator.validate(imageBlock);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("imageUrlWeb")));
    }

    @Test
    void testGetBlockTypeReturnsImage() {
        ImageContentBlockEntity imageBlock = new ImageContentBlockEntity();
        assertEquals(ContentBlockType.IMAGE, imageBlock.getBlockType());
    }

    @Test
    void testBuilderWithAllFields() {
        // Test the builder pattern with all fields
        ImageContentBlockEntity imageBlock = ImageContentBlockEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .blockType(ContentBlockType.IMAGE)
                .caption("A beautiful mountain landscape")
                .title("Mountain Landscape")
                .imageUrlWeb("https://example.com/images/mountain.jpg")
                .imageWidth(1920)
                .imageHeight(1080)
                .iso(100)
                .author("John Doe")
                .rating(5)
                .fStop("f/2.8")
                .lens("24-70mm")
                .blackAndWhite(false)
                .isFilm(false)
                .shutterSpeed("1/125")
                .rawFileName("IMG_1234.CR2")
                .camera("Canon EOS R5")
                .focalLength("50mm")
                .location("Yosemite National Park")
                .imageUrlRaw("https://example.com/images/raw/mountain.cr2")
                .createDate("2023-05-15")
                .build();

        // Verify all fields were set correctly
        assertEquals(1L, imageBlock.getCollectionId());
        assertEquals(0, imageBlock.getOrderIndex());
        assertEquals(ContentBlockType.IMAGE, imageBlock.getBlockType());
        assertEquals("A beautiful mountain landscape", imageBlock.getCaption());
        assertEquals("Mountain Landscape", imageBlock.getTitle());
        assertEquals("https://example.com/images/mountain.jpg", imageBlock.getImageUrlWeb());
        assertEquals(1920, imageBlock.getImageWidth());
        assertEquals(1080, imageBlock.getImageHeight());
        assertEquals(100, imageBlock.getIso());
        assertEquals("John Doe", imageBlock.getAuthor());
        assertEquals(5, imageBlock.getRating());
        assertEquals("f/2.8", imageBlock.getFStop());
        assertEquals("24-70mm", imageBlock.getLens());
        assertFalse(imageBlock.getBlackAndWhite());
        assertFalse(imageBlock.getIsFilm());
        assertEquals("1/125", imageBlock.getShutterSpeed());
        assertEquals("IMG_1234.CR2", imageBlock.getRawFileName());
        assertEquals("Canon EOS R5", imageBlock.getCamera());
        assertEquals("50mm", imageBlock.getFocalLength());
        assertEquals("Yosemite National Park", imageBlock.getLocation());
        assertEquals("https://example.com/images/raw/mountain.cr2", imageBlock.getImageUrlRaw());
        assertEquals("2023-05-15", imageBlock.getCreateDate());
    }

    @Test
    void testEqualsAndHashCode() {
        // Create two identical image blocks
        ImageContentBlockEntity imageBlock1 = ImageContentBlockEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .blockType(ContentBlockType.IMAGE)
                .imageUrlWeb("https://example.com/images/mountain.jpg")
                .build();

        ImageContentBlockEntity imageBlock2 = ImageContentBlockEntity.builder()
                .collectionId(1L)
                .orderIndex(0)
                .blockType(ContentBlockType.IMAGE)
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