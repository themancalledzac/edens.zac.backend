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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ContentCollectionUpdateDTO
 * Tests partial update validation, password handling, and content block operations
 */
class ContentCollectionUpdateDTOTest {

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
            List<ContentCollectionUpdateDTO.ContentBlockReorderOperation> reorderOps = new ArrayList<>();
            List<Long> idsToRemove = Arrays.asList(1L, 2L, 3L);
            List<String> newTextBlocks = Arrays.asList("New text content 1", "New text content 2");
            List<String> newCodeBlocks = Arrays.asList("console.log('Hello');", "function test() {}");
            
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .id(1L)
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Updated Client Gallery")
                    .slug("updated-client-gallery")
                    .description("Updated professional client gallery")
                    .location("Updated Location")
                    .collectionDate(now)
                    .visible(true)
                    .priority(2)
                    .isPasswordProtected(true)
                    .hasAccess(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .password("newpassword123")
                    .blocksPerPage(25)
                    .reorderOperations(reorderOps)
                    .contentBlockIdsToRemove(idsToRemove)
                    .newTextBlocks(newTextBlocks)
                    .newCodeBlocks(newCodeBlocks)
                    .build();

            assertNotNull(dto);
            assertEquals(1L, dto.getId());
            assertEquals(CollectionType.CLIENT_GALLERY, dto.getType());
            assertEquals("Updated Client Gallery", dto.getTitle());
            assertEquals("updated-client-gallery", dto.getSlug());
            assertEquals("Updated professional client gallery", dto.getDescription());
            assertEquals("Updated Location", dto.getLocation());
            assertEquals(now, dto.getCollectionDate());
            assertTrue(dto.getVisible());
            assertEquals(2, dto.getPriority());
            assertTrue(dto.getIsPasswordProtected());
            assertTrue(dto.getHasAccess());
            assertEquals(now, dto.getCreatedAt());
            assertEquals(now, dto.getUpdatedAt());
            assertEquals("newpassword123", dto.getPassword());
            assertEquals(25, dto.getBlocksPerPage());
            assertEquals(reorderOps, dto.getReorderOperations());
            assertEquals(idsToRemove, dto.getContentBlockIdsToRemove());
            assertEquals(newTextBlocks, dto.getNewTextBlocks());
            assertEquals(newCodeBlocks, dto.getNewCodeBlocks());
        }

        @Test
        @DisplayName("Should create DTO with minimal fields for partial update")
        void shouldCreateDTOWithMinimalFields() {
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .id(1L)
                    .title("Updated Title")
                    .build();

            assertNotNull(dto);
            assertEquals(1L, dto.getId());
            assertEquals("Updated Title", dto.getTitle());
            assertNull(dto.getType());
            assertNull(dto.getSlug());
            assertNull(dto.getPassword());
            assertNull(dto.getBlocksPerPage());
            assertNull(dto.getReorderOperations());
            assertNull(dto.getContentBlockIdsToRemove());
            assertNull(dto.getNewTextBlocks());
            assertNull(dto.getNewCodeBlocks());
        }

        @Test
        @DisplayName("Should create DTO with only content operations")
        void shouldCreateDTOWithOnlyContentOperations() {
            List<Long> idsToRemove = Arrays.asList(5L, 10L);
            List<String> newTextBlocks = Arrays.asList("Just added this text");
            
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .id(1L)
                    .contentBlockIdsToRemove(idsToRemove)
                    .newTextBlocks(newTextBlocks)
                    .build();

            assertNotNull(dto);
            assertEquals(1L, dto.getId());
            assertEquals(idsToRemove, dto.getContentBlockIdsToRemove());
            assertEquals(newTextBlocks, dto.getNewTextBlocks());
            assertNull(dto.getTitle());
            assertNull(dto.getPassword());
            assertNull(dto.getReorderOperations());
            assertNull(dto.getNewCodeBlocks());
        }

