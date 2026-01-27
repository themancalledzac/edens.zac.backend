# Flyway Database Migration Implementation Plan

## Overview

This document outlines the complete plan for implementing Flyway database migrations in the portfolio backend. Flyway will replace the current `postgres-init.sql` initialization script with a versioned, incremental migration system that supports schema evolution without data loss.

---

## ⚠️ Critical Issues Found & Fixed

**Last Updated**: 2026-01-27

### Critical Issues Identified:

1. **Missing `location` table**: The `postgres-init.sql` does not include the `location` table that is required by `LocationEntity` and `LocationDao`. **FIXED**: Added section 2.3 with required schema.

2. **Unused `home_card_id_seq` sequence**: Created but never used (legacy code). **FIXED**: Documented for removal in Phase 2.1.

3. **Foreign key constraints not idempotent**: Current `postgres-init.sql` uses plain `ALTER TABLE ... ADD CONSTRAINT` which will fail on re-runs. **FIXED**: Clarified requirement for conditional constraint creation in Phase 2.2.

4. **Contradiction on `IF NOT EXISTS` usage**: Phase 2.1 said to remove them, but Phase 4.2 and examples use them. **FIXED**: Updated Phase 2.1 to recommend keeping `IF NOT EXISTS` for idempotency (best practice).

5. **Spring Boot version mismatch**: Document said 3.4.1, but `pom.xml` uses 3.4.4. **FIXED**: Updated to 3.4.4.

6. **Test configuration clarification needed**: Tests use H2, but documentation suggested PostgreSQL. **FIXED**: Added clarification in Phase 5.2 with both options.

7. **CI/CD integration not implemented**: Phase 6.1 describes steps that don't exist in `.github/workflows/ci-cd.yml`. **FIXED**: Updated to note current status and provide implementation guidance.

8. **Transaction limitations not documented**: Some PostgreSQL DDL cannot run in transactions. **FIXED**: Added note in Phase 2.1 and 4.2.

9. **Baseline strategy needs clarification**: Process for existing production databases needs more detail. **FIXED**: Enhanced Phase 8.1 with verification steps and alternatives.

10. **Maven plugin version missing**: Plugin configuration didn't specify version. **FIXED**: Added version in Phase 7.1.

---

## Goals

1. **Versioned Migrations**: Track database schema changes with version numbers
2. **Incremental Updates**: Add new columns/tables without dropping/recreating the database
3. **Reproducible Deployments**: Same migrations run consistently across dev, test, and production
4. **Rollback Support**: Ability to create undo migrations when needed
5. **CI/CD Integration**: Automatic migration execution during deployments
6. **Team Collaboration**: Clear process for adding new schema changes

---

## Phase 1: Flyway Setup & Configuration

### 1.1 Add Flyway Dependency

**File**: `pom.xml`

Add Flyway dependency to the `<dependencies>` section:

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

**Note**: Spring Boot 3.4.4 (current project version) includes Flyway auto-configuration, so no additional configuration class needed.

### 1.2 Configure Flyway Properties

**File**: `src/main/resources/application.properties`

Add Flyway configuration section:

```properties
#----------------------------------------#
# Flyway Database Migration Configuration
#----------------------------------------#
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=1
spring.flyway.baseline-description=Initial baseline
spring.flyway.validate-on-migrate=true
spring.flyway.clean-disabled=true
spring.flyway.out-of-order=false
spring.flyway.placeholders.enabled=false

# Flyway schema history table (default: flyway_schema_history)
spring.flyway.table=flyway_schema_history

# Migration file naming pattern: V{version}__{description}.sql
# Example: V1__initial_schema.sql, V2__add_rating_column.sql
```

**Configuration Explanation**:

- `baseline-on-migrate=true`: Creates baseline for existing databases without Flyway history
- `baseline-version=1`: Sets baseline version to 1 (our initial schema)
- `validate-on-migrate=true`: Validates migration checksums on startup
- `clean-disabled=true`: Prevents accidental database drops in production
- `out-of-order=false`: Requires migrations to run in version order

