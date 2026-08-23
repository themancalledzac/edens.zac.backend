package edens.zac.portfolio.backend.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edens.zac.portfolio.backend.types.CollectionVisibility;
import edens.zac.portfolio.backend.types.DisplayMode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for CollectionRequests.Update */
class CollectionUpdateRequestTest {

  private Validator validator;

  /**
   * Build Update with only non-null args; order: id, title, slug, description, location,
   * collectionDate, clearCollectionDate, visibility, displayMode, contentPerPage, rowsWide,
   * coverImageId, tags, people, collections. Rating is always passed as null.
   */
  private static CollectionRequests.Update update(
      Long id,
      String title,
      String slug,
      String description,
      CollectionRequests.LocationUpdate location,
      LocalDate collectionDate,
      Boolean clearCollectionDate,
      CollectionVisibility visibility,
      DisplayMode displayMode,
      Integer contentPerPage,
      Integer rowsWide,
      Long coverImageId,
      CollectionRequests.TagUpdate tags,
      CollectionRequests.PersonUpdate people,
      CollectionRequests.CollectionUpdate collections) {
    return new CollectionRequests.Update(
        id,
        title,
        slug,
        description,
        location,
        collectionDate,
        clearCollectionDate,
        visibility,
        null,
        displayMode,
        contentPerPage,
        rowsWide,
        coverImageId,
        tags,
        people,
        collections,
        null);
  }

  @BeforeEach
  void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    @DisplayName("Should require ID field")
    void shouldRequireIdField() {
      CollectionRequests.Update dto =
          update(
              null, "Test", null, null, null, null, null, null, null, null, null, null, null, null,
              null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertFalse(violations.isEmpty());
      assertTrue(
          violations.stream()
              .anyMatch(v -> v.getMessage().contains("Collection ID is required for updates")));
    }

    @Test
    @DisplayName("Should validate title length constraints")
    void shouldValidateTitleLengthConstraints() {
      CollectionRequests.Update shortDto =
          update(
              1L, "AB", null, null, null, null, null, null, null, null, null, null, null, null,
              null);

      Set<ConstraintViolation<CollectionRequests.Update>> shortViolations =
          validator.validate(shortDto);
      assertFalse(shortViolations.isEmpty());
      assertTrue(
          shortViolations.stream()
              .anyMatch(
                  v -> v.getMessage().contains("Title must be between 3 and 100 characters")));

      String longTitle = "A".repeat(101);
      CollectionRequests.Update longDto =
          update(
              1L, longTitle, null, null, null, null, null, null, null, null, null, null, null, null,
              null);

      Set<ConstraintViolation<CollectionRequests.Update>> longViolations =
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
      CollectionRequests.Update shortDto =
          update(
              1L, null, "AB", null, null, null, null, null, null, null, null, null, null, null,
              null);

      Set<ConstraintViolation<CollectionRequests.Update>> shortViolations =
          validator.validate(shortDto);
      assertFalse(shortViolations.isEmpty());
      assertTrue(
          shortViolations.stream()
              .anyMatch(v -> v.getMessage().contains("Slug must be between 3 and 150 characters")));

      String longSlug = "a".repeat(151);
      CollectionRequests.Update longDto =
          update(
              1L, null, longSlug, null, null, null, null, null, null, null, null, null, null, null,
              null);

      Set<ConstraintViolation<CollectionRequests.Update>> longViolations =
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
      CollectionRequests.Update dto =
          update(
              1L,
              null,
              null,
              longDescription,
              null,
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
              .anyMatch(v -> v.getMessage().contains("Description cannot exceed 500 characters")));
    }

    @Test
    @DisplayName("Should reject contentPerPage below minimum")
    void shouldRejectContentPerPageBelowMin() {
      CollectionRequests.Update dto =
          update(
              1L, "Blog", "blog", null, null, null, null, null, null, 0, null, null, null, null,
              null);

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
          update(
              1L,
              "Portfolio",
              "portfolio",
              null,
              null,
              null,
              null,
              null,
              null,
              1,
              null,
              null,
              null,
              null,
              null);

      Set<ConstraintViolation<CollectionRequests.Update>> violations = validator.validate(dto);
      assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Frontend sends plural 'locations' JSON key - must populate location update")
    void shouldDeserializePluralLocationsJsonKey() throws Exception {
      // Regression guard: the manage page PUTs the location association under the PLURAL
      // key "locations" (matching tags/people/collections). When the DTO field was singular
      // "location", Jackson silently dropped this key and collection location edits never saved.
      ObjectMapper mapper = new ObjectMapper();
      String json = "{ \"id\": 102, \"locations\": { \"prev\": [3] } }";

      CollectionRequests.Update dto = mapper.readValue(json, CollectionRequests.Update.class);

      assertNotNull(dto.locations(), "plural 'locations' key must deserialize into the DTO");
      assertEquals(List.of(3L), dto.locations().prev());
    }

    @Test
    @DisplayName("FE collection update sends tags/people/collections plural keys - all populate")
    void shouldDeserializeCollectionRelationalSiblingKeys() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      String json =
          "{\"id\":102,"
              + "\"tags\":{\"prev\":[1]},"
              + "\"people\":{\"prev\":[2]},"
              + "\"collections\":{\"remove\":[3]}}";

      CollectionRequests.Update dto = mapper.readValue(json, CollectionRequests.Update.class);

      assertNotNull(dto.tags());
      assertEquals(List.of(1L), dto.tags().prev());
      assertNotNull(dto.people());
      assertEquals(List.of(2L), dto.people().prev());
      assertNotNull(dto.collections());
      assertEquals(List.of(3L), dto.collections().remove());
    }

    @Test
    @DisplayName("collectionEndDate JSON key deserializes into the DTO (wire-contract guard)")
    void shouldDeserializeCollectionEndDateJsonKey() throws Exception {
      // Regression guard for the 2026-05-31 "locations" class of bug: a DTO component whose name
      // does not match the JSON wire name is silently dropped, and positional-constructor tests
      // never catch it because they bypass Jackson property mapping. This test deserializes real
      // JSON to prove collectionDate + collectionEndDate land on the correct fields.
      ObjectMapper mapper = new ObjectMapper();
      mapper.registerModule(new JavaTimeModule());
      String json =
          "{ \"id\": 55, \"collectionDate\": \"2026-03-05\", \"collectionEndDate\": \"2026-03-07\" }";

      CollectionRequests.Update dto = mapper.readValue(json, CollectionRequests.Update.class);

      assertEquals(LocalDate.of(2026, 3, 5), dto.collectionDate());
      assertEquals(
          LocalDate.of(2026, 3, 7),
          dto.collectionEndDate(),
          "collectionEndDate JSON key must map onto the DTO's collectionEndDate component");
    }

    @Test
    @DisplayName("clearCollectionEndDate JSON key deserializes into the DTO")
    void shouldDeserializeClearCollectionEndDateJsonKey() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      String json = "{ \"id\": 55, \"clearCollectionEndDate\": true }";

      CollectionRequests.Update dto = mapper.readValue(json, CollectionRequests.Update.class);

      assertEquals(Boolean.TRUE, dto.clearCollectionEndDate());
      assertNull(dto.collectionEndDate());
    }
  }

  @Nested
  class ChildCollectionMutualFlag {
    @Test
    void sixArgConstructor_defaultsMutualToNull() {
      Records.ChildCollection link = new Records.ChildCollection(10L, null, null, null, true, 0);

      assertThat(link.mutual()).isNull();
    }
  }
}
