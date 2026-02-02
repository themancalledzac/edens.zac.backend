package edens.zac.portfolio.backend.model;

import static org.junit.jupiter.api.Assertions.*;

import edens.zac.portfolio.backend.types.CollectionType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for CollectionUpdateRequest Tests partial update validation, password handling, and
 * relationship updates using prev/new/remove pattern
 */
class CollectionUpdateRequestTest {

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
      LocalDate today = LocalDate.now();
      // List<Long> idsToRemove = Arrays.asList(1L, 2L, 3L);

      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder()
              .id(1L)
              .type(CollectionType.CLIENT_GALLERY)
              .title("Updated Client Gallery")
              .slug("updated-client-gallery")
              .description("Updated professional client gallery")
              .location(LocationUpdate.builder().newValue("Updated Location").build())
              .collectionDate(today)
              .visible(true)
              .password("newpassword123")
              .contentPerPage(25)
              .build();

      assertNotNull(dto);
      assertEquals(1L, dto.getId());
      assertEquals(CollectionType.CLIENT_GALLERY, dto.getType());
      assertEquals("Updated Client Gallery", dto.getTitle());
      assertEquals("updated-client-gallery", dto.getSlug());
      assertEquals("Updated professional client gallery", dto.getDescription());
      assertNotNull(dto.getLocation());
      assertEquals("Updated Location", dto.getLocation().getNewValue());
      assertEquals(today, dto.getCollectionDate());
      assertTrue(dto.getVisible());
      assertEquals(25, dto.getContentPerPage());
    }

    @Test
    @DisplayName("Should create DTO with minimal fields for partial update")
    void shouldCreateDTOWithMinimalFields() {
      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder().id(1L).title("Updated Title").build();

      assertNotNull(dto);
      assertEquals(1L, dto.getId());
      assertEquals("Updated Title", dto.getTitle());
      assertNull(dto.getType());
      assertNull(dto.getSlug());
      assertNull(dto.getPassword());
      assertNull(dto.getContentPerPage());
    }

    @Test
    @DisplayName("Should create DTO with only content operations")
    void shouldCreateDTOWithOnlyContentOperations() {
      // List<Long> idsToRemove = Arrays.asList(5L, 10L);
      // List<String> newTextContent = Arrays.asList("Just added this text");

      CollectionUpdateRequest dto = CollectionUpdateRequest.builder().id(1L).build();

      assertNotNull(dto);
      assertEquals(1L, dto.getId());
      assertNull(dto.getTitle());
    }

    @Test
    @DisplayName("Should create DTO using no-args constructor")
    void shouldCreateDTOWithNoArgsConstructor() {
      CollectionUpdateRequest dto = new CollectionUpdateRequest();

      assertNotNull(dto);
      assertNull(dto.getId());
      assertNull(dto.getType());
      assertNull(dto.getTitle());
      assertNull(dto.getPassword());
      assertNull(dto.getContentPerPage());
    }
  }

  @Nested
  @DisplayName("Password Validation Tests")
  class PasswordValidationTests {

    @Test
    @DisplayName("Should accept valid password")
    void shouldAcceptValidPassword() {
      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder()
              .id(1L)
              .type(CollectionType.CLIENT_GALLERY)
              .title("Client Gallery")
              .slug("client-gallery")
              .password("validpass123")
              .build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertEquals("validpass123", dto.getPassword());
    }

    @Test
    @DisplayName("Should accept null password for partial updates")
    void shouldAcceptNullPassword() {
      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder()
              .id(1L)
              .type(CollectionType.BLOG)
              .title("Blog Update")
              .slug("blog-update")
              .password(null) // Null means no password change
              .build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNull(dto.getPassword());
    }

    @Test
    @DisplayName("Should reject password that is too short")
    void shouldRejectPasswordTooShort() {
      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder()
              .id(1L)
              .type(CollectionType.CLIENT_GALLERY)
              .title("Client Gallery")
              .slug("client-gallery")
              .password("short") // Only 5 characters
              .build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertFalse(violations.isEmpty());
      assertTrue(
          violations.stream()
              .anyMatch(
                  v -> v.getMessage().contains("Password must be between 8 and 100 characters")));
    }

    @Test
    @DisplayName("Should reject password that is too long")
    void shouldRejectPasswordTooLong() {
      String longPassword = "a".repeat(101); // 101 characters

      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder()
              .id(1L)
              .type(CollectionType.CLIENT_GALLERY)
              .title("Client Gallery")
              .slug("client-gallery")
              .password(longPassword)
              .build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertFalse(violations.isEmpty());
      assertTrue(
          violations.stream()
              .anyMatch(
                  v -> v.getMessage().contains("Password must be between 8 and 100 characters")));
    }

    @Test
    @DisplayName("Should accept password at boundary lengths")
    void shouldAcceptPasswordAtBoundaryLengths() {
      // Test minimum boundary
      CollectionUpdateRequest minDto =
          CollectionUpdateRequest.builder()
              .id(1L)
              .type(CollectionType.CLIENT_GALLERY)
              .title("Client Gallery")
              .slug("client-gallery")
              .password("12345678") // Exactly 8 characters
              .build();

      Set<ConstraintViolation<CollectionUpdateRequest>> minViolations = validator.validate(minDto);
      assertTrue(minViolations.isEmpty());

      // Test maximum boundary
      String maxPassword = "a".repeat(100); // Exactly 100 characters
      CollectionUpdateRequest maxDto =
          CollectionUpdateRequest.builder()
              .id(1L)
              .type(CollectionType.CLIENT_GALLERY)
              .title("Client Gallery")
              .slug("client-gallery")
              .password(maxPassword)
              .build();

      Set<ConstraintViolation<CollectionUpdateRequest>> maxViolations = validator.validate(maxDto);
      assertTrue(maxViolations.isEmpty());
    }
  }

  @Nested
  @DisplayName("Content Per Page Validation Tests")
  class ContentPerPageValidationTests {

    @Test
    @DisplayName("Should accept valid contentPerPage")
    void shouldAcceptValidContentPerPage() {
      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder()
              .id(1L)
              .type(CollectionType.PORTFOLIO)
              .title("Portfolio")
              .slug("portfolio")
              .contentPerPage(30)
              .build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertEquals(30, dto.getContentPerPage());
    }

    @Test
    @DisplayName("Should accept null contentPerPage for partial updates")
    void shouldAcceptNullContentPerPage() {
      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder()
              .id(1L)
              .type(CollectionType.ART_GALLERY)
              .title("Art Gallery")
              .slug("art-gallery")
              .contentPerPage(null) // Null means no change
              .build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNull(dto.getContentPerPage());
    }

    @Test
    @DisplayName("Should reject contentPerPage below minimum")
    void shouldRejectContentPerPageBelowMin() {
      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder()
              .id(1L)
              .type(CollectionType.BLOG)
              .title("Blog")
              .slug("blog")
              .contentPerPage(0) // Invalid - below minimum
              .build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertFalse(violations.isEmpty());
      assertTrue(
          violations.stream()
              .anyMatch(v -> v.getMessage().contains("Content per page must be 1 or greater")));
    }

    @Test
    @DisplayName("Should accept contentPerPage at minimum boundary")
    void shouldAcceptContentPerPageAtMinBoundary() {
      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder()
              .id(1L)
              .type(CollectionType.PORTFOLIO)
              .title("Portfolio")
              .slug("portfolio")
              .contentPerPage(1) // Exactly at minimum
              .build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
    }
  }

  @Nested
  @DisplayName("Display Mode Tests")
  class DisplayModeTests {

    @Test
    @DisplayName("Should accept CHRONOLOGICAL display mode")
    void shouldAcceptChronologicalDisplayMode() {
      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder()
              .id(1L)
              .type(CollectionType.BLOG)
              .displayMode(CollectionBaseModel.DisplayMode.CHRONOLOGICAL)
              .build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertEquals(CollectionBaseModel.DisplayMode.CHRONOLOGICAL, dto.getDisplayMode());
    }

    @Test
    @DisplayName("Should accept ORDERED display mode")
    void shouldAcceptOrderedDisplayMode() {
      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder()
              .id(1L)
              .type(CollectionType.PORTFOLIO)
              .displayMode(CollectionBaseModel.DisplayMode.ORDERED)
              .build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertEquals(CollectionBaseModel.DisplayMode.ORDERED, dto.getDisplayMode());
    }

    @Test
    @DisplayName("Should accept null display mode for partial updates")
    void shouldAcceptNullDisplayMode() {
      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder()
              .id(1L)
              .type(CollectionType.ART_GALLERY)
              .displayMode(null)
              .build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNull(dto.getDisplayMode());
    }
  }

  @Nested
  @DisplayName("Cover Image Tests")
  class CoverImageTests {

    @Test
    @DisplayName("Should accept valid cover image ID")
    void shouldAcceptValidCoverImageId() {
      Long imageId = 123L;
      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder().id(1L).coverImageId(imageId).build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertEquals(imageId, dto.getCoverImageId());
    }

    @Test
    @DisplayName("Should accept null cover image for partial updates")
    void shouldAcceptNullCoverImage() {
      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder().id(1L).coverImageId(null).build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNull(dto.getCoverImageId());
    }

    @Test
    @DisplayName("Should accept cover image ID of zero to clear cover image")
    void shouldAcceptCoverImageIdZero() {
      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder().id(1L).coverImageId(0L).build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertEquals(0L, dto.getCoverImageId());
    }

    @Test
    @DisplayName("Should accept valid cover image ID values")
    void shouldAcceptValidCoverImageIdValues() {
      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder().id(1L).coverImageId(456L).build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertEquals(456L, dto.getCoverImageId());
    }
  }

  @Nested
  @DisplayName("Tag Update Tests")
  class TagUpdateTests {

    @Test
    @DisplayName("Should accept tag updates with prev pattern")
    void shouldAcceptTagUpdatesWithPrev() {
      TagUpdate tagUpdate = TagUpdate.builder().prev(Arrays.asList(1L, 2L, 3L)).build();

      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder().id(1L).tags(tagUpdate).build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNotNull(dto.getTags());
      assertEquals(3, dto.getTags().getPrev().size());
    }

    @Test
    @DisplayName("Should accept tag updates with newValue pattern")
    void shouldAcceptTagUpdatesWithNewValue() {
      TagUpdate tagUpdate =
          TagUpdate.builder().newValue(Arrays.asList("landscape", "nature", "photography")).build();

      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder().id(1L).tags(tagUpdate).build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNotNull(dto.getTags());
      assertEquals(3, dto.getTags().getNewValue().size());
    }

    @Test
    @DisplayName("Should accept tag updates with remove pattern")
    void shouldAcceptTagUpdatesWithRemove() {
      TagUpdate tagUpdate = TagUpdate.builder().remove(Arrays.asList(5L, 10L)).build();

      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder().id(1L).tags(tagUpdate).build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNotNull(dto.getTags());
      assertEquals(2, dto.getTags().getRemove().size());
    }

    @Test
    @DisplayName("Should accept tag updates with all patterns combined")
    void shouldAcceptTagUpdatesWithAllPatterns() {
      TagUpdate tagUpdate =
          TagUpdate.builder()
              .prev(Arrays.asList(1L, 2L))
              .newValue(Arrays.asList("new-tag-1", "new-tag-2"))
              .remove(Arrays.asList(3L))
              .build();

      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder().id(1L).tags(tagUpdate).build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNotNull(dto.getTags());
      assertEquals(2, dto.getTags().getPrev().size());
      assertEquals(2, dto.getTags().getNewValue().size());
      assertEquals(1, dto.getTags().getRemove().size());
    }

    @Test
    @DisplayName("Should accept null tags for partial updates")
    void shouldAcceptNullTags() {
      CollectionUpdateRequest dto = CollectionUpdateRequest.builder().id(1L).tags(null).build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNull(dto.getTags());
    }
  }

  @Nested
  @DisplayName("Person Update Tests")
  class PersonUpdateTests {

    @Test
    @DisplayName("Should accept person updates with prev pattern")
    void shouldAcceptPersonUpdatesWithPrev() {
      PersonUpdate personUpdate = PersonUpdate.builder().prev(Arrays.asList(1L, 2L)).build();

      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder().id(1L).people(personUpdate).build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNotNull(dto.getPeople());
      assertEquals(2, dto.getPeople().getPrev().size());
    }

    @Test
    @DisplayName("Should accept person updates with newValue pattern")
    void shouldAcceptPersonUpdatesWithNewValue() {
      PersonUpdate personUpdate =
          PersonUpdate.builder().newValue(Arrays.asList("John Doe", "Jane Smith")).build();

      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder().id(1L).people(personUpdate).build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNotNull(dto.getPeople());
      assertEquals(2, dto.getPeople().getNewValue().size());
    }

    @Test
    @DisplayName("Should accept person updates with remove pattern")
    void shouldAcceptPersonUpdatesWithRemove() {
      PersonUpdate personUpdate = PersonUpdate.builder().remove(Arrays.asList(3L, 4L)).build();

      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder().id(1L).people(personUpdate).build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNotNull(dto.getPeople());
      assertEquals(2, dto.getPeople().getRemove().size());
    }

    @Test
    @DisplayName("Should accept person updates with all patterns combined")
    void shouldAcceptPersonUpdatesWithAllPatterns() {
      PersonUpdate personUpdate =
          PersonUpdate.builder()
              .prev(Arrays.asList(1L))
              .newValue(Arrays.asList("Alice Johnson"))
              .remove(Arrays.asList(5L, 6L))
              .build();

      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder().id(1L).people(personUpdate).build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNotNull(dto.getPeople());
      assertEquals(1, dto.getPeople().getPrev().size());
      assertEquals(1, dto.getPeople().getNewValue().size());
      assertEquals(2, dto.getPeople().getRemove().size());
    }

    @Test
    @DisplayName("Should accept null people for partial updates")
    void shouldAcceptNullPeople() {
      CollectionUpdateRequest dto = CollectionUpdateRequest.builder().id(1L).people(null).build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNull(dto.getPeople());
    }
  }

  @Nested
  @DisplayName("Collection Update Tests")
  class CollectionUpdateTests {

    @Test
    @DisplayName("Should accept collection updates with prev pattern")
    void shouldAcceptCollectionUpdatesWithPrev() {
      ChildCollection childCollection =
          ChildCollection.builder().collectionId(10L).visible(true).orderIndex(0).build();

      CollectionUpdate collectionUpdate =
          CollectionUpdate.builder().prev(Arrays.asList(childCollection)).build();

      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder().id(1L).collections(collectionUpdate).build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNotNull(dto.getCollections());
      assertEquals(1, dto.getCollections().getPrev().size());
    }

    @Test
    @DisplayName("Should accept collection updates with newValue pattern")
    void shouldAcceptCollectionUpdatesWithNewValue() {
      ChildCollection childCollection1 =
          ChildCollection.builder().collectionId(20L).visible(true).orderIndex(5).build();

      ChildCollection childCollection2 =
          ChildCollection.builder().collectionId(21L).visible(false).orderIndex(10).build();

      CollectionUpdate collectionUpdate =
          CollectionUpdate.builder()
              .newValue(Arrays.asList(childCollection1, childCollection2))
              .build();

      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder().id(1L).collections(collectionUpdate).build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNotNull(dto.getCollections());
      assertEquals(2, dto.getCollections().getNewValue().size());
    }

    @Test
    @DisplayName("Should accept collection updates with remove pattern")
    void shouldAcceptCollectionUpdatesWithRemove() {
      CollectionUpdate collectionUpdate =
          CollectionUpdate.builder().remove(Arrays.asList(3L, 7L, 9L)).build();

      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder().id(1L).collections(collectionUpdate).build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNotNull(dto.getCollections());
      assertEquals(3, dto.getCollections().getRemove().size());
    }

    @Test
    @DisplayName("Should accept collection updates with all patterns combined")
    void shouldAcceptCollectionUpdatesWithAllPatterns() {
      ChildCollection prevCollection =
          ChildCollection.builder().collectionId(1L).visible(true).orderIndex(0).build();

      ChildCollection newCollection =
          ChildCollection.builder().collectionId(2L).visible(true).orderIndex(5).build();

      CollectionUpdate collectionUpdate =
          CollectionUpdate.builder()
              .prev(Arrays.asList(prevCollection))
              .newValue(Collections.singletonList(newCollection))
              .remove(Arrays.asList(3L, 4L))
              .build();

      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder().id(1L).collections(collectionUpdate).build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNotNull(dto.getCollections());
      assertEquals(1, dto.getCollections().getPrev().size());
      assertEquals(1, dto.getCollections().getNewValue().size());
      assertEquals(2, dto.getCollections().getRemove().size());
    }

    @Test
    @DisplayName("Should accept null collections for partial updates")
    void shouldAcceptNullCollections() {
      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder().id(1L).collections(null).build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNull(dto.getCollections());
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    @DisplayName("Should require ID field")
    void shouldRequireIdField() {
      CollectionUpdateRequest dto = CollectionUpdateRequest.builder().title("Test").build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertFalse(violations.isEmpty());
      assertTrue(
          violations.stream()
              .anyMatch(v -> v.getMessage().contains("Collection ID is required for updates")));
    }

    @Test
    @DisplayName("Should validate title length constraints")
    void shouldValidateTitleLengthConstraints() {
      // Too short
      CollectionUpdateRequest shortDto =
          CollectionUpdateRequest.builder().id(1L).title("AB").build();

      Set<ConstraintViolation<CollectionUpdateRequest>> shortViolations =
          validator.validate(shortDto);
      assertFalse(shortViolations.isEmpty());
      assertTrue(
          shortViolations.stream()
              .anyMatch(
                  v -> v.getMessage().contains("Title must be between 3 and 100 characters")));

      // Too long
      String longTitle = "A".repeat(101);
      CollectionUpdateRequest longDto =
          CollectionUpdateRequest.builder().id(1L).title(longTitle).build();

      Set<ConstraintViolation<CollectionUpdateRequest>> longViolations =
          validator.validate(longDto);
      assertFalse(longViolations.isEmpty());
      assertTrue(
          longViolations.stream()
              .anyMatch(
                  v -> v.getMessage().contains("Title must be between 3 and 100 characters")));
    }

    @Test
    @DisplayName("Should validate slug length constraints")
    void shouldValidateSlugLengthConstraints() {
      // Too short
      CollectionUpdateRequest shortDto =
          CollectionUpdateRequest.builder().id(1L).slug("AB").build();

      Set<ConstraintViolation<CollectionUpdateRequest>> shortViolations =
          validator.validate(shortDto);
      assertFalse(shortViolations.isEmpty());
      assertTrue(
          shortViolations.stream()
              .anyMatch(v -> v.getMessage().contains("Slug must be between 3 and 150 characters")));

      // Too long
      String longSlug = "a".repeat(151);
      CollectionUpdateRequest longDto =
          CollectionUpdateRequest.builder().id(1L).slug(longSlug).build();

      Set<ConstraintViolation<CollectionUpdateRequest>> longViolations =
          validator.validate(longDto);
      assertFalse(longViolations.isEmpty());
      assertTrue(
          longViolations.stream()
              .anyMatch(v -> v.getMessage().contains("Slug must be between 3 and 150 characters")));
    }

    @Test
    @DisplayName("Should validate description length constraint")
    void shouldValidateDescriptionLengthConstraint() {
      String longDescription = "A".repeat(501);
      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder().id(1L).description(longDescription).build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertFalse(violations.isEmpty());
      assertTrue(
          violations.stream()
              .anyMatch(v -> v.getMessage().contains("Description cannot exceed 500 characters")));
    }

    @Test
    @DisplayName("Should validate location length constraint")
    void shouldValidateLocationLengthConstraint() {
      String longLocation = "A".repeat(256);
      CollectionUpdateRequest dto =
          CollectionUpdateRequest.builder()
              .id(1L)
              .location(LocationUpdate.builder().newValue(longLocation).build())
              .build();

      Set<ConstraintViolation<CollectionUpdateRequest>> violations = validator.validate(dto);
      assertFalse(violations.isEmpty());
      assertTrue(
          violations.stream()
              .anyMatch(v -> v.getMessage().contains("Location cannot exceed 255 characters")));
    }
  }
}