        @Test
        @DisplayName("Should create DTO using no-args constructor")
        void shouldCreateDTOWithNoArgsConstructor() {
            ContentCollectionUpdateDTO dto = new ContentCollectionUpdateDTO();
            
            assertNotNull(dto);
            assertNull(dto.getId());
            assertNull(dto.getType());
            assertNull(dto.getTitle());
            assertNull(dto.getPassword());
            assertNull(dto.getBlocksPerPage());
            assertNull(dto.getReorderOperations());
            assertNull(dto.getContentBlockIdsToRemove());
            assertNull(dto.getNewTextBlocks());
            assertNull(dto.getNewCodeBlocks());
        }
    }

    @Nested
    @DisplayName("Password Validation Tests")
    class PasswordValidationTests {

        @Test
        @DisplayName("Should accept valid password")
        void shouldAcceptValidPassword() {
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Client Gallery")
                    .slug("client-gallery")
                    .password("validpass123")
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            assertEquals("validpass123", dto.getPassword());
        }

        @Test
        @DisplayName("Should accept null password for partial updates")
        void shouldAcceptNullPassword() {
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .type(CollectionType.BLOG)
                    .title("Blog Update")
                    .slug("blog-update")
                    .password(null) // Null means no password change
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            assertNull(dto.getPassword());
        }

        @Test
        @DisplayName("Should reject password that is too short")
        void shouldRejectPasswordTooShort() {
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Client Gallery")
                    .slug("client-gallery")
                    .password("short") // Only 5 characters
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> violations = validator.validate(dto);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Password must be between 8 and 100 characters")));
        }

        @Test
        @DisplayName("Should reject password that is too long")
        void shouldRejectPasswordTooLong() {
            String longPassword = "a".repeat(101); // 101 characters
            
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Client Gallery")
                    .slug("client-gallery")
                    .password(longPassword)
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> violations = validator.validate(dto);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Password must be between 8 and 100 characters")));
        }

        @Test
        @DisplayName("Should accept password at boundary lengths")
        void shouldAcceptPasswordAtBoundaryLengths() {
            // Test minimum boundary
            ContentCollectionUpdateDTO minDto = ContentCollectionUpdateDTO.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Client Gallery")
                    .slug("client-gallery")
                    .password("12345678") // Exactly 8 characters
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> minViolations = validator.validate(minDto);
            assertTrue(minViolations.isEmpty());

            // Test maximum boundary
            String maxPassword = "a".repeat(100); // Exactly 100 characters
            ContentCollectionUpdateDTO maxDto = ContentCollectionUpdateDTO.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Client Gallery")
                    .slug("client-gallery")
                    .password(maxPassword)
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> maxViolations = validator.validate(maxDto);
            assertTrue(maxViolations.isEmpty());
        }
    }

    @Nested
    @DisplayName("Blocks Per Page Validation Tests")
    class BlocksPerPageValidationTests {

        @Test
        @DisplayName("Should accept valid blocksPerPage")
        void shouldAcceptValidBlocksPerPage() {
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Portfolio")
                    .slug("portfolio")
                    .blocksPerPage(30)
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            assertEquals(30, dto.getBlocksPerPage());
        }

        @Test
        @DisplayName("Should accept null blocksPerPage for partial updates")
        void shouldAcceptNullBlocksPerPage() {
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .type(CollectionType.ART_GALLERY)
                    .title("Art Gallery")
                    .slug("art-gallery")
                    .blocksPerPage(null) // Null means no change
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            assertNull(dto.getBlocksPerPage());
        }

        @Test
        @DisplayName("Should reject blocksPerPage below minimum")
        void shouldRejectBlocksPerPageBelowMin() {
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .type(CollectionType.BLOG)
                    .title("Blog")
                    .slug("blog")
                    .blocksPerPage(0) // Invalid - below minimum
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> violations = validator.validate(dto);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Blocks per page must be 1 or greater")));
        }

        @Test
        @DisplayName("Should accept blocksPerPage at minimum boundary")
        void shouldAcceptBlocksPerPageAtMinBoundary() {
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Portfolio")
                    .slug("portfolio")
                    .blocksPerPage(1) // Exactly at minimum
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
        }
    }

    @Nested
    @DisplayName("Content Block Operations Tests")
    class ContentBlockOperationsTests {

        @Test
        @DisplayName("Should accept valid reorder operations")
        void shouldAcceptValidReorderOperations() {
            List<ContentCollectionUpdateDTO.ContentBlockReorderOperation> reorderOps = Arrays.asList(
                    ContentCollectionUpdateDTO.ContentBlockReorderOperation.builder()
                            .contentBlockId(1L)
                            .newOrderIndex(0)
                            .build(),
                    ContentCollectionUpdateDTO.ContentBlockReorderOperation.builder()
                            .contentBlockId(2L)
                            .newOrderIndex(1)
                            .build()
            );
            
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .type(CollectionType.ART_GALLERY)
                    .title("Art Gallery")
                    .slug("art-gallery")
                    .reorderOperations(reorderOps)
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            assertEquals(2, dto.getReorderOperations().size());
        }

        @Test
        @DisplayName("Should accept empty content operation lists")
        void shouldAcceptEmptyContentOperationLists() {
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .type(CollectionType.BLOG)
                    .title("Blog")
                    .slug("blog")
                    .reorderOperations(new ArrayList<>())
                    .contentBlockIdsToRemove(new ArrayList<>())
                    .newTextBlocks(new ArrayList<>())
                    .newCodeBlocks(new ArrayList<>())
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            assertTrue(dto.getReorderOperations().isEmpty());
            assertTrue(dto.getContentBlockIdsToRemove().isEmpty());
            assertTrue(dto.getNewTextBlocks().isEmpty());
            assertTrue(dto.getNewCodeBlocks().isEmpty());
        }

        @Test
        @DisplayName("Should accept null content operation lists for partial updates")
        void shouldAcceptNullContentOperationLists() {
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Portfolio")
                    .slug("portfolio")
                    .reorderOperations(null)
                    .contentBlockIdsToRemove(null)
                    .newTextBlocks(null)
                    .newCodeBlocks(null)
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            assertNull(dto.getReorderOperations());
            assertNull(dto.getContentBlockIdsToRemove());
            assertNull(dto.getNewTextBlocks());
            assertNull(dto.getNewCodeBlocks());
        }

        @Test
        @DisplayName("Should accept valid content block IDs to remove")
        void shouldAcceptValidContentBlockIdsToRemove() {
            List<Long> idsToRemove = Arrays.asList(1L, 5L, 10L, 15L);
            
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Client Gallery")
                    .slug("client-gallery")
                    .contentBlockIdsToRemove(idsToRemove)
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            assertEquals(4, dto.getContentBlockIdsToRemove().size());
            assertTrue(dto.getContentBlockIdsToRemove().containsAll(Arrays.asList(1L, 5L, 10L, 15L)));
        }

        @Test
        @DisplayName("Should accept valid new text blocks")
        void shouldAcceptValidNewTextBlocks() {
            List<String> newTextBlocks = Arrays.asList(
                    "This is a new text block with some content.",
                    "Another text block that provides additional information.",
                    "A final text block to complete the story."
            );
            
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .type(CollectionType.BLOG)
                    .title("Blog")
                    .slug("blog")
                    .newTextBlocks(newTextBlocks)
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            assertEquals(3, dto.getNewTextBlocks().size());
            assertEquals("This is a new text block with some content.", dto.getNewTextBlocks().get(0));
        }

        @Test
        @DisplayName("Should accept valid new code blocks")
        void shouldAcceptValidNewCodeBlocks() {
            List<String> newCodeBlocks = Arrays.asList(
                    "function greet(name) {\n    return `Hello, ${name}!`;\n}",
                    "const result = greet('World');\nconsole.log(result);",
                    "// This is a comment\nlet x = 42;"
            );
            
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Portfolio")
                    .slug("portfolio")
                    .newCodeBlocks(newCodeBlocks)
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            assertEquals(3, dto.getNewCodeBlocks().size());
            assertTrue(dto.getNewCodeBlocks().get(0).contains("function greet"));
        }
    }

    @Nested
    @DisplayName("Content Block Reorder Operation Tests")
    class ContentBlockReorderOperationTests {

        @Test
        @DisplayName("Should create valid reorder operation")
        void shouldCreateValidReorderOperation() {
            ContentCollectionUpdateDTO.ContentBlockReorderOperation operation = 
                    ContentCollectionUpdateDTO.ContentBlockReorderOperation.builder()
                            .contentBlockId(5L)
                            .newOrderIndex(2)
                            .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO.ContentBlockReorderOperation>> violations = 
                    validator.validate(operation);
            assertTrue(violations.isEmpty());
            assertEquals(5L, operation.getContentBlockId());
            assertEquals(2, operation.getNewOrderIndex());
        }

        @Test
        @DisplayName("Should fail validation when contentBlockId is null")
        void shouldFailValidationWhenContentBlockIdIsNull() {
            ContentCollectionUpdateDTO.ContentBlockReorderOperation operation = 
                    ContentCollectionUpdateDTO.ContentBlockReorderOperation.builder()
                            .contentBlockId(null) // Required field missing
                            .newOrderIndex(1)
                            .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO.ContentBlockReorderOperation>> violations = 
                    validator.validate(operation);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Content block ID is required")));
        }

        @Test
        @DisplayName("Should fail validation when newOrderIndex is negative")
        void shouldFailValidationWhenNewOrderIndexIsNegative() {
            ContentCollectionUpdateDTO.ContentBlockReorderOperation operation = 
                    ContentCollectionUpdateDTO.ContentBlockReorderOperation.builder()
                            .contentBlockId(3L)
                            .newOrderIndex(-1) // Invalid - below minimum
                            .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO.ContentBlockReorderOperation>> violations = 
                    validator.validate(operation);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("New order index must be 0 or greater")));
        }

        @Test
        @DisplayName("Should accept newOrderIndex at minimum boundary")
        void shouldAcceptNewOrderIndexAtMinBoundary() {
            ContentCollectionUpdateDTO.ContentBlockReorderOperation operation = 
                    ContentCollectionUpdateDTO.ContentBlockReorderOperation.builder()
                            .contentBlockId(7L)
                            .newOrderIndex(0) // Exactly at minimum
                            .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO.ContentBlockReorderOperation>> violations = 
                    validator.validate(operation);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should create reorder operation using no-args constructor")
        void shouldCreateReorderOperationWithNoArgsConstructor() {
            ContentCollectionUpdateDTO.ContentBlockReorderOperation operation = 
                    new ContentCollectionUpdateDTO.ContentBlockReorderOperation();
            
            assertNotNull(operation);
            assertNull(operation.getContentBlockId());
            assertNull(operation.getNewOrderIndex());
        }

        @Test
        @DisplayName("Should create reorder operation using all-args constructor")
        void shouldCreateReorderOperationWithAllArgsConstructor() {
            ContentCollectionUpdateDTO.ContentBlockReorderOperation operation = 
                    new ContentCollectionUpdateDTO.ContentBlockReorderOperation(9L, 4);
            
            assertNotNull(operation);
            assertEquals(9L, operation.getContentBlockId());
            assertEquals(4, operation.getNewOrderIndex());
        }
    }

    @Nested
    @DisplayName("Collection Type Specific Tests")
    class CollectionTypeSpecificTests {

        @Test
        @DisplayName("Should handle client gallery password update")
        void shouldHandleClientGalleryPasswordUpdate() {
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Johnson Wedding Update")
                    .slug("johnson-wedding")
                    .isPasswordProtected(true)
                    .password("newpassword2024")
                    .blocksPerPage(50)
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            assertEquals(CollectionType.CLIENT_GALLERY, dto.getType());
            assertTrue(dto.getIsPasswordProtected());
            assertEquals("newpassword2024", dto.getPassword());
            assertEquals(50, dto.getBlocksPerPage());
        }

        @Test
        @DisplayName("Should handle blog content update with text blocks")
        void shouldHandleBlogContentUpdateWithTextBlocks() {
            List<String> newTextBlocks = Arrays.asList(
                    "Today was an amazing day at the park.",
                    "The lighting was perfect for photography.",
                    "Can't wait to share more moments like this."
            );
            
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .type(CollectionType.BLOG)
                    .title("Daily Moments - Updated")
                    .slug("daily-moments")
                    .blocksPerPage(15)
                    .newTextBlocks(newTextBlocks)
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            assertEquals(CollectionType.BLOG, dto.getType());
            assertEquals(15, dto.getBlocksPerPage());
            assertEquals(3, dto.getNewTextBlocks().size());
        }

        @Test
        @DisplayName("Should handle portfolio reordering")
        void shouldHandlePortfolioReordering() {
            List<ContentCollectionUpdateDTO.ContentBlockReorderOperation> reorderOps = Arrays.asList(
                    ContentCollectionUpdateDTO.ContentBlockReorderOperation.builder()
                            .contentBlockId(10L)
                            .newOrderIndex(0)
                            .build(),
                    ContentCollectionUpdateDTO.ContentBlockReorderOperation.builder()
                            .contentBlockId(5L)
                            .newOrderIndex(1)
                            .build(),
                    ContentCollectionUpdateDTO.ContentBlockReorderOperation.builder()
                            .contentBlockId(15L)
                            .newOrderIndex(2)
                            .build()
            );
            
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .type(CollectionType.PORTFOLIO)
                    .title("Wedding Portfolio - Reordered")
                    .slug("wedding-portfolio")
                    .reorderOperations(reorderOps)
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            assertEquals(CollectionType.PORTFOLIO, dto.getType());
            assertEquals(3, dto.getReorderOperations().size());
            assertEquals(10L, dto.getReorderOperations().get(0).getContentBlockId());
            assertEquals(0, dto.getReorderOperations().get(0).getNewOrderIndex());
        }

        @Test
        @DisplayName("Should handle art gallery content removal")
        void shouldHandleArtGalleryContentRemoval() {
            List<Long> idsToRemove = Arrays.asList(8L, 12L, 16L);
            
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .type(CollectionType.ART_GALLERY)
                    .title("Urban Landscapes - Curated")
                    .slug("urban-landscapes")
                    .description("Updated curated collection")
                    .contentBlockIdsToRemove(idsToRemove)
                    .blocksPerPage(20)
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            assertEquals(CollectionType.ART_GALLERY, dto.getType());
            assertEquals("Updated curated collection", dto.getDescription());
            assertEquals(3, dto.getContentBlockIdsToRemove().size());
            assertTrue(dto.getContentBlockIdsToRemove().contains(8L));
        }
    }

    @Nested
    @DisplayName("Inheritance Tests")
    class InheritanceTests {

        @Test
        @DisplayName("Should inherit base model validation rules")
        void shouldInheritBaseModelValidationRules() {
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .type(CollectionType.BLOG)
                    .title("AB") // Too short - should trigger base model validation
                    .slug("valid-slug")
                    .password("validpass123")
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> violations = validator.validate(dto);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getMessage().contains("Title must be between 3 and 100 characters")));
        }

        @Test
        @DisplayName("Should combine base and subclass validation rules")
        void shouldCombineBaseAndSubclassValidationRules() {
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("AB") // Base validation error - title too short
                    .slug("valid-slug")
                    .password("short") // UpdateDTO validation error - password too short
                    .blocksPerPage(0) // UpdateDTO validation error - below minimum
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> violations = validator.validate(dto);
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
            List<String> textBlocks = Arrays.asList("Same text content");
            
            ContentCollectionUpdateDTO dto1 = ContentCollectionUpdateDTO.builder()
                    .id(1L)
                    .type(CollectionType.BLOG)
                    .title("Test Blog")
                    .slug("test-blog")
                    .createdAt(now)
                    .password("password123")
                    .blocksPerPage(20)
                    .newTextBlocks(textBlocks)
                    .build();

            ContentCollectionUpdateDTO dto2 = ContentCollectionUpdateDTO.builder()
                    .id(1L)
                    .type(CollectionType.BLOG)
                    .title("Test Blog")
                    .slug("test-blog")
                    .createdAt(now)
                    .password("password123")
                    .blocksPerPage(20)
                    .newTextBlocks(textBlocks)
                    .build();

            ContentCollectionUpdateDTO dto3 = ContentCollectionUpdateDTO.builder()
                    .id(1L)
                    .type(CollectionType.BLOG)
                    .title("Test Blog")
                    .slug("test-blog")
                    .createdAt(now)
                    .password("differentpass") // Different password
                    .blocksPerPage(20)
                    .newTextBlocks(textBlocks)
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
            List<String> newTextBlocks = Arrays.asList("New content");
            
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .id(1L)
                    .type(CollectionType.PORTFOLIO)
                    .title("Test Portfolio")
                    .slug("test-portfolio")
                    .password("password123")
                    .blocksPerPage(25)
                    .contentBlockIdsToRemove(idsToRemove)
                    .newTextBlocks(newTextBlocks)
                    .build();

            String toString = dto.toString();
            
            assertNotNull(toString);
            // Should contain both base class and subclass information
            assertTrue(toString.contains("ContentCollectionUpdateDTO"));
            assertTrue(toString.contains("id=1"));
            assertTrue(toString.contains("PORTFOLIO"));
            assertTrue(toString.contains("Test Portfolio"));
            assertTrue(toString.contains("password=password123"));
            assertTrue(toString.contains("blocksPerPage=25"));
        }
    }

    @Nested
    @DisplayName("Complex Operation Scenarios Tests")
    class ComplexOperationScenariosTests {

        @Test
        @DisplayName("Should handle comprehensive content update with all operations")
        void shouldHandleComprehensiveContentUpdateWithAllOperations() {
            List<ContentCollectionUpdateDTO.ContentBlockReorderOperation> reorderOps = Arrays.asList(
                    ContentCollectionUpdateDTO.ContentBlockReorderOperation.builder()
                            .contentBlockId(1L)
                            .newOrderIndex(2)
                            .build()
            );
            List<Long> idsToRemove = Arrays.asList(5L, 10L);
            List<String> newTextBlocks = Arrays.asList("New text content");
            List<String> newCodeBlocks = Arrays.asList("console.log('new code');");
            
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .id(1L)
                    .type(CollectionType.BLOG)
                    .title("Comprehensive Update")
                    .slug("comprehensive-update")
                    .description("This update includes all possible operations")
                    .visible(true)
                    .priority(1)
                    .blocksPerPage(30)
                    .reorderOperations(reorderOps)
                    .contentBlockIdsToRemove(idsToRemove)
                    .newTextBlocks(newTextBlocks)
                    .newCodeBlocks(newCodeBlocks)
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            
            // Verify all operations are present
            assertEquals(1, dto.getReorderOperations().size());
            assertEquals(2, dto.getContentBlockIdsToRemove().size());
            assertEquals(1, dto.getNewTextBlocks().size());
            assertEquals(1, dto.getNewCodeBlocks().size());
            
            // Verify metadata updates
            assertEquals("Comprehensive Update", dto.getTitle());
            assertEquals("This update includes all possible operations", dto.getDescription());
            assertTrue(dto.getVisible());
            assertEquals(1, dto.getPriority());
            assertEquals(30, dto.getBlocksPerPage());
        }

        @Test
        @DisplayName("Should handle client gallery security update scenario")
        void shouldHandleClientGallerySecurityUpdateScenario() {
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .id(5L)
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("Smith Family Session - Updated")
                    .isPasswordProtected(true)
                    .hasAccess(true)
                    .password("newclientsecure2024")
                    .blocksPerPage(40)
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            
            assertEquals(CollectionType.CLIENT_GALLERY, dto.getType());
            assertTrue(dto.getIsPasswordProtected());
            assertTrue(dto.getHasAccess());
            assertEquals("newclientsecure2024", dto.getPassword());
            assertEquals(40, dto.getBlocksPerPage());
        }

        @Test
        @DisplayName("Should handle minimal update scenario")
        void shouldHandleMinimalUpdateScenario() {
            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .id(3L)
                    .visible(false) // Just changing visibility
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
            
            assertEquals(3L, dto.getId());
            assertFalse(dto.getVisible());
            assertNull(dto.getTitle());
            assertNull(dto.getPassword());
            assertNull(dto.getReorderOperations());
            assertNull(dto.getContentBlockIdsToRemove());
        }
    }

    @Nested
    @DisplayName("Multiple Validation Errors Tests")
    class MultipleValidationErrorsTests {

        @Test
        @DisplayName("Should capture multiple validation errors from both base and subclass")
        void shouldCaptureMultipleValidationErrorsFromBothBaseAndSubclass() {
            String longDescription = "A".repeat(501); // Base class validation error

            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .type(CollectionType.CLIENT_GALLERY)
                    .title("AB") // Error 1: Title too short (base validation)
                    .slug("A") // Error 2: Slug too short (base validation)
                    .description(longDescription) // Error 3: Description too long (base validation)
                    .password("short") // Error 4: Password too short (UpdateDTO validation)
                    .blocksPerPage(0) // Error 5: Blocks per page below minimum (UpdateDTO validation)
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> violations = validator.validate(dto);
            assertEquals(5, violations.size());
        }

        @Test
        @DisplayName("Should handle validation errors in nested reorder operations")
        void shouldHandleValidationErrorsInNestedReorderOperations() {
            List<ContentCollectionUpdateDTO.ContentBlockReorderOperation> reorderOps = Arrays.asList(
                    ContentCollectionUpdateDTO.ContentBlockReorderOperation.builder()
                            .contentBlockId(null) // Error: Required field missing
                            .newOrderIndex(0)
                            .build(),
                    ContentCollectionUpdateDTO.ContentBlockReorderOperation.builder()
                            .contentBlockId(2L)
                            .newOrderIndex(-1) // Error: Below minimum
                            .build()
            );

            ContentCollectionUpdateDTO dto = ContentCollectionUpdateDTO.builder()
                    .type(CollectionType.ART_GALLERY)
                    .title("Valid Title")
                    .slug("valid-slug")
                    .reorderOperations(reorderOps)
                    .build();

            Set<ConstraintViolation<ContentCollectionUpdateDTO>> violations = validator.validate(dto);
            assertEquals(2, violations.size()); // Two nested validation errors
        }
    }
}
