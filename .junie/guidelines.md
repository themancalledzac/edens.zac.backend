# Development Guidelines for Portfolio Backend

## Project Overview
This project is a Java Spring Boot backend application for a portfolio system, currently undergoing a refactoring from a simple Catalog/Image system to a more flexible ContentCollection system.

### Key Technologies
- Java 23
- Spring Boot 3.4.1 (update)
- Spring Data JPA
- MySQL (Production) / H2 (Development)
- AWS S3 for media storage
- Lombok for reducing boilerplate
- Thymeleaf for templating
- Various image processing libraries

## Architecture Overview

### Current Refactoring: Collection System
- TODO: Update this to reflect our abstract 'collection' system, which is just a 'folder' system, where collections can store collections, as well as any other content.
The project is transitioning from a simple Collection/Image system to a flexible Collection system with four distinct types:
1. **BLOG** - Daily moments, casual content, mixed text/images, chronological
2. **ART_GALLERY** - Curated artistic collections
3. **CLIENT_GALLERY** - Private client deliveries with simple password protection
4. **PORTFOLIO** - Professional showcases

### Key Components
- **ContentCollectionEntity**: Replaces Catalog entities
- **ContentBlockEntity**: System allowing mixed content types (images, text, code, video, gifs)
- **Pagination strategy**: For collections with 30+ content blocks
- **Storage strategy**: Text/code in database, media (images/videos/gifs) in S3

## Java & Spring Boot Guidelines

### 1. Import Statements
- **ALWAYS place all imports at the top of the file** after the package declaration
- **NEVER use fully qualified class names inline** (e.g., `org.springframework.web.multipart.MultipartFile`)
- Add proper import statements instead of using fully qualified names in code
- Example of WRONG usage: `List<org.springframework.web.multipart.MultipartFile> files`
- Example of CORRECT usage: Add `import org.springframework.web.multipart.MultipartFile;` at top, then use `List<MultipartFile> files`

### 2. Dependency Injection
- **ALWAYS use constructor injection** for mandatory dependencies
- Declare dependencies as `final` fields
- No `@Autowired` needed for single constructor (Spring auto-detects)
- **NEVER use field injection** in production code
- **NEVER use setter injection** unless specifically needed for optional dependencies

### 3. Visibility & Access Control
- **Prefer package-private over public** for Spring components
- Controllers, `@Configuration` classes, and `@Bean` methods should be package-private when possible
- Only make classes/methods public when they need to be accessed from other packages

### 4. Transaction Management
- **Every Service method is a transactional unit**
- `@Transactional(readOnly = true)` for query-only methods
- `@Transactional` for data-modifying methods
- **Keep transaction scope minimal** - only what needs to be transactional

### 5. JPA Configuration
- **ALWAYS disable Open Session in View**: `spring.jpa.open-in-view=false`
- Use explicit fetch strategies instead of lazy loading in views

### 6. API Layer Separation
- **NEVER expose entities directly** in REST responses
- **ALWAYS use explicit DTOs/Records** for request/response
- Apply Jakarta Validation annotations on request DTOs
- Use record classes for immutable DTOs when possible

### 7. REST API Design
- **Versioned URLs**: `/api/v1/resource` pattern
- **Consistent resource naming**: plural nouns for collections
- **Use ResponseEntity<T>** for explicit HTTP status codes
- **Implement pagination** for unbounded collections
- **JSON responses must be objects** (not arrays) at top level for future extensibility
- **Consistent naming**: use camelCase for JSON properties

### 8. Exception Handling
- **Centralized exception handling** with `@ControllerAdvice`
- Return consistent error responses using ProblemDetails (RFC 9457)
- **NEVER expose stack traces** to clients in production
- Log exceptions appropriately based on severity

## Testing Guidelines

### 1. Testing Philosophy
- **Target 100% code coverage** gradually - add tests for new code first
- **Test behavior, not implementation details**
- **Prefer TestContainers over mocking** for integration tests
- **Use real dependencies when possible**, avoid excessive mocking
- **Test all edge cases and error conditions**

### 2. Test Structure & Organization
- **Mirror package structure** in test directory
- **One test class per production class** as a starting point
- **Clear test method names**: `methodName_scenario_expectedResult()`
- **Arrange-Act-Assert pattern** in all tests
- **Independent tests** - no test should depend on another

### 3. Spring Boot Test Types
- **`@SpringBootTest(webEnvironment = RANDOM_PORT)`** for integration tests
- **`@WebMvcTest`** for controller layer testing
- **`@DataJpaTest`** for repository layer testing
- **`@JsonTest`** for JSON serialization testing
- **Plain JUnit** for pure business logic (no Spring context needed)

## Code Quality & Performance

### 1. Logging Best Practices
- **Use SLF4J** with Logback (never System.out.println)
- **Guard expensive log operations**: `if (logger.isDebugEnabled())`
- **Never log sensitive data** (credentials, PII, secrets)
- **Structured logging** with consistent format
- **Appropriate log levels**: ERROR for problems, WARN for potential issues, INFO for important events, DEBUG for troubleshooting

