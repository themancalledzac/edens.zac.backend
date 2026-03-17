# Photography and Coding Portfolio Backend API

[![CI Pipeline](https://github.com/themancalledzac/edens.zac.backend/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/themancalledzac/edens.zac.backend/actions/workflows/ci-cd.yml)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-23-orange.svg)](https://openjdk.java.net/)
[![MIT License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

Backend API for a photography and coding portfolio. Handles image upload/processing, collection management, metadata extraction, and AWS S3 storage with CloudFront CDN delivery.

## Tech Stack

- **Runtime**: Java 23, Spring Boot 3.4.1
- **Database**: PostgreSQL 16 (Flyway migrations)
- **Data Access**: JDBC via `NamedParameterJdbcTemplate` (not JPA/Hibernate)
- **Cloud**: AWS S3 (image storage), CloudFront (CDN), EC2 (hosting)
- **Image Processing**: Thumbnailator, Apache Commons Imaging, Metadata Extractor
- **Build**: Maven, Docker multi-stage builds
- **CI**: GitHub Actions (lint, test, build, security scan)
- **Code Style**: Google Java Format via Spotless + Checkstyle

## Getting Started

### Prerequisites

- Java 23+
- Maven 3.8+
- PostgreSQL 16 (local or EC2 via SSH tunnel)
- AWS credentials for S3

### Local Development

1. Clone and build:
   ```bash
   git clone https://github.com/themancalledzac/edens.zac.backend.git
   cd edens.zac.backend
   mvn clean install
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
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```

### Docker

```bash
# Set POSTGRES_HOST and other POSTGRES_* vars in .env
docker compose up --build
```

The backend connects to an external PostgreSQL instance. There is no local database container in this stack -- the DB runs separately on EC2 in `~/portfolio-db/`.

### Code Formatting

The build uses [Spotless](https://github.com/diffplug/spotless) with [google-java-format](https://github.com/google/google-java-format).

- **VS Code / Cursor**: Install [Run On Save](https://marketplace.visualstudio.com/items?itemName=emeraldwalk.RunOnSave). The repo's `.vscode/settings.json` runs `mvn spotless:apply` on save.
- **Command line**: `mvn spotless:apply`

## Build & Test

```bash
mvn clean install          # Build with tests
mvn test                   # Run tests only
mvn spotless:apply         # Format code
mvn checkstyle:check       # Verify style
```

## Deployment

Manual deploy via SSH to EC2:

```bash
ssh -i ~/key.pem ec2-user@<ec2-ip>
bash ~/portfolio-backend/repo/deploy.sh
```

See [ai_docs/ai_deployment_strategy.md](ai_docs/ai_deployment_strategy.md) for the full deployment guide.

## Infrastructure

AWS infrastructure is managed with Terraform in the `terraform/` directory:
- EC2 instance (application host)
- S3 bucket (image storage + DB backups)
- CloudFront distribution (CDN with OAC)
- Security groups, IAM users/policies

## API Endpoints

- **Read (public)**: `/api/read/collections`, `/api/read/collections/{slug}`, `/api/read/content/{id}`
- **Admin (dev profile)**: `/api/admin/collections`, `/api/admin/content/upload`

For detailed endpoint documentation, see the controller classes.
