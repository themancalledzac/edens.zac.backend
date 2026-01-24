# Content Collection Backend TODO


## Overview
This document outlines the remaining backend work for the ContentCollection system refactor, organized by priority. All completed work has been removed, and frontend work has been deferred.



#### ISSUES WE SEE NOW
* - database needs culling, reorganizing. remove 'priority', not needed, in favor of 'orderIndex
- '

---

## 🔴 HIGH PRIORITY - Critical Missing Features

### New Features List


* New Updates/Features going forward
*  - Get all tags endpoint
*  - Get all people endpoint
*  - create new tag endpoint
*  - create new 'person' endpoint
*  - Collections can maybe be ALSO a collection of a single person?
*  - - I'm imagining a 'edens-family' collection, that has different vacations, but also a link to each person?
*  - - Something like, a picture of Bodin that redirects to the 'Bodin' Collection
* 
- With all of these in mind, we probably need a 'ContentBlockControllerDev/Prod'
*  - - THese include all changes to ImageContentBlock, GifContentBLock, TextContentBLock, and CodeContentBlock
*  - Endpoints that are Image specific (ImageContentBLock)
*  - Edit Metadata of Single Image
*  - Edit Metadata of List of Images
*  - Add List of images to existing collection
*  - get All images by:
*  - - Tag ( new general 'tags' array for images )
*  - - - will need to be it's own table maybe? why or why not?
*  - - Person ( new 'person' array for images )
*  - - - will probably need to be it's own table. a many to many relationship with images and collections
*  - - date
*  - - location
*  - - rating
*  - - isFilm
*  - - Need to really think about this 'get By', and whether we want a more General 'get by', and then we filter on the frontend. these are all probably acceptable right now, though.
* 
*  - Will Tags need to be it's own 'controller/service/repository' layers? or can we do that in 'contentBlock'? does it exist as a contentBlock as well? what about 'People'
*  - 
*  - We need a `don't upload duplicate images` feature for image upload logic.
- - - as we continue to add new images, we need this explicit feature to allow us to upload more freely, without worry of duplication.


### Gif workflow implementation

#### Current State Analysis
**✅ Already Implemented:**
- `GifContentBlockEntity` - Database schema with fields for gifUrl, thumbnailUrl, width, height, author, createDate
- `GifContentBlockModel` - DTO for API responses
- `ContentBlockType.GIF` - Enum support
- Basic JPA inheritance structure in ContentCollection system

**❌ Missing Key Components:**
1. No gif upload endpoint (current `ImageControllerDev` only handles images)
2. No gif processing service (no `GifProcessingUtil` equivalent)
3. No gif metadata extraction capabilities
4. No first-frame extraction for thumbnail generation
5. No S3 folder structure for gifs (currently only `/images/{date}/`)
6. No gif-specific validation (file size, format, etc.)

#### Implementation Tasks

**🔴 HIGH PRIORITY - Core Gif Upload Functionality**

- [ ] **Create GifProcessingUtil Service**
  - Mirror structure of `ImageProcessingUtil` for consistency
  - Extract gif metadata (dimensions, frame count, duration, loop count)
  - Generate thumbnail from first frame (save as JPEG/WebP)
  - Handle S3 upload to `gifs/{date}/` folder structure
  - File size validation (gifs can be large - set reasonable limits)

- [ ] **Create Gif Upload Endpoint**
  - **Option A**: Extend `ImageControllerDev.postImages()` to handle gif files
  - **Option B**: Create dedicated `GifControllerDev` with `postGifs()` endpoint
  - **Recommendation**: Option A for code reuse, detect file type and route accordingly
  - Support batch gif uploads similar to image batch processing
  - Return gif metadata including gifUrl and thumbnailUrl

- [ ] **Implement First-Frame Thumbnail Generation**
  - Use ImageIO or similar library to extract first frame
  - Convert first frame to optimized JPEG/WebP format
  - Upload both gif and thumbnail to S3
  - Store both URLs in `GifContentBlockEntity`

- [ ] **Update S3 Folder Structure**
  - Add `gifs/{date}/` parallel to existing `images/{date}/`
  - Update S3Config if needed for gif-specific settings
  - Ensure CloudFront distribution covers gif files
  - Consider CDN optimization for large gif files

**🟠 MEDIUM PRIORITY - Enhanced Gif Features**

- [ ] **Gif Metadata Extraction**
  - Extract technical metadata: frame count, duration, bit rate
  - Extract EXIF data if present (author, create date, camera info)
  - Determine if gif is animated or static
  - Calculate file size optimization metrics

