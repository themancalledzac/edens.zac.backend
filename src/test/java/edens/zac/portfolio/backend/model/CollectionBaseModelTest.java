package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.types.CollectionType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CollectionBaseModel
 * Tests validation annotations, builder pattern, and common functionality
 * Uses CollectionModel as concrete implementation to test the abstract base class
 */
class CollectionBaseModelTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    /**
     * Helper method to create a test ImageContentBlockModel
     */
    private static ImageContentModel createTestContentImage(String imageUrl) {
        ImageContentModel imageBlock = new ImageContentModel();
        imageBlock.setId(123L);
        imageBlock.setImageUrlWeb(imageUrl);
        imageBlock.setImageWidth(1920);
        imageBlock.setImageHeight(1080);
        return imageBlock;
    }

    @Nested
    @DisplayName("Builder Pattern Tests")
    class BuilderPatternTests {

        @Test
        @DisplayName("Should create model with all valid fields using builder")
        void shouldCreateModelWithAllValidFields() {
            LocalDateTime now = LocalDateTime.now();
            LocalDate today = LocalDate.now();

            CollectionModel model = CollectionModel.builder()
                    .id(1L)
                    .type(CollectionType.PORTFOLIO)
                    .title("Valid Title")
                    .slug("valid-slug")
                    .description("Valid description")
                    .location("Valid location")
                    .collectionDate(today)
                    .visible(true)
                    .priority(1)
                    .coverImage(createTestContentImage("https://example.com/cover.jpg"))
                    .isPasswordProtected(false)
                    .hasAccess(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            assertNotNull(model);
            assertEquals(1L, model.getId());
            assertEquals(CollectionType.PORTFOLIO, model.getType());
            assertEquals("Valid Title", model.getTitle());
            assertEquals("valid-slug", model.getSlug());
            assertEquals("Valid description", model.getDescription());
            assertEquals("Valid location", model.getLocation());
            assertEquals(today, model.getCollectionDate());
            assertTrue(model.getVisible());
            assertEquals(1, model.getPriority());
            assertNotNull(model.getCoverImage());
            assertEquals("https://example.com/cover.jpg", model.getCoverImage().getImageUrlWeb());
            assertFalse(model.getIsPasswordProtected());
            assertTrue(model.getHasAccess());
            assertEquals(now, model.getCreatedAt());
            assertEquals(now, model.getUpdatedAt());
        }

        @Test
        @DisplayName("Should create model with minimal required fields")
        void shouldCreateModelWithMinimalFields() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.BLOG)
                    .title("Min")
                    .slug("min")
                    .build();

            assertNotNull(model);
            assertEquals(CollectionType.BLOG, model.getType());
            assertEquals("Min", model.getTitle());
            assertEquals("min", model.getSlug());
            assertNull(model.getId());
            assertNull(model.getDescription());
            assertNull(model.getLocation());
        }

        @Test
        @DisplayName("Should create model using no-args constructor")
        void shouldCreateModelWithNoArgsConstructor() {
            CollectionModel model = new CollectionModel();
            
            assertNotNull(model);
            assertNull(model.getId());
            assertNull(model.getType());
            assertNull(model.getTitle());
        }
    }

    @Nested
    @DisplayName("Title Validation Tests")
    class TitleValidationTests {

        @Test
        @DisplayName("Should accept valid title")
        void shouldAcceptValidTitle() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.BLOG)
                    .title("Valid Title")
                    .slug("valid-slug")
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should reject title that is too short")
        void shouldRejectTitleTooShort() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.BLOG)
                    .title("AB") // Only 2 characters
                    .slug("valid-slug")
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Title must be between 3 and 100 characters")));
        }

        @Test
        @DisplayName("Should reject title that is too long")
        void shouldRejectTitleTooLong() {
            String longTitle = "A".repeat(101); // 101 characters
            
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.BLOG)
                    .title(longTitle)
                    .slug("valid-slug")
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Title must be between 3 and 100 characters")));
        }

        @Test
        @DisplayName("Should accept title at minimum length boundary")
        void shouldAcceptTitleAtMinBoundary() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.BLOG)
                    .title("ABC") // Exactly 3 characters
                    .slug("valid-slug")
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should accept title at maximum length boundary")
        void shouldAcceptTitleAtMaxBoundary() {
            String maxTitle = "A".repeat(100); // Exactly 100 characters
            
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.BLOG)
                    .title(maxTitle)
                    .slug("valid-slug")
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertTrue(violations.isEmpty());
        }
    }

    @Nested
    @DisplayName("Slug Validation Tests")
    class SlugValidationTests {

        @Test
        @DisplayName("Should accept valid slug")
        void shouldAcceptValidSlug() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Valid Title")
                    .slug("valid-slug-123")
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should reject slug that is too short")
        void shouldRejectSlugTooShort() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Valid Title")
                    .slug("AB") // Only 2 characters
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Slug must be between 3 and 150 characters")));
        }

        @Test
        @DisplayName("Should reject slug that is too long")
        void shouldRejectSlugTooLong() {
            String longSlug = "a".repeat(151); // 151 characters
            
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Valid Title")
                    .slug(longSlug)
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Slug must be between 3 and 150 characters")));
        }

        @Test
        @DisplayName("Should accept slug at boundary lengths")
        void shouldAcceptSlugAtBoundaries() {
            // Test minimum boundary
            CollectionModel minModel = CollectionModel.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Valid Title")
                    .slug("abc") // Exactly 3 characters
                    .build();

            Set<ConstraintViolation<CollectionModel>> minViolations = validator.validate(minModel);
            assertTrue(minViolations.isEmpty());

            // Test maximum boundary
            String maxSlug = "a".repeat(150); // Exactly 150 characters
            CollectionModel maxModel = CollectionModel.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Valid Title")
                    .slug(maxSlug)
                    .build();

            Set<ConstraintViolation<CollectionModel>> maxViolations = validator.validate(maxModel);
            assertTrue(maxViolations.isEmpty());
        }
    }

    @Nested
    @DisplayName("Description Validation Tests")
    class DescriptionValidationTests {

        @Test
        @DisplayName("Should accept valid description")
        void shouldAcceptValidDescription() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.ART_GALLERY)
                    .title("Valid Title")
                    .slug("valid-slug")
                    .description("This is a valid description of the collection")
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should accept null description")
        void shouldAcceptNullDescription() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.ART_GALLERY)
                    .title("Valid Title")
                    .slug("valid-slug")
                    .description(null)
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should reject description that is too long")
        void shouldRejectDescriptionTooLong() {
            String longDescription = "A".repeat(501); // 501 characters
            
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.ART_GALLERY)
                    .title("Valid Title")
                    .slug("valid-slug")
                    .description(longDescription)
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Description cannot exceed 500 characters")));
        }

        @Test
        @DisplayName("Should accept description at maximum length")
        void shouldAcceptDescriptionAtMaxLength() {
            String maxDescription = "A".repeat(500); // Exactly 500 characters
            
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.ART_GALLERY)
                    .title("Valid Title")
                    .slug("valid-slug")
                    .description(maxDescription)
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertTrue(violations.isEmpty());
        }
    }

    @Nested
    @DisplayName("Location Validation Tests")
    class LocationValidationTests {

        @Test
        @DisplayName("Should accept valid location")
        void shouldAcceptValidLocation() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Valid Title")
                    .slug("valid-slug")
                    .location("Arches National Park, Utah")
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should accept null location")
        void shouldAcceptNullLocation() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Valid Title")
                    .slug("valid-slug")
                    .location(null)
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should reject location that is too long")
        void shouldRejectLocationTooLong() {
            String longLocation = "A".repeat(256); // 256 characters
            
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Valid Title")
                    .slug("valid-slug")
                    .location(longLocation)
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Location cannot exceed 255 characters")));
        }

        @Test
        @DisplayName("Should accept location at maximum length")
        void shouldAcceptLocationAtMaxLength() {
            String maxLocation = "A".repeat(255); // Exactly 255 characters
            
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Valid Title")
                    .slug("valid-slug")
                    .location(maxLocation)
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertTrue(violations.isEmpty());
        }
    }

    @Nested
    @DisplayName("Priority Validation Tests")
    class PriorityValidationTests {

        @Test
        @DisplayName("Should accept all valid priority values")
        void shouldAcceptValidPriorityValues() {
            for (int priority = 1; priority <= 4; priority++) {
                CollectionModel model = CollectionModel.builder()
                        .type(CollectionType.PORTFOLIO)
                        .title("Valid Title")
                        .slug("valid-slug")
                        .priority(priority)
                        .build();

                Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
                assertTrue(violations.isEmpty(), "Priority " + priority + " should be valid");
            }
        }

        @Test
        @DisplayName("Should accept null priority")
        void shouldAcceptNullPriority() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Valid Title")
                    .slug("valid-slug")
                    .priority(null)
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should reject priority below minimum")
        void shouldRejectPriorityBelowMin() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Valid Title")
                    .slug("valid-slug")
                    .priority(0)
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Priority must be between 1 and 4")));
        }

        @Test
        @DisplayName("Should reject priority above maximum")
        void shouldRejectPriorityAboveMax() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Valid Title")
                    .slug("valid-slug")
                    .priority(5)
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Priority must be between 1 and 4")));
        }
    }

    @Nested
    @DisplayName("Collection Type Tests")
    class CollectionTypeTests {

        @Test
        @DisplayName("Should accept all collection types")
        void shouldAcceptAllCollectionTypes() {
            for (CollectionType type : CollectionType.values()) {
                CollectionModel model = CollectionModel.builder()
                        .type(type)
                        .title("Valid Title")
                        .slug("valid-slug")
                        .build();

                Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
                assertTrue(violations.isEmpty(), "Collection type " + type + " should be valid");
            }
        }

        @Test
        @DisplayName("Should accept null collection type")
        void shouldAcceptNullCollectionType() {
            CollectionModel model = CollectionModel.builder()
                    .type(null)
                    .title("Valid Title")
                    .slug("valid-slug")
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertTrue(violations.isEmpty());
        }
    }

    @Nested
    @DisplayName("Data Annotation Tests")
    class DataAnnotationTests {

        @Test
        @DisplayName("Should have working equals and hashCode")
        void shouldHaveWorkingEqualsAndHashCode() {
            LocalDateTime now = LocalDateTime.now();
            
            CollectionModel model1 = CollectionModel.builder()
                    .id(1L)
                    .type(CollectionType.BLOG)
                    .title("Test Title")
                    .slug("test-slug")
                    .createdAt(now)
                    .build();

            CollectionModel model2 = CollectionModel.builder()
                    .id(1L)
                    .type(CollectionType.BLOG)
                    .title("Test Title")
                    .slug("test-slug")
                    .createdAt(now)
                    .build();

            CollectionModel model3 = CollectionModel.builder()
                    .id(2L)
                    .type(CollectionType.BLOG)
                    .title("Test Title")
                    .slug("test-slug")
                    .createdAt(now)
                    .build();

            // Test equals
            assertEquals(model1, model2);
            assertNotEquals(model1, model3);

            // Test hashCode consistency
            assertEquals(model1.hashCode(), model2.hashCode());
        }

        @Test
        @DisplayName("Should have working toString method")
        void shouldHaveWorkingToString() {
            CollectionModel model = CollectionModel.builder()
                    .id(1L)
                    .type(CollectionType.PORTFOLIO)
                    .title("Test Portfolio")
                    .slug("test-portfolio")
                    .build();

            String toString = model.toString();
            
            assertNotNull(toString);
            assertTrue(toString.contains("CollectionModel"));
            assertTrue(toString.contains("id=1"));
            assertTrue(toString.contains("PORTFOLIO"));
            assertTrue(toString.contains("Test Portfolio"));
        }
    }

    @Nested
    @DisplayName("Security Field Tests")
    class SecurityFieldTests {

        @Test
        @DisplayName("Should handle client gallery security fields")
        void shouldHandleClientGallerySecurityFields() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Client Gallery")
                    .slug("client-gallery")
                    .isPasswordProtected(true)
                    .hasAccess(false)
                    .build();

            assertNotNull(model);
            assertTrue(model.getIsPasswordProtected());
            assertFalse(model.getHasAccess());
        }

        @Test
        @DisplayName("Should handle null security fields")
        void shouldHandleNullSecurityFields() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.BLOG)
                    .title("Blog Post")
                    .slug("blog-post")
                    .isPasswordProtected(null)
                    .hasAccess(null)
                    .build();

            assertNotNull(model);
            assertNull(model.getIsPasswordProtected());
            assertNull(model.getHasAccess());
        }
    }

    @Nested
    @DisplayName("Timestamp Tests")
    class TimestampTests {

        @Test
        @DisplayName("Should handle timestamp fields correctly")
        void shouldHandleTimestampFields() {
            LocalDateTime created = LocalDateTime.now().minusDays(1);
            LocalDateTime updated = LocalDateTime.now();
            LocalDate collectionDate = LocalDate.now().minusMonths(1);

            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Portfolio")
                    .slug("portfolio")
                    .collectionDate(collectionDate)
                    .createdAt(created)
                    .updatedAt(updated)
                    .build();

            assertNotNull(model);
            assertEquals(collectionDate, model.getCollectionDate());
            assertEquals(created, model.getCreatedAt());
            assertEquals(updated, model.getUpdatedAt());
        }

        @Test
        @DisplayName("Should handle null timestamp fields")
        void shouldHandleNullTimestampFields() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.BLOG)
                    .title("Blog")
                    .slug("blog")
                    .collectionDate(null)
                    .createdAt(null)
                    .updatedAt(null)
                    .build();

            assertNotNull(model);
            assertNull(model.getCollectionDate());
            assertNull(model.getCreatedAt());
            assertNull(model.getUpdatedAt());
        }
    }
}