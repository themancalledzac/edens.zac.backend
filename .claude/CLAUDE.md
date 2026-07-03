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
controller/admin/ - Admin/write endpoints (run in BOTH dev and prod)
controller/auth/  - Login, logout, session, WebAuthn passkeys, invites
controller/dev/   - Dev-only admin surface, @Profile("dev") (e.g. AdminController)
controller/prod/  - Public read endpoints (/api/read/...), run in both profiles
controller/pub/   - Unauthenticated public endpoints (/api/public/...)
controller/user/  - Authenticated per-user endpoints
services/         - Business logic (concrete *Service classes, no interface/Impl split)
dao/              - Data access (JDBC via NamedParameterJdbcTemplate; *Repository classes)
entity/           - Database entities (*Entity suffix)
model/            - DTOs, requests, responses (records, *Model, *Request)
types/            - Enums and type definitions
config/           - Spring configuration, security, filters
```

**Profile note**: Only `controller/dev/AdminController` is `@Profile("dev")`-gated. Every
other controller (including `controller/admin/*` and `controller/prod/*`) runs in both
profiles. In prod, admin/write routes are protected by the BFF + `InternalSecretFilter`
(a `@Profile("prod")` shared-secret filter), not by profile exclusion. Auth/session routes
are protected by Spring Security (`SecurityConfig`).

## Naming Conventions
- **Entities**: `*Entity` (e.g., `CollectionEntity`, `ContentEntity`, `ContentImageEntity`)
- **Models/DTOs**: `*Model`, `*Request`, `*ResponseDTO`
- **Services**: Concrete class `*Service` (no interface, no `*ServiceImpl` suffix)
- **Controllers**: named for family/role -- `*ControllerProd` (read), plain `*Controller` under `admin/`, `auth/`, `pub/`, and `AdminController` (dev-only). No `*ControllerDev` suffix.

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

### API Design
- NEVER expose entities directly -- always use DTOs/Models
- Use `ResponseEntity<T>` with typed returns (never `ResponseEntity<?>`)
- URL families: `/api/read/...` (public read), `/api/admin/...` (admin/write), `/api/auth/...` (session + passkeys), `/api/public/...` (unauthenticated)
- JSON responses must be objects (not arrays) at top level, camelCase properties
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
- Dev profile: PostgreSQL over SSH tunnel (`scripts/db-tunnel.sh`), relaxed CORS, cookies non-Secure
- Prod profile: PostgreSQL in Docker on EC2, CloudFront integration, `InternalSecretFilter` shared-secret perimeter
- Key env vars: `POSTGRES_*`, `AWS_*` (incl. `AWS_PORTFOLIO_S3_BUCKET`, `AWS_CLOUDFRONT_DOMAIN`), `INTERNAL_API_SECRET`, `ACCESS_TOKEN_SECRET`, `WEBAUTHN_*`, `EMAIL_*` -- see `.env.example` for the full list

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
- [auth.md](auth.md) - Authentication model: sessions, passkeys, invites, the BFF secret perimeter
- [testing.md](testing.md) - Test patterns, naming, what to test
- [api-patterns.md](api-patterns.md) - Endpoint design, response formats, error handling
- [ai_deployment_strategy.md](../ai_docs/ai_deployment_strategy.md) - Deployment pipeline, EC2 setup, backups, HTTPS
- [ai_ec2.md](../ai_docs/ai_ec2.md) - EC2 instance layout, DB stack, SSH tunnel, ops runbook
