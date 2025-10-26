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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ContentCollectionPageDTO
 * Tests pagination-specific validation annotations, required fields, and content block handling
 */
class ContentCollectionPageDTOTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    /**
     * Creates a valid default builder for ContentCollectionPageDTO with all required fields populated
     */
    private static ContentCollectionPageDTO.ContentCollectionPageDTOBuilder<?, ?> defaultValidBuilder() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        return ContentCollectionPageDTO.builder()
                .id(1L)
                .type(CollectionType.PORTFOLIO)
                .title("Test Portfolio")
                .slug("test-portfolio")
                .description("Test description")
                .location("Test Location")
                .collectionDate(today)
                .visible(true)
                .priority(1)
                .isPasswordProtected(false)
                .hasAccess(true)
                .createdAt(now)
                .updatedAt(now)
                .currentPage(1)
                .pageSize(25)
                .totalElements(100)
                .totalPages(4)
                .hasPrevious(false)
                .hasNext(true)
                .isFirst(true)
                .isLast(false)
                .previousPage(null)
                .nextPage(2)
                .imageBlockCount(20)
                .textBlockCount(3)
                .codeBlockCount(1)
                .gifBlockCount(1)
                .contentBlocks(new ArrayList<>());
}

    @Nested
    @DisplayName("Valid Object Creation Tests")
    class ValidObjectCreationTests {

        @Test
        @DisplayName("Should create valid DTO with all fields")
        void shouldCreateValidDTOWithAllFields() {
            ContentCollectionPageDTO dto = defaultValidBuilder().build();
            
            Set<ConstraintViolation<ContentCollectionPageDTO>> violations = validator.validate(dto);
            
            assertTrue(violations.isEmpty(), "Should have no validation errors");
            assertEquals(1L, dto.getId());
            assertEquals(CollectionType.PORTFOLIO, dto.getType());
            assertEquals("Test Portfolio", dto.getTitle());
        }

        @Test
        @DisplayName("Should create valid DTO with minimal required fields")
        void shouldCreateValidDTOWithMinimalFields() {
            ContentCollectionPageDTO dto = ContentCollectionPageDTO.builder()
                    .currentPage(1)
                    .pageSize(25)
                    .totalElements(0)
                    .totalPages(0)
                    .hasPrevious(false)
                    .hasNext(false)
                    .isFirst(true)
                    .isLast(true)
                    .contentBlocks(new ArrayList<>())
                    .build();

            Set<ConstraintViolation<ContentCollectionPageDTO>> violations = validator.validate(dto);
            
            assertTrue(violations.isEmpty(), "Should have no validation errors with minimal fields");
        }
    }

    @Nested
    @DisplayName("Required Field Validation Tests")
    class RequiredFieldValidationTests {

        /**
         * Provides test cases for required pagination fields that should fail when null
         */
        static Stream<TestCase> requiredFieldTestCases() {
            return Stream.of(
                new TestCase("currentPage", builder -> builder.currentPage(null), "Current page is required"),
                new TestCase("pageSize", builder -> builder.pageSize(null), "Page size is required"),
                new TestCase("totalElements", builder -> builder.totalElements(null), "Total elements is required"),
                new TestCase("totalPages", builder -> builder.totalPages(null), "Total pages is required"),
                new TestCase("hasPrevious", builder -> builder.hasPrevious(null), "Has previous page flag is required"),
                new TestCase("hasNext", builder -> builder.hasNext(null), "Has next page flag is required"),
                new TestCase("isFirst", builder -> builder.isFirst(null), "Is first page flag is required"),
                new TestCase("isLast", builder -> builder.isLast(null), "Is last page flag is required"),
                new TestCase("contentBlocks", builder -> builder.contentBlocks(null), "Content blocks list is required")
            );
        }

        @ParameterizedTest(name = "Should fail validation when {0} is null")
        @MethodSource("requiredFieldTestCases")
        @DisplayName("Should fail validation when required field is null")
        void shouldFailValidationWhenRequiredFieldIsNull(TestCase testCase) {
            ContentCollectionPageDTO dto = testCase.applyToBuilder(defaultValidBuilder()).build();
            
            Set<ConstraintViolation<ContentCollectionPageDTO>> violations = validator.validate(dto);
            
            assertFalse(violations.isEmpty(), "Should have validation errors when " + testCase.fieldName + " is null");
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains(testCase.expectedMessage)),
                "Should contain expected error message: " + testCase.expectedMessage);
        }
    }

    @Nested
    @DisplayName("Boundary Value Validation Tests")
    class BoundaryValueValidationTests {

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -10})
        @DisplayName("Should reject currentPage below minimum")
        void shouldRejectCurrentPageBelowMin(int invalidValue) {
            ContentCollectionPageDTO dto = defaultValidBuilder()
                    .currentPage(invalidValue)
                    .build();

            Set<ConstraintViolation<ContentCollectionPageDTO>> violations = validator.validate(dto);
            
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Current page must be 1 or greater")));
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -10})
        @DisplayName("Should reject pageSize below minimum")
        void shouldRejectPageSizeBelowMin(int invalidValue) {
            ContentCollectionPageDTO dto = defaultValidBuilder()
                    .pageSize(invalidValue)
                    .build();

            Set<ConstraintViolation<ContentCollectionPageDTO>> violations = validator.validate(dto);
            
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Page size must be 1 or greater")));
        }

        @ParameterizedTest
        @ValueSource(ints = {101, 200, 1000})
        @DisplayName("Should reject pageSize above maximum")
        void shouldRejectPageSizeAboveMax(int invalidValue) {
            ContentCollectionPageDTO dto = defaultValidBuilder()
                    .pageSize(invalidValue)
                    .build();

            Set<ConstraintViolation<ContentCollectionPageDTO>> violations = validator.validate(dto);
            
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Page size cannot exceed 100")));
        }

        @Test
        @DisplayName("Should accept boundary values for pagination fields")
        void shouldAcceptBoundaryValues() {
            ContentCollectionPageDTO dto = defaultValidBuilder()
                    .currentPage(1)      // minimum
                    .pageSize(1)         // minimum
                    .totalElements(0)    // minimum
                    .totalPages(0)       // minimum
                    .build();

            Set<ConstraintViolation<ContentCollectionPageDTO>> violations = validator.validate(dto);
            
            assertTrue(violations.isEmpty(), "Should accept minimum boundary values");

            // Test maximum pageSize
            dto = defaultValidBuilder()
                    .pageSize(100)       // maximum
                    .build();

            violations = validator.validate(dto);
            assertTrue(violations.isEmpty(), "Should accept maximum pageSize");
        }
    }

    @Nested
    @DisplayName("Navigation Logic Tests")
    class NavigationLogicTests {

        @Test
        @DisplayName("Should handle navigation for middle page")
        void shouldHandleNavigationForMiddlePage() {
            ContentCollectionPageDTO dto = defaultValidBuilder()
                    .currentPage(2)
                    .totalPages(5)
                    .hasPrevious(true)
                    .hasNext(true)
                    .isFirst(false)
                    .isLast(false)
                    .previousPage(1)
                    .nextPage(3)
                    .build();

            Set<ConstraintViolation<ContentCollectionPageDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            
            assertEquals(2, dto.getCurrentPage());
            assertTrue(dto.getHasPrevious());
            assertTrue(dto.getHasNext());
            assertFalse(dto.getIsFirst());
            assertFalse(dto.getIsLast());
        }

        @Test
        @DisplayName("Should handle navigation for first page")
        void shouldHandleNavigationForFirstPage() {
            ContentCollectionPageDTO dto = defaultValidBuilder()
                    .currentPage(1)
                    .totalPages(5)
                    .hasPrevious(false)
                    .hasNext(true)
                    .isFirst(true)
                    .isLast(false)
                    .previousPage(null)
                    .nextPage(2)
                    .build();

            Set<ConstraintViolation<ContentCollectionPageDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            
            assertEquals(1, dto.getCurrentPage());
            assertFalse(dto.getHasPrevious());
            assertTrue(dto.getHasNext());
            assertTrue(dto.getIsFirst());
            assertFalse(dto.getIsLast());
        }

        @Test
        @DisplayName("Should handle navigation for single page")
        void shouldHandleNavigationForSinglePage() {
            ContentCollectionPageDTO dto = defaultValidBuilder()
                    .currentPage(1)
                    .totalPages(1)
                    .hasPrevious(false)
                    .hasNext(false)
                    .isFirst(true)
                    .isLast(true)
                    .previousPage(null)
                    .nextPage(null)
                    .build();

            Set<ConstraintViolation<ContentCollectionPageDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            
            assertEquals(1, dto.getCurrentPage());
            assertFalse(dto.getHasPrevious());
            assertFalse(dto.getHasNext());
            assertTrue(dto.getIsFirst());
            assertTrue(dto.getIsLast());
        }
    }

    @Nested
    @DisplayName("Content Summary Tests")
    class ContentSummaryTests {

        @Test
        @DisplayName("Should accept null content block counts")
        void shouldAcceptNullContentBlockCounts() {
            ContentCollectionPageDTO dto = defaultValidBuilder()
                    .imageBlockCount(null)
                    .textBlockCount(null)
                    .codeBlockCount(null)
                    .gifBlockCount(null)
                    .build();

            Set<ConstraintViolation<ContentCollectionPageDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty(), "Should accept null content block counts");
        }

        @Test
        @DisplayName("Should accept zero content block counts")
        void shouldAcceptZeroContentBlockCounts() {
            ContentCollectionPageDTO dto = defaultValidBuilder()
                    .imageBlockCount(0)
                    .textBlockCount(0)
                    .codeBlockCount(0)
                    .gifBlockCount(0)
                    .build();

            Set<ConstraintViolation<ContentCollectionPageDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty(), "Should accept zero content block counts");
        }
    }

    @Nested
    @DisplayName("Inheritance and Data Annotation Tests")
    class InheritanceAndDataAnnotationTests {

        @Test
        @DisplayName("Should inherit base model validation rules")
        void shouldInheritBaseModelValidationRules() {
            ContentCollectionPageDTO dto = defaultValidBuilder()
                    .title("ab")  // Too short for base model validation
                    .build();

            Set<ConstraintViolation<ContentCollectionPageDTO>> violations = validator.validate(dto);
            
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Title must be between 3 and 100 characters")));
        }

    @Test
    @DisplayName("Should have working equals and hashCode")
    void shouldHaveWorkingEqualsAndHashCode() {
        // Use a fixed timestamp to ensure consistent comparisons
        LocalDateTime fixedTime = LocalDateTime.of(2025, 6, 7, 14, 0, 0);
        LocalDate fixedDate = LocalDate.of(2025, 6, 7);

        ContentCollectionPageDTO dto1 = defaultValidBuilder()
                .collectionDate(fixedDate)
                .createdAt(fixedTime)
                .updatedAt(fixedTime)
                .build();

        ContentCollectionPageDTO dto2 = defaultValidBuilder()
                .collectionDate(fixedDate)
                .createdAt(fixedTime)
                .updatedAt(fixedTime)
                .build();

        ContentCollectionPageDTO dto3 = defaultValidBuilder()
                .collectionDate(fixedDate)
                .createdAt(fixedTime)
                .updatedAt(fixedTime)
                .title("Different Title")
                .build();

        // Test equals
        assertEquals(dto1, dto2, "Objects with same data should be equal");
        assertNotEquals(dto1, dto3, "Objects with different data should not be equal");
        assertNotEquals(dto1, null, "Object should not equal null");
        assertNotEquals(dto1, "string", "Object should not equal different type");

        // Test hashCode
        assertEquals(dto1.hashCode(), dto2.hashCode(), "Equal objects should have same hashCode");

        // Test reflexivity
        assertEquals(dto1, dto1, "Object should equal itself");
    }

        @Test
        @DisplayName("Should have working toString method")
        void shouldHaveWorkingToString() {
            ContentCollectionPageDTO dto = defaultValidBuilder().build();
            String toString = dto.toString();
            
            assertNotNull(toString);
            // Should contain both base class and subclass information
            assertTrue(toString.contains("ContentCollectionPageDTO"));
            assertTrue(toString.contains("currentPage=1"));
            assertTrue(toString.contains("pageSize=25"));
        }
    }

    /**
     * Helper class for parameterized testing of required fields
     */
    private static class TestCase {
        final String fieldName;
        final Consumer<ContentCollectionPageDTO.ContentCollectionPageDTOBuilder> builderModifier;
        final String expectedMessage;

        TestCase(String fieldName, Consumer<ContentCollectionPageDTO.ContentCollectionPageDTOBuilder> builderModifier, String expectedMessage) {
            this.fieldName = fieldName;
            this.builderModifier = builderModifier;
            this.expectedMessage = expectedMessage;
        }

        ContentCollectionPageDTO.ContentCollectionPageDTOBuilder applyToBuilder(ContentCollectionPageDTO.ContentCollectionPageDTOBuilder builder) {
            builderModifier.accept(builder);
            return builder;
        }

        @Override
        public String toString() {
            return fieldName;
        }
    }
}