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
 * Unit tests for CollectionRequests.Update Tests partial update validation,
 * password handling, and
 * relationship updates using prev/new/remove pattern
 */
class CollectionUpdateRequestTest {


  private Validator validator;

  /** Build Update with only non-null args; order: id, type, title, slug, description, location, collectionDate, visible, displayMode, password, contentPerPage, rowsWide, coverImageId, tags, people, collections */
  private static CollectionRequests.Update update(
      Long id,
      CollectionType type,
      String title,
      String slug,
      String description,
      CollectionRequests.LocationUpdate location,
      LocalDate collectionDate,
      Boolean visible,
      CollectionBaseModel.DisplayMode displayMode,
      String password,
      Integer contentPerPage,
      Integer rowsWide,
      Long coverImageId,
      CollectionRequests.TagUpdate tags,
      CollectionRequests.PersonUpdate people,
      CollectionRequests.CollectionUpdate collections) {
    return new CollectionRequests.Update(
        id, type, title, slug, description, location, collectionDate, visible, displayMode,
        password, contentPerPage, rowsWide, coverImageId, tags, people, collections);
  }

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

      CollectionRequests.Update dto =
          update(
              1L,
              CollectionType.CLIENT_GALLERY,
              "Updated Client Gallery",
              "updated-client-gallery",
              "Updated professional client gallery",
              new CollectionRequests.LocationUpdate(null, "Updated Location", null),
              today,
              true,
              null,
              "newpassword123",
              25,
              null,
              null,
              null,
              null,
              null);