### 1.3 Create Migration Directory Structure

**Directory**: `src/main/resources/db/migration/`

Create the following structure:

```
src/main/resources/
└── db/
    └── migration/
        ├── V1__initial_schema.sql          # Baseline migration (from postgres-init.sql)
        └── .gitkeep                        # Keep directory in git
```

**Action**: Create directory structure and `.gitkeep` file.

---

## Phase 2: Convert Existing Schema to V1 Migration

### 2.1 Create Baseline Migration

**File**: `src/main/resources/db/migration/V1__initial_schema.sql`

**Action**: Convert `scripts/postgres-init.sql` to Flyway migration format:

1. **Use `IF NOT EXISTS` for idempotency**: While Flyway ensures migrations run only once, using `IF NOT EXISTS` provides additional safety and makes migrations more resilient
2. **Keep `CREATE SEQUENCE IF NOT EXISTS`**: Use `CREATE SEQUENCE IF NOT EXISTS` for safety
3. **Keep `CREATE TABLE IF NOT EXISTS`**: Use `CREATE TABLE IF NOT EXISTS` for safety
4. **Keep `CREATE INDEX IF NOT EXISTS`**: Use `CREATE INDEX IF NOT EXISTS` for safety
5. **Make `ALTER TABLE` constraints idempotent**: Use conditional constraint creation (see Phase 2.2)
6. **Add transaction control**: Wrap in transaction (PostgreSQL supports most DDL in transactions, but see note below)
7. **Add missing `location` table**: The `postgres-init.sql` is missing the `location` table used by `LocationEntity` and `LocationDao`
8. **Remove unused `home_card_id_seq`**: This sequence is created but not used (legacy code)

**Key Changes**:

- Keep `IF NOT EXISTS` clauses for idempotency (recommended best practice)
- Ensure proper ordering (sequences before tables, tables before constraints)
- Add the missing `location` table (id, location_name VARCHAR(255) UNIQUE NOT NULL, created_at TIMESTAMP)
- Remove unused `home_card_id_seq` sequence
- Add comments explaining each section
- Use consistent formatting

**Note on Transactions**: Most PostgreSQL DDL can be wrapped in transactions, but some operations cannot:
- `CREATE INDEX CONCURRENTLY` cannot run in a transaction
- `ALTER TABLE ... SET TABLESPACE` cannot run in a transaction
- If you need these operations, execute them outside the transaction block

### 2.2 Handle Foreign Key Constraints

**Strategy**: Use conditional constraint creation to avoid errors on re-runs. The current `postgres-init.sql` uses plain `ALTER TABLE ... ADD CONSTRAINT` which will fail if constraints already exist.

**Required Pattern**: All foreign key constraints in V1 migration must use this pattern:

```sql
-- Example pattern for idempotent constraints
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_content_image_lens'
    ) THEN
        ALTER TABLE content_image
            ADD CONSTRAINT fk_content_image_lens
            FOREIGN KEY (lens_id) REFERENCES content_lenses(id);
    END IF;
END $$;
```

**Action Required**: Convert all `ALTER TABLE ... ADD CONSTRAINT` statements in `postgres-init.sql` to use this conditional pattern.

**Alternative**: Use Flyway's `outOfOrder` or create separate migration files for constraints (not recommended for baseline migration).

### 2.3 Add Missing Location Table

**Critical**: The `postgres-init.sql` is missing the `location` table that is used by `LocationEntity` and `LocationDao`.

**Required Addition** to V1 migration:

```sql
-- Location table (missing from postgres-init.sql)
CREATE SEQUENCE IF NOT EXISTS location_id_seq;

CREATE TABLE IF NOT EXISTS location (
    id BIGINT PRIMARY KEY DEFAULT nextval('location_id_seq'),
    location_name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_location_name ON location(location_name);

COMMENT ON TABLE location IS 'Geographic locations that can be associated with collections and content images';
```

### 2.4 Test Baseline Migration

**Steps**:

