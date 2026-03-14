package edens.zac.portfolio.backend.model;

import static org.assertj.core.api.Assertions.assertThat;

import edens.zac.portfolio.backend.types.CollectionType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Validates Jakarta Bean Validation annotations on ContentRequests and CollectionRequests records.
 */
class ContentRequestsTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  // ---------------------------------------------------------------------------
  // ContentRequests.CreateTag
  // ---------------------------------------------------------------------------

  @Test
  void createTag_valid_noViolations() {
    var tag = new ContentRequests.CreateTag("landscape");
    Set<ConstraintViolation<ContentRequests.CreateTag>> violations = validator.validate(tag);
    assertThat(violations).isEmpty();
  }

  @Test
  void createTag_nullTagName_hasViolation() {
    var tag = new ContentRequests.CreateTag(null);
    Set<ConstraintViolation<ContentRequests.CreateTag>> violations = validator.validate(tag);
    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getMessage().contains("Tag name is required"));
  }

  @Test
  void createTag_blankTagName_hasViolation() {
    var tag = new ContentRequests.CreateTag("   ");
    Set<ConstraintViolation<ContentRequests.CreateTag>> violations = validator.validate(tag);
    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getMessage().contains("Tag name is required"));
  }

  @Test
  void createTag_tagNameExceeds50Chars_hasViolation() {
    String longName = "a".repeat(51);
    var tag = new ContentRequests.CreateTag(longName);
    Set<ConstraintViolation<ContentRequests.CreateTag>> violations = validator.validate(tag);
    assertThat(violations).isNotEmpty();
    assertThat(violations)
        .anyMatch(v -> v.getMessage().contains("Tag name must be between 1 and 50 characters"));
  }

  @Test
  void createTag_tagNameExactly50Chars_noViolations() {
    String exactName = "a".repeat(50);
    var tag = new ContentRequests.CreateTag(exactName);
    Set<ConstraintViolation<ContentRequests.CreateTag>> violations = validator.validate(tag);
    assertThat(violations).isEmpty();
  }

  // ---------------------------------------------------------------------------
  // ContentRequests.CreatePerson
  // ---------------------------------------------------------------------------

  @Test
  void createPerson_valid_noViolations() {
    var person = new ContentRequests.CreatePerson("John Doe");
    Set<ConstraintViolation<ContentRequests.CreatePerson>> violations = validator.validate(person);
    assertThat(violations).isEmpty();
  }

  @Test
  void createPerson_nullPersonName_hasViolation() {
    var person = new ContentRequests.CreatePerson(null);
    Set<ConstraintViolation<ContentRequests.CreatePerson>> violations = validator.validate(person);
    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getMessage().contains("Person name is required"));
  }

  @Test
  void createPerson_blankPersonName_hasViolation() {
    var person = new ContentRequests.CreatePerson("");
    Set<ConstraintViolation<ContentRequests.CreatePerson>> violations = validator.validate(person);
    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getMessage().contains("Person name is required"));
  }

  @Test
  void createPerson_personNameExceeds100Chars_hasViolation() {
    String longName = "a".repeat(101);
    var person = new ContentRequests.CreatePerson(longName);
    Set<ConstraintViolation<ContentRequests.CreatePerson>> violations = validator.validate(person);
    assertThat(violations).isNotEmpty();
    assertThat(violations)
        .anyMatch(v -> v.getMessage().contains("Person name must be between 1 and 100 characters"));
  }

  // ---------------------------------------------------------------------------
  // ContentRequests.NewFilmType
  // ---------------------------------------------------------------------------

  @Test
  void newFilmType_valid_noViolations() {
    var filmType = new ContentRequests.NewFilmType("Kodak Portra 400", 400);
    Set<ConstraintViolation<ContentRequests.NewFilmType>> violations = validator.validate(filmType);
    assertThat(violations).isEmpty();
  }

  @Test
  void newFilmType_nullDefaultIso_hasViolation() {
    var filmType = new ContentRequests.NewFilmType("Kodak Portra 400", null);
    Set<ConstraintViolation<ContentRequests.NewFilmType>> violations = validator.validate(filmType);
    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getMessage().contains("Default ISO is required"));
  }

  @Test
  void newFilmType_negativeDefaultIso_hasViolation() {
    var filmType = new ContentRequests.NewFilmType("Kodak Portra 400", -100);
    Set<ConstraintViolation<ContentRequests.NewFilmType>> violations = validator.validate(filmType);
    assertThat(violations).isNotEmpty();
    assertThat(violations)
        .anyMatch(v -> v.getMessage().contains("Default ISO must be a positive integer"));
  }

  @Test
  void newFilmType_zeroDefaultIso_hasViolation() {
    var filmType = new ContentRequests.NewFilmType("Kodak Portra 400", 0);
    Set<ConstraintViolation<ContentRequests.NewFilmType>> violations = validator.validate(filmType);
    assertThat(violations).isNotEmpty();
    assertThat(violations)
        .anyMatch(v -> v.getMessage().contains("Default ISO must be a positive integer"));
  }

  @Test
  void newFilmType_blankFilmTypeName_hasViolation() {
    var filmType = new ContentRequests.NewFilmType("", 400);
    Set<ConstraintViolation<ContentRequests.NewFilmType>> violations = validator.validate(filmType);
    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getMessage().contains("Film type name is required"));
  }

  @Test
  void newFilmType_nullFilmTypeName_hasViolation() {
    var filmType = new ContentRequests.NewFilmType(null, 400);
    Set<ConstraintViolation<ContentRequests.NewFilmType>> violations = validator.validate(filmType);
    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getMessage().contains("Film type name is required"));
  }

  // ---------------------------------------------------------------------------
  // ContentRequests.CreateTextContent
  // ---------------------------------------------------------------------------

  @Test
  void createTextContent_valid_noViolations() {
    var content = new ContentRequests.CreateTextContent(1L, "Title", "Desc", "Some text", null);
    Set<ConstraintViolation<ContentRequests.CreateTextContent>> violations =
        validator.validate(content);
    assertThat(violations).isEmpty();
  }

  @Test
  void createTextContent_nullCollectionId_hasViolation() {
    var content = new ContentRequests.CreateTextContent(null, "Title", "Desc", "Some text", null);
    Set<ConstraintViolation<ContentRequests.CreateTextContent>> violations =
        validator.validate(content);
    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getMessage().contains("Collection ID is required"));
  }

  @Test
  void createTextContent_blankTextContent_hasViolation() {
    var content = new ContentRequests.CreateTextContent(1L, "Title", "Desc", "  ", null);
    Set<ConstraintViolation<ContentRequests.CreateTextContent>> violations =
        validator.validate(content);
    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getMessage().contains("Text content is required"));
  }

  @Test
  void createTextContent_nullTextContent_hasViolation() {
    var content = new ContentRequests.CreateTextContent(1L, "Title", "Desc", null, null);
    Set<ConstraintViolation<ContentRequests.CreateTextContent>> violations =
        validator.validate(content);
    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getMessage().contains("Text content is required"));
  }

  // ---------------------------------------------------------------------------
  // CollectionRequests.Create
  // ---------------------------------------------------------------------------

  @Test
  void collectionCreate_valid_noViolations() {
    var create = new CollectionRequests.Create(CollectionType.BLOG, "My Blog");
    Set<ConstraintViolation<CollectionRequests.Create>> violations = validator.validate(create);
    assertThat(violations).isEmpty();
  }

  @Test
  void collectionCreate_nullType_hasViolation() {
    var create = new CollectionRequests.Create(null, "My Blog");
    Set<ConstraintViolation<CollectionRequests.Create>> violations = validator.validate(create);
    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getMessage().contains("Type is required"));
  }

  @Test
  void collectionCreate_nullTitle_hasViolation() {
    var create = new CollectionRequests.Create(CollectionType.BLOG, null);
    Set<ConstraintViolation<CollectionRequests.Create>> violations = validator.validate(create);
    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getMessage().contains("Title is required"));
  }

  @Test
  void collectionCreate_titleTooShort_hasViolation() {
    var create = new CollectionRequests.Create(CollectionType.BLOG, "AB");
    Set<ConstraintViolation<CollectionRequests.Create>> violations = validator.validate(create);
    assertThat(violations).isNotEmpty();
    assertThat(violations)
        .anyMatch(v -> v.getMessage().contains("Title must be between 3 and 100 characters"));
  }

  @Test
  void collectionCreate_titleExactly3Chars_noViolations() {
    var create = new CollectionRequests.Create(CollectionType.BLOG, "ABC");
    Set<ConstraintViolation<CollectionRequests.Create>> violations = validator.validate(create);
    assertThat(violations).isEmpty();
  }

  @Test
  void collectionCreate_titleExceeds100Chars_hasViolation() {
    String longTitle = "a".repeat(101);
    var create = new CollectionRequests.Create(CollectionType.BLOG, longTitle);
    Set<ConstraintViolation<CollectionRequests.Create>> violations = validator.validate(create);
    assertThat(violations).isNotEmpty();
    assertThat(violations)
        .anyMatch(v -> v.getMessage().contains("Title must be between 3 and 100 characters"));
  }

  @Test
  void collectionCreate_titleExactly100Chars_noViolations() {
    String exactTitle = "a".repeat(100);
    var create = new CollectionRequests.Create(CollectionType.BLOG, exactTitle);
    Set<ConstraintViolation<CollectionRequests.Create>> violations = validator.validate(create);
    assertThat(violations).isEmpty();
  }
}
