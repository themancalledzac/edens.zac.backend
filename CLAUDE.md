# Cursor Rules for Portfolio Backend

## Project Context

- Java 23 / Spring Boot 3.4.1
- Package: `edens.zac.portfolio.backend`
- Current system: Collection/Content architecture (refactor complete)
- MySQL (prod) / H2 (dev)
- AWS S3 for media storage

## Naming Conventions

- **Entities**: `*Entity` (e.g., `CollectionEntity`, `ContentEntity`, `ContentImageEntity`, `CollectionContentEntity`)
- **Models/DTOs**: `*Model`, `*Request`, `*ResponseDTO` (e.g., `CollectionModel`, `ContentImageUpdateRequest`)
- **Services**: Interface `*Service`, Implementation `*ServiceImpl` (package-private)
- **Repositories**: `*Repository`
- **Controllers**: `*ControllerDev` (dev) / `*ControllerProd` (prod)

## Code Generation Principles

**CRITICAL: Keep changes SMALL and CONCISE**

- Simplify existing code, don't rebuild
- Reuse existing utilities and patterns
- Make minimal, focused changes
- Avoid over-engineering or rearchitecting
- Prefer refactoring existing code over creating new abstractions

## Best Practices

### Dependencies & Injection

- **ALWAYS use constructor injection** with `@RequiredArgsConstructor`
- Declare dependencies as `final` fields
- Prefer package-private visibility for Spring components
- **NEVER use field injection** or `@Autowired`

### Imports

- Place ALL imports at top of file
- **NEVER use fully qualified names inline**
- Use proper import statements

### Transactions

- `@Transactional(readOnly = true)` for queries
- `@Transactional` for modifications
- Keep transaction scope minimal

### API Design

- **NEVER expose entities directly** - use DTOs/Models
- Use `ResponseEntity<T>` for explicit HTTP status codes
- Versioned URLs: `/api/v1/resource` pattern
- JSON responses must be objects (not arrays) at top level
- Use camelCase for JSON properties

### Entity Design

- Use Jakarta Validation annotations
- Use Lombok (`@Data`, `@Builder`, `@RequiredArgsConstructor`)
- Add proper database indexes
- Use `@CreationTimestamp` and `@UpdateTimestamp`

### Testing

- Use TestContainers for integration tests
- Prefer real dependencies over excessive mocking
- Test behavior, not implementation
- Clear test names: `methodName_scenario_expectedResult()`
- Arrange-Act-Assert pattern
- Target single logic focus per test

## Warnings - Patterns to AVOID

### DO NOT:

- Expose entities in REST responses
- Use field injection (`@Autowired` on fields)
- Use fully qualified class names inline
- Create unnecessary abstractions or utility classes
- Over-engineer solutions (keep it simple)
- Rebuild existing working code
- Use emojis or Unicode symbols (ASCII only)
- Run build/compile commands (user handles builds)
- Create large code changes - prefer incremental, focused edits
- Create multiple similar files (e.g., multiple SQL scripts for the same fix) - create ONE file with the solution

### DO:

- Use existing utilities and patterns
- Make small, focused code changes
- Reuse existing DTOs and models
- Follow existing architectural patterns
- Keep code simple and testable

## File Organization

- `controller/` - REST endpoints (dev/prod separation)
- `services/` - Business logic (interface + implementation)
- `repository/` - Data access
- `entity/` - JPA entities
- `model/` - DTOs, requests, responses
- `config/` - Configuration classes
- `types/` - Enums and type definitions

## Code Style

- Modern Java: records, switch expressions, `var` when type obvious
- Use `Optional` properly (avoid `.get()`, use `.orElse()`, `.orElseThrow()`)
- Use Stream API appropriately
- Logging: SLF4J with Logback, never `System.out.println`
- Guard expensive log operations: `if (logger.isDebugEnabled())`

## Character Encoding

- **ALWAYS use plain ASCII** - no emojis, Unicode symbols, or special characters
- Use `->` instead of arrows, `*` for bullets, `[x]` for checkboxes