- [ ] **Gif Optimization Capabilities**
  - Analyze if gifs need compression (user mentioned they're already optimized)
  - Implement optional gif resizing for web delivery
  - Consider generating multiple sizes (original, web-optimized)
  - Add quality validation (reject corrupted gifs)

- [ ] **ContentCollection Integration**
  - Ensure gif content blocks work properly in collections
  - Test gif content blocks in all collection types (BLOG, ART_GALLERY, CLIENT_GALLERY, PORTFOLIO)
  - Implement proper ordering with mixed content types
  - Add gif support to collection export functionality

**🟡 LOW PRIORITY - Advanced Gif Features**

- [ ] **Gif Preprocessing Options**
  - Optional frame rate adjustment
  - Loop count modification
  - Color palette optimization
  - Progressive loading support

- [ ] **Gif Search and Filtering**
  - Search by gif-specific metadata (duration, frame count)
  - Filter by animated vs static gifs
  - Advanced gif collection management
  - Bulk gif operations

#### Technical Considerations

**File Size Management:**
- Gifs are typically larger than images - implement appropriate size limits
- Consider streaming upload for very large gifs
- Monitor S3 storage costs with gif uploads

**Performance Considerations:**
- First-frame extraction may be CPU intensive
- Consider async processing for large batch uploads
- Implement proper error handling for corrupted gif files

**Frontend Integration:**
- Ensure frontend can handle large gif files
- Implement proper loading states (show thumbnail while gif loads)
- Consider lazy loading for gif-heavy collections

**S3 Structure Recommendation:**
```
portfolio-bucket/
├── images/
│   └── {date}/
│       ├── original_image.jpg
│       └── compressed_image.webp
└── gifs/
    └── {date}/
        ├── original_animation.gif
        └── thumbnail_first_frame.jpg
```

#### Questions for Consideration

1. **Endpoint Design**: Extend existing image endpoint or create separate gif endpoint?
2. **Metadata Storage**: Store gif-specific metadata in dedicated fields or JSON blob?
3. **Thumbnail Strategy**: Always generate thumbnails or make optional?
4. **Size Limits**: What's the maximum acceptable gif file size?
5. **Browser Compatibility**: Any specific gif format requirements for web delivery?

#### Implementation Order Recommendation

1. **Phase 1**: Basic gif upload (GifProcessingUtil + endpoint extension)
2. **Phase 2**: First-frame thumbnail generation and S3 structure
3. **Phase 3**: Metadata extraction and ContentCollection integration
4. **Phase 4**: Advanced features and optimization

### Individual ContentBlock CRUD Operations
- [ ] **ContentBlock Update Endpoint** - `PUT /api/write/collections/{id}/content/{blockId}`
  - TODO: This should probably just be a "update ALl contentBlocks in collection" endpoint.
    - What I mean by this, is that we always pas an array or List<contentBlock>, as all edits would happen from a ContentCollection page initially. 
    - we can create a 'null', or unused for future use 'individual contentBlock update'
  - Individual content block updates without affecting collection
  - Support for reordering individual blocks
  - Proper transaction consistency

- [ ] **ContentBlock Delete Endpoint** - `DELETE /api/write/collections/{id}/content/{blockId}`
  - Individual content block deletion with proper cleanup
  - Handle cascade operations correctly
  - Prevent orphaned content blocks

- [ ] **ContentBlock Reordering Service Methods**
  - Service methods for fine-grained content block reordering
  - Bulk reordering operations
  - Order validation and gap handling

### Complete Image Management System
- [ ] **Individual Image Update Endpoint** - Complete `updateImage` functionality
  - Currently returns `null` in `ImageControllerDev.updateImage()`
  - Image metadata updates
  - Image replacement functionality

- [ ] **Batch Image Operations**
  - Bulk image metadata extraction
  - Batch image uploads for collections
  - Image search and filtering capabilities

- [ ] **Image Metadata Preservation**
  - Ensure all metadata from old system is preserved in ContentBlocks
  - Advanced image search by metadata (ISO, camera, lens, etc.)
  - Orphaned images detection and cleanup

### Error Handling Standardization
- [ ] **Consistent Error Response Patterns**
  - Standardize error responses across all controllers
  - Implement ProblemDetails (RFC 9457) consistently
  - Improve exception messaging

- [ ] **Centralized Exception Handling**
  - Complete `@ControllerAdvice` implementation
  - Proper logging strategies
  - Hide stack traces from production clients

---

## 🟠 MEDIUM PRIORITY - Enhancement Features

### Complete Home Page System
- [ ] **Finish updateHomePage Implementation**
  - Currently returns `null` in `HomeControllerDev.updateHomePage()`
  - Complete home card management
  - Priority-based sorting

- [ ] **Dynamic Home Page Generation**
  - Featured content rotation
  - Priority management for home cards
  - Integration with ContentCollections

### Advanced Search Implementation
- [ ] **Port ImageSpecification Logic**
  - Adapt existing advanced search capabilities for ContentBlocks
  - Advanced filtering combinations
  - Full-text search across collections

- [ ] **ContentCollection Search Capabilities**
  - Search by collection type
  - Tag-based content discovery
  - Combined search filters

### Bulk Operations
- [ ] **Batch Content Block Operations**
  - Bulk content block updates
  - Mass import/export capabilities
  - Batch collection operations

- [ ] **Collection-Level Bulk Actions**
  - Collection duplication
  - Collection archival/restoration
  - Collection export functionality

---

## 🟡 LOW PRIORITY - Nice to Have Features

### Performance Optimizations
- [ ] **Query Optimizations**
  - Optimize slow queries discovered in production
  - Database index improvements
  - Lazy loading optimizations

- [ ] **Caching Strategies**
  - Cache frequently accessed collections
  - S3 usage pattern optimizations
  - Connection pooling improvements

### Advanced Features
- [ ] **Collection Templates**
  - Pre-defined collection structures
  - Template-based collection creation
  - Type-specific templates

- [ ] **Content Versioning**
  - Version history for content blocks
  - Rollback capabilities
  - Change tracking

- [ ] **Audit Logging**
  - User action logging
  - Content change history
  - Security audit trails

---

## 🧪 COMPREHENSIVE TESTING REQUIREMENTS

### Missing Test Files

#### Entity Layer Tests
- [ ] **ContentCollectionHomeCardEntityTest.java** - Missing
  - Entity validation tests
  - Relationship mapping tests
  - Constraint validation tests

#### Repository Layer Tests
- [ ] **ContentBlockRepositoryTest.java** - Missing
  - Repository method tests
  - Pagination functionality tests
  - Custom query method validation
  - Performance tests with large datasets

- [ ] **HomeCardRepositoryTest.java** - Missing (if exists)
  - Repository method tests
  - Query performance tests

#### Service Layer Tests
- [ ] **ContentCollectionServiceImplTest.java** - Needs Enhancement
  - Current tests may not cover `addContentBlocks` flow fully
  - Test client gallery password validation
  - Test pagination edge cases
  - Test error scenarios and rollbacks

- [ ] **HomeServiceImplTest.java** - Missing
  - Home page generation tests
  - Priority management tests
  - Integration with ContentCollection tests

- [ ] **HomeProcessingUtilTest.java** - Missing
  - Utility method tests
  - Data conversion tests
  - Error handling tests

- [ ] **ImageServiceImplTest.java** - Missing
  - Image processing workflow tests
  - Metadata extraction tests
  - S3 integration tests

- [ ] **CatalogServiceImplTest.java** - Missing
  - Legacy system tests for comparison
  - Migration compatibility tests

- [ ] **ImageProcessingServiceTest.java** - Missing (if exists)
  - Image processing pipeline tests
  - Performance tests
  - Error scenario tests

#### Controller Layer Tests
- [ ] **HomeControllerProdTest.java** - Missing
  - Endpoint integration tests
  - Response format validation
  - Error handling tests

- [ ] **HomeControllerDevTest.java** - Missing
  - Development endpoint tests
  - Update functionality tests
  - Validation tests

- [ ] **ImageControllerProdTest.java** - Missing
  - Image serving tests
  - Metadata endpoint tests
  - Search functionality tests

- [ ] **ImageControllerDevTest.java** - Missing
  - Image upload tests
  - Update functionality tests (when implemented)
  - Batch operation tests

- [ ] **CatalogControllerProdTest.java** - Missing
  - Legacy endpoint tests
  - Compatibility tests
  - Migration validation tests

- [ ] **CatalogControllerDevTest.java** - Missing
  - Legacy development endpoint tests
  - CRUD operation tests

#### Integration Tests
- [ ] **ContentCollectionIntegrationTest.java** - Missing
  - Full workflow integration tests
  - Cross-service interaction tests
  - Database integration tests

- [ ] **ImageProcessingIntegrationTest.java** - Missing
  - S3 upload integration tests
  - Metadata extraction integration tests
  - Full image workflow tests

- [ ] **MigrationIntegrationTest.java** - Missing (when migration is implemented)
  - End-to-end migration tests
  - Data integrity validation tests
  - Performance tests with large datasets

#### Configuration Tests
- [ ] **S3ConfigTest.java** - Missing
  - Configuration validation tests
  - Connection tests
  - Error scenario tests

- [ ] **WebConfigTest.java** - Missing
  - Web configuration tests
  - CORS configuration tests
  - Security configuration tests

#### Utility Tests
- [ ] **PaginationUtilTest.java** - Missing
  - Pagination logic tests
  - Edge case handling tests
  - Performance tests

- [ ] **ExceptionUtilsTest.java** - Missing
  - Exception handling utility tests
  - Error message formatting tests
  - Stack trace handling tests

### Test Coverage Requirements

#### Unit Tests (Target: 100% for new code)
- **Entity Validation**: All Bean Validation annotations tested
- **Repository Methods**: All custom query methods tested
- **Service Logic**: All business logic paths tested
- **Utility Functions**: All helper methods tested
- **Model Conversions**: All entity-to-model conversions tested

#### Integration Tests
- **API Endpoints**: All endpoints tested with realistic data
- **Database Operations**: All CRUD operations tested
- **S3 Integration**: File upload/download workflows tested
- **Pagination**: Large dataset pagination tested
- **Security**: Client gallery access controls tested

#### Performance Tests
- **Large Collections**: Test with 200+ content blocks
- **Database Queries**: Query performance validation
- **S3 Operations**: Batch upload performance tests
- **Memory Usage**: Large dataset memory footprint tests

#### Error Scenario Tests
- **Database Failures**: Transaction rollback tests
- **S3 Failures**: File upload failure handling tests
- **Validation Failures**: Invalid input handling tests
- **Authentication Failures**: Security violation tests

---

## 🚀 CI/CD & DEPLOYMENT AUTOMATION

### Current State Analysis
**Current Deployment Process:**
- Manual GitHub push/merge
- SSH into EC2 instance via `ec2Login()`
- Manual execution of `./deploy.sh` on server
- **Pain Points:** Manual intervention, no rollback strategy, single point of failure, no deployment validation

---

### 🔴 HIGH PRIORITY - GitHub Actions CI/CD Pipeline Implementation

#### Step 1: Maven Linting Configuration
- [ ] **Add Checkstyle Plugin to pom.xml**
  - Version: 3.3.1 with Checkstyle 10.12.7
  - Config: `google_checks.xml` (Google Java Style Guide)
  - Failure settings: `failsOnError=true`, `violationSeverity=warning`
  - Phase: `validate` (runs early in build)
  - Exclude: Test source directory

- [ ] **Add SpotBugs Plugin to pom.xml**
  - Version: 4.8.3.0 with FindSecBugs for security checks
  - Effort: Max, Threshold: Low
  - Exclusion file: `spotbugs-exclude.xml`
  - Phase: `verify` (runs after compile)
  - Failure: `failOnError=true`

- [ ] **Add Maven Surefire Plugin to pom.xml**
  - Version: 3.2.5
  - Includes: `**/*Test.java`
  - Generates JUnit XML reports for GitHub Actions

#### Step 2: Create Configuration Files
- [ ] **Create `spotbugs-exclude.xml`** (root directory)
  - Exclude Lombok-generated inner classes
  - Exclude Lombok equals/hashCode warnings in entities
  - Exclude Spring dependency injection warnings

- [ ] **Create `dependency-check-suppressions.xml`** (root directory)
  - OWASP dependency check suppression template
  - For known false positives or accepted vulnerabilities

#### Step 3: GitHub Actions Workflow
- [ ] **Create `.github/workflows/ci-cd.yml`**
  - **Stage 1: Lint** (BLOCKING) - `mvn checkstyle:check && mvn spotbugs:check`
  - **Stage 2: Test** (depends on lint) - PostgreSQL 16 container, `mvn test -B`
  - **Stage 3: Build** (depends on test) - `mvn clean package -DskipTests -B`, Docker build
  - **Stage 4: Security Scan** (depends on lint) - OWASP Dependency Check, CVSS threshold 7
  - **Stage 5: Deploy** (depends on build) - SSH to EC2, execute `deploy.sh`, health check
  - Maven dependency caching with `actions/cache@v4`
  - All artifacts retained for 7-14 days

#### Step 4: GitHub Configuration
- [ ] **Configure GitHub Secrets** (Settings → Secrets and variables → Actions)
  - `EC2_SSH_PRIVATE_KEY` - Private SSH key (.pem file content)
  - `EC2_HOST` - EC2 public IP or hostname
  - `EC2_USER` - SSH username (e.g., `ubuntu`, `ec2-user`)
  - `POSTGRES_PASSWORD` - PostgreSQL password for production
  - `AWS_ACCESS_KEY_ID` - AWS credentials for S3
  - `AWS_SECRET_ACCESS_KEY` - AWS secret key
  - Optional: `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`

- [ ] **Set Up Branch Protection Rules** (Settings → Branches)
  - Branch: `main`
  - ✅ Require pull request before merging
  - ✅ Require status checks: `lint`, `test`, `build`, `security-scan`
  - ✅ Require branches to be up to date before merging
  - ✅ Require conversation resolution before merging

#### Step 5: Testing & Validation
- [ ] **Fix Existing Linting Violations**
  - Run locally: `mvn checkstyle:check`
  - Run locally: `mvn spotbugs:check`
  - Fix fully-qualified exception names, lambda formatting, unused imports, magic numbers

- [ ] **Local Testing Before Commit**
  - Test build: `mvn clean package`
  - Test Docker build: `docker build -t edens.zac.backend:test .`
  - Verify all checks pass

- [ ] **Create Feature Branch & Test Pipeline**
  - Branch: `feature/github-actions-cicd`
  - Push changes and verify workflow runs
  - Check all 5 stages complete successfully
  - Review uploaded artifacts

- [ ] **Test Branch Protection**
  - Create PR to `main`
  - Verify required checks appear
  - Cannot merge until all checks pass

- [ ] **Test Automatic Deployment**
  - Merge PR to main
  - Verify deploy job triggers automatically
  - SSH connection succeeds, `deploy.sh` executes
  - Health check passes: `curl http://localhost:8080/actuator/health`

#### Step 6: Documentation
- [ ] **Add CI/CD Status Badge to README.md**
  - Badge: `[![CI/CD Pipeline](https://github.com/themancalledzac/edens.zac.backend/actions/workflows/ci-cd.yml/badge.svg)](...)`

- [ ] **Document Rollback Procedure**
  - SSH to EC2
  - Find previous Docker image SHA
  - Tag previous image as latest
  - Restart containers with previous version

---

### 🟠 MEDIUM PRIORITY - Future Terraform & Advanced CI/CD

#### Environment Configuration Management
  - Secure secrets management via GitHub Actions secrets
  - Environment-specific variables (staging vs production)
  - Database connection string management
  - S3 bucket configuration per environment

#### Terraform Infrastructure as Code
- [ ] **Create `terraform/` Directory Structure**
  ```
  terraform/
  ├── environments/
  │   ├── staging/
  │   └── production/
  ├── modules/
  │   ├── ec2/
  │   ├── rds/
  │   ├── s3/
  │   └── networking/
  └── shared/
  ```

- [ ] **EC2 Infrastructure Module**
  - Auto Scaling Group configuration
  - Load Balancer setup for high availability
  - Security Groups with least-privilege access
  - Automated EC2 instance provisioning

- [ ] **Database Infrastructure Module**
  - RDS instance configuration
  - Automated backups and snapshots
  - Multi-AZ deployment for production
  - Parameter group optimization

- [ ] **Storage Infrastructure Module**
  - S3 bucket creation and policies
  - CloudFront distribution for image delivery
  - Backup and versioning strategies
  - Cross-region replication for disaster recovery

#### Deployment Automation Scripts
- [ ] **Create `scripts/deploy/` Directory**
  - `build.sh` - Application build automation
  - `deploy.sh` - Enhanced deployment with validation
  - `rollback.sh` - Automated rollback mechanism
  - `health-check.sh` - Post-deployment validation

- [ ] **Blue-Green Deployment Strategy**
  - Dual environment setup (blue/green)
  - Zero-downtime deployment process
  - Automated traffic switching
  - Quick rollback capabilities

---

### 🟠 MEDIUM PRIORITY - Advanced CI/CD Features

#### Monitoring & Observability
- [ ] **Application Performance Monitoring**
  - CloudWatch integration for EC2 metrics
  - Application-level logging and metrics
  - Database performance monitoring
  - S3 usage and cost tracking

- [ ] **Deployment Monitoring**
  - Deployment success/failure notifications
  - Performance regression detection
  - Automated alerting for critical issues
  - Slack/Discord integration for team notifications

#### Security & Compliance
- [ ] **Automated Security Scanning**
  - Dependency vulnerability scanning
  - Container image security scanning (if using Docker)
  - Infrastructure security compliance checks
  - Secrets scanning in code commits

- [ ] **Access Control & Audit**
  - IAM roles with minimal required permissions
  - Deployment audit logging
  - Multi-factor authentication for production deployments
  - Environment access controls

#### Testing Integration
- [ ] **Automated Testing in Pipeline**
  - Unit test execution in CI
  - Integration test execution in staging
  - Performance test baseline validation
  - Database migration testing

- [ ] **Quality Gates**
  - Code coverage thresholds
  - Performance regression prevention
  - Security vulnerability blocking
  - Manual approval gates for production

---

### 🟡 LOW PRIORITY - Advanced Deployment Features

#### Multi-Environment Management
- [ ] **Environment Parity**
  - Consistent infrastructure across environments
  - Environment-specific scaling rules
  - Configuration drift detection
  - Automated environment provisioning

- [ ] **Feature Flag Integration**
  - Feature toggle implementation
  - Gradual rollout capabilities
  - A/B testing infrastructure
  - Dynamic configuration management

#### Cost Optimization
- [ ] **Resource Optimization**
  - Automated scaling based on load
  - Cost monitoring and alerting
  - Reserved instance management
  - Unused resource cleanup automation

- [ ] **Deployment Cost Tracking**
  - Per-deployment cost attribution
  - Resource usage optimization
  - Backup and storage cost optimization
  - Multi-cloud cost comparison

---

### 🔧 MIGRATION STRATEGY - Current to Automated

#### Phase 1: Foundation (Week 1-2)
- [ ] **Setup GitHub Actions Basic CI**
  - Basic build and test pipeline
  - Code quality checks
  - Security scanning setup

- [ ] **Create Terraform Modules**
  - Start with staging environment
  - Basic EC2 and RDS modules
  - S3 bucket management

#### Phase 2: Automated Deployment (Week 3-4)
- [ ] **Implement Blue-Green Deployment**
  - Create deployment scripts
  - Setup staging environment automation
  - Test automated rollback procedures

- [ ] **Production Pipeline Setup**
  - Manual approval gates
  - Production-specific security measures
  - Monitoring and alerting integration

#### Phase 3: Advanced Features (Week 5-6)
- [ ] **Enhanced Monitoring**
  - Application performance monitoring
  - Cost tracking and optimization
  - Advanced alerting rules

- [ ] **Documentation and Training**
  - Runbook creation for common scenarios
  - Team training on new deployment process
  - Emergency procedures documentation

---

### 🚨 CRITICAL DEPLOYMENT CONSIDERATIONS

#### Security Requirements
- **Never commit secrets to repository** - Use GitHub Actions secrets exclusively
- **Implement least-privilege IAM roles** - Separate roles for CI/CD vs runtime
- **Enable audit logging** - Track all deployment activities and changes
- **Secure network configuration** - VPC, security groups, and NACLs properly configured

#### Data Safety
- **Automated backups before deployment** - RDS snapshots and S3 versioning
- **Database migration validation** - Test migrations in staging first
- **Rollback strategy for data changes** - Plan for schema rollbacks
- **Zero-downtime deployment validation** - Ensure no service interruption

#### Performance Requirements
- **Health checks during deployment** - Validate application functionality
- **Performance baseline monitoring** - Detect performance regressions
- **Resource utilization tracking** - Monitor CPU, memory, and disk usage
- **Load testing in staging** - Validate performance before production

### 📋 RECOMMENDED IMPLEMENTATION ORDER

**🎓 LEARNING-FOCUSED INCREMENTAL APPROACH**
Each step must be implemented incrementally with full understanding before proceeding to the next component. This approach prioritizes learning and comprehension over speed.

1. **Setup GitHub Actions CI pipeline** (Immediate - can run parallel with current deployment)
2. **Create basic Terraform modules** (Week 1 - start with staging environment)
3. **Implement blue-green deployment** (Week 2 - eliminates manual SSH requirement)
4. **Add automated monitoring** (Week 3 - visibility into deployment success)
5. **Production pipeline with approval gates** (Week 4 - replace manual production deployment)
6. **Advanced features and optimization** (Ongoing - continuous improvement)

### 🔄 INCREMENTAL IMPLEMENTATION METHODOLOGY

**Core Principle: One Concept at a Time**
- Each Terraform resource added individually with full explanation
- Test and validate each component before adding the next
- Build understanding through hands-on implementation
- Always start with the simplest working version first

#### Terraform Incremental Strategy
**Phase 1: Minimal Working Infrastructure**
- [ ] **Step 1:** Single EC2 instance with basic configuration
  - Understand: AWS provider, resource blocks, variables
  - Test: Manual deployment to verify Terraform works
  - Learn: `terraform plan`, `terraform apply`, state management

- [ ] **Step 2:** Add basic security group
  - Understand: Security group rules, ingress/egress
  - Test: Verify EC2 instance is accessible with new security rules
  - Learn: Resource dependencies, terraform state inspection

- [ ] **Step 3:** Add VPC with single subnet
  - Understand: Network isolation, CIDR blocks, availability zones
  - Test: EC2 instance deployed within custom VPC
  - Learn: Resource relationships, implicit vs explicit dependencies

**Phase 2: Database Integration**
- [ ] **Step 4:** Add RDS instance (smallest configuration)
  - Understand: RDS basics, subnet groups, parameter groups
  - Test: Database connectivity from EC2 instance
  - Learn: Multi-resource coordination, sensitive data handling

- [ ] **Step 5:** Add database subnet group
  - Understand: Multi-AZ concepts, database networking
  - Test: RDS deployment across multiple subnets
  - Learn: Resource references, cross-resource communication

**Phase 3: Storage and Load Balancing**
- [ ] **Step 6:** Add S3 bucket with basic configuration
  - Understand: S3 bucket policies, versioning, lifecycle rules
  - Test: File upload/download from application
  - Learn: IAM integration, resource permissions

- [ ] **Step 7:** Add Application Load Balancer
  - Understand: Load balancing concepts, target groups, health checks
  - Test: Traffic distribution, failover scenarios
  - Learn: Advanced networking, service discovery

**Phase 4: Advanced Features (One at a Time)**
- [ ] **Step 8:** Add Auto Scaling Group (start with fixed size)
  - Understand: Launch configurations, scaling policies
  - Test: Instance replacement, capacity management
  - Learn: Dynamic infrastructure, state management

- [ ] **Step 9:** Add CloudWatch monitoring
  - Understand: Metrics, alarms, log groups
  - Test: Alert triggering, log aggregation
  - Learn: Observability, automated responses

#### GitHub Actions Incremental Strategy
**Phase 1: Basic CI Pipeline**
- [ ] **Step 1:** Simple build-only workflow
  - Understand: YAML syntax, job structure, runners
  - Test: Code compilation and basic validation
  - Learn: GitHub Actions fundamentals

- [ ] **Step 2:** Add automated testing
  - Understand: Test execution, result reporting
  - Test: Test suite execution on every commit
  - Learn: Quality gates, failure handling

- [ ] **Step 3:** Add security scanning
  - Understand: Vulnerability detection, dependency analysis
  - Test: Security report generation and review
  - Learn: Security automation, compliance checking

**Phase 2: Deployment Automation**
- [ ] **Step 4:** Add staging deployment
  - Understand: Environment-specific deployment, secrets management
  - Test: Automated deployment to staging environment
  - Learn: Environment isolation, configuration management

- [ ] **Step 5:** Add production deployment with manual approval
  - Understand: Approval workflows, environment promotion
  - Test: Controlled production deployment process
  - Learn: Risk mitigation, change management

### 🧠 LEARNING VALIDATION CHECKLIST

After each incremental step, verify understanding by answering:
- **What does this component do?** (Functional understanding)
- **Why is it configured this way?** (Design rationale)
- **How does it interact with other components?** (System integration)
- **What would happen if this failed?** (Failure scenarios)
- **How would I troubleshoot issues?** (Operational knowledge)

### 📚 DOCUMENTATION REQUIREMENTS

For each incremental step, create:
- [ ] **Step-by-step implementation notes** - What was done and why
- [ ] **Configuration explanations** - Purpose of each setting/parameter
- [ ] **Testing validation** - How to verify the step worked correctly
- [ ] **Troubleshooting guide** - Common issues and solutions encountered
- [ ] **Next step preparation** - What needs to be understood before proceeding

### 🚫 ANTI-PATTERNS TO AVOID

- **Never implement multiple Terraform resources simultaneously**
- **Never skip testing/validation between steps**
- **Never proceed without understanding the current step**
- **Never copy-paste large configuration blocks without explanation**
- **Never implement "advanced" features before mastering basics**

This approach allows for gradual migration while maintaining current deployment capability as a fallback during the transition period, with the added benefit of building deep understanding of each component.

---

## 🧹 LEGACY CLEANUP - Removal of Deprecated Catalog System

### Overview
The new `ContentCollection` system has fully replaced the legacy `Catalog` system. All catalog functionality is now handled through `ContentCollectionControllerDev` and `ContentCollectionControllerProd`. This section documents the strategy for removing all legacy code while ensuring no functionality currently in use is broken.

### Strategy
**Three-Phase Approach:**
1. **Phase 1: Verification** - Confirm no new code depends on legacy components
2. **Phase 2: Incremental Removal** - Remove components in dependency order
3. **Phase 3: Database Cleanup** - Plan database migration for legacy tables

---

### 🔴 HIGH PRIORITY - Phase 1: Verification & Analysis

#### Verify New System Independence
- [ ] **Audit ContentCollection Controllers**
  - Verify `ContentCollectionControllerDev` has no imports of Catalog classes
  - Verify `ContentCollectionControllerProd` has no imports of Catalog classes
  - Confirm all functionality works without Catalog dependencies
  - Test all ContentCollection endpoints to ensure they work independently

- [ ] **Audit ContentCollection Services**
  - Verify `ContentCollectionService` and `ContentCollectionServiceImpl` don't reference Catalog
  - Check `ContentBlockProcessingUtil` for Catalog dependencies
  - Confirm all business logic uses new ContentBlock entities only

- [ ] **Document Current Usage**
  - Create list of all files that import Catalog classes
  - Document which methods in shared services use Catalog (HomeService, ImageService)
  - Identify any database queries that join Catalog tables
  - Map all REST endpoints that serve Catalog data

---

### 🔴 HIGH PRIORITY - Phase 2: Code Removal

#### Controllers to Remove (Priority Order)

- [ ] **Remove Catalog Controllers**
  - `src/main/java/edens/zac/portfolio/backend/controller/dev/CatalogControllerDev.java`
    - Endpoints: All `/api/write/catalog/*` endpoints
    - Used by: Legacy frontend only (confirm before removal)
  - `src/main/java/edens/zac/portfolio/backend/controller/prod/CatalogControllerProd.java`
    - Endpoints: All `/api/catalog/*` endpoints
    - Used by: Legacy frontend only (confirm before removal)

- [ ] **Remove or Update Image Controllers**
  - **Decision Point**: Remove entirely or clean up Catalog-specific endpoints?
  - **Option A**: Remove both ImageControllerDev and ImageControllerProd (if image upload is now through ContentCollection)
  - **Option B**: Keep controllers but remove Catalog-specific endpoints only
  - **Recommendation**: Review if image upload is fully integrated into ContentCollection workflow

  Files:
  - `src/main/java/edens/zac/portfolio/backend/controller/dev/ImageControllerDev.java`
    - Remove endpoint: `POST /api/write/image/postImagesForCatalog/{catalogTitle}` (line 59-65)
    - Remove method: `postImagesForCatalog()`
  - `src/main/java/edens/zac/portfolio/backend/controller/prod/ImageControllerProd.java`
    - Remove endpoint: `GET /api/image/catalog/{catalog}` (references `getAllImagesByCatalog`)
    - Remove method that calls `imageService.getAllImagesByCatalog()`

#### Services to Remove

- [ ] **Remove Catalog Service Layer**
  - `src/main/java/edens/zac/portfolio/backend/services/CatalogService.java` (interface)
  - `src/main/java/edens/zac/portfolio/backend/services/CatalogServiceImpl.java` (implementation)
  - `src/main/java/edens/zac/portfolio/backend/services/CatalogProcessingUtil.java` (utility)

  **Dependencies to check before removal:**
  - These services call `HomeService.createHomeCardFromCatalog()` and `HomeService.updateHomeCard()`
  - No other services should depend on these

#### Services to Update (Remove Catalog-Specific Methods)

- [ ] **Update HomeService and HomeServiceImpl**
  - `src/main/java/edens/zac/portfolio/backend/services/HomeService.java`
    - Remove method: `void createHomeCardFromCatalog(CatalogEntity catalog);`
    - Remove method: `void updateHomeCard(CatalogEntity catalog);`
    - Keep: `upsertHomeCardForCollection()`, `syncHomeCardOnCollectionUpdate()` (these are for ContentCollection)

  - `src/main/java/edens/zac/portfolio/backend/services/HomeServiceImpl.java`
    - Remove method: `createHomeCardFromCatalog(CatalogEntity catalog)` (line 65-78)
    - Remove method: `updateHomeCard(CatalogEntity catalog)` (line 81-95)
    - Remove method: `getHomeCardEntity(CatalogEntity catalog, ...)` (line 157-165)
    - Remove import: `import edens.zac.portfolio.backend.entity.CatalogEntity;`

- [ ] **Update HomeProcessingUtil**
  - `src/main/java/edens/zac/portfolio/backend/services/HomeProcessingUtil.java`
    - Remove method: `createHomeCardFromCatalog(CatalogEntity catalog)` (line 29-43)
    - Remove import: `import edens.zac.portfolio.backend.entity.CatalogEntity;`
    - Keep: `convertModel()` method (used for ContentCollection home cards)

- [ ] **Update ImageService and ImageServiceImpl**
  - `src/main/java/edens/zac/portfolio/backend/services/ImageService.java`
    - Remove method: `List<ImageModel> postImagesForCatalog(List<MultipartFile> images, String catalogTitle) throws IOException;`
    - Remove method: `List<ImageModel> getAllImagesByCatalog(String catalogTitle);`

  - `src/main/java/edens/zac/portfolio/backend/services/ImageServiceImpl.java`
    - Remove method: `postImagesForCatalog()` implementation
    - Remove method: `getAllImagesByCatalog()` implementation
    - Remove constructor parameter: `CatalogRepository catalogRepository`
    - Remove field: `private final CatalogRepository catalogRepository;`
    - Remove import: `import edens.zac.portfolio.backend.entity.CatalogEntity;`
    - Remove import: `import edens.zac.portfolio.backend.repository.CatalogRepository;`

- [ ] **Update ImageProcessingUtil**
  - `src/main/java/edens/zac/portfolio/backend/services/ImageProcessingUtil.java`
    - Remove method: `getCatalogEntity(String catalogTitle)` (private method)
    - Remove any code in `postImageProcessing()` that references Catalog
    - Remove any code that adds images to catalog collections
    - Remove constructor parameter: `CatalogRepository catalogRepository`
    - Remove field: `private final CatalogRepository catalogRepository;`
    - Remove import: `import edens.zac.portfolio.backend.entity.CatalogEntity;`
    - Remove import: `import edens.zac.portfolio.backend.repository.CatalogRepository;`
    - Update `convertImageModel()` to remove catalog-related code:
      - Remove line: `imageModel.setCatalog(imageEntity.getCatalogNames());`

#### Entities to Remove

- [ ] **Remove CatalogEntity**
  - `src/main/java/edens/zac/portfolio/backend/entity/CatalogEntity.java`
  - **CRITICAL**: This will break the ManyToMany relationship in ImageEntity
  - Must update ImageEntity first (see below)

#### Entities to Update

- [ ] **Update ImageEntity**
  - `src/main/java/edens/zac/portfolio/backend/entity/ImageEntity.java`
    - Remove field: `private Set<CatalogEntity> catalogs = new HashSet<>();` (lines 51-52)
    - Remove annotation: `@ManyToMany(mappedBy = "images", cascade = CascadeType.PERSIST)`
    - Remove method: `getCatalogNames()` (lines 60-62)
  - **Database Impact**: The join table `catalog_images` (or similar) can be dropped after this change
  - **Testing**: Ensure ImageContentBlock functionality still works after removal

#### Models/DTOs to Remove

- [ ] **Remove All Catalog Models**
  - `src/main/java/edens/zac/portfolio/backend/model/CatalogModel.java`
  - `src/main/java/edens/zac/portfolio/backend/model/CatalogCreateDTO.java`
  - `src/main/java/edens/zac/portfolio/backend/model/CatalogUpdateDTO.java`
  - `src/main/java/edens/zac/portfolio/backend/model/CatalogImagesDTO.java`

#### Repositories to Remove

- [ ] **Remove CatalogRepository**
  - `src/main/java/edens/zac/portfolio/backend/repository/CatalogRepository.java`
  - Verify no other repositories depend on this
  - Update services that inject this repository (see Services section above)

#### Exceptions to Remove

- [ ] **Remove Catalog Exception Classes**
  - `src/main/java/edens/zac/portfolio/backend/exceptions/catalogExceptions.java`
  - Check if any remaining code references these exceptions
  - Replace with generic exceptions if needed

#### Tests to Remove/Update

- [ ] **Audit Test Files**
  - Search for any Catalog-related test files (none found currently, but verify)
  - Update any integration tests that reference Catalog endpoints
  - Remove any mocked Catalog dependencies in remaining tests

---

### 🟠 MEDIUM PRIORITY - Phase 3: Database Cleanup

#### Database Migration Planning

- [ ] **Analyze Database Impact**
  - Identify all Catalog-related tables: `catalogs`, `catalog_images` (join table), etc.
  - Count rows in each table to understand data volume
  - Check for foreign key constraints that reference these tables
  - Document any triggers or stored procedures that use Catalog tables

- [ ] **Create Database Migration Scripts**
  - **Pre-Migration Backup**: Always backup database before any schema changes
  - **Drop Join Table**: `DROP TABLE catalog_images;` (or similar name)
  - **Drop Catalog Table**: `DROP TABLE catalogs;`
  - **Update ImageEntity columns**: Remove any catalog-related columns if they exist
  - Test migration scripts on development database first

- [ ] **Data Archival Strategy**
  - Decide if old Catalog data should be archived before deletion
  - Export Catalog data to JSON/CSV for historical records
  - Document migration date and archived data location
  - Consider keeping archived data for 90 days before permanent deletion

---

### 🟡 LOW PRIORITY - Phase 4: Documentation & Cleanup

#### Update Documentation

- [ ] **Update API Documentation**
  - Remove all Catalog endpoints from API documentation (README.md)
  - Update endpoint lists to only show ContentCollection endpoints
  - Add migration notes explaining the change from Catalog to ContentCollection

- [ ] **Update README.md**
  - Remove endpoint documentation for:
    - `/api/write/catalog/*`
    - `/api/catalog/*`
    - `/api/write/image/postImagesForCatalog/{catalogTitle}`
    - `/api/image/catalog/{catalog}`
  - Update architecture diagrams if they reference Catalog system

- [ ] **Update Project Context Files**
  - Update `.junie/guidelines.md` to remove Catalog references
  - Update `CLAUDE.md` if it references Catalog system
  - Update this `todo.md` file after cleanup is complete

---

### 🚨 CRITICAL SAFETY CHECKS

#### Pre-Removal Validation Checklist

Before removing ANY legacy code, verify:

- [ ] **No Production Dependencies**
  - Run full test suite to ensure no tests fail
  - Verify no new ContentCollection code imports Catalog classes
  - Check application logs for any Catalog-related errors
  - Confirm frontend no longer calls Catalog endpoints

- [ ] **Database Backup**
  - Create full database backup before schema changes
  - Document backup location and restore procedure
  - Test restore procedure on development environment

- [ ] **Gradual Rollout**
  - Remove code in small batches, not all at once
  - Commit each batch separately with clear commit messages
  - Test application after each batch removal
  - Be prepared to revert if issues arise

- [ ] **Feature Flag Option (Advanced)**
  - Consider implementing feature flag to toggle between systems
  - Allows quick rollback without code deployment
  - Useful if you're uncertain about production dependencies

---

### 📋 IMPLEMENTATION ORDER (RECOMMENDED)

**Week 1: Verification Phase**
1. Audit all files to confirm no ContentCollection code depends on Catalog
2. Document all current Catalog usage (controllers, services, database)
3. Create comprehensive test plan for validation
4. Backup production database

**Week 2: Service Layer Cleanup**
5. Remove Catalog-specific methods from HomeService, HomeServiceImpl, HomeProcessingUtil
6. Remove Catalog-specific methods from ImageService, ImageServiceImpl, ImageProcessingUtil
7. Run full test suite after each change
8. Deploy to staging and validate

**Week 3: Controller & Core Removal**
9. Remove CatalogControllerDev and CatalogControllerProd
10. Remove or clean up ImageControllerDev and ImageControllerProd
11. Remove CatalogService, CatalogServiceImpl, CatalogProcessingUtil
12. Remove CatalogRepository
13. Deploy to staging and validate all ContentCollection endpoints work

**Week 4: Entity & Database Cleanup**
14. Update ImageEntity to remove Catalog relationship
15. Remove CatalogEntity
16. Remove all Catalog models/DTOs
17. Remove catalogExceptions.java
18. Run database migration scripts to drop tables
19. Final validation and deploy to production

**Week 5: Documentation & Finalization**
20. Update all documentation to remove Catalog references
21. Archive old Catalog data if needed
22. Update project guidelines and context files
23. Mark this section as completed in todo.md

---

### 📝 VERIFICATION CRITERIA

**How do you know the cleanup is complete?**

- [ ] Zero grep results for `import.*Catalog` in `/src/main/java` directory
- [ ] Zero references to `CatalogEntity` in codebase
- [ ] Zero references to `CatalogRepository` in codebase
- [ ] All tests pass with no Catalog-related failures
- [ ] Application starts without Catalog-related errors
- [ ] All ContentCollection endpoints return expected data
- [ ] No database foreign key constraints reference Catalog tables
- [ ] Documentation updated to reflect new architecture

---

## 📊 Current System Status

| Component | Implementation Status | Test Coverage Status |
|-----------|----------------------|---------------------|
| Core Entities | ✅ Complete | ⚠️ Partial |
| Repositories | ✅ Complete | ⚠️ Partial |
| Service Layer | ✅ Mostly Complete | ⚠️ Partial |
| Read Controllers | ✅ Complete | ✅ Good |
| Write Controllers | ⚠️ Missing CRUD ops | ⚠️ Partial |
| Models/DTOs | ✅ Complete | ✅ Good |
| Utilities | ✅ Complete | ✅ Good |
| Home Integration | ❌ Incomplete | ❌ Missing |
| Image Management | ❌ Incomplete | ❌ Missing |
| Migration Tools | ❌ Not Started | ❌ Not Started |

---

## 🚨 Critical Implementation Notes

### Security Considerations
- **Client Gallery Passwords**: Currently using SHA-256, need to migrate to BCrypt before production use
- **Input Validation**: Ensure all user inputs are sanitized to prevent injection attacks
- **Sensitive Data**: Never expose sensitive data in API responses or logs

### Performance Monitoring
- **Database Performance**: Monitor new pagination queries
- **S3 Usage**: Track usage patterns with mixed content types
- **Memory Usage**: Monitor memory footprint with large collections

### Code Quality Requirements
- **Follow Java 17+ best practices**: Use modern Java features appropriately
- **Maintain Spring Boot patterns**: Follow established conventions
- **Comprehensive logging**: Proper log levels and structured logging
- **Exception handling**: Consistent error responses and proper HTTP status codes

### Data Migration Strategy
- **Parallel Development**: Keep existing system functional during transition
- **Gradual Migration**: Migrate collections by risk level (art galleries first, client galleries last)
- **Data Validation**: Comprehensive validation before and after migration
- **Rollback Planning**: Ability to revert migrations if issues arise

---

## 📝 Next Immediate Steps

1. **Implement missing ContentBlock CRUD operations** (High Priority)
2. **Complete Home Page system integration** (High Priority)
3. **Finish Image management functionality** (High Priority)
4. **Create comprehensive test suite** (Critical for reliability)
5. **Implement migration service** (Required for production deployment)
6. **Performance testing with realistic data** (Required before production use)

This refactor provides significant architectural improvements but requires careful implementation to maintain existing functionality while gaining the benefits of the new flexible design.



## THOUGHTS:

- NEED to verify ALL todos to fully deploy the postgreSQL db will work.
  - Already exists, so don't need to create
  - Deploy of backend only effects the backend part of the EC2 instance
  - What part of our 'docker' deploy needs to be our DB? do we need it at all? why do we even have it as part of our deployment strategy if not?
- NEED to add a 'docker builder prune -f' or something on a successful deploy, so as to keep our EC2 instance light
- Want to look at the idea of having 'generated' collections, such as: 
  - all images from this city
  - A parent collection for a certain Tag or Location, (i.e. America), that adds child collections based on if the collection fits, or maybe if a certain percentage of images also have that same tag/locagion
  - a 'tag' collection
  - a 'person' collection
- Want to work on 'image locations' and 'collection' locations as the same database ( if not yet )
- Want to update frontend location to a dropdown
- Want to update 'textContent' to match our new frontend, which includes things like:
  - (side note, the 'header text box' is using data from the collection)
  - 'title', 'description', 'date'(inherited?), 'paragraph2?', thoughts?
- Need to make sure we add 'locations' to our metadata list ( for the 'Manage/Update' api call)
  - This makes it like 'filmType', 'people', 'tags', etc
- FRONTEND:
  - Need the 'edit multiple images' to have a 'select all'
  - Need the 'edit image' box to have the 'save' on the bottom ALWAYS visible
  - Need to condense 'manage' top header to be far more concise
  - Need any text box to be more dynamic with layout
  - Need to work on 'line algorithm'
    - This should work similarly to our 'ImageMetadata' ENUM:
    - a concise way of describing each 'line organization'/grouping/shape
    - a concise way of describing the required images for that grouping/shape
    - a better name for our 'line algorithm' - box organizer or some shit
  - Need to add 'people' and 'tags' from all images in a collection, to the collection header textBox
    - Do we need to think about how we 'organize' these 'people/tags'? do we want the backend to have these as items IN the collection, so the frontend doesn't need to do logic to pull them out of each image that is in the API response body?
    - Backend would need to update the 'getCollection' endpoint to simply, for each associated image, add any people/tag/location to the collection itself
  - Need
- UPDATE 'collection type' to include a 'parent' collection type ( naming can change )
  - This is specifically for collections that are solely for the purpose of organizing other collections. maybe lacks a 'date', or other normal collection data
- Need to be able to upload images at the same time as creating collection, if wanted
  - Reason being, sometimes idk what collection/name i'll use until i get the images
- Future goal - ai model to help 'suggest' tags, based on image inputs. Will require MORE of my images to be uploaded
- NEED way of being able to change the 'siteSlug' when the title of the collection changes
- 