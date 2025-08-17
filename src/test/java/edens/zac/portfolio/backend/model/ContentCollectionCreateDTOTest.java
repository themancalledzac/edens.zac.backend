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

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ContentCollectionCreateDTO
 * Tests creation-specific validation annotations, required fields, and password handling
 */
class ContentCollectionCreateDTOTest {

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
        @DisplayName("Should create DTO with all valid fields using builder")
        void shouldCreateDTOWithAllValidFields() {
            LocalDateTime now = LocalDateTime.now();
            ContentCollectionCreateDTO dto = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Wedding Album - Smith Family")
                    .slug("wedding-smith-2024")
                    .description("Beautiful wedding moments captured")
                    .location("Arches National Park")
                    .collectionDate(now)
                    .visible(true)
                    .priority(1)
                    .coverImageUrl("https://example.com/cover.jpg")
                    .isPasswordProtected(true)
                    .hasAccess(false)
                    .configJson("{\"theme\": \"elegant\"}")
                    .password("SecurePass123!")
                    .blocksPerPage(25)
                    .build();

            assertNotNull(dto);
            assertEquals(CollectionType.CLIENT_GALLERY, dto.getType());
            assertEquals("Wedding Album - Smith Family", dto.getTitle());
            assertEquals("wedding-smith-2024", dto.getSlug());
            assertEquals("Beautiful wedding moments captured", dto.getDescription());
            assertEquals("Arches National Park", dto.getLocation());
            assertEquals(now, dto.getCollectionDate());
            assertTrue(dto.getVisible());
            assertEquals(1, dto.getPriority());
            assertEquals("https://example.com/cover.jpg", dto.getCoverImageUrl());
            assertTrue(dto.getIsPasswordProtected());
            assertFalse(dto.getHasAccess());
            assertEquals("{\"theme\": \"elegant\"}", dto.getConfigJson());
            assertEquals("SecurePass123!", dto.getPassword());
            assertEquals(25, dto.getBlocksPerPage());
        }

        @Test
        @DisplayName("Should create DTO with minimal required fields")
        void shouldCreateDTOWithMinimalFields() {
            ContentCollectionCreateDTO dto = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.BLOG)
                    .title("Daily Moments")
                    .visible(true)
                    .build();

