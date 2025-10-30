package edens.zac.portfolio.backend.entity;

import edens.zac.portfolio.backend.types.CollectionType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CollectionEntityTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testValidContentCollection() {
        // Create a valid content collection
        CollectionEntity collection = new CollectionEntity();
        collection.setType(CollectionType.BLOG);
        collection.setTitle("Test Collection");
        collection.setSlug("test-collection");

        Set<ConstraintViolation<CollectionEntity>> violations = validator.validate(collection);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testInvalidContentCollectionMissingRequiredFields() {
        // Create an invalid content collection (missing required fields)
        CollectionEntity collection = new CollectionEntity();

        Set<ConstraintViolation<CollectionEntity>> violations = validator.validate(collection);
        assertFalse(violations.isEmpty());
        assertEquals(3, violations.size()); // type, title, and slug are required

        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("type")));
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("title")));
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("slug")));
    }

    @Test
    void testTitleLengthValidation() {
        // Test with title that is too short
        CollectionEntity shortTitle = new CollectionEntity();
        shortTitle.setType(CollectionType.BLOG);
        shortTitle.setTitle("AB"); // Less than minimum 3 characters
        shortTitle.setSlug("test-collection");

        Set<ConstraintViolation<CollectionEntity>> shortViolations = validator.validate(shortTitle);
        assertFalse(shortViolations.isEmpty());
        assertTrue(shortViolations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("title")));

        // Test with title that is too long
        // 101 characters, max is 100
        String longTitleBuilder = "A".repeat(101);

        CollectionEntity longTitle = new CollectionEntity();
        longTitle.setType(CollectionType.BLOG);
        longTitle.setTitle(longTitleBuilder);
        longTitle.setSlug("test-collection");

        Set<ConstraintViolation<CollectionEntity>> longViolations = validator.validate(longTitle);
        assertFalse(longViolations.isEmpty());
        assertTrue(longViolations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("title")));
    }

    @Test
    void testSlugLengthValidation() {
        // Test with slug that is too short
        CollectionEntity shortSlug = new CollectionEntity();
        shortSlug.setType(CollectionType.BLOG);
        shortSlug.setTitle("Test Collection");
        shortSlug.setSlug("ab"); // Less than minimum 3 characters

        Set<ConstraintViolation<CollectionEntity>> shortViolations = validator.validate(shortSlug);
        assertFalse(shortViolations.isEmpty());
        assertTrue(shortViolations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("slug")));

        // Test with slug that is too long
        // 151 characters, max is 150
        String longSlugBuilder = "a".repeat(151);

        CollectionEntity longSlug = new CollectionEntity();
        longSlug.setType(CollectionType.BLOG);
        longSlug.setTitle("Test Collection");
        longSlug.setSlug(longSlugBuilder);

        Set<ConstraintViolation<CollectionEntity>> longViolations = validator.validate(longSlug);
        assertFalse(longViolations.isEmpty());
        assertTrue(longViolations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("slug")));
    }

    @Test
    void testDescriptionLengthValidation() {
        // Test with description that is too long
        // 501 characters, max is 500
        String longDescriptionBuilder = "A".repeat(501);

        CollectionEntity longDescription = new CollectionEntity();
        longDescription.setType(CollectionType.BLOG);
        longDescription.setTitle("Test Collection");
        longDescription.setSlug("test-collection");
        longDescription.setDescription(longDescriptionBuilder);

        Set<ConstraintViolation<CollectionEntity>> violations = validator.validate(longDescription);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("description")));
    }

    @Test
    void testLocationLengthValidation() {
        // Test with location that is too long
        // 256 characters, max is 255
        String longLocationBuilder = "A".repeat(256);

        CollectionEntity longLocation = new CollectionEntity();
        longLocation.setType(CollectionType.BLOG);
        longLocation.setTitle("Test Collection");
        longLocation.setSlug("test-collection");
        longLocation.setLocation(longLocationBuilder);

        Set<ConstraintViolation<CollectionEntity>> violations = validator.validate(longLocation);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("location")));
    }

    @Test
    void testPriorityMinimumValidation() {
        // Test with negative priority
        CollectionEntity negativePriority = new CollectionEntity();
        negativePriority.setType(CollectionType.BLOG);
        negativePriority.setTitle("Test Collection");
        negativePriority.setSlug("test-collection");
        negativePriority.setPriority(-1); // Should be minimum 0

        Set<ConstraintViolation<CollectionEntity>> violations = validator.validate(negativePriority);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("priority")));
    }

    @Test
    void testBlocksPerPageMinimumValidation() {
        // Test with blocks_per_page less than 1
        CollectionEntity zeroBlocksPerPage = new CollectionEntity();
        zeroBlocksPerPage.setType(CollectionType.BLOG);
        zeroBlocksPerPage.setTitle("Test Collection");
        zeroBlocksPerPage.setSlug("test-collection");
        zeroBlocksPerPage.setBlocksPerPage(0); // Should be minimum 1

        Set<ConstraintViolation<CollectionEntity>> violations = validator.validate(zeroBlocksPerPage);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("blocksPerPage")));
    }

    @Test
    void testIsPasswordProtected() {
        // Test with password protection enabled and hash present
        CollectionEntity protectedCollection = new CollectionEntity();
        protectedCollection.setType(CollectionType.CLIENT_GALLERY);
        protectedCollection.setTitle("Protected Collection");
        protectedCollection.setSlug("protected-collection");
        protectedCollection.setPasswordProtected(true);
        protectedCollection.setPasswordHash("$2a$10$someHashValue");

        assertTrue(protectedCollection.isPasswordProtected());

        // Test with password protection disabled
        CollectionEntity unprotectedCollection = new CollectionEntity();
        unprotectedCollection.setType(CollectionType.BLOG);
        unprotectedCollection.setTitle("Public Collection");
        unprotectedCollection.setSlug("public-collection");
        unprotectedCollection.setPasswordProtected(false);
        unprotectedCollection.setPasswordHash("$2a$10$someHashValue");

        assertFalse(unprotectedCollection.isPasswordProtected());

        // Test with password protection enabled but no hash
        CollectionEntity incompleteCollection = new CollectionEntity();
        incompleteCollection.setType(CollectionType.CLIENT_GALLERY);
        incompleteCollection.setTitle("Incomplete Collection");
        incompleteCollection.setSlug("incomplete-collection");
        incompleteCollection.setPasswordProtected(true);
        incompleteCollection.setPasswordHash("");


        assertFalse(incompleteCollection.isPasswordProtected());

        // Test with null values
        CollectionEntity nullCollection = new CollectionEntity();
        nullCollection.setType(CollectionType.CLIENT_GALLERY);
        nullCollection.setTitle("Null Collection");
        nullCollection.setSlug("null-collection");
        nullCollection.setPasswordProtected(null);
        nullCollection.setPasswordHash(null);

        assertFalse(nullCollection.isPasswordProtected());
    }

    @Test
    void testGetTotalPages() {
        // Test with typical values
        CollectionEntity collection = new CollectionEntity();
        collection.setType(CollectionType.BLOG);
        collection.setTitle("Test Collection");
        collection.setSlug("test-collection");
        collection.setTotalBlocks(100);
        collection.setBlocksPerPage(30);

        assertEquals(4, collection.getTotalPages()); // 100/30 = 3.33, rounded up to 4

        // Test with exact division
        collection.setTotalBlocks(90);
        assertEquals(3, collection.getTotalPages()); // 90/30 = 3.0

        // Test with zero blocks
        collection.setTotalBlocks(0);
        assertEquals(0, collection.getTotalPages());

        // Test with null values
        collection.setTotalBlocks(null);
        assertEquals(0, collection.getTotalPages());

        collection.setTotalBlocks(100);
        collection.setBlocksPerPage(null);
        assertEquals(0, collection.getTotalPages());

        // Test with zero blocks per page
        collection.setBlocksPerPage(0);
        assertEquals(0, collection.getTotalPages());
    }

    @Test
    void testBuilderWithAllFields() {
        LocalDate today = LocalDate.now();

        // Test builder with all fields
        CollectionEntity collection = new CollectionEntity();
        collection.setType(CollectionType.PORTFOLIO);
        collection.setTitle("Complete Portfolio");
        collection.setSlug("complete-portfolio");
        collection.setDescription("A portfolio with all fields populated");
        collection.setLocation("Test Location");
        collection.setCollectionDate(today);
        collection.setPriority(5);
        collection.setBlocksPerPage(20);
        collection.setTotalBlocks(100);
        collection.setPasswordHash("$2a$10$someHashValue");
        collection.setPasswordProtected(true);
        collection.setCoverImageBlockId(123L);

        // Verify all fields were set correctly
        assertEquals(CollectionType.PORTFOLIO, collection.getType());
        assertEquals("Complete Portfolio", collection.getTitle());
        assertEquals("complete-portfolio", collection.getSlug());
        assertEquals("A portfolio with all fields populated", collection.getDescription());
        assertEquals("Test Location", collection.getLocation());
        assertEquals(today, collection.getCollectionDate());
        assertTrue(collection.getVisible());
        assertEquals(5, collection.getPriority());
        assertEquals(20, collection.getBlocksPerPage());
        assertEquals(100, collection.getTotalBlocks());
        assertEquals("$2a$10$someHashValue", collection.getPasswordHash());
        assertTrue(collection.getPasswordProtected());
        assertEquals(123L, collection.getCoverImageBlockId());
    }

    @Test
    void testEqualsAndHashCode() {
        // Create two identical collections
        CollectionEntity collection1 = new CollectionEntity();
        collection1.setType(CollectionType.BLOG);
        collection1.setTitle("Test Collection");
        collection1.setSlug("test-collection");

        CollectionEntity collection2 = new CollectionEntity();
        collection2.setType(CollectionType.BLOG);
        collection2.setTitle("Test Collection");
        collection2.setSlug("test-collection");

        // Test equals
        assertEquals(collection1, collection2);
        assertEquals(collection1.hashCode(), collection2.hashCode());

        // Modify field and test again
        collection2.setTitle("Different Title");
        assertNotEquals(collection1, collection2);
        assertNotEquals(collection1.hashCode(), collection2.hashCode());
    }

    @Test
    void testCollectionTypesForAllEnumValues() {
        // Test all enum values work properly with the entity
        CollectionEntity blogCollection = new CollectionEntity();
        blogCollection.setType(CollectionType.BLOG);
        blogCollection.setTitle("Blog Collection");
        blogCollection.setSlug("blog-collection");

        CollectionEntity artGalleryCollection = new CollectionEntity();
        artGalleryCollection.setType(CollectionType.ART_GALLERY);
        artGalleryCollection.setTitle("Art Gallery Collection");
        artGalleryCollection.setSlug("art-gallery-collection");

        CollectionEntity clientGalleryCollection = new CollectionEntity();
        clientGalleryCollection.setType(CollectionType.CLIENT_GALLERY);
        clientGalleryCollection.setTitle("Client Gallery Collection");
        clientGalleryCollection.setSlug("client-gallery-collection");

        CollectionEntity portfolioCollection = new CollectionEntity();
        portfolioCollection.setType(CollectionType.PORTFOLIO);
        portfolioCollection.setTitle("Portfolio Collection");
        portfolioCollection.setSlug("portfolio-collection");

        // Verify types were set correctly
        assertEquals(CollectionType.BLOG, blogCollection.getType());
        assertEquals(CollectionType.ART_GALLERY, artGalleryCollection.getType());
        assertEquals(CollectionType.CLIENT_GALLERY, clientGalleryCollection.getType());
        assertEquals(CollectionType.PORTFOLIO, portfolioCollection.getType());

        // Verify no validation errors for any type
        assertTrue(validator.validate(artGalleryCollection).isEmpty());
        assertTrue(validator.validate(clientGalleryCollection).isEmpty());
        assertTrue(validator.validate(portfolioCollection).isEmpty());
    }
}