# Testing Patterns

## Naming Convention

```
methodName_scenario_expectedResult()
```

Examples:

- `testValidContentImage()` - validates entity with all fields
- `testInvalidContentImageMissingRequiredField()` - missing required field
- `testGetContentTypeReturnsImage()` - verifies enum return
- `testEqualsAndHashCode()` - Lombok-generated methods

## Test Structure (AAA Pattern)

```java
@Test
void methodName_scenario_expectedResult() {
    // Arrange - set up test data
    ContentImageEntity image = ContentImageEntity.builder()
        .contentType(ContentType.IMAGE)
        .imageUrlWeb("https://example.com/image.jpg")
        .build();

    // Act - perform the action
    Set<ConstraintViolation<ContentImageEntity>> violations =
        validator.validate(image);

    // Assert - verify results
    assertTrue(violations.isEmpty());
    assertEquals(ContentType.IMAGE, image.getContentType());
}
```

## Test Categories

### Entity Tests

- Validation annotations work (`@NotNull`, etc.)
- Builder pattern functions correctly
- `equals()` and `hashCode()` behave properly
- Type-specific methods return expected values

### Service Tests

- Business logic correctness
- Entity-to-model conversion
- Error handling paths
- Edge cases (null, empty collections)

### Controller Tests

- HTTP status codes
- Request/response serialization
- Parameter validation
- Error response format

## Common Setup

```java
private Validator validator;

@BeforeEach
void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
}
```

## What to Test

- Required field validation
- Business logic branches
- Conversion between entities and models
- Error conditions and edge cases

## What NOT to Test

- Lombok-generated getters/setters (unless custom logic)
- Framework behavior (Spring, JPA)
- Third-party library internals

## Running Tests

```bash
mvn test                           # All tests
mvn test -Dtest=ContentImageEntityTest  # Single class
mvn test -Dtest=*ServiceTest       # Pattern match
```
