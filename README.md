# edens.zac.backend

[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4.1-6DB33F?logo=spring-boot)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-23-ED8B00?logo=openjdk)](https://openjdk.java.net/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![AWS](https://img.shields.io/badge/AWS-S3_+_CloudFront-FF9900?logo=amazon-aws)](https://aws.amazon.com/)
[![MIT License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![CI Pipeline](https://github.com/themancalledzac/edens.zac.backend/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/themancalledzac/edens.zac.backend/actions/workflows/ci-cd.yml)

Backend REST API for a photography portfolio and content management platform. Handles image upload and processing, collection management, multi-dimensional search, client gallery access control, passkey/session authentication, and media delivery through AWS S3 + CloudFront.

The API is consumed by a Next.js frontend via a server-side BFF (backend-for-frontend) proxy; browsers never call this API directly in production.

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Runtime** | Java 23, Spring Boot 3.4.1 |
| **Database** | PostgreSQL 16, Flyway migrations |
| **Data Access** | JDBC via `NamedParameterJdbcTemplate` (not JPA/Hibernate) |
| **Auth** | Spring Security, opaque DB-backed sessions, WebAuthn passkeys (webauthn4j) |
| **Storage** | AWS S3 (images, GIFs) |
| **CDN** | AWS CloudFront with Origin Access Control |
| **Hosting** | AWS EC2 (Docker Compose) |
| **Image Processing** | Thumbnailator, Apache Commons Imaging, Metadata Extractor |
| **Build** | Maven, Docker multi-stage build |
| **CI** | GitHub Actions (lint, test, build, security scan) |
| **Code Style** | Google Java Format (Spotless) + Checkstyle |
| **Testing** | JUnit 5, Mockito, AssertJ, MockMvc |

---

## Features

- **Image Upload & Processing** -- Multi-resolution image generation, EXIF metadata extraction (camera, lens, GPS, exposure, film metadata), WebP conversion
- **Collection Management** -- Multiple collection types (Blog, Portfolio, Art Gallery, Client Gallery, Home, Parent, Misc) with pagination, ordering, nested/child collections, and per-collection visibility
- **Multi-Dimensional Image Search** -- Filter by tags, people, camera, lens, location, rating, film/digital, and date range with dynamic SQL query building
- **Location Pages** -- Collections and orphan images grouped by location with count hints for frontend clickability
- **Client Gallery Protection** -- Password-gated galleries, HMAC access tokens, per-recipient allow-lists, rate-limited access
- **Authentication** -- Session login with WebAuthn passkeys and a password break-glass path, invite-based account creation, HttpOnly session cookies
- **User Space** -- Per-user saved images, followed collections, content selects, and rating overrides
- **GIF & Video Support** -- GIF upload with first-frame thumbnail extraction, MP4 variant handling
- **Metadata Management** -- Tags, people, cameras, lenses, locations, and film types with batch operations
- **Content Reordering** -- Bulk SQL reorder for drag-and-drop collection management

---

## Architecture

```
+------------------+
|  Next.js         |
|  frontend + BFF  |
+--------+---------+
         |
         | REST API (X-Internal-Secret in prod)
         |
+--------v---------+
|   Spring Boot    |
|   REST API       |
|   (EC2, Docker)  |
+--------+---------+
         |
  +------+------+
  |             |
+-v-------+  +--v-----------+
| Postgres |  | AWS S3       |
| (EC2,    |  | + CloudFront |
|  Docker) |  |              |
+---------+  +--------------+
```

**API Layer** -- Controllers grouped by family under `controller/` (see [API Endpoints](#api-endpoints)). Global exception handling via `@ControllerAdvice` (`GlobalExceptionHandler`). In production, all write/admin traffic is fronted by the BFF and gated by a shared-secret filter (`InternalSecretFilter`); session/passkey routes are protected by Spring Security.

**Service Layer** -- Concrete service classes (no interface/impl split). Processing utilities handle entity-to-model conversion, metadata population, and batch operations.

**Data Access** -- Raw JDBC with `NamedParameterJdbcTemplate` and hand-written SQL. Row mappers, parameter sources, and a `BaseDao` utility. DAO classes live in `dao/` and are named `*Repository`. No ORM.

**Storage** -- Images uploaded to S3 in multiple resolutions; CloudFront serves them globally. Flyway manages schema evolution.

---

## Project Structure

```
src/main/java/edens/zac/portfolio/backend/
  controller/
    admin/    Admin/write endpoints (run in dev + prod)
    auth/     Login, logout, session, WebAuthn passkeys, invites
    dev/      Dev-only admin surface (@Profile("dev"))
    prod/     Public read endpoints (/api/read/...)
    pub/      Unauthenticated public endpoints (/api/public/...)
    user/     Authenticated per-user endpoints
  services/   Business logic (concrete *Service classes)
  dao/        Data access (JDBC, NamedParameterJdbcTemplate; *Repository classes)
  entity/     Database entities (*Entity suffix)
  model/      DTOs, requests, responses (records, *Model, *Request)
  types/      Enums (CollectionType, ContentType, CollectionVisibility, DisplayMode, ...)
  config/     Spring configuration, security, filters, exception handling
src/main/resources/
  db/migration/  Flyway SQL migrations (V2 .. V41)
terraform/       AWS infrastructure as code
scripts/         Deployment, DB tunnel, and backup utilities
ai_docs/         Deployment / infrastructure reference docs
```

---

## API Endpoints

Endpoints are organised into families by base path. The tables below list the base path and the controller that serves each family; consult the controller for the exact method-level routes (kept there to avoid documentation drift).

| Family | Base path | Auth in prod | Controller(s) |
|--------|-----------|--------------|---------------|
| **Public read** | `/api/read/...` | BFF shared-secret | [`controller/prod/`](src/main/java/edens/zac/portfolio/backend/controller/prod) |
| **Per-user** | `/api/read/user/...` | Session cookie + BFF secret | [`UserControllerProd`](src/main/java/edens/zac/portfolio/backend/controller/prod/UserControllerProd.java), [`UserSavesControllerProd`](src/main/java/edens/zac/portfolio/backend/controller/prod/UserSavesControllerProd.java), [`UserFollowsControllerProd`](src/main/java/edens/zac/portfolio/backend/controller/prod/UserFollowsControllerProd.java), [`UserSelectsControllerProd`](src/main/java/edens/zac/portfolio/backend/controller/prod/UserSelectsControllerProd.java), [`UserRatingOverrideControllerProd`](src/main/java/edens/zac/portfolio/backend/controller/user/UserRatingOverrideControllerProd.java) |
| **Admin / write** | `/api/admin/...` | BFF shared-secret | [`controller/admin/`](src/main/java/edens/zac/portfolio/backend/controller/admin) (prod + dev), [`controller/dev/AdminController`](src/main/java/edens/zac/portfolio/backend/controller/dev/AdminController.java) (`@Profile("dev")` only) |
| **Auth** | `/api/auth/...` | Spring Security (see below) | [`AuthController`](src/main/java/edens/zac/portfolio/backend/controller/auth/AuthController.java), [`WebAuthnController`](src/main/java/edens/zac/portfolio/backend/controller/auth/WebAuthnController.java), [`InviteController`](src/main/java/edens/zac/portfolio/backend/controller/auth/InviteController.java) |
| **Public (unauth)** | `/api/public/...` | Public + rate-limited | [`MessagesControllerPublic`](src/main/java/edens/zac/portfolio/backend/controller/pub/MessagesControllerPublic.java) |

**Read family highlights** (`/api/read/...`): paginated collection list and single-collection reads (`/collections`, `/collections/{slug}`, `/collections/{slug}/meta`, `/collections/location/{slug}`), a multi-filter image search (`/content/images/search`), metadata lookups (`/content/tags`, `/content/people`, `/content/cameras`, `/content/lenses`, `/content/locations`, `/content/film-metadata`), and authenticated content downloads.

For the authentication model (sessions, passkeys, invites, the BFF secret perimeter), see [`.claude/auth.md`](.claude/auth.md).

---

## Getting Started

### Prerequisites

- Java 23+
- Maven 3.8+ (or use the included `./mvnw` wrapper)
- Access to the PostgreSQL 16 database (see [Database & Local Development](#database--local-development))
- AWS credentials for S3

### Local Development

1. Clone and build:
   ```bash
   git clone https://github.com/themancalledzac/edens.zac.backend.git
   cd edens.zac.backend
   ./mvnw clean install
   ```

2. Set up environment:
   ```bash
   cp .env.example .env
   # Edit .env with your database and AWS credentials (see .env.example for every key)
   ```

3. Open a tunnel to the database (see below), then run:
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

The API runs at `http://localhost:8080`.

### Docker

```bash
docker compose up --build
```

The backend container connects to an **external** PostgreSQL instance (there is no database service in `docker-compose.yml`). `SPRING_PROFILES_ACTIVE` must be set explicitly to `dev` or `prod`.

---

## Database & Local Development

The database is **PostgreSQL 16 running in its own Docker Compose stack** on the EC2 host (container `portfolio-postgres`, managed from `~/portfolio-db/`). The Spring Boot application runs as a **separate** Docker Compose stack on the same instance, so redeploying the app never touches the database. Schema is managed by Flyway (`src/main/resources/db/migration`, currently through `V41`).

Port `5432` is **not** open in the EC2 security group. Local development reaches the database over an SSH tunnel that forwards `localhost:5432` to the instance:

```bash
./scripts/db-tunnel.sh up      # ensure SSH access + open the tunnel, prints connection info
./scripts/db-tunnel.sh psql    # up, then drop into an interactive psql session
./scripts/db-tunnel.sh down    # close the tunnel
```

`db-tunnel.sh` reads all host and credential values from the environment (this repo is public, so nothing is hard-coded) -- set `EC2_PEM_FILE`, `EC2_USER`, `EC2_HOST`, and the `POSTGRES_*` values in your shell profile or a git-ignored `.env`. With the tunnel up, point Spring (and any GUI client) at `jdbc:postgresql://localhost:5432/edens_zac`.

See [ai_docs/ai_deployment_strategy.md](ai_docs/ai_deployment_strategy.md) and [ai_docs/ai_ec2.md](ai_docs/ai_ec2.md) for the full infrastructure layout.

---

## Build & Test

```bash
./mvnw clean install          # Build with tests
./mvnw test                   # Run tests only
./mvnw spotless:apply         # Format code (Google Java Format)
./mvnw checkstyle:check       # Verify style compliance
```

### Code Formatting

The build uses [Spotless](https://github.com/diffplug/spotless) with [google-java-format](https://github.com/google/google-java-format).

- **VS Code / Cursor**: Install [Run On Save](https://marketplace.visualstudio.com/items?itemName=emeraldwalk.RunOnSave). The repo's `.vscode/settings.json` runs `mvn spotless:apply` on save.
- **Command line**: `./mvnw spotless:apply`

---

## Environment Variables

Every key the application consumes is documented in [`.env.example`](.env.example). The essentials:

| Variable | Description |
|----------|-------------|
| `SPRING_PROFILES_ACTIVE` | `dev` (local) or `prod` (EC2). No silent default. |
| `POSTGRES_HOST` / `POSTGRES_PORT` / `POSTGRES_DB` | Database connection (host is `localhost` when tunnelling) |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | Database credentials |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | AWS credentials for S3 |
| `AWS_PORTFOLIO_S3_BUCKET` | S3 bucket for image storage |
| `AWS_CLOUDFRONT_DOMAIN` | CloudFront distribution domain |
| `AWS_CLOUDFRONT_DISTRIBUTION_ID` | CloudFront distribution id, for cache invalidation on delete (optional) |
| `INTERNAL_API_SECRET` | Shared secret between the Next.js BFF proxy and this backend (prod perimeter) |
| `ACCESS_TOKEN_SECRET` | Secret for client-gallery HMAC access tokens (required) |
| `WEBAUTHN_RP_ID` / `WEBAUTHN_RP_NAME` / `WEBAUTHN_ALLOWED_ORIGINS` | WebAuthn relying-party config |
| `EMAIL_ENABLED` / `EMAIL_FROM_ADDRESS` / `EMAIL_FRONTEND_BASE_URL` | Transactional email (AWS SES v2) |

---

## Deployment

Manual deploy via SSH to EC2 (CI runs checks on merge; deployment is intentionally not automated):

```bash
ssh -i ~/key.pem ec2-user@<ec2-ip>
bash ~/portfolio-backend/repo/deploy.sh
```

See [ai_docs/ai_deployment_strategy.md](ai_docs/ai_deployment_strategy.md) for the full deployment guide and [ai_docs/ai_cicd.md](ai_docs/ai_cicd.md) for the CI pipeline.

---

## Infrastructure

AWS infrastructure is managed with Terraform in the `terraform/` directory:

- **EC2** -- Application host (Spring Boot + PostgreSQL, each as its own Docker Compose stack)
- **S3** -- Image storage
- **CloudFront** -- CDN with Origin Access Control
- **Security Groups** -- Network access rules (port 5432 deliberately closed; use the SSH tunnel)
- **IAM** -- User and policies for S3 access

---

## License

MIT
