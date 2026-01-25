# Portfolio Backend - Claude Context

## What This Is
Spring Boot 3.4.1 / Java 23 backend for a photography and coding portfolio. Manages images, collections, and metadata with PostgreSQL storage and AWS S3 for media.

## Before Writing Code
1. **Read first** - Always read relevant existing code before proposing changes
2. **Ask questions** - If requirements are unclear, ask before implementing
3. **Check patterns** - Look at similar existing code for established patterns
4. **Simplify** - Prefer the simplest solution that works; avoid over-engineering

## Project Structure
```
controller/dev/   - Development endpoints (@Profile("dev"))
controller/prod/  - Production endpoints (@Profile("prod"))
services/         - Business logic (interface + *ServiceImpl)
dao/              - Data access (JDBC, not JPA repositories)
entity/           - JPA entities (*Entity suffix)
model/            - DTOs, requests, responses (*Model, *Request, *ResponseDTO)
types/            - Enums and type definitions
config/           - Spring configuration
```

## Key Conventions
- See [.cursorrules](../.cursorrules) for detailed coding standards
- Constructor injection only (`@RequiredArgsConstructor`, `final` fields)
- Never expose entities in API responses - use DTOs/Models
- Controllers separated by profile: `*ControllerDev` vs `*ControllerProd`
- Services: public interface, package-private `*ServiceImpl`

## Build & Test
```bash
mvn clean install          # Build with tests
mvn test                   # Run tests only
mvn spotless:apply         # Format code (Google Java Format)
mvn checkstyle:check       # Verify style compliance
```

## Common Patterns

### Adding an endpoint
1. Add method to existing controller in appropriate profile (dev/prod)
2. Use existing service methods or extend service interface
3. Return `ResponseEntity<T>` with proper status codes
4. Use existing DTOs or create new ones in model/

### Working with entities
- ContentEntity uses JOINED inheritance (IMAGE, TEXT, GIF, COLLECTION types)
- CollectionContentEntity joins collections to content with ordering
- Always convert entities to models before returning from controllers

### Image processing
- ImageProcessingService handles uploads and optimization
- Metadata extracted via ImageMetadata class (EXIF/XMP)
- S3 storage with CloudFront CDN URLs

## Environment
- Dev profile: Local PostgreSQL, relaxed CORS
- Prod profile: AWS RDS, CloudFront integration
- Key env vars: `POSTGRES_*`, `AWS_*`, `CLOUDFRONT_DOMAIN`

## What to Avoid
- Creating new abstractions when existing utilities work
- Duplicating logic that exists elsewhere
- Large sweeping changes - prefer small, focused edits
- Field injection, inline qualified names, emojis

## Deep Dive Documentation
Read these only when working in that specific area:

- [architecture.md](architecture.md) - Entity relationships, inheritance, system design
- [database.md](database.md) - Schema structure, table relationships, queries
- [testing.md](testing.md) - Test patterns, naming, what to test
- [api-patterns.md](api-patterns.md) - Endpoint design, response formats, error handling
