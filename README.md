# edens.zac.backend

[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4.1-6DB33F?logo=spring-boot)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-23-ED8B00?logo=openjdk)](https://openjdk.java.net/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![AWS](https://img.shields.io/badge/AWS-S3_+_CloudFront-FF9900?logo=amazon-aws)](https://aws.amazon.com/)
[![MIT License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![CI Pipeline](https://github.com/themancalledzac/edens.zac.backend/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/themancalledzac/edens.zac.backend/actions/workflows/ci-cd.yml)

Backend REST API for a photography portfolio and content management platform. Handles image upload and processing, collection management, multi-dimensional search, client gallery access control, and media delivery through AWS S3 + CloudFront.

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Runtime** | Java 23, Spring Boot 3.4.1 |
| **Database** | PostgreSQL 16, Flyway migrations |
| **Data Access** | JDBC via `NamedParameterJdbcTemplate` (not JPA/Hibernate) |
| **Storage** | AWS S3 (images, GIFs, backups) |
| **CDN** | AWS CloudFront with Origin Access Control |
| **Hosting** | AWS EC2 |
| **Image Processing** | Thumbnailator, Apache Commons Imaging, Metadata Extractor |
| **Build** | Maven, Docker multi-stage builds |
| **CI** | GitHub Actions (lint, test, build, security scan) |
| **Code Style** | Google Java Format (Spotless) + Checkstyle |
| **Testing** | JUnit 5, Mockito, AssertJ, MockMvc |

---

## Features

- **Image Upload & Processing** -- Multi-resolution thumbnail generation (web, thumbnail, small), EXIF metadata extraction (camera, lens, GPS, exposure), WebP conversion
- **Collection Management** -- Four collection types (Blog, Portfolio, Art Gallery, Client Gallery) with pagination, ordering, and nested collection support
- **Multi-Dimensional Image Search** -- Filter by tags, people, camera, lens, location, rating, film/digital, date range with dynamic SQL query building
- **Location Pages** -- Collections and orphan images grouped by location with count hints for frontend clickability
- **Client Gallery Protection** -- SHA-256 password hashing, access token flow, rate-limited authentication
- **GIF & Video Support** -- GIF upload with first-frame WebP thumbnail extraction, MP4 processing
- **Metadata Management** -- Tags, people, cameras, lenses, locations, film types with batch operations
- **Content Reordering** -- Bulk CASE-based SQL reorder for drag-and-drop collection management

---

## Architecture

```
+------------------+
|   Next.js 15     |
|   (Amplify)      |
+--------+---------+
         |
         | REST API
         |
+--------v---------+
|   Spring Boot    |
|   REST API       |
|   (EC2)          |
+--------+---------+
         |
  +------+------+
  |             |
+-v------+  +--v-----------+
| Postgres|  | AWS S3       |
| (EC2)  |  | + CloudFront |
+--------+  +--------------+
```

**API Layer** -- Dual-profile controllers: `prod` for public read endpoints, `dev` for admin/write operations. Global exception handling via `@ControllerAdvice`.

**Service Layer** -- Concrete service classes (no interface/impl split). Processing utilities handle entity-to-model conversion, metadata population, and batch operations.

**Data Access** -- Raw JDBC with `NamedParameterJdbcTemplate` and hand-written SQL. Row mappers, parameter sources, and a `BaseDao` utility class. No ORM magic.

**Storage** -- Images uploaded to S3 in multiple resolutions. CloudFront serves them globally. Flyway manages database schema evolution.

---

## Project Structure

```
src/main/java/edens/zac/portfolio/backend/
  controller/
    dev/              Admin endpoints (@Profile("dev"))
    prod/             Public read endpoints (@Profile("prod"))
  services/           Business logic (concrete *Service classes)
  dao/                Data access (JDBC, NamedParameterJdbcTemplate)
  entity/             Database entities (*Entity suffix)
  model/              DTOs, requests, responses (*Model, *Request, Records)
  types/              Enums (CollectionType, ContentType, DisplayMode)
  config/             Spring configuration, exception handling
src/main/resources/
  db/migration/       Flyway SQL migrations (V1-V5)
terraform/            AWS infrastructure as code
docker/               Docker configurations
scripts/              Deployment and utility scripts
```

---

## API Endpoints

### Public Read Endpoints (`/api/read/`)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/collections` | Paginated list of all collections |
| `GET` | `/collections/{slug}` | Collection with paginated content (supports `?accessToken=`) |
| `GET` | `/collections/{slug}/meta` | Lightweight collection metadata (SEO) |
| `GET` | `/collections/type/{type}` | Visible collections by type |
| `GET` | `/collections/location/{name}` | Collections + orphan images at a location |
| `POST` | `/collections/{slug}/access` | Validate client gallery password, returns access token |
| `GET` | `/content/images/search` | Multi-filter image search with pagination |
| `GET` | `/content/tags` | All tags |
| `GET` | `/content/people` | All people |
| `GET` | `/content/cameras` | All cameras |
| `GET` | `/content/lenses` | All lenses |
| `GET` | `/content/locations` | Locations with collection/image counts |
| `GET` | `/content/film-metadata` | Film types and formats |

### Admin Endpoints (`/api/admin/`)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/collections` | All collections (admin view) |
| `POST` | `/collections` | Create collection |
| `PUT` | `/collections/{id}` | Update collection metadata, tags, people |
| `DELETE` | `/collections/{id}` | Delete collection |
| `POST` | `/collections/{id}/children` | Create child collection |
| `PUT` | `/collections/{id}/reorder` | Reorder collection content |
| `POST` | `/content/upload/images` | Batch image upload with EXIF extraction |
| `POST` | `/content/upload/gif` | GIF upload with thumbnail generation |
| `PATCH` | `/content/images/batch` | Batch update image metadata |
| `POST` | `/content/{collectionId}/add` | Add content to collection |
| `DELETE` | `/content/{collectionId}/remove` | Remove content from collection |

---

## Getting Started

### Prerequisites

- Java 23+
- Maven 3.8+ (or use the included `./mvnw` wrapper)
- PostgreSQL 16 (local or EC2 via SSH tunnel)
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
   # Edit .env with your database and AWS credentials
   ```

3. Connect to the EC2 database via SSH tunnel:
   ```bash
   ssh -L 5432:localhost:5432 -i ~/key.pem ec2-user@<ec2-ip>
   ```

4. Run:
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

The API runs at `http://localhost:8080`.

### Docker

```bash
docker compose up --build
```

The backend connects to an external PostgreSQL instance. The database runs separately on EC2.

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

| Variable | Description |
|----------|-------------|
| `POSTGRES_HOST` | Database host |
| `POSTGRES_PORT` | Database port (default: 5432) |
| `POSTGRES_DB` | Database name |
| `POSTGRES_USERNAME` | Database user |
| `POSTGRES_PASSWORD` | Database password |
| `AWS_ACCESS_KEY_ID` | AWS access key |
| `AWS_SECRET_ACCESS_KEY` | AWS secret key |
| `AWS_REGION` | AWS region (default: us-west-2) |
| `S3_BUCKET_NAME` | S3 bucket for image storage |
| `CLOUDFRONT_DOMAIN` | CloudFront distribution domain |
| `INTERNAL_SECRET` | Secret for prod endpoint authentication |

---

## Deployment

Manual deploy via SSH to EC2:

```bash
ssh -i ~/key.pem ec2-user@<ec2-ip>
bash ~/portfolio-backend/repo/deploy.sh
```

See [ai_docs/ai_deployment_strategy.md](ai_docs/ai_deployment_strategy.md) for the full deployment guide.

---

## Infrastructure

AWS infrastructure is managed with Terraform in the `terraform/` directory:

- **EC2** -- Application host (Spring Boot + PostgreSQL)
- **S3** -- Image storage and database backups
- **CloudFront** -- CDN with Origin Access Control
- **Security Groups** -- Network access rules
- **IAM** -- Users and policies for S3 access

---

## License

MIT
