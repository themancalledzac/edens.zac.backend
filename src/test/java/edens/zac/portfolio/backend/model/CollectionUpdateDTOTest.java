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
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CollectionUpdateDTO
 * Tests partial update validation, password handling, and content operations
 */
class CollectionUpdateDTOTest {

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
            LocalDate today = LocalDate.now();
            List<CollectionUpdateDTO.ContentReorderOperation> reorderOps = new ArrayList<>();
            List<Long> idsToRemove = Arrays.asList(1L, 2L, 3L);
            List<String> newTextContent = Arrays.asList("New text content 1", "New text content 2");
            List<String> newCodeContent = Arrays.asList("console.log('Hello');", "function test() {}");

            CollectionUpdateDTO dto = CollectionUpdateDTO.builder()
                    .id(1L)
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Updated Client Gallery")
                    .slug("updated-client-gallery")
                    .description("Updated professional client gallery")
                    .location("Updated Location")
                    .collectionDate(today)
                    .visible(true)
                    .isPasswordProtected(true)
                    .hasAccess(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .password("newpassword123")
                    .contentPerPage(25)
                    .reorderOperations(reorderOps)
                    .contentIdsToRemove(idsToRemove)
                    .build();

            assertNotNull(dto);
            assertEquals(1L, dto.getId());
            assertEquals(CollectionType.CLIENT_GALLERY, dto.getType());
            assertEquals("Updated Client Gallery", dto.getTitle());
            assertEquals("updated-client-gallery", dto.getSlug());
            assertEquals("Updated professional client gallery", dto.getDescription());
            assertEquals("Updated Location", dto.getLocation());
            assertEquals(today, dto.getCollectionDate());
            assertTrue(dto.getVisible());
            assertTrue(dto.getIsPasswordProtected());
            assertTrue(dto.getHasAccess());
            assertEquals(now, dto.getCreatedAt());
            assertEquals(now, dto.getUpdatedAt());
            assertEquals("newpassword123", dto.getPassword());
            assertEquals(25, dto.getContentPerPage());
            assertEquals(reorderOps, dto.getReorderOperations());
            assertEquals(idsToRemove, dto.getContentIdsToRemove());
        }

        @Test
        @DisplayName("Should create DTO with minimal fields for partial update")
        void shouldCreateDTOWithMinimalFields() {
            CollectionUpdateDTO dto = CollectionUpdateDTO.builder()
                    .id(1L)
                    .title("Updated Title")
                    .build();

            assertNotNull(dto);
            assertEquals(1L, dto.getId());
            assertEquals("Updated Title", dto.getTitle());
            assertNull(dto.getType());
            assertNull(dto.getSlug());
            assertNull(dto.getPassword());
            assertNull(dto.getContentPerPage());
            assertNull(dto.getReorderOperations());
            assertNull(dto.getContentIdsToRemove());
        }

        @Test
        @DisplayName("Should create DTO with only content operations")
        void shouldCreateDTOWithOnlyContentOperations() {
            List<Long> idsToRemove = Arrays.asList(5L, 10L);
            List<String> newTextContent = Arrays.asList("Just added this text");

            CollectionUpdateDTO dto = CollectionUpdateDTO.builder()
                    .id(1L)
                    .contentIdsToRemove(idsToRemove)
                    .build();

            assertNotNull(dto);
            assertEquals(1L, dto.getId());
            assertEquals(idsToRemove, dto.getContentIdsToRemove());
            assertNull(dto.getTitle());
            assertNull(dto.getPassword());
            assertNull(dto.getReorderOperations());
        }

