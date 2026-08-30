# Portfolio Backend - Claude Context

## What This Is
Spring Boot 3.4.1 / Java 23 backend for a photography and coding portfolio. Manages images, collections, and metadata with PostgreSQL storage and AWS S3 for media.

Package: `edens.zac.portfolio.backend`

## Before Writing Code
1. **Read first** - Always read relevant existing code before proposing changes
2. **Ask questions** - If requirements are unclear, ask before implementing
3. **Check patterns** - Look at similar existing code for established patterns
4. **Simplify** - Prefer the simplest solution that works; avoid over-engineering
5. **Keep changes SMALL** - Minimal, focused edits. Do not rebuild or rearchitect.

## Project Structure
```
controller/admin/ - Admin/write endpoints, all profiles (gated by SecurityConfig hasRole("ADMIN"))
controller/edit/  - Collaborator write endpoints, all profiles (gated by CollaboratorAccessInterceptor at COLLABORATOR+)
controller/prod/  - Public read endpoints (/api/read/...), all profiles (no @Profile gating)
services/         - Business logic (concrete *Service classes, no interface/Impl split)
dao/              - Data access (JDBC via NamedParameterJdbcTemplate, not JPA repositories)
entity/           - JPA entities (*Entity suffix)
model/            - DTOs, requests, responses (*Model, *Request, *ResponseDTO)
types/            - Enums and type definitions
config/           - Spring configuration
```

## Naming Conventions
- **Entities**: `*Entity` (e.g., `CollectionEntity`, `ContentEntity`, `ContentImageEntity`)
- **Models/DTOs**: `*Model`, `*Request`, `*ResponseDTO`
- **Services**: Concrete class `*Service` (no interface, no `*ServiceImpl` suffix)
- **Controllers**: `AdminController` (admin, all profiles, security-gated) / `*ControllerProd` (prod profile)

## Key Rules

### Dependencies & Injection
- Constructor injection ONLY: `@RequiredArgsConstructor` + `final` fields
- NEVER use `@Autowired` or field injection
- Prefer package-private visibility for Spring components

### Code Style
- Modern Java: records, switch expressions, `var` when type is obvious
- ALL imports at top of file -- NEVER use fully qualified names inline
- Use `Optional` properly (`.orElse()`, `.orElseThrow()` -- never `.get()`)
- Logging: SLF4J with Logback, never `System.out.println`
- ASCII only -- no emojis, Unicode symbols, or special characters

### Comments: NEVER write inline comments
This is absolute. Do not write `//` comments inside method bodies, constructors, test
methods, or against fields. Not to explain a decision, not to justify a design, not to
mark a section, not to record why a test is shaped the way it is. There is no threshold
of importance that earns one.

Javadoc (`/** ... */`) on a class, method, or field is the only place prose belongs. If
something needs saying, say it there. If it does not fit in a docblock, it does not go in
the file -- put it in the PR description or the review doc.

When editing any file, delete the inline comments you find in it. Do not preserve one
because it looks useful.

### API Design
- NEVER expose entities directly -- always use DTOs/Models
- Use `ResponseEntity<T>` with typed returns (never `ResponseEntity<?>`)
- URL structure: `/api/read/...` (prod), `/api/edit/...` (collaborator+), `/api/admin/...` (admin)
- JSON responses use camelCase properties
- A list endpoint MAY return a top-level JSON array. Wrapping it in an object is not
  required (decided 2026-08-30: the frontend consumes bare arrays directly, and 17
  endpoints already ship this way). Prefer an object when the response carries anything
  besides the list itself, such as paging or a total.
- No try-catch in controllers -- GlobalExceptionHandler handles all exceptions
- Always use `@Valid` on `@RequestBody` parameters

### Transactions
- `@Transactional(readOnly = true)` for queries
- `@Transactional` for modifications

### Entity Design
- Lombok: `@Data`, `@Builder`, `@RequiredArgsConstructor`
- Jakarta Validation annotations for constraints
- `@CreationTimestamp` and `@UpdateTimestamp` for audit fields

## Build & Test
```bash
mvn clean install          # Build with tests
mvn test                   # Run tests only
mvn spotless:apply         # Format code (Google Java Format)
mvn checkstyle:check       # Verify style compliance
```

## Environment
- Dev profile: EC2 PostgreSQL, relaxed CORS
- Prod profile: EC2 PostgreSQL, CloudFront integration, InternalSecretFilter
- Key env vars: `POSTGRES_*`, `AWS_*`, `CLOUDFRONT_DOMAIN`

## What to Avoid
- Exposing entities in REST responses
- Field injection (`@Autowired` on fields)
- Fully qualified class names inline
- Creating unnecessary abstractions or utility classes
- Large sweeping changes -- prefer small, focused edits
- Rebuilding existing working code
- Emojis or Unicode symbols

## Deep Dive Documentation
Read these only when working in that specific area:

- [architecture.md](architecture.md) - Entity relationships, inheritance, system design
- [database.md](database.md) - Schema structure, table relationships, queries
- [testing.md](testing.md) - Test patterns, naming, what to test
- [api-patterns.md](api-patterns.md) - Endpoint design, response formats, error handling
- [ai_deployment_strategy.md](../ai_docs/ai_deployment_strategy.md) - Deployment pipeline, EC2 setup, backups, HTTPS