      assertNotNull(dto);
      assertEquals(1L, dto.id());
      assertEquals(CollectionType.CLIENT_GALLERY, dto.type());
      assertEquals("Updated Client Gallery", dto.title());
      assertEquals("updated-client-gallery", dto.slug());
      assertEquals("Updated professional client gallery", dto.description());
      assertNotNull(dto.location());
      assertEquals("Updated Location", dto.location().newValue());
      assertEquals(today, dto.collectionDate());
      assertTrue(dto.visible());
      assertEquals(25, dto.contentPerPage());
    }

    @Test
    @DisplayName("Should create DTO with minimal fields for partial update")
    void shouldCreateDTOWithMinimalFields() {
      CollectionRequests.Update dto = update(1L, null, "Updated Title", null, null, null, null, null, null, null, null, null, null, null, null, null);

      assertNotNull(dto);
      assertEquals(1L, dto.id());
      assertEquals("Updated Title", dto.title());
      assertNull(dto.type());
      assertNull(dto.slug());
      assertNull(dto.password());
      assertNull(dto.contentPerPage());
    }

    @Test
    @DisplayName("Should create DTO with only content operations")
    void shouldCreateDTOWithOnlyContentOperations() {
      // List<Long> idsToRemove = Arrays.asList(5L, 10L);
      // List<String> newTextContent = Arrays.asList("Just added this text");

      CollectionRequests.Update dto = update(1L, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

      assertNotNull(dto);
      assertEquals(1L, dto.id());
      assertNull(dto.title());
    }

    @Test
    @DisplayName("Should create DTO with all nulls (validation will fail for id)")
    void shouldCreateDTOWithNoArgsConstructor() {
      CollectionRequests.Update dto = update(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

      assertNotNull(dto);
      assertNull(dto.id());
      assertNull(dto.type());
      assertNull(dto.title());
      assertNull(dto.password());
      assertNull(dto.contentPerPage());
    }
  }

  @Nested
  @DisplayName("Password Validation Tests")
  class PasswordValidationTests {

    @Test
    @DisplayName("Should accept valid password")
    void shouldAcceptValidPassword() {
      CollectionRequests.Update dto =
          update(1L, CollectionType.CLIENT_GALLERY, "Client Gallery", "client-gallery", null, null, null, null, null, "validpass123", null, null, null, null, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertEquals("validpass123", dto.password());
    }

    @Test
    @DisplayName("Should accept null password for partial updates")
    void shouldAcceptNullPassword() {
      CollectionRequests.Update dto =
          update(1L, CollectionType.BLOG, "Blog Update", "blog-update", null, null, null, null, null, null, null, null, null, null, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNull(dto.password());
    }

    @Test
    @DisplayName("Should reject password that is too short")
    void shouldRejectPasswordTooShort() {
      CollectionRequests.Update dto =
          update(1L, CollectionType.CLIENT_GALLERY, "Client Gallery", "client-gallery", null, null, null, null, null, "short", null, null, null, null, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
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

      CollectionRequests.Update dto =
          update(1L, CollectionType.CLIENT_GALLERY, "Client Gallery", "client-gallery", null, null, null, null, null, longPassword, null, null, null, null, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
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
      CollectionRequests.Update minDto =
          update(1L, CollectionType.CLIENT_GALLERY, "Client Gallery", "client-gallery", null, null, null, null, null, "12345678", null, null, null, null, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> minViolations = validator.validate(minDto);
      assertTrue(minViolations.isEmpty());

      // Test maximum boundary
      String maxPassword = "a".repeat(100); // Exactly 100 characters
      CollectionRequests.Update maxDto =
          update(1L, CollectionType.CLIENT_GALLERY, "Client Gallery", "client-gallery", null, null, null, null, null, maxPassword, null, null, null, null, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> maxViolations = validator.validate(maxDto);
      assertTrue(maxViolations.isEmpty());
    }
  }

  @Nested
  @DisplayName("Content Per Page Validation Tests")
  class ContentPerPageValidationTests {

    @Test
    @DisplayName("Should accept valid contentPerPage")
    void shouldAcceptValidContentPerPage() {
      CollectionRequests.Update dto =
          update(1L, CollectionType.PORTFOLIO, "Portfolio", "portfolio", null, null, null, null, null, null, 30, null, null, null, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertEquals(30, dto.contentPerPage());
    }

    @Test
    @DisplayName("Should accept null contentPerPage for partial updates")
    void shouldAcceptNullContentPerPage() {
      CollectionRequests.Update dto =
          update(1L, CollectionType.ART_GALLERY, "Art Gallery", "art-gallery", null, null, null, null, null, null, null, null, null, null, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNull(dto.contentPerPage());
    }

    @Test
    @DisplayName("Should reject contentPerPage below minimum")
    void shouldRejectContentPerPageBelowMin() {
      CollectionRequests.Update dto =
          update(1L, CollectionType.BLOG, "Blog", "blog", null, null, null, null, null, null, 0, null, null, null, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertFalse(violations.isEmpty());
      assertTrue(
          violations.stream()
              .anyMatch(v -> v.getMessage().contains("Content per page must be 1 or greater")));
    }

    @Test
    @DisplayName("Should accept contentPerPage at minimum boundary")
    void shouldAcceptContentPerPageAtMinBoundary() {
      CollectionRequests.Update dto =
          update(1L, CollectionType.PORTFOLIO, "Portfolio", "portfolio", null, null, null, null, null, null, 1, null, null, null, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
    }
  }

  @Nested
  @DisplayName("Display Mode Tests")
  class DisplayModeTests {

    @Test
    @DisplayName("Should accept CHRONOLOGICAL display mode")
    void shouldAcceptChronologicalDisplayMode() {
      CollectionRequests.Update dto =
          update(1L, CollectionType.BLOG, null, null, null, null, null, null, CollectionBaseModel.DisplayMode.CHRONOLOGICAL, null, null, null, null, null, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertEquals(CollectionBaseModel.DisplayMode.CHRONOLOGICAL, dto.displayMode());
    }

    @Test
    @DisplayName("Should accept ORDERED display mode")
    void shouldAcceptOrderedDisplayMode() {
      CollectionRequests.Update dto =
          update(1L, CollectionType.PORTFOLIO, null, null, null, null, null, null, CollectionBaseModel.DisplayMode.ORDERED, null, null, null, null, null, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertEquals(CollectionBaseModel.DisplayMode.ORDERED, dto.displayMode());
    }

    @Test
    @DisplayName("Should accept null display mode for partial updates")
    void shouldAcceptNullDisplayMode() {
      CollectionRequests.Update dto =
          update(1L, CollectionType.ART_GALLERY, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNull(dto.displayMode());
    }
  }

  @Nested
  @DisplayName("Cover Image Tests")
  class CoverImageTests {

    @Test
    @DisplayName("Should accept valid cover image ID")
    void shouldAcceptValidCoverImageId() {
      Long imageId = 123L;
      CollectionRequests.Update dto = update(1L, null, null, null, null, null, null, null, null, null, null, null, imageId, null, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertEquals(imageId, dto.coverImageId());
    }

    @Test
    @DisplayName("Should accept null cover image for partial updates")
    void shouldAcceptNullCoverImage() {
      CollectionRequests.Update dto = update(1L, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNull(dto.coverImageId());
    }

    @Test
    @DisplayName("Should accept cover image ID of zero to clear cover image")
    void shouldAcceptCoverImageIdZero() {
      CollectionRequests.Update dto = update(1L, null, null, null, null, null, null, null, null, null, null, null, 0L, null, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertEquals(0L, dto.coverImageId());
    }

    @Test
    @DisplayName("Should accept valid cover image ID values")
    void shouldAcceptValidCoverImageIdValues() {
      CollectionRequests.Update dto = update(1L, null, null, null, null, null, null, null, null, null, null, null, 456L, null, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertEquals(456L, dto.coverImageId());
    }
  }

  @Nested
  @DisplayName("Tag Update Tests")
  class TagUpdateTests {

    @Test
    @DisplayName("Should accept tag updates with prev pattern")
    void shouldAcceptTagUpdatesWithPrev() {
      CollectionRequests.TagUpdate tagUpdate = new CollectionRequests.TagUpdate(Arrays.asList(1L, 2L, 3L), null, null);

      CollectionRequests.Update dto = update(1L, null, null, null, null, null, null, null, null, null, null, null, null, tagUpdate, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNotNull(dto.tags());
      assertEquals(3, dto.tags().prev().size());
    }

    @Test
    @DisplayName("Should accept tag updates with newValue pattern")
    void shouldAcceptTagUpdatesWithNewValue() {
      CollectionRequests.TagUpdate tagUpdate =
          new CollectionRequests.TagUpdate(null, Arrays.asList("landscape", "nature", "photography"), null);

      CollectionRequests.Update dto = update(1L, null, null, null, null, null, null, null, null, null, null, null, null, tagUpdate, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNotNull(dto.tags());
      assertEquals(3, dto.tags().newValue().size());
    }

    @Test
    @DisplayName("Should accept tag updates with remove pattern")
    void shouldAcceptTagUpdatesWithRemove() {
      CollectionRequests.TagUpdate tagUpdate = new CollectionRequests.TagUpdate(null, null, Arrays.asList(5L, 10L));

      CollectionRequests.Update dto = update(1L, null, null, null, null, null, null, null, null, null, null, null, null, tagUpdate, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNotNull(dto.tags());
      assertEquals(2, dto.tags().remove().size());
    }

    @Test
    @DisplayName("Should accept tag updates with all patterns combined")
    void shouldAcceptTagUpdatesWithAllPatterns() {
      CollectionRequests.TagUpdate tagUpdate =
          new CollectionRequests.TagUpdate(
              Arrays.asList(1L, 2L), Arrays.asList("new-tag-1", "new-tag-2"), Arrays.asList(3L));

      CollectionRequests.Update dto = update(1L, null, null, null, null, null, null, null, null, null, null, null, null, tagUpdate, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNotNull(dto.tags());
      assertEquals(2, dto.tags().prev().size());
      assertEquals(2, dto.tags().newValue().size());
      assertEquals(1, dto.tags().remove().size());
    }

    @Test
    @DisplayName("Should accept null tags for partial updates")
    void shouldAcceptNullTags() {
      CollectionRequests.Update dto = update(1L, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNull(dto.tags());
    }
  }

  @Nested
  @DisplayName("Person Update Tests")
  class PersonUpdateTests {

    @Test
    @DisplayName("Should accept person updates with prev pattern")
    void shouldAcceptPersonUpdatesWithPrev() {
      CollectionRequests.PersonUpdate personUpdate =
          new CollectionRequests.PersonUpdate(Arrays.asList(1L, 2L), null, null);

      CollectionRequests.Update dto = update(1L, null, null, null, null, null, null, null, null, null, null, null, null, null, personUpdate, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNotNull(dto.people());
      assertEquals(2, dto.people().prev().size());
    }

    @Test
    @DisplayName("Should accept person updates with newValue pattern")
    void shouldAcceptPersonUpdatesWithNewValue() {
      CollectionRequests.PersonUpdate personUpdate =
          new CollectionRequests.PersonUpdate(null, Arrays.asList("John Doe", "Jane Smith"), null);

      CollectionRequests.Update dto = update(1L, null, null, null, null, null, null, null, null, null, null, null, null, null, personUpdate, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNotNull(dto.people());
      assertEquals(2, dto.people().newValue().size());
    }

    @Test
    @DisplayName("Should accept person updates with remove pattern")
    void shouldAcceptPersonUpdatesWithRemove() {
      CollectionRequests.PersonUpdate personUpdate =
          new CollectionRequests.PersonUpdate(null, null, Arrays.asList(3L, 4L));

      CollectionRequests.Update dto = update(1L, null, null, null, null, null, null, null, null, null, null, null, null, null, personUpdate, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNotNull(dto.people());
      assertEquals(2, dto.people().remove().size());
    }

    @Test
    @DisplayName("Should accept person updates with all patterns combined")
    void shouldAcceptPersonUpdatesWithAllPatterns() {
      CollectionRequests.PersonUpdate personUpdate =
          new CollectionRequests.PersonUpdate(
              Arrays.asList(1L), Arrays.asList("Alice Johnson"), Arrays.asList(5L, 6L));

      CollectionRequests.Update dto = update(1L, null, null, null, null, null, null, null, null, null, null, null, null, null, personUpdate, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNotNull(dto.people());
      assertEquals(1, dto.people().prev().size());
      assertEquals(1, dto.people().newValue().size());
      assertEquals(2, dto.people().remove().size());
    }

    @Test
    @DisplayName("Should accept null people for partial updates")
    void shouldAcceptNullPeople() {
      CollectionRequests.Update dto = update(1L, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNull(dto.people());
    }
  }

  @Nested
  @DisplayName("Collection Update Tests")
  class CollectionUpdateTests {

    @Test
    @DisplayName("Should accept collection updates with prev pattern")
    void shouldAcceptCollectionUpdatesWithPrev() {
      Records.ChildCollection childCollection =
          new Records.ChildCollection(10L, null, null, null, true, 0);

      CollectionRequests.CollectionUpdate collectionUpdate =
          new CollectionRequests.CollectionUpdate(Arrays.asList(childCollection), null, null);

      CollectionRequests.Update dto = update(1L, null, null, null, null, null, null, null, null, null, null, null, null, null, null, collectionUpdate);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNotNull(dto.collections());
      assertEquals(1, dto.collections().prev().size());
    }

    @Test
    @DisplayName("Should accept collection updates with newValue pattern")
    void shouldAcceptCollectionUpdatesWithNewValue() {
      Records.ChildCollection childCollection1 =
          new Records.ChildCollection(20L, null, null, null, true, 5);

      Records.ChildCollection childCollection2 =
          new Records.ChildCollection(21L, null, null, null, false, 10);

      CollectionRequests.CollectionUpdate collectionUpdate =
          new CollectionRequests.CollectionUpdate(null, Arrays.asList(childCollection1, childCollection2), null);

      CollectionRequests.Update dto = update(1L, null, null, null, null, null, null, null, null, null, null, null, null, null, null, collectionUpdate);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNotNull(dto.collections());
      assertEquals(2, dto.collections().newValue().size());
    }

    @Test
    @DisplayName("Should accept collection updates with remove pattern")
    void shouldAcceptCollectionUpdatesWithRemove() {
      CollectionRequests.CollectionUpdate collectionUpdate =
          new CollectionRequests.CollectionUpdate(null, null, Arrays.asList(3L, 7L, 9L));

      CollectionRequests.Update dto = update(1L, null, null, null, null, null, null, null, null, null, null, null, null, null, null, collectionUpdate);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNotNull(dto.collections());
      assertEquals(3, dto.collections().remove().size());
    }

    @Test
    @DisplayName("Should accept collection updates with all patterns combined")
    void shouldAcceptCollectionUpdatesWithAllPatterns() {
      Records.ChildCollection prevCollection =
          new Records.ChildCollection(1L, null, null, null, true, 0);

      Records.ChildCollection newCollection =
          new Records.ChildCollection(2L, null, null, null, true, 5);

      CollectionRequests.CollectionUpdate collectionUpdate =
          new CollectionRequests.CollectionUpdate(
              Arrays.asList(prevCollection),
              Collections.singletonList(newCollection),
              Arrays.asList(3L, 4L));

      CollectionRequests.Update dto = update(1L, null, null, null, null, null, null, null, null, null, null, null, null, null, null, collectionUpdate);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNotNull(dto.collections());
      assertEquals(1, dto.collections().prev().size());
      assertEquals(1, dto.collections().newValue().size());
      assertEquals(2, dto.collections().remove().size());
    }

    @Test
    @DisplayName("Should accept null collections for partial updates")
    void shouldAcceptNullCollections() {
      CollectionRequests.Update dto = update(1L, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
      assertNull(dto.collections());
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    @DisplayName("Should require ID field")
    void shouldRequireIdField() {
      CollectionRequests.Update dto = update(null, null, "Test", null, null, null, null, null, null, null, null, null, null, null, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertFalse(violations.isEmpty());
      assertTrue(
          violations.stream()
              .anyMatch(v -> v.getMessage().contains("Collection ID is required for updates")));
    }

    @Test
    @DisplayName("Should validate title length constraints")
    void shouldValidateTitleLengthConstraints() {
      // Too short
      CollectionRequests.Update shortDto = update(1L, null, "AB", null, null, null, null, null, null, null, null, null, null, null, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> shortViolations = validator.validate(shortDto);
      assertFalse(shortViolations.isEmpty());
      assertTrue(
          shortViolations.stream()
              .anyMatch(
                  v -> v.getMessage().contains("Title must be between 3 and 100 characters")));

      // Too long
      String longTitle = "A".repeat(101);
      CollectionRequests.Update longDto = update(1L, null, longTitle, null, null, null, null, null, null, null, null, null, null, null, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> longViolations = validator.validate(longDto);
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
      CollectionRequests.Update shortDto = update(1L, null, null, "AB", null, null, null, null, null, null, null, null, null, null, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> shortViolations = validator.validate(shortDto);
      assertFalse(shortViolations.isEmpty());
      assertTrue(
          shortViolations.stream()
              .anyMatch(v -> v.getMessage().contains("Slug must be between 3 and 150 characters")));

      // Too long
      String longSlug = "a".repeat(151);
      CollectionRequests.Update longDto = update(1L, null, null, longSlug, null, null, null, null, null, null, null, null, null, null, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> longViolations = validator.validate(longDto);
      assertFalse(longViolations.isEmpty());
      assertTrue(
          longViolations.stream()
              .anyMatch(v -> v.getMessage().contains("Slug must be between 3 and 150 characters")));
    }

    @Test
    @DisplayName("Should validate description length constraint")
    void shouldValidateDescriptionLengthConstraint() {
      String longDescription = "A".repeat(501);
      CollectionRequests.Update dto = update(1L, null, null, null, longDescription, null, null, null, null, null, null, null, null, null, null, null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertFalse(violations.isEmpty());
      assertTrue(
          violations.stream()
              .anyMatch(v -> v.getMessage().contains("Description cannot exceed 500 characters")));
    }

    @Test
    @DisplayName("Should validate location length constraint")
    void shouldValidateLocationLengthConstraint() {
      String longLocation = "A".repeat(256);
      CollectionRequests.Update dto =
          update(
              1L,
              null,
              null,
              null,
              null,
              new CollectionRequests.LocationUpdate(null, longLocation, null),
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertFalse(violations.isEmpty());
      assertTrue(
          violations.stream()
              .anyMatch(v -> v.getMessage().contains("Location cannot exceed 255 characters")));
    }
  }
}