            assertNotNull(dto);
            assertEquals(CollectionType.BLOG, dto.getType());
            assertEquals("Daily Moments", dto.getTitle());
            assertTrue(dto.getVisible());
            assertNull(dto.getSlug());
            assertNull(dto.getDescription());
            assertNull(dto.getPassword());
            assertNull(dto.getBlocksPerPage());
        }

        @Test
        @DisplayName("Should create DTO using no-args constructor")
        void shouldCreateDTOWithNoArgsConstructor() {
            ContentCollectionCreateDTO dto = new ContentCollectionCreateDTO();
            
            assertNotNull(dto);
            assertNull(dto.getType());
            assertNull(dto.getTitle());
            assertNull(dto.getVisible());
            assertNull(dto.getPassword());
            assertNull(dto.getBlocksPerPage());
        }
    }

    @Nested
    @DisplayName("Required Fields Validation Tests")
    class RequiredFieldsValidationTests {

        @Test
        @DisplayName("Should pass validation with all required fields")
        void shouldPassValidationWithAllRequiredFields() {
            ContentCollectionCreateDTO dto = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Photography Portfolio")
                    .visible(true)
                    .build();

            Set<ConstraintViolation<ContentCollectionCreateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should fail validation when type is null")
        void shouldFailValidationWhenTypeIsNull() {
            ContentCollectionCreateDTO dto = ContentCollectionCreateDTO.builder()
                    .type(null) // Required field missing
                    .title("Portfolio")
                    .visible(true)
                    .build();

            Set<ConstraintViolation<ContentCollectionCreateDTO>> violations = validator.validate(dto);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Collection type is required")));
        }

        @Test
        @DisplayName("Should fail validation when title is null")
        void shouldFailValidationWhenTitleIsNull() {
            ContentCollectionCreateDTO dto = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.BLOG)
                    .title(null) // Required field missing
                    .visible(true)
                    .build();

            Set<ConstraintViolation<ContentCollectionCreateDTO>> violations = validator.validate(dto);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Title is required")));
        }

        @Test
        @DisplayName("Should fail validation when title is blank")
        void shouldFailValidationWhenTitleIsBlank() {
            ContentCollectionCreateDTO dto = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.ART_GALLERY)
                    .title("   ") // Blank title
                    .visible(true)
                    .build();

            Set<ConstraintViolation<ContentCollectionCreateDTO>> violations = validator.validate(dto);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Title is required")));
        }

        @Test
        @DisplayName("Should fail validation when visible is null")
        void shouldFailValidationWhenVisibleIsNull() {
            ContentCollectionCreateDTO dto = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Client Gallery")
                    .visible(null) // Required field missing
                    .build();

            Set<ConstraintViolation<ContentCollectionCreateDTO>> violations = validator.validate(dto);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Visibility is required")));
        }
    }

    @Nested
    @DisplayName("Password Validation Tests")
    class PasswordValidationTests {

        @Test
        @DisplayName("Should accept valid password")
        void shouldAcceptValidPassword() {
            ContentCollectionCreateDTO dto = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Client Gallery")
                    .visible(false)
                    .password("MySecurePassword123!")
                    .build();

            Set<ConstraintViolation<ContentCollectionCreateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should accept null password")
        void shouldAcceptNullPassword() {
            ContentCollectionCreateDTO dto = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Public Portfolio")
                    .visible(true)
                    .password(null) // Should be valid for non-protected collections
                    .build();

            Set<ConstraintViolation<ContentCollectionCreateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should reject password that is too short")
        void shouldRejectPasswordTooShort() {
            ContentCollectionCreateDTO dto = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Client Gallery")
                    .visible(false)
                    .password("short") // Only 5 characters
                    .build();

            Set<ConstraintViolation<ContentCollectionCreateDTO>> violations = validator.validate(dto);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Password must be between 8 and 100 characters")));
        }

        @Test
        @DisplayName("Should reject password that is too long")
        void shouldRejectPasswordTooLong() {
            String longPassword = "A".repeat(101); // 101 characters
            
            ContentCollectionCreateDTO dto = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Client Gallery")
                    .visible(false)
                    .password(longPassword)
                    .build();

            Set<ConstraintViolation<ContentCollectionCreateDTO>> violations = validator.validate(dto);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Password must be between 8 and 100 characters")));
        }

        @Test
        @DisplayName("Should accept password at minimum length boundary")
        void shouldAcceptPasswordAtMinBoundary() {
            ContentCollectionCreateDTO dto = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Client Gallery")
                    .visible(false)
                    .password("12345678") // Exactly 8 characters
                    .build();

            Set<ConstraintViolation<ContentCollectionCreateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should accept password at maximum length boundary")
        void shouldAcceptPasswordAtMaxBoundary() {
            String maxPassword = "A".repeat(100); // Exactly 100 characters
            
            ContentCollectionCreateDTO dto = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Client Gallery")
                    .visible(false)
                    .password(maxPassword)
                    .build();

            Set<ConstraintViolation<ContentCollectionCreateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
        }
    }

    @Nested
    @DisplayName("Pagination Settings Validation Tests")
    class PaginationSettingsValidationTests {

        @Test
        @DisplayName("Should accept valid blocksPerPage")
        void shouldAcceptValidBlocksPerPage() {
            ContentCollectionCreateDTO dto = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.ART_GALLERY)
                    .title("Art Gallery")
                    .visible(true)
                    .blocksPerPage(30)
                    .build();

            Set<ConstraintViolation<ContentCollectionCreateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should accept null blocksPerPage")
        void shouldAcceptNullBlocksPerPage() {
            ContentCollectionCreateDTO dto = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.BLOG)
                    .title("Blog")
                    .visible(true)
                    .blocksPerPage(null) // Will use service defaults
                    .build();

            Set<ConstraintViolation<ContentCollectionCreateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should reject blocksPerPage below minimum")
        void shouldRejectBlocksPerPageBelowMin() {
            ContentCollectionCreateDTO dto = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Portfolio")
                    .visible(true)
                    .blocksPerPage(0) // Invalid - below minimum
                    .build();

            Set<ConstraintViolation<ContentCollectionCreateDTO>> violations = validator.validate(dto);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Blocks per page must be 1 or greater")));
        }

        @Test
        @DisplayName("Should accept blocksPerPage at minimum boundary")
        void shouldAcceptBlocksPerPageAtMinBoundary() {
            ContentCollectionCreateDTO dto = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.BLOG)
                    .title("Blog")
                    .visible(true)
                    .blocksPerPage(1) // Exactly at minimum
                    .build();

            Set<ConstraintViolation<ContentCollectionCreateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
        }
    }


    @Nested
    @DisplayName("Collection Type Specific Tests")
    class CollectionTypeSpecificTests {

        @Test
        @DisplayName("Should handle blog creation with text content")
        void shouldHandleBlogCreationWithTextContent() {
            ContentCollectionCreateDTO dto = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.BLOG)
                    .title("Arches Adventure")
                    .visible(true)
                    .description("Daily moments from my photography trip")
                    .location("Arches National Park, Utah")
                    .priority(1)
                    .blocksPerPage(20)
                    .build();

            Set<ConstraintViolation<ContentCollectionCreateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            assertEquals(CollectionType.BLOG, dto.getType());
        }

        @Test
        @DisplayName("Should handle client gallery creation with password")
        void shouldHandleClientGalleryCreationWithPassword() {
            ContentCollectionCreateDTO dto = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Wedding - Johnson Family")
                    .visible(false) // Client galleries typically start private
                    .description("Beautiful wedding ceremony and reception")
                    .location("Grand Canyon National Park")
                    .isPasswordProtected(true)
                    .password("Johnson2024Wedding!")
                    .blocksPerPage(50)
                    .build();

            Set<ConstraintViolation<ContentCollectionCreateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            assertEquals(CollectionType.CLIENT_GALLERY, dto.getType());
            assertFalse(dto.getVisible());
            assertTrue(dto.getIsPasswordProtected());
            assertEquals("Johnson2024Wedding!", dto.getPassword());
        }

        @Test
        @DisplayName("Should handle portfolio creation")
        void shouldHandlePortfolioCreation() {
            ContentCollectionCreateDTO dto = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Wedding Photography Portfolio")
                    .visible(true)
                    .description("Showcasing my best wedding photography work")
                    .priority(1)
                    .coverImageUrl("https://example.com/portfolio-cover.jpg")
                    .blocksPerPage(25)
                    .build();

            Set<ConstraintViolation<ContentCollectionCreateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            assertEquals(CollectionType.PORTFOLIO, dto.getType());
        }

        @Test
        @DisplayName("Should handle art gallery creation")
        void shouldHandleArtGalleryCreation() {
            ContentCollectionCreateDTO dto = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.ART_GALLERY)
                    .title("Urban Landscapes Collection")
                    .visible(true)
                    .description("Capturing the beauty of city architecture")
                    .location("Downtown Salt Lake City")
                    .priority(2)
                    .blocksPerPage(15) // Fewer per page for artistic presentation
                    .build();

            Set<ConstraintViolation<ContentCollectionCreateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            assertEquals(CollectionType.ART_GALLERY, dto.getType());
            assertEquals(15, dto.getBlocksPerPage());
        }
    }

    @Nested
    @DisplayName("Inheritance Tests")
    class InheritanceTests {

        @Test
        @DisplayName("Should inherit base model validation rules")
        void shouldInheritBaseModelValidationRules() {
            // Test inherited slug validation from base model
            ContentCollectionCreateDTO dto = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.BLOG)
                    .title("Valid Title")
                    .visible(true)
                    .slug("AB") // Too short - should trigger base model validation
                    .build();

            Set<ConstraintViolation<ContentCollectionCreateDTO>> violations = validator.validate(dto);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Slug must be between 3 and 150 characters")));
        }

        @Test
        @DisplayName("Should override base model field validation with stricter rules")
        void shouldOverrideBaseModelFieldValidationWithStricterRules() {
            // Title is @Size in base but @NotBlank + @Size in CreateDTO (stricter)
            ContentCollectionCreateDTO dto = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("") // Empty string - should fail @NotBlank in CreateDTO
                    .visible(true)
                    .build();

            Set<ConstraintViolation<ContentCollectionCreateDTO>> violations = validator.validate(dto);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Title is required")));
        }
    }

    @Nested
    @DisplayName("Data Annotation Tests")
    class DataAnnotationTests {

        @Test
        @DisplayName("Should have working equals and hashCode with inheritance")
        void shouldHaveWorkingEqualsAndHashCodeWithInheritance() {
            ContentCollectionCreateDTO dto1 = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.BLOG)
                    .title("Test Blog")
                    .visible(true)
                    .password("password123")
                    .blocksPerPage(30)
                    .build();

            ContentCollectionCreateDTO dto2 = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.BLOG)
                    .title("Test Blog")
                    .visible(true)
                    .password("password123")
                    .blocksPerPage(30)
                    .build();

            ContentCollectionCreateDTO dto3 = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.BLOG)
                    .title("Test Blog")
                    .visible(true)
                    .password("differentpass") // Different password
                    .blocksPerPage(30)
                    .build();

            // Test equals
            assertEquals(dto1, dto2);
            assertNotEquals(dto1, dto3);

            // Test hashCode consistency
            assertEquals(dto1.hashCode(), dto2.hashCode());
        }

        @Test
        @DisplayName("Should have working toString method with inheritance")
        void shouldHaveWorkingToStringWithInheritance() {
            ContentCollectionCreateDTO dto = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Client Gallery")
                    .visible(false)
                    .password("secretpass")
                    .blocksPerPage(25)
                    .build();

            String toString = dto.toString();
            
            assertNotNull(toString);
            assertTrue(toString.contains("ContentCollectionCreateDTO"));
            assertTrue(toString.contains("CLIENT_GALLERY"));
            assertTrue(toString.contains("Client Gallery"));
            assertTrue(toString.contains("visible=false"));
            assertTrue(toString.contains("blocksPerPage=25"));
        }
    }

    @Nested
    @DisplayName("Multiple Validation Errors Tests")
    class MultipleValidationErrorsTests {

        @Test
        @DisplayName("Should capture multiple validation errors from both base and subclass")
        void shouldCaptureMultipleValidationErrorsFromBothBaseAndSubclass() {
            String longDescription = "A".repeat(501); // Base class validation error
            
            ContentCollectionCreateDTO dto = ContentCollectionCreateDTO.builder()
                    .type(null) // Error 1: Required field missing
                    .title("") // Error 2: NotBlank validation
                    .visible(null) // Error 3: Required field missing
                    .slug("AB") // Error 4: Slug too short (base validation)
                    .description(longDescription) // Error 5: Description too long (base validation)
                    .password("short") // Error 6: Password too short
                    .blocksPerPage(0) // Error 7: BlocksPerPage below minimum
                    .build();

            Set<ConstraintViolation<ContentCollectionCreateDTO>> violations = validator.validate(dto);
            assertEquals(8, violations.size());
        }

        @Test
        @DisplayName("Should validate complex client gallery creation scenario")
        void shouldValidateComplexClientGalleryCreationScenario() {
            ContentCollectionCreateDTO dto = ContentCollectionCreateDTO.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("") // Error 1: Required field blank
                    .visible(null) // Error 2: Required field missing
                    .slug("a") // Error 3: Slug too short
                    .password("weak") // Error 4: Password too short
                    .blocksPerPage(-1) // Error 5: Below minimum
                    .build();

            Set<ConstraintViolation<ContentCollectionCreateDTO>> violations = validator.validate(dto);
            assertEquals(6, violations.size());
        }
    }
}