        @Test
        @DisplayName("Should create DTO using no-args constructor")
        void shouldCreateDTOWithNoArgsConstructor() {
            CollectionUpdateDTO dto = new CollectionUpdateDTO();

            assertNotNull(dto);
            assertNull(dto.getId());
            assertNull(dto.getType());
            assertNull(dto.getTitle());
            assertNull(dto.getPassword());
            assertNull(dto.getContentPerPage());
            assertNull(dto.getReorderOperations());
            assertNull(dto.getContentIdsToRemove());
        }
    }

    @Nested
    @DisplayName("Password Validation Tests")
    class PasswordValidationTests {

        @Test
        @DisplayName("Should accept valid password")
        void shouldAcceptValidPassword() {
            CollectionUpdateDTO dto = CollectionUpdateDTO.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Client Gallery")
                    .slug("client-gallery")
                    .password("validpass123")
                    .build();

            Set<ConstraintViolation<CollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            assertEquals("validpass123", dto.getPassword());
        }

        @Test
        @DisplayName("Should accept null password for partial updates")
        void shouldAcceptNullPassword() {
            CollectionUpdateDTO dto = CollectionUpdateDTO.builder()
                    .type(CollectionType.BLOG)
                    .title("Blog Update")
                    .slug("blog-update")
                    .password(null) // Null means no password change
                    .build();

            Set<ConstraintViolation<CollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            assertNull(dto.getPassword());
        }

        @Test
        @DisplayName("Should reject password that is too short")
        void shouldRejectPasswordTooShort() {
            CollectionUpdateDTO dto = CollectionUpdateDTO.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Client Gallery")
                    .slug("client-gallery")
                    .password("short") // Only 5 characters
                    .build();

            Set<ConstraintViolation<CollectionUpdateDTO>> violations = validator.validate(dto);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Password must be between 8 and 100 characters")));
        }

        @Test
        @DisplayName("Should reject password that is too long")
        void shouldRejectPasswordTooLong() {
            String longPassword = "a".repeat(101); // 101 characters

            CollectionUpdateDTO dto = CollectionUpdateDTO.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Client Gallery")
                    .slug("client-gallery")
                    .password(longPassword)
                    .build();

            Set<ConstraintViolation<CollectionUpdateDTO>> violations = validator.validate(dto);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Password must be between 8 and 100 characters")));
        }

        @Test
        @DisplayName("Should accept password at boundary lengths")
        void shouldAcceptPasswordAtBoundaryLengths() {
            // Test minimum boundary
            CollectionUpdateDTO minDto = CollectionUpdateDTO.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Client Gallery")
                    .slug("client-gallery")
                    .password("12345678") // Exactly 8 characters
                    .build();

            Set<ConstraintViolation<CollectionUpdateDTO>> minViolations = validator.validate(minDto);
            assertTrue(minViolations.isEmpty());

            // Test maximum boundary
            String maxPassword = "a".repeat(100); // Exactly 100 characters
            CollectionUpdateDTO maxDto = CollectionUpdateDTO.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Client Gallery")
                    .slug("client-gallery")
                    .password(maxPassword)
                    .build();

            Set<ConstraintViolation<CollectionUpdateDTO>> maxViolations = validator.validate(maxDto);
            assertTrue(maxViolations.isEmpty());
        }
    }

    @Nested
    @DisplayName("content Per Page Validation Tests")
    class ContentPerPageValidationTests {

        @Test
        @DisplayName("Should accept valid contentPerPage")
        void shouldAcceptValidContentPerPage() {
            CollectionUpdateDTO dto = CollectionUpdateDTO.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Portfolio")
                    .slug("portfolio")
                    .contentPerPage(30)
                    .build();

            Set<ConstraintViolation<CollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            assertEquals(30, dto.getContentPerPage());
        }

        @Test
        @DisplayName("Should accept null contentPerPage for partial updates")
        void shouldAcceptNullContentPerPage() {
            CollectionUpdateDTO dto = CollectionUpdateDTO.builder()
                    .type(CollectionType.ART_GALLERY)
                    .title("Art Gallery")
                    .slug("art-gallery")
                    .contentPerPage(null) // Null means no change
                    .build();

            Set<ConstraintViolation<CollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            assertNull(dto.getContentPerPage());
        }

        @Test
        @DisplayName("Should reject contentPerPage below minimum")
        void shouldRejectContentPerPageBelowMin() {
            CollectionUpdateDTO dto = CollectionUpdateDTO.builder()
                    .type(CollectionType.BLOG)
                    .title("Blog")
                    .slug("blog")
                    .contentPerPage(0) // Invalid - below minimum
                    .build();

            Set<ConstraintViolation<CollectionUpdateDTO>> violations = validator.validate(dto);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Content per page must be 1 or greater")));
        }

        @Test
        @DisplayName("Should accept contentPerPage at minimum boundary")
        void shouldAcceptContentPerPageAtMinBoundary() {
            CollectionUpdateDTO dto = CollectionUpdateDTO.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Portfolio")
                    .slug("portfolio")
                    .contentPerPage(1) // Exactly at minimum
                    .build();

            Set<ConstraintViolation<CollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
        }
    }

    @Nested
    @DisplayName("Content Operations Tests")
    class ContentOperationsTests {

        @Test
        @DisplayName("Should accept valid reorder operations")
        void shouldAcceptValidReorderOperations() {
            List<CollectionUpdateDTO.ContentReorderOperation> reorderOps = Arrays.asList(
                    CollectionUpdateDTO.ContentReorderOperation.builder()
                            .contentId(1L)
                            .newOrderIndex(0)
                            .build(),
                    CollectionUpdateDTO.ContentReorderOperation.builder()
                            .contentId(2L)
                            .newOrderIndex(1)
                            .build()
            );

            CollectionUpdateDTO dto = CollectionUpdateDTO.builder()
                    .type(CollectionType.ART_GALLERY)
                    .title("Art Gallery")
                    .slug("art-gallery")
                    .reorderOperations(reorderOps)
                    .build();

            Set<ConstraintViolation<CollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            assertEquals(2, dto.getReorderOperations().size());
        }

        @Test
        @DisplayName("Should accept empty content operation lists")
        void shouldAcceptEmptyContentOperationLists() {
            CollectionUpdateDTO dto = CollectionUpdateDTO.builder()
                    .type(CollectionType.BLOG)
                    .title("Blog")
                    .slug("blog")
                    .reorderOperations(new ArrayList<>())
                    .contentIdsToRemove(new ArrayList<>())
                    .build();

            Set<ConstraintViolation<CollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            assertTrue(dto.getReorderOperations().isEmpty());
            assertTrue(dto.getContentIdsToRemove().isEmpty());
        }

        @Test
        @DisplayName("Should accept null content operation lists for partial updates")
        void shouldAcceptNullContentOperationLists() {
            CollectionUpdateDTO dto = CollectionUpdateDTO.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Portfolio")
                    .slug("portfolio")
                    .reorderOperations(null)
                    .contentIdsToRemove(null)
                    .build();

            Set<ConstraintViolation<CollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            assertNull(dto.getReorderOperations());
            assertNull(dto.getContentIdsToRemove());
        }

        @Test
        @DisplayName("Should accept valid content IDs to remove")
        void shouldAcceptValidContentIdsToRemove() {
            List<Long> idsToRemove = Arrays.asList(1L, 5L, 10L, 15L);

            CollectionUpdateDTO dto = CollectionUpdateDTO.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Client Gallery")
                    .slug("client-gallery")
                    .contentIdsToRemove(idsToRemove)
                    .build();

            Set<ConstraintViolation<CollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            assertEquals(4, dto.getContentIdsToRemove().size());
            assertTrue(dto.getContentIdsToRemove().containsAll(Arrays.asList(1L, 5L, 10L, 15L)));
        }

        @Nested
        @DisplayName("Content Reorder Operation Tests")
        class ContentReorderOperationTests {

            @Test
            @DisplayName("Should create valid reorder operation")
            void shouldCreateValidReorderOperation() {
                CollectionUpdateDTO.ContentReorderOperation operation =
                        CollectionUpdateDTO.ContentReorderOperation.builder()
                                .contentId(5L)
                                .newOrderIndex(2)
                                .build();

                Set<ConstraintViolation<CollectionUpdateDTO.ContentReorderOperation>> violations =
                        validator.validate(operation);
                assertTrue(violations.isEmpty());
                assertEquals(5L, operation.getContentId());
                assertEquals(2, operation.getNewOrderIndex());
            }

            @Test
            @DisplayName("Should accept null contentId when oldOrderIndex is provided")
            void shouldAcceptNullContentIdWhenOldOrderIndexProvided() {
                CollectionUpdateDTO.ContentReorderOperation operation =
                        CollectionUpdateDTO.ContentReorderOperation.builder()
                                .contentId(null) // Can be null if oldOrderIndex is set
                                .oldOrderIndex(0)
                                .newOrderIndex(1)
                                .build();

                Set<ConstraintViolation<CollectionUpdateDTO.ContentReorderOperation>> violations =
                        validator.validate(operation);
                assertTrue(violations.isEmpty());
            }

            @Test
            @DisplayName("Should fail validation when newOrderIndex is negative")
            void shouldFailValidationWhenNewOrderIndexIsNegative() {
                CollectionUpdateDTO.ContentReorderOperation operation =
                        CollectionUpdateDTO.ContentReorderOperation.builder()
                                .contentId(3L)
                                .newOrderIndex(-1) // Invalid - below minimum
                                .build();

                Set<ConstraintViolation<CollectionUpdateDTO.ContentReorderOperation>> violations =
                        validator.validate(operation);
                assertFalse(violations.isEmpty());
                assertTrue(violations.stream()
                        .anyMatch(v -> v.getMessage().contains("New order index must be 0 or greater")));
            }

            @Test
            @DisplayName("Should accept newOrderIndex at minimum boundary")
            void shouldAcceptNewOrderIndexAtMinBoundary() {
                CollectionUpdateDTO.ContentReorderOperation operation =
                        CollectionUpdateDTO.ContentReorderOperation.builder()
                                .contentId(7L)
                                .newOrderIndex(0) // Exactly at minimum
                                .build();

                Set<ConstraintViolation<CollectionUpdateDTO.ContentReorderOperation>> violations =
                        validator.validate(operation);
                assertTrue(violations.isEmpty());
            }

            @Test
            @DisplayName("Should create reorder operation using no-args constructor")
            void shouldCreateReorderOperationWithNoArgsConstructor() {
                CollectionUpdateDTO.ContentReorderOperation operation =
                        new CollectionUpdateDTO.ContentReorderOperation();

                assertNotNull(operation);
                assertNull(operation.getContentId());
                assertNull(operation.getNewOrderIndex());
            }

            @Test
            @DisplayName("Should create reorder operation using all-args constructor")
            void shouldCreateReorderOperationWithAllArgsConstructor() {
                CollectionUpdateDTO.ContentReorderOperation operation =
                        new CollectionUpdateDTO.ContentReorderOperation(9L, 4);

                assertNotNull(operation);
                assertEquals(9L, operation.getContentId());
                assertEquals(4, operation.getNewOrderIndex());
            }
        }

        @Nested
        @DisplayName("Collection Type Specific Tests")
        class CollectionTypeSpecificTests {

            @Test
            @DisplayName("Should handle client gallery password update")
            void shouldHandleClientGalleryPasswordUpdate() {
                CollectionUpdateDTO dto = CollectionUpdateDTO.builder()
                        .type(CollectionType.CLIENT_GALLERY)
                        .title("Johnson Wedding Update")
                        .slug("johnson-wedding")
                        .isPasswordProtected(true)
                        .password("newpassword2024")
                        .contentPerPage(50)
                        .build();

                Set<ConstraintViolation<CollectionUpdateDTO>> violations = validator.validate(dto);
                assertTrue(violations.isEmpty());
                assertEquals(CollectionType.CLIENT_GALLERY, dto.getType());
                assertTrue(dto.getIsPasswordProtected());
                assertEquals("newpassword2024", dto.getPassword());
                assertEquals(50, dto.getContentPerPage());
            }

            @Test
            @DisplayName("Should handle portfolio reordering")
            void shouldHandlePortfolioReordering() {
                List<CollectionUpdateDTO.ContentReorderOperation> reorderOps = Arrays.asList(
                        CollectionUpdateDTO.ContentReorderOperation.builder()
                                .contentId(10L)
                                .newOrderIndex(0)
                                .build(),
                        CollectionUpdateDTO.ContentReorderOperation.builder()
                                .contentId(5L)
                                .newOrderIndex(1)
                                .build(),
                        CollectionUpdateDTO.ContentReorderOperation.builder()
                                .contentId(15L)
                                .newOrderIndex(2)
                                .build()
                );

                CollectionUpdateDTO dto = CollectionUpdateDTO.builder()
                        .type(CollectionType.PORTFOLIO)
                        .title("Wedding Portfolio - Reordered")
                        .slug("wedding-portfolio")
                        .reorderOperations(reorderOps)
                        .build();

                Set<ConstraintViolation<CollectionUpdateDTO>> violations = validator.validate(dto);
                assertTrue(violations.isEmpty());
                assertEquals(CollectionType.PORTFOLIO, dto.getType());
                assertEquals(3, dto.getReorderOperations().size());
                assertEquals(10L, dto.getReorderOperations().get(0).getContentId());
                assertEquals(0, dto.getReorderOperations().get(0).getNewOrderIndex());
            }

            @Test
            @DisplayName("Should handle art gallery content removal")
            void shouldHandleArtGalleryContentRemoval() {
                List<Long> idsToRemove = Arrays.asList(8L, 12L, 16L);

                CollectionUpdateDTO dto = CollectionUpdateDTO.builder()
                        .type(CollectionType.ART_GALLERY)
                        .title("Urban Landscapes - Curated")
                        .slug("urban-landscapes")
                        .description("Updated curated collection")
                        .contentIdsToRemove(idsToRemove)
                        .contentPerPage(20)
                        .build();

                Set<ConstraintViolation<CollectionUpdateDTO>> violations = validator.validate(dto);
                assertTrue(violations.isEmpty());
                assertEquals(CollectionType.ART_GALLERY, dto.getType());
                assertEquals("Updated curated collection", dto.getDescription());
                assertEquals(3, dto.getContentIdsToRemove().size());
                assertTrue(dto.getContentIdsToRemove().contains(8L));
            }
        }

        @Nested
        @DisplayName("Inheritance Tests")
        class InheritanceTests {

            @Test
            @DisplayName("Should inherit base model validation rules")
            void shouldInheritBaseModelValidationRules() {
                CollectionUpdateDTO dto = CollectionUpdateDTO.builder()
                        .type(CollectionType.BLOG)
                        .title("AB") // Too short - should trigger base model validation
                        .slug("valid-slug")
                        .password("validpass123")
                        .build();

                Set<ConstraintViolation<CollectionUpdateDTO>> violations = validator.validate(dto);
                assertFalse(violations.isEmpty());
                assertTrue(violations.stream()
                        .anyMatch(v -> v.getMessage().contains("Title must be between 3 and 100 characters")));
            }

            @Test
            @DisplayName("Should combine base and subclass validation rules")
            void shouldCombineBaseAndSubclassValidationRules() {
                CollectionUpdateDTO dto = CollectionUpdateDTO.builder()
                        .type(CollectionType.CLIENT_GALLERY)
                        .title("AB") // Base validation error - title too short
                        .slug("valid-slug")
                        .password("short") // UpdateDTO validation error - password too short
                        .contentPerPage(0) // UpdateDTO validation error - below minimum
                        .build();

                Set<ConstraintViolation<CollectionUpdateDTO>> violations = validator.validate(dto);
                assertEquals(3, violations.size()); // Should have both base and UpdateDTO validation errors
            }
        }

        @Nested
        @DisplayName("Data Annotation Tests")
        class DataAnnotationTests {

            @Test
            @DisplayName("Should have working equals and hashCode with inheritance")
            void shouldHaveWorkingEqualsAndHashCodeWithInheritance() {
                LocalDateTime now = LocalDateTime.now();
                List<String> textContent = Arrays.asList("Same text content");

                CollectionUpdateDTO dto1 = CollectionUpdateDTO.builder()
                        .id(1L)
                        .type(CollectionType.BLOG)
                        .title("Test Blog")
                        .slug("test-blog")
                        .createdAt(now)
                        .password("password123")
                        .contentPerPage(20)
                        .build();

                CollectionUpdateDTO dto2 = CollectionUpdateDTO.builder()
                        .id(1L)
                        .type(CollectionType.BLOG)
                        .title("Test Blog")
                        .slug("test-blog")
                        .createdAt(now)
                        .password("password123")
                        .contentPerPage(20)
                        .build();

                CollectionUpdateDTO dto3 = CollectionUpdateDTO.builder()
                        .id(1L)
                        .type(CollectionType.BLOG)
                        .title("Test Blog")
                        .slug("test-blog")
                        .createdAt(now)
                        .password("differentpass") // Different password
                        .contentPerPage(20)
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
                List<Long> idsToRemove = Arrays.asList(1L, 2L);
                List<String> newTextContent = Arrays.asList("New content");

                CollectionUpdateDTO dto = CollectionUpdateDTO.builder()
                        .id(1L)
                        .type(CollectionType.PORTFOLIO)
                        .title("Test Portfolio")
                        .slug("test-portfolio")
                        .password("password123")
                        .contentPerPage(25)
                        .contentIdsToRemove(idsToRemove)
                        .build();

                String toString = dto.toString();

                assertNotNull(toString);
                // Should contain both base class and subclass information
                assertTrue(toString.contains("CollectionUpdateDTO"));
                assertTrue(toString.contains("id=1"));
                assertTrue(toString.contains("PORTFOLIO"));
                assertTrue(toString.contains("Test Portfolio"));
                assertTrue(toString.contains("password=password123"));
                assertTrue(toString.contains("contentPerPage=25"));
            }
        }

        @Nested
        @DisplayName("Complex Operation Scenarios Tests")
        class ComplexOperationScenariosTests {

            @Test
            @DisplayName("Should handle comprehensive content update with all operations")
            void shouldHandleComprehensiveContentUpdateWithAllOperations() {
                List<CollectionUpdateDTO.ContentReorderOperation> reorderOps = Arrays.asList(
                        CollectionUpdateDTO.ContentReorderOperation.builder()
                                .contentId(1L)
                                .newOrderIndex(2)
                                .build()
                );
                List<Long> idsToRemove = Arrays.asList(5L, 10L);
                List<String> newTextContent = Arrays.asList("New text content");
                List<String> newCodeContent = Arrays.asList("console.log('new code');");

                CollectionUpdateDTO dto = CollectionUpdateDTO.builder()
                        .id(1L)
                        .type(CollectionType.BLOG)
                        .title("Comprehensive Update")
                        .slug("comprehensive-update")
                        .description("This update includes all possible operations")
                        .visible(true)
                        .contentPerPage(30)
                        .reorderOperations(reorderOps)
                        .contentIdsToRemove(idsToRemove)
                        .build();

                Set<ConstraintViolation<CollectionUpdateDTO>> violations = validator.validate(dto);
                assertTrue(violations.isEmpty());

                // Verify all operations are present
                assertEquals(1, dto.getReorderOperations().size());
                assertEquals(2, dto.getContentIdsToRemove().size());

                // Verify metadata updates
                assertEquals("Comprehensive Update", dto.getTitle());
                assertEquals("This update includes all possible operations", dto.getDescription());
                assertTrue(dto.getVisible());
                assertEquals(30, dto.getContentPerPage());
            }

            @Test
            @DisplayName("Should handle client gallery security update scenario")
            void shouldHandleClientGallerySecurityUpdateScenario() {
                CollectionUpdateDTO dto = CollectionUpdateDTO.builder()
                        .id(5L)
                        .type(CollectionType.CLIENT_GALLERY)
                        .title("Smith Family Session - Updated")
                        .isPasswordProtected(true)
                        .hasAccess(true)
                        .password("newclientsecure2024")
                        .contentPerPage(40)
                        .build();

                Set<ConstraintViolation<CollectionUpdateDTO>> violations = validator.validate(dto);
                assertTrue(violations.isEmpty());

                assertEquals(CollectionType.CLIENT_GALLERY, dto.getType());
                assertTrue(dto.getIsPasswordProtected());
                assertTrue(dto.getHasAccess());
                assertEquals("newclientsecure2024", dto.getPassword());
                assertEquals(40, dto.getContentPerPage());
            }

            @Test
            @DisplayName("Should handle minimal update scenario")
            void shouldHandleMinimalUpdateScenario() {
                CollectionUpdateDTO dto = CollectionUpdateDTO.builder()
                        .id(3L)
                        .visible(false) // Just changing visibility
                        .build();

                Set<ConstraintViolation<CollectionUpdateDTO>> violations = validator.validate(dto);
                assertTrue(violations.isEmpty());

                assertEquals(3L, dto.getId());
                assertFalse(dto.getVisible());
                assertNull(dto.getTitle());
                assertNull(dto.getPassword());
                assertNull(dto.getReorderOperations());
                assertNull(dto.getContentIdsToRemove());
            }
        }

        @Nested
        @DisplayName("Multiple Validation Errors Tests")
        class MultipleValidationErrorsTests {

            @Test
            @DisplayName("Should capture multiple validation errors from both base and subclass")
            void shouldCaptureMultipleValidationErrorsFromBothBaseAndSubclass() {
                String longDescription = "A".repeat(501); // Base class validation error

                CollectionUpdateDTO dto = CollectionUpdateDTO.builder()
                        .type(CollectionType.CLIENT_GALLERY)
                        .title("AB") // Error 1: Title too short (base validation)
                        .slug("A") // Error 2: Slug too short (base validation)
                        .description(longDescription) // Error 3: Description too long (base validation)
                        .password("short") // Error 4: Password too short (UpdateDTO validation)
                        .contentPerPage(0) // Error 5: Content per page below minimum (UpdateDTO validation)
                        .build();

                Set<ConstraintViolation<CollectionUpdateDTO>> violations = validator.validate(dto);
                assertEquals(5, violations.size());
            }

            @Test
            @DisplayName("Should handle validation errors in nested reorder operations")
            void shouldHandleValidationErrorsInNestedReorderOperations() {
                List<CollectionUpdateDTO.ContentReorderOperation> reorderOps = Arrays.asList(
                        CollectionUpdateDTO.ContentReorderOperation.builder()
                                .contentId(1L)
                                .newOrderIndex(-5) // Error: Below minimum
                                .build(),
                        CollectionUpdateDTO.ContentReorderOperation.builder()
                                .contentId(2L)
                                .newOrderIndex(-1) // Error: Below minimum
                                .build()
                );

                CollectionUpdateDTO dto = CollectionUpdateDTO.builder()
                        .type(CollectionType.ART_GALLERY)
                        .title("Valid Title")
                        .slug("valid-slug")
                        .reorderOperations(reorderOps)
                        .build();

                Set<ConstraintViolation<CollectionUpdateDTO>> violations = validator.validate(dto);
                assertEquals(2, violations.size()); // Two nested validation errors
            }
        }
    }
}