### 2. Performance Considerations
- **Lazy load collections** in JPA entities appropriately
- **Use pagination** for large result sets
- **Database indexes** on frequently queried columns
- **Avoid N+1 queries** - use JOIN FETCH or @BatchSize
- **Connection pooling** configuration appropriate for load
- **Cache frequently accessed, rarely changing data**

### 3. Modern Java Practices
- **Use records** for immutable data transfer objects
- **Use switch expressions** (Java 14+) over traditional switch statements
- **Use var** for local variables when type is obvious
- **Use Optional** properly - avoid .get(), use .orElse(), .orElseThrow()
- **Use Stream API** for collection processing, but don't force it everywhere
- **Use sealed classes** when you have a known set of subtypes

## ContentCollection System Specific Guidelines

### 1. Entity Design
- **Use inheritance for ContentBlockEntity** types
- **Add proper indexes** for collection_id and order_index fields
- **Include validation annotations** on all entity fields
- **Use @Lob for text/code content** stored in the database

### 2. Service Layer
- **Create utility classes** for processing specific content types
- **Implement proper pagination** with default page size of 30 content blocks
- **Use BCrypt for password hashing** in client galleries
- **Implement type-specific processing** based on CollectionType

### 3. Repository Layer
- **Create custom query methods** for common access patterns
- **Implement pagination support** in repository methods
- **Add counting methods** for pagination metadata

### 4. Security Considerations
- **Sanitize all user inputs** to prevent injection attacks
- **Validate client gallery access** with proper password hashing
- **Never expose sensitive data** in API responses

## Frontend Integration Guidelines

### 1. API Design for Frontend
- **Consistent error responses** for better frontend handling
- **Pagination metadata** in all collection responses
- **Type-specific fields** based on CollectionType

### 2. Testing Frontend Integration
- **Test all API endpoints** with realistic frontend requests
- **Verify pagination works** with large collections
- **Test client gallery access flows**

## Migration Strategy

### 1. Parallel Development
- **Build the new ContentCollection system completely parallel** to the existing Catalog/Image system
- **Migrate data gradually** starting with lowest risk collections
- **Validate migrated data** thoroughly before switching systems

### 2. Performance Monitoring
- **Monitor database performance** with new pagination queries
- **Track S3 usage patterns** with mixed content types
- **Optimize slow queries** discovered in production

## Docker Development Environment

### 1. Build & Compilation
- **DO NOT run build/compile commands in Claude Code sessions** - The development environment does not support running builds
- **NEVER use `./mvnw` directly** - Java runtime is not available on the host machine
- **NEVER use `docker-compose run --rm app mvn`** - This is not supported in the Claude Code environment
- **The user will handle builds manually** outside of the Claude Code session
- **Focus on code changes only** - Trust that the code will compile correctly based on Java type safety
- Examples of commands to AVOID:
  ```bash
  # WRONG - Don't do any of these
  ./mvnw clean compile
  ./mvnw test
  docker-compose run --rm app mvn clean compile
  docker-compose run --rm app mvn test
  ```

### 2. Running the Application
- **Use docker-compose up** to start the application (user will do this manually)
- **Database is managed by docker-compose** - don't expect local MySQL
- **Environment variables** are defined in docker-compose.yml

### 3. Testing
- **DO NOT run tests via Claude Code** - Tests will be run manually by the user
- **Integration tests need docker** for TestContainers and database access (user handles this)

## File Writing Guidelines

### 1. Character Encoding & Special Characters
- **ALWAYS use plain ASCII characters** in all files (code, documentation, markdown)
- **NEVER use emojis** unless explicitly requested by the user
- **NEVER use Unicode symbols** (arrows, checkmarks, etc.) - use ASCII alternatives:
  - Use `->` instead of `→`
  - Use `<->` instead of `↔`
  - Use `*` or `-` for bullets, not `•`
  - Use `[x]` and `[ ]` for checkboxes, not `☑` and `☐`
- **Examples of WRONG characters to avoid**:
  - Emojis: ♻️ 🗑️ ✅ ❌ 🔴 🟠 🟡
  - Arrows: → ← ↔ ⇒ ⇐
  - Symbols: • ✓ ✗ ★ ☆
- **Use standard ASCII alternatives**:
  - `->` for arrows
  - `*` for bullets
  - `[x]` for checked items
  - `[ ]` for unchecked items
- **Reason**: Special characters can cause encoding issues, IDE warnings, and compatibility problems

### 2. Markdown & Documentation
- Use standard markdown syntax only
- Avoid fancy formatting that requires special characters
- Keep documentation simple and ASCII-compatible
- Test that files open without encoding errors

## Summary

These guidelines prioritize:
1. **Security first** - validate inputs, protect sensitive data
2. **Performance awareness** - efficient queries, proper pagination
3. **Maintainable code** - clear structure, comprehensive tests
4. **Modern practices** - latest Java features, proper patterns
5. **Real testing** - TestContainers over mocks, comprehensive coverage
6. **Docker-first development** - all builds and tests run in containers
7. **Plain ASCII encoding** - no emojis or special Unicode characters

When in doubt, choose the approach that makes the code more **secure**, **performant**, **testable**, **maintainable**, and **compatible**.