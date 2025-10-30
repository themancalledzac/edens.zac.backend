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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ContentCollectionModel
 * Tests validation annotations, builder pattern, pagination metadata, and content blocks
 */
class CollectionModelTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Nested
    @DisplayName("Builder Pattern Tests")
    class BuilderPatternTests {

        @Test
        @DisplayName("Should create model with all valid fields using builder")
        void shouldCreateModelWithAllValidFields() {
            LocalDateTime now = LocalDateTime.now();
            LocalDate today = LocalDate.now();
            List<ContentModel> contentBlocks = new ArrayList<>();

            CollectionModel model = CollectionModel.builder()
                    .id(1L)
                    .type(CollectionType.PORTFOLIO)
                    .title("Test Portfolio")
                    .slug("test-portfolio")
                    .description("Test description")
                    .location("Test location")
                    .collectionDate(today)
                    .visible(true)
                    .priority(1)
                    .isPasswordProtected(false)
                    .hasAccess(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .blocksPerPage(30)
                    .totalBlocks(150)
                    .currentPage(1)
                    .totalPages(5)
                    .contentBlocks(contentBlocks)
                    .build();

            assertNotNull(model);
            assertEquals(1L, model.getId());
            assertEquals(CollectionType.PORTFOLIO, model.getType());
            assertEquals("Test Portfolio", model.getTitle());
            assertEquals("test-portfolio", model.getSlug());
            assertEquals("Test description", model.getDescription());
            assertEquals("Test location", model.getLocation());
            assertEquals(today, model.getCollectionDate());
            assertTrue(model.getVisible());
            assertEquals(1, model.getPriority());
            assertNull(model.getCoverImage());
            assertFalse(model.getIsPasswordProtected());
            assertTrue(model.getHasAccess());
            assertEquals(now, model.getCreatedAt());
            assertEquals(now, model.getUpdatedAt());
            assertEquals(30, model.getBlocksPerPage());
            assertEquals(150, model.getTotalBlocks());
            assertEquals(1, model.getCurrentPage());
            assertEquals(5, model.getTotalPages());
            assertEquals(contentBlocks, model.getContentBlocks());
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
            assertNull(model.getBlocksPerPage());
            assertNull(model.getTotalBlocks());
            assertNull(model.getCurrentPage());
            assertNull(model.getTotalPages());
            assertNull(model.getContentBlocks());
        }

        @Test
        @DisplayName("Should create model using no-args constructor")
        void shouldCreateModelWithNoArgsConstructor() {
            CollectionModel model = new CollectionModel();
            
            assertNotNull(model);
            assertNull(model.getId());
            assertNull(model.getType());
            assertNull(model.getTitle());
            assertNull(model.getBlocksPerPage());
            assertNull(model.getTotalBlocks());
            assertNull(model.getCurrentPage());
            assertNull(model.getTotalPages());
            assertNull(model.getContentBlocks());
        }
    }

    @Nested
    @DisplayName("Pagination Validation Tests")
    class PaginationValidationTests {

        @Test
        @DisplayName("Should accept valid pagination metadata")
        void shouldAcceptValidPaginationMetadata() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.ART_GALLERY)
                    .title("Art Gallery")
                    .slug("art-gallery")
                    .blocksPerPage(30)
                    .totalBlocks(150)
                    .currentPage(1)
                    .totalPages(5)
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should reject blocksPerPage below minimum")
        void shouldRejectBlocksPerPageBelowMin() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.BLOG)
                    .title("Blog Post")
                    .slug("blog-post")
                    .blocksPerPage(0) // Invalid - below minimum
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Blocks per page must be 30 or greater")));
        }

        @Test
        @DisplayName("Should accept blocksPerPage at minimum boundary")
        void shouldAcceptBlocksPerPageAtMinBoundary() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.BLOG)
                    .title("Blog Post")
                    .slug("blog-post")
                    .blocksPerPage(30) // Exactly at minimum
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should reject totalBlocks below minimum")
        void shouldRejectTotalBlocksBelowMin() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Portfolio")
                    .slug("portfolio")
                    .totalBlocks(-1) // Invalid - below minimum
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Total blocks must be 0 or greater")));
        }

        @Test
        @DisplayName("Should accept totalBlocks at minimum boundary")
        void shouldAcceptTotalBlocksAtMinBoundary() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Portfolio")
                    .slug("portfolio")
                    .totalBlocks(0) // Exactly at minimum
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should reject currentPage below minimum")
        void shouldRejectCurrentPageBelowMin() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Client Gallery")
                    .slug("client-gallery")
                    .currentPage(0) // Invalid - below minimum
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Current page must be 1 or greater")));
        }

        @Test
        @DisplayName("Should accept currentPage at minimum boundary")
        void shouldAcceptCurrentPageAtMinBoundary() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Client Gallery")
                    .slug("client-gallery")
                    .currentPage(1) // Exactly at minimum
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should reject totalPages below minimum")
        void shouldRejectTotalPagesBelowMin() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.ART_GALLERY)
                    .title("Art Gallery")
                    .slug("art-gallery")
                    .totalPages(-1) // Invalid - below minimum
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Total pages must be 0 or greater")));
        }

        @Test
        @DisplayName("Should accept totalPages at minimum boundary")
        void shouldAcceptTotalPagesAtMinBoundary() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.ART_GALLERY)
                    .title("Art Gallery")
                    .slug("art-gallery")
                    .totalPages(0) // Exactly at minimum
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should accept null pagination fields")
        void shouldAcceptNullPaginationFields() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.BLOG)
                    .title("Blog Post")
                    .slug("blog-post")
                    .blocksPerPage(null)
                    .totalBlocks(null)
                    .currentPage(null)
                    .totalPages(null)
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertTrue(violations.isEmpty());
        }
    }

    @Nested
    @DisplayName("Content Blocks Tests")
    class ContentBlocksTests {

        @Test
        @DisplayName("Should accept valid content blocks list")
        void shouldAcceptValidContentBlocksList() {
            List<ContentModel> contentBlocks = new ArrayList<>();
            // Note: We're not adding actual ContentBlockModel instances since we'd need to validate those separately
            
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Portfolio")
                    .slug("portfolio")
                    .contentBlocks(contentBlocks)
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should accept null content blocks")
        void shouldAcceptNullContentBlocks() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.BLOG)
                    .title("Blog Post")
                    .slug("blog-post")
                    .contentBlocks(null)
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should accept empty content blocks list")
        void shouldAcceptEmptyContentBlocksList() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.ART_GALLERY)
                    .title("Art Gallery")
                    .slug("art-gallery")
                    .contentBlocks(new ArrayList<>())
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertTrue(violations.isEmpty());
        }
    }

    @Nested
    @DisplayName("Inheritance Tests")
    class InheritanceTests {

        @Test
        @DisplayName("Should inherit base model validation rules")
        void shouldInheritBaseModelValidationRules() {
            // Test inherited title validation
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.BLOG)
                    .title("AB") // Too short - should trigger base model validation
                    .slug("valid-slug")
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Title must be between 3 and 100 characters")));
        }

        @Test
        @DisplayName("Should combine base and subclass validation rules")
        void shouldCombineBaseAndSubclassValidationRules() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("AB") // Base validation error
                    .slug("valid-slug")
                    .blocksPerPage(0) // Subclass validation error
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertEquals(2, violations.size()); // Should have both base and subclass validation errors
        }
    }

    @Nested
    @DisplayName("Data Annotation Tests")
    class DataAnnotationTests {

        @Test
        @DisplayName("Should have working equals and hashCode with inheritance")
        void shouldHaveWorkingEqualsAndHashCodeWithInheritance() {
            LocalDateTime now = LocalDateTime.now();
            
            CollectionModel model1 = CollectionModel.builder()
                    .id(1L)
                    .type(CollectionType.BLOG)
                    .title("Test Blog")
                    .slug("test-blog")
                    .createdAt(now)
                    .blocksPerPage(30)
                    .totalBlocks(150)
                    .build();

            CollectionModel model2 = CollectionModel.builder()
                    .id(1L)
                    .type(CollectionType.BLOG)
                    .title("Test Blog")
                    .slug("test-blog")
                    .createdAt(now)
                    .blocksPerPage(30)
                    .totalBlocks(150)
                    .build();

            CollectionModel model3 = CollectionModel.builder()
                    .id(1L)
                    .type(CollectionType.BLOG)
                    .title("Test Blog")
                    .slug("test-blog")
                    .createdAt(now)
                    .blocksPerPage(20) // Different pagination
                    .totalBlocks(150)
                    .build();

            // Test equals
            assertEquals(model1, model2);
            assertNotEquals(model1, model3);

            // Test hashCode consistency
            assertEquals(model1.hashCode(), model2.hashCode());
        }

        @Test
        @DisplayName("Should have working toString method with inheritance")
        void shouldHaveWorkingToStringWithInheritance() {
            CollectionModel model = CollectionModel.builder()
                    .id(1L)
                    .type(CollectionType.PORTFOLIO)
                    .title("Test Portfolio")
                    .slug("test-portfolio")
                    .blocksPerPage(30)
                    .totalBlocks(150)
                    .currentPage(1)
                    .totalPages(5)
                    .build();

            String toString = model.toString();
            
            assertNotNull(toString);
            // Should contain both base class and subclass information
            assertTrue(toString.contains("ContentCollectionModel"));
            assertTrue(toString.contains("id=1"));
            assertTrue(toString.contains("PORTFOLIO"));
            assertTrue(toString.contains("Test Portfolio"));
            assertTrue(toString.contains("blocksPerPage=30"));
            assertTrue(toString.contains("totalBlocks=150"));
        }
    }

    @Nested
    @DisplayName("Type-Specific Tests")
    class TypeSpecificTests {

        @Test
        @DisplayName("Should work with all collection types")
        void shouldWorkWithAllCollectionTypes() {
            for (CollectionType type : CollectionType.values()) {
                CollectionModel model = CollectionModel.builder()
                        .type(type)
                        .title("Test " + type.getDisplayName())
                        .slug("test-" + type.name().toLowerCase())
                        .blocksPerPage(30)
                        .totalBlocks(100)
                        .currentPage(1)
                        .totalPages(4)
                        .build();

                Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
                assertTrue(violations.isEmpty(), "Collection type " + type + " should be valid");
            }
        }

        @Test
        @DisplayName("Should handle client gallery with pagination")
        void shouldHandleClientGalleryWithPagination() {
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Client Wedding Gallery")
                    .slug("client-wedding-gallery")
                    .isPasswordProtected(true)
                    .hasAccess(false)
                    .blocksPerPage(30)
                    .totalBlocks(200)
                    .currentPage(1)
                    .totalPages(8)
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertTrue(violations.isEmpty());
            assertEquals(CollectionType.CLIENT_GALLERY, model.getType());
            assertTrue(model.getIsPasswordProtected());
            assertFalse(model.getHasAccess());
            assertEquals(30, model.getBlocksPerPage());
            assertEquals(200, model.getTotalBlocks());
        }
    }

    @Nested
    @DisplayName("Multiple Validation Errors Tests")
    class MultipleValidationErrorsTests {

        @Test
        @DisplayName("Should capture multiple validation errors from both base and subclass")
        void shouldCaptureMultipleValidationErrorsFromBothBaseAndSubclass() {
            String longTitle = "A".repeat(101); // Base class validation error
            String longDescription = "B".repeat(501); // Base class validation error
            
            CollectionModel model = CollectionModel.builder()
                    .type(CollectionType.BLOG)
                    .title(longTitle) // Error 1: Title too long
                    .slug("AB") // Error 2: Slug too short
                    .description(longDescription) // Error 3: Description too long
                    .blocksPerPage(0) // Error 4: BlocksPerPage below minimum
                    .currentPage(0) // Error 5: CurrentPage below minimum
                    .totalBlocks(-1) // Error 6: TotalBlocks below minimum
                    .build();

            Set<ConstraintViolation<CollectionModel>> violations = validator.validate(model);
            assertEquals(6, violations.size());
        }
    }
}