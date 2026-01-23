# Photography and Coding Portfolio Backend API

[![CI Pipeline](https://github.com/themancalledzac/edens.zac.backend/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/themancalledzac/edens.zac.backend/actions/workflows/ci-cd.yml)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.java.net/)
[![MIT License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

A scalable, enterprise-grade backend API service powering a photography and coding portfolio platform. This service provides comprehensive image processing capabilities, sophisticated metadata extraction, flexible catalog management, and seamless AWS S3 integration for secure cloud storage.

## 🚀 Features

- **Image Management**
  - Upload and process images with metadata extraction
  - Generate optimized versions (thumbnails, web-optimized, etc.)
  - Store original and processed images on AWS S3
  - Search and filter images based on various metadata parameters

- **Catalog System**
  - Create and manage collections of images
  - Organize images into themes, locations, or project categories
  - Support for tags, people, and other metadata

- **AWS Integration**
  - Secure image storage and retrieval from S3
  - Support for different storage classes (standard, glacier)
  - CloudFront integration for efficient content delivery

- **Metadata Management**
  - Extract EXIF data from image files
  - Store camera settings, lens information, dates, and other technical data
  - Custom metadata fields for organization and categorization

## 🛠️ Tech Stack

- **Backend Framework**: Spring Boot 3.4.x
- **Database**: MySQL/H2 with JPA/Hibernate
- **Cloud Storage**: AWS S3 for image hosting
- **Image Processing**: Thumbnailator, Apache Commons Imaging, Metadata Extractor
- **Build Tool**: Maven

## ⚡ Performance Considerations

- Image optimization reduces file sizes by ~60-80% while maintaining quality
- API response times typically under 300ms for catalog operations
- Batch processing capabilities for handling multiple images efficiently
- Caching strategies implemented for frequently accessed resources

## 🏁 Getting Started

### Prerequisites

- Java 17 or later
- Maven 3.8+
- MySQL database (optional for local development, can use H2)
- AWS account with S3 access

### Setup and Installation

1. Clone the repository
   ```bash
   git clone https://github.com/themancalledzac/portfolio-backend.git
   cd portfolio-backend
   ```

2. Configure application properties
   - Create `application-local.properties` with your local configuration
   - Set up database connection information
   - Configure AWS credentials

3. Build the project
   ```bash
   mvn clean install
   ```

4. Run the application
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```

5. The API will be available at http://localhost:8080

## 📝 API Documentation

The API is organized into read and write operations to clearly separate data retrieval from data modification endpoints.

### Read Operations (Public Access)

| Endpoint | Method | Description | Parameters |
|----------|--------|-------------|------------|
| `/api/read/catalog/bySlug/{slug}` | GET | Retrieve a catalog by its slug | `slug`: URL-friendly identifier |
| `/api/read/catalog/byId/{id}` | GET | Retrieve a catalog by its ID | `id`: Numeric ID |
| `/api/read/catalog/getAllCatalogs` | GET | Get all available catalogs | None |
| `/api/read/image/byId/{id}` | GET | Retrieve an image by its ID | `id`: Numeric ID |
| `/api/read/image/getByCatalogId/{catalogId}` | GET | Get all images for a catalog | `catalogId`: Catalog's ID |
| `/api/read/home` | GET | Get homepage data including featured catalogs | None |

### Write Operations (Authenticated)

| Endpoint | Method | Description | Request Body |
|----------|--------|-------------|-------------|
| `/api/write/catalog/uploadCatalogWithImages` | POST | Create a new catalog with images | `catalogDTO`: Catalog data (JSON)<br>`images`: Image files (multipart) |
| `/api/write/catalog/update` | PUT | Update an existing catalog | Catalog data (JSON) |
| `/api/write/image/postImages/{type}` | POST | Upload images with a type | `files`: Image files (multipart)<br>`type`: Image type |
| `/api/write/image/postImagesForCatalog/{catalogTitle}` | POST | Add images to existing catalog | `catalogTitle`: Title of catalog<br>`images`: Image files |
| `/api/write/image/update/image` | PUT | Update image metadata | Image data (JSON) |
| `/api/write/image/getBatchImageMetadata` | POST | Extract metadata from batch of images | `files`: Image files (multipart) |
| `/api/write/home/update` | PUT | Update homepage data | Homepage data (JSON) |

For detailed endpoint documentation, refer to the controller classes or the project wiki.

### Future Documentation Plans
- [ ] Implement Swagger/OpenAPI documentation
- [ ] Create detailed API reference guides
- [ ] Add example requests and responses for each endpoint

## 🧪 Testing

Run unit tests:
```bash
mvn test
```


## Database Selection and Sequel Ace Guide

This backend uses MySQL only. There is no H2 or DynamoDB configuration.

How the datasource is chosen:
- application.properties sets:
  - spring.datasource.url = ${SPRING_DATASOURCE_URL:jdbc:mysql://${EC2_HOST:localhost}:3306/edens_zac?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC}
  - spring.datasource.username = ${SPRING_DATASOURCE_USERNAME:zedens}
  - spring.datasource.password = ${SPRING_DATASOURCE_PASSWORD:password}
- If SPRING_DATASOURCE_URL is provided, it wins. Otherwise the URL falls back to jdbc:mysql://${EC2_HOST:localhost}:3306/edens_zac.
- Active profiles are set via SPRING_PROFILES_ACTIVE. The dev controller (@Profile("dev")) loads only when the dev profile is active; the prod controller is always active.

Typical scenarios:
1) Docker Compose (recommended for local):
   - DB: MySQL container (service name: mysql), exposed on localhost:3306.
   - Backend env: SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/edens_zac (from docker-compose.yml), SPRING_PROFILES_ACTIVE=dev by default.
   - Sequel Ace: host 127.0.0.1, port 3306, user zedens, password password (unless you override), database edens_zac.

2) Local run (outside Docker) without SPRING_DATASOURCE_URL:
   - DB URL: jdbc:mysql://${EC2_HOST:localhost}:3306/edens_zac
   - If EC2_HOST is set in your shell, the app connects to your EC2/RDS MySQL endpoint; otherwise it connects to your local MySQL on port 3306.
   - Sequel Ace must point to the same host: either your EC2/RDS host:3306 or 127.0.0.1:3306.

3) Local run (outside Docker) with SPRING_DATASOURCE_URL:
   - DB URL: whatever you set, e.g., jdbc:mysql://127.0.0.1:3306/edens_zac
   - Sequel Ace: match the same host/port/user/database.

Runtime verification:
- On startup, the app logs active profiles and the configured datasource URL/username and attempts to log the DB product and driver versions. Look for log lines from DatabaseInfoLogger to confirm exactly which DB you’re connected to.

Troubleshooting tips:
- If you don’t see new data in Sequel Ace, double-check that Sequel Ace is targeting the same MySQL host/port/database as the backend.
- DynamoDB endpoints are not compatible—ensure Sequel Ace points to a MySQL host.
- In Docker, the default creds are: user=z edens, password=password, db=edens_zac (see docker-compose.yml). Change via MYSQL_* envs if needed.