1. Create fresh PostgreSQL database
2. Run application (Flyway will execute V1 migration)
3. Verify all tables, sequences, indexes created (including `location` table)
4. Verify Flyway history table (`flyway_schema_history`) created
5. Check migration checksum recorded
6. Verify `LocationDao` can query the `location` table successfully

**Verification Query**:

```sql
-- Check migration history
SELECT * FROM flyway_schema_history ORDER BY installed_rank;

-- Verify location table exists
SELECT table_name FROM information_schema.tables WHERE table_name = 'location';

-- Verify all expected tables exist
SELECT table_name FROM information_schema.tables 
WHERE table_schema = 'public' 
ORDER BY table_name;
```

---

## Phase 3: Migration Naming Convention & Versioning

### 3.1 Version Numbering Strategy

**Format**: `V{version}__{description}.sql`

**Version Number Rules**:

- Start at `V1` for baseline
- Increment sequentially: `V2`, `V3`, `V4`, etc.
- Use leading zeros for sorting: `V01`, `V02` (optional, not required)
- Never skip versions or reuse version numbers
- Use descriptive descriptions (lowercase, underscores)

**Examples**:

- `V1__initial_schema.sql`
- `V2__add_rating_to_content_image.sql`
- `V3__add_indexes_for_performance.sql`
- `V4__add_created_at_to_collection.sql`

### 3.2 Migration File Template

**Template**:

```sql
-- Migration: V{version}__{description}
-- Description: {Detailed description of what this migration does}
-- Author: {Your name}
-- Date: {YYYY-MM-DD}

BEGIN;

-- Add your migration SQL here
-- Example: ALTER TABLE content_image ADD COLUMN rating INTEGER;

COMMIT;
```

### 3.3 Undo Migrations (Optional)

**Format**: `U{version}__{description}.sql`

**When to Use**:

- Critical schema changes that may need rollback
- Data migrations that can be reversed
- Not required for all migrations (Flyway doesn't auto-apply undo migrations)

**Example**:

- `V5__add_rating_column.sql` (forward migration)
- `U5__remove_rating_column.sql` (undo migration, manual execution)

---

## Phase 4: Migration Workflow & Best Practices

### 4.1 Creating New Migrations

**Process**:

1. **Identify Schema Change**: Determine what needs to change (new column, table, index, etc.)
2. **Determine Version**: Check `flyway_schema_history` for latest version, increment by 1
3. **Create Migration File**: Create `V{next_version}__{description}.sql` in `db/migration/`
4. **Write SQL**: Use PostgreSQL-compatible SQL, wrap in transaction
5. **Test Locally**: Run migration against local dev database
6. **Commit**: Add migration file to git
7. **Deploy**: Migration runs automatically on application startup

**Example Workflow**:

```bash
# 1. Check current version
psql -d edens_zac -c "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;"

# 2. Create new migration file
touch src/main/resources/db/migration/V2__add_rating_to_content_image.sql

# 3. Write migration SQL
# 4. Test locally
mvn spring-boot:run

# 5. Verify migration applied
psql -d edens_zac -c "SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;"
```

### 4.2 Migration Best Practices

**DO**:

- ✅ Always wrap migrations in transactions (`BEGIN; ... COMMIT;`) when possible
- ✅ Make migrations idempotent using `IF NOT EXISTS` patterns (recommended best practice)
- ✅ Test migrations on copy of production data before deploying
- ✅ Keep migrations small and focused (one logical change per migration)
- ✅ Use descriptive names that explain what the migration does
- ✅ Document breaking changes in migration comments
- ✅ Verify data integrity after data migrations
- ✅ Use conditional constraint creation for foreign keys (DO blocks)
- ✅ Note: Some DDL operations (like `CREATE INDEX CONCURRENTLY`) cannot run in transactions

**DON'T**:

- ❌ Never modify existing migration files after they've been applied to production
- ❌ Never delete migration files (they're part of history)
- ❌ Don't include data migrations in schema migrations (create separate migrations)
- ❌ Don't use `DROP TABLE` or `DROP COLUMN` without careful consideration
- ❌ Don't skip version numbers
- ❌ Don't run migrations manually in production (let Flyway handle it)

### 4.3 Handling Data Migrations

**Strategy**: Separate data migrations from schema migrations

**Example**:

- `V10__add_rating_column.sql` - Schema change (adds column)
- `V11__populate_rating_defaults.sql` - Data migration (sets default values)

**Data Migration Template**:

```sql
-- Migration: V{version}__{description}
-- Type: Data Migration
-- Description: {What data is being migrated}

BEGIN;

-- Update existing rows with default values
UPDATE content_image
SET rating = 3
WHERE rating IS NULL;

-- Verify migration
DO $$
DECLARE
    null_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO null_count
    FROM content_image
    WHERE rating IS NULL;

    IF null_count > 0 THEN
        RAISE EXCEPTION 'Data migration failed: % rows still have NULL rating', null_count;
    END IF;
END $$;

COMMIT;
```

---

## Phase 5: Testing Strategy

### 5.1 Local Development Testing

**Process**:

1. Create fresh database: `createdb edens_zac_test`
2. Run application: `mvn spring-boot:run`
3. Verify migrations applied: Check `flyway_schema_history`
4. Test application functionality: Ensure DAOs work with new schema
5. Rollback test: Drop database, re-run to ensure idempotency

### 5.2 Integration Testing

**Note**: Current test configuration uses H2 in-memory database (`src/test/resources/application.properties`). For Flyway testing, you have two options:

**Option 1: Use TestContainers with PostgreSQL (Recommended)**

**File**: `src/test/resources/application-test.properties`

Create test-specific Flyway configuration:

```properties
# Test Flyway Configuration with PostgreSQL
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
spring.flyway.clean-disabled=false  # Allow clean in tests
spring.datasource.url=jdbc:postgresql://localhost:5432/edens_zac_test
spring.datasource.username=zedens
spring.datasource.password=password
```

**Test Database Setup**:

- Use TestContainers with PostgreSQL
- Run all migrations before tests
- Verify schema matches expected state

**Option 2: Keep H2 for Unit Tests**

If keeping H2 for unit tests, note that:
- H2 has different SQL syntax than PostgreSQL
- Some PostgreSQL-specific features may not work
- Consider using TestContainers for integration tests that require PostgreSQL-specific features

### 5.3 Migration Validation Testing

**Create Test**: `src/test/java/.../FlywayMigrationTest.java`

```java
@SpringBootTest
class FlywayMigrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void testAllMigrationsApplied() throws SQLException {
        // Verify flyway_schema_history exists
        // Verify all expected migrations are present
        // Verify no failed migrations
    }

    @Test
    void testSchemaMatchesExpected() throws SQLException {
        // Verify all tables exist
        // Verify all columns exist
        // Verify all indexes exist
    }
}
```

---

## Phase 6: CI/CD Integration

### 6.1 GitHub Actions Integration

**File**: `.github/workflows/ci-cd.yml`

**Status**: Currently not implemented. Add migration validation steps to the existing CI pipeline.

**Add Migration Check Step** (before tests, in the `test` job):

```yaml
- name: Validate Flyway Migrations
  run: |
    mvn flyway:info
    mvn flyway:validate

- name: Test Migrations Against PostgreSQL
  env:
    SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/edens_zac_test
    SPRING_DATASOURCE_USERNAME: zedens
    SPRING_DATASOURCE_PASSWORD: password
  run: |
    # Migrations will run automatically when Spring Boot starts
    # This step validates migrations can be applied successfully
    mvn flyway:info -Dflyway.url=${{ env.SPRING_DATASOURCE_URL }} -Dflyway.user=${{ env.SPRING_DATASOURCE_USERNAME }} -Dflyway.password=${{ env.SPRING_DATASOURCE_PASSWORD }}
```

**Note**: The existing CI pipeline already has a PostgreSQL service container, so migrations can be tested there. The `mvn test` step will automatically run Flyway migrations when Spring Boot starts.

### 6.2 Production Deployment

**Strategy**: Migrations run automatically on application startup

**Process**:

1. Application starts
2. Flyway checks `flyway_schema_history` table
3. Compares applied migrations with files in `db/migration/`
4. Applies any pending migrations in version order
5. Application continues startup only if migrations succeed

**Safety Measures**:

- Always backup database before deployment
- Monitor migration execution in logs
- Have rollback plan ready
- Test migrations on staging first

### 6.3 Deployment Script Updates

**File**: `scripts/deploy.sh`

**Add Migration Verification**:

```bash
# Before starting application
echo "Checking database migrations..."
mvn flyway:info

# Start application (migrations run automatically)
echo "Starting application (migrations will run automatically)..."
docker-compose up -d backend

# Verify migrations completed
sleep 10
mvn flyway:info
```

---

## Phase 7: Migration Management Commands

### 7.1 Maven Flyway Plugin

**File**: `pom.xml`

Add Flyway Maven plugin to `<build><plugins>`:

```xml
<plugin>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-maven-plugin</artifactId>
    <version>10.8.1</version>
    <configuration>
        <url>jdbc:postgresql://${POSTGRES_HOST:-localhost}:${POSTGRES_PORT:-5432}/${POSTGRES_DB:-edens_zac}</url>
        <user>${POSTGRES_USER:-zedens}</user>
        <password>${POSTGRES_PASSWORD:-password}</password>
        <locations>
            <location>classpath:db/migration</location>
        </locations>
        <baselineOnMigrate>true</baselineOnMigrate>
        <baselineVersion>1</baselineVersion>
        <baselineDescription>Initial baseline</baselineDescription>
    </configuration>
</plugin>
```

**Note**: The plugin configuration uses environment variables with defaults. For consistency with Spring Boot properties, you can also use Maven properties that match your `application.properties` patterns.

### 7.2 Useful Flyway Commands

**Check Migration Status**:

```bash
mvn flyway:info
```

**Validate Migrations**:

```bash
mvn flyway:validate
```

**Repair Migration Checksums**:

```bash
mvn flyway:repair
```

**Baseline Existing Database**:

```bash
mvn flyway:baseline -Dflyway.baselineVersion=1 -Dflyway.baselineDescription="Initial baseline"
```

**Clean Database** (dev only):

```bash
mvn flyway:clean
```

---

## Phase 8: Migration from Current System

### 8.1 Baseline Existing Production Database

**Scenario**: Database already exists with schema from `postgres-init.sql`

**Important Considerations**:

1. **Verify Schema Completeness**: Before baselining, ensure the existing database schema matches what will be in V1 migration (including the missing `location` table)
2. **Handle Missing Tables**: If `location` table is missing, you have two options:
   - Option A: Add `location` table manually before baselining, then baseline
   - Option B: Baseline first, then create V2 migration to add `location` table
3. **Verify Foreign Keys**: Ensure all foreign key constraints exist and match the V1 migration

**Process**:

1. **Backup Database**: Create full backup before baseline
2. **Verify Schema**: Compare existing schema with V1 migration requirements
3. **Add Missing Components**: If `location` table is missing, add it manually or plan for V2 migration
4. **Baseline Flyway**: Mark current schema as version 1
5. **Verify**: Check `flyway_schema_history` table created
6. **Test**: Create V2 migration and verify it applies correctly

**Commands**:

```bash
# 1. Backup
pg_dump -h $POSTGRES_HOST -U $POSTGRES_USER -d edens_zac > backup_before_flyway_$(date +%Y%m%d_%H%M%S).sql

# 2. Verify location table exists (if not, add it manually or plan V2 migration)
psql -h $POSTGRES_HOST -U $POSTGRES_USER -d edens_zac -c "\d location"

# 3. Baseline (run once) - only if database schema matches V1 migration
mvn flyway:baseline -Dflyway.baselineVersion=1 -Dflyway.baselineDescription="Initial baseline from postgres-init.sql"

# 4. Verify
psql -h $POSTGRES_HOST -U $POSTGRES_USER -d edens_zac -c "SELECT * FROM flyway_schema_history;"
```

**Alternative**: If using `baseline-on-migrate=true` (recommended), Flyway will automatically baseline on first run, but you should still verify the schema matches V1 first.

### 8.2 Deprecate Old Initialization Script

**File**: `scripts/postgres-init.sql`

**Action**:

1. Add deprecation notice at top of file
2. Keep file for reference/documentation
3. Update documentation to reference Flyway migrations
4. Remove from EC2 deployment scripts (if used)

**Deprecation Notice**:

```sql
-- DEPRECATED: This file is no longer used for database initialization
-- Database schema is now managed by Flyway migrations in src/main/resources/db/migration/
-- See flyway_migration.md for migration workflow
-- This file is kept for reference only
```

---

## Phase 9: Example Migrations

### 9.1 Adding a New Column

**File**: `src/main/resources/db/migration/V2__add_rating_to_content_image.sql`

```sql
-- Migration: V2__add_rating_to_content_image
-- Description: Add rating column to content_image table
-- Author: Development Team
-- Date: 2026-01-27

BEGIN;

-- Add rating column (nullable initially, can be made NOT NULL in later migration)
ALTER TABLE content_image
ADD COLUMN IF NOT EXISTS rating INTEGER CHECK (rating >= 1 AND rating <= 5);

-- Add comment
COMMENT ON COLUMN content_image.rating IS 'Image rating from 1-5 stars';

-- Create index for filtering by rating
CREATE INDEX IF NOT EXISTS idx_content_image_rating ON content_image(rating);

COMMIT;
```

### 9.2 Adding a New Table

**File**: `src/main/resources/db/migration/V3__add_content_analytics_table.sql`

```sql
-- Migration: V3__add_content_analytics_table
-- Description: Create analytics tracking table for content views
-- Author: Development Team
-- Date: 2026-01-27

BEGIN;

-- Create sequence
CREATE SEQUENCE IF NOT EXISTS content_analytics_id_seq;

-- Create table
CREATE TABLE IF NOT EXISTS content_analytics (
    id BIGINT PRIMARY KEY DEFAULT nextval('content_analytics_id_seq'),
    content_id BIGINT NOT NULL,
    view_count INTEGER NOT NULL DEFAULT 0,
    last_viewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Add foreign key
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_content_analytics_content'
    ) THEN
        ALTER TABLE content_analytics
            ADD CONSTRAINT fk_content_analytics_content
            FOREIGN KEY (content_id) REFERENCES content(id) ON DELETE CASCADE;
    END IF;
END $$;

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_content_analytics_content_id ON content_analytics(content_id);
CREATE INDEX IF NOT EXISTS idx_content_analytics_view_count ON content_analytics(view_count);

COMMIT;
```

### 9.3 Modifying Existing Column

**File**: `src/main/resources/db/migration/V4__make_rating_not_null.sql`

```sql
-- Migration: V4__make_rating_not_null
-- Description: Make rating column NOT NULL after populating defaults
-- Prerequisites: V2 must have been applied, and V11 (data migration) must have populated defaults
-- Author: Development Team
-- Date: 2026-01-27

BEGIN;

-- First, ensure no NULL values exist (safety check)
DO $$
DECLARE
    null_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO null_count
    FROM content_image
    WHERE rating IS NULL;

    IF null_count > 0 THEN
        RAISE EXCEPTION 'Cannot make rating NOT NULL: % rows have NULL rating. Run data migration first.', null_count;
    END IF;
END $$;

-- Now make column NOT NULL
ALTER TABLE content_image
ALTER COLUMN rating SET NOT NULL;

COMMIT;
```

---

## Phase 10: Monitoring & Troubleshooting

### 10.1 Migration Logging

**Configuration**: Already enabled via Spring Boot logging

**Log Levels**:

- `DEBUG`: Detailed migration execution
- `INFO`: Migration summary (applied, pending, etc.)
- `WARN`: Migration warnings (checksum mismatches, etc.)
- `ERROR`: Migration failures

**Monitor Logs**:

```bash
# Application startup logs
tail -f logs/application.log | grep -i flyway

# Docker logs
docker logs -f portfolio-backend | grep -i flyway
```

### 10.2 Common Issues & Solutions

**Issue**: Checksum Mismatch

- **Cause**: Migration file modified after being applied
- **Solution**: Use `mvn flyway:repair` to update checksums (only if change was intentional)

**Issue**: Migration Failed

- **Cause**: SQL syntax error, constraint violation, etc.
- **Solution**: Fix migration SQL, mark failed migration as resolved in `flyway_schema_history`

**Issue**: Out of Order Migration

- **Cause**: Migration version number conflicts
- **Solution**: Rename migration file to correct version number

**Issue**: Baseline Required

- **Cause**: Database exists but no Flyway history
- **Solution**: Run `mvn flyway:baseline` with appropriate version

### 10.3 Emergency Rollback Procedure

**Scenario**: Migration causes production issue

**Process**:

1. **Stop Application**: Prevent further damage
2. **Assess Impact**: Determine what migration caused issue
3. **Manual Rollback**: Execute undo migration or manual SQL to reverse changes
4. **Mark Migration**: Update `flyway_schema_history` to mark migration as resolved
5. **Fix Migration**: Create corrected migration (V{n+1}) with proper fix
6. **Restart Application**: Verify fix works

**Manual Rollback Example**:

```sql
-- If V5 caused issues, manually reverse it
BEGIN;
ALTER TABLE content_image DROP COLUMN IF EXISTS problematic_column;
COMMIT;

-- Mark V5 as resolved in flyway_schema_history
UPDATE flyway_schema_history
SET success = true
WHERE version = '5';
```

---

## Phase 11: Documentation Updates

### 11.1 Update README.md

**Add Section**: "Database Migrations"

```markdown
## Database Migrations

This project uses [Flyway](https://flywaydb.org/) for database schema management.

### Creating a New Migration

1. Check current version: `mvn flyway:info`
2. Create new migration file: `src/main/resources/db/migration/V{n}__{description}.sql`
3. Write migration SQL (wrap in `BEGIN; ... COMMIT;`)
4. Test locally: `mvn spring-boot:run`
5. Commit and push

### Migration Commands

- `mvn flyway:info` - Show migration status
- `mvn flyway:validate` - Validate migrations
- `mvn flyway:repair` - Repair checksums

See `flyway_migration.md` for complete documentation.
```

### 11.2 Update Deployment Documentation

**File**: `ai_docs/ai_ec2.md` or deployment docs

**Add Note**: Migrations run automatically on application startup. No manual SQL execution needed.

---

## Phase 12: Implementation Checklist

### Setup Phase

- [ ] Add Flyway dependencies to `pom.xml`
- [ ] Configure Flyway in `application.properties`
- [ ] Create `src/main/resources/db/migration/` directory
- [ ] Add Flyway Maven plugin to `pom.xml`

### Migration Creation

- [ ] Convert `postgres-init.sql` to `V1__initial_schema.sql`
- [ ] Keep `IF NOT EXISTS` clauses for idempotency
- [ ] Convert all foreign key constraints to use conditional creation (DO blocks)
- [ ] Add missing `location` table to V1 migration
- [ ] Remove unused `home_card_id_seq` sequence
- [ ] Add transaction wrappers (where applicable)
- [ ] Test V1 migration on fresh database
- [ ] Verify `LocationDao` works with migrated schema

### Baseline Existing Database

- [ ] Backup production database
- [ ] Run `flyway:baseline` on production
- [ ] Verify `flyway_schema_history` table created
- [ ] Test V2 migration applies correctly

### Testing

- [ ] Create Flyway migration test
- [ ] Test migrations in CI/CD pipeline
- [ ] Verify migrations work with TestContainers

### Documentation

- [ ] Update README.md with migration workflow
- [ ] Update deployment documentation
- [ ] Add deprecation notice to `postgres-init.sql`

### CI/CD Integration

- [ ] Add Flyway validation to GitHub Actions
- [ ] Update deployment scripts
- [ ] Test automatic migration execution

---

## Success Criteria

✅ **Migrations run automatically** on application startup  
✅ **New schema changes** can be added via migration files  
✅ **No data loss** when applying migrations  
✅ **Reproducible** across all environments  
✅ **Versioned history** of all schema changes  
✅ **Team can create migrations** following documented process  
✅ **CI/CD validates** migrations before deployment

---

## Next Steps After Implementation

1. **Create V2 Migration**: Add any pending schema changes as V2
2. **Team Training**: Share migration workflow with team
3. **Monitor First Deployments**: Watch migration execution in production
4. **Iterate**: Refine process based on experience

---

## Phase 13: Collection Location Schema Update (V2)

### 13.1 Migration Overview

**Migration File**: `V2__schema_updates.sql`

**Purpose**: V2 schema updates to add `location_id` foreign key column to `collection` table, enabling collections to reference the `location` table instead of storing location as a hardcoded VARCHAR string.

**Note**: This is a consolidated V2 migration file that includes all V2 schema changes. Additional V2 changes should be added to this file rather than creating separate V2 migration files.

**Context**: 
- Database is clean with no existing data
- This migration is schema-only - no data migration is performed
- The `location` table may already exist in production (from V1 or manual creation)
- `CollectionEntity` supports both `location` (legacy VARCHAR) and `locationId` (FK) fields
- Code has been updated to use `locationId` going forward

### 13.2 Migration Steps

**Schema Changes Only** (no data migration):

1. **Ensure location table exists**: Create `location` table if it doesn't already exist (idempotent)
2. **Add location_id column**: Add `location_id BIGINT` column to `collection` table (if it doesn't exist)
3. **Add foreign key constraint**: Create FK constraint from `collection.location_id` to `location.id`

**Code Updates** (already completed):
- `CollectionProcessingUtil` uses `LocationDao` and converts `locationId` to `LocationModel`
- `CollectionUpdateRequest` uses `locationId` (Long) instead of `location` (String)
- `CollectionBaseModel` uses `LocationModel` instead of `location` string
- `CollectionDao` continues reading both fields for backward compatibility

### 13.3 Migration SQL

The migration performs three schema steps:

1. **Create location table**: Ensures `location` table exists with sequence, table, index, and comment
2. **Add location_id column**: Adds nullable `location_id BIGINT` column to `collection` table
3. **Add foreign key constraint**: Creates FK constraint allowing NULL values (for collections without locations)

**Note**: No data migration is included - this assumes a clean database with no existing collection records.

### 13.4 Code Changes

**Files Modified**:
- `src/main/resources/db/migration/V2__schema_updates.sql` (new)
- `src/main/java/edens/zac/portfolio/backend/services/CollectionProcessingUtil.java`
- `src/main/java/edens/zac/portfolio/backend/model/CollectionUpdateRequest.java`
- `src/main/java/edens/zac/portfolio/backend/model/CollectionBaseModel.java`

**Key Changes**:
- `CollectionBaseModel.location`: Changed from `String` to `LocationModel`
- `CollectionUpdateRequest.location`: Changed from `String` to `Long locationId`
- `CollectionProcessingUtil`: Added `LocationDao` dependency, converts `locationId` to `LocationModel` when reading, uses `LocationDao.findOrCreate()` when updating

### 13.5 Future Cleanup (V3)

After migration is stable and all collections use `location_id`, create a follow-up migration to:
- Remove `location` VARCHAR column from `collection` table
- Update `CollectionEntity` to remove `location` field
- Update `CollectionDao` to stop reading/writing `location` string
- Update models to remove any remaining `location` string references

### 13.6 Testing

- Verify `location` table is created with correct schema
- Verify `location_id` column is added to `collection` table
- Verify foreign key constraint is created correctly
- Test collection creation with `locationId`
- Test collection update with `locationId`
- Test collection retrieval (location should come from location table via `location_id`)
- Verify `location_id` can be NULL (for collections without locations)

---

## References

- [Flyway Documentation](https://flywaydb.org/documentation/)
- [Spring Boot Flyway Integration](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-initialization.migration-tool.flyway)
- [Flyway Best Practices](https://flywaydb.org/documentation/learnmore/best-practices)
