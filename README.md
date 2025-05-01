# Photography and Coding Portfolio Backend API

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