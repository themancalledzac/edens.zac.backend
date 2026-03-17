# Backend Refactoring Guide - 2026

Modernization roadmap for `edens.zac.portfolio.backend` (Spring Boot 3.4.1 / Java 23).

---

## Current State (as of 2026-03-15)

| Metric | Current | Target | Notes |
|--------|---------|--------|-------|
| Model/DTO files | 12 | 10-12 | Target met |
| Service line counts | ~930 / ~920 | <300 each | CollectionService / ContentService |
| DAOs | 7 | 5-6 | 6 repos + BaseDao (target met) |
| Dependencies per service | 8-9 | 2-4 | CollectionService=8, ContentService=9 |
| Tests | 344 | -- | +11 this session (dedupe + date parsing) |

---

## Completed Work (Phases 1-9, 7b)

All phase-specific files have been archived. Concise summary:

| Category | Phases | What Was Done |
|----------|--------|---------------|
| Quick Wins | 1 | GlobalExceptionHandler, @Valid, deleted stale request class |
| Records & Models | 2, 2a-c | Records.java, CollectionRequests, ContentRequests, sealed ContentModel, flat CollectionModel |
| Service Cleanup | 3a-c | Removed interface/impl split, extracted MetadataService (14->9 deps), fixed pool starvation (2-phase upload) |
| DAO Consolidation | 4 | 14 DAOs -> 6 repositories + BaseDao |
| Bug Fixes | 7a, QF | XMP multi-namespace fallback, EXIF/XMP date chain, inline FQN fix |
| Error Handling | 8a-c | ResourceNotFoundException, ImageUploadResult structured response, silent failure fixes |
| Test Coverage | 9a-e | GlobalExceptionHandler, ImageMetadata/XMP, request validation, date parsing, CollectionService |
| Smart Dedupe | 7b | Replaced file_identifier with (original_filename, capture_date) identity; Flyway V4; DedupeResult with CREATE/UPDATE/SKIP; lastExportDate freshness comparison |

**Phase 7b review cleanup (this session):**
- Fixed Flyway V4 migration: EXIF colon-format dates, filename extraction regex
- Fixed dedupe null-guard inversion (null lastExportDate -> SKIP not UPDATE)
- Fixed S3/DB atomicity (DB save before S3 delete, capture old URLs before overwrite)
- Fixed isFilm heuristic (use metadata instead of fStop == null)
- Fixed orderIndex skip (increment in SKIP branch)
- Removed ~300 lines dead code: `applyImageUpdates()`, `processImageContent()` duplication, `createImages()` sequential, `getAllImages()` non-paginated, `unproxyContentEntity()`, `savePreparedImage()` wrapper, 4 dead location helpers
- Simplified `loadContentTags()` (removed unnecessary entity copy)
- Added 11 tests (6 date parsing, 5 dedupe logic)

**Build: 344 tests, 0 failures.**

---

## Remaining Work

### Tier 5: Features

| Phase | File | Summary | Effort |
|-------|------|---------|--------|
| 5 | [phase-5-gif-mp4-upload.md](phase-5-gif-mp4-upload.md) | MP4-as-GIF upload support (endpoint, validator, S3 path) | 1 day |

### Tier 6: Future

| Phase | File | Summary | Effort |
|-------|------|---------|--------|
| 6 | [phase-6-future.md](phase-6-future.md) | Home page, image search, individual content management, perf optimizations | TBD |

### Minor Items
- Pool size revert (20 -> 10) pending manual test with batch upload (from Phase 3c)
- `@Valid` missing on `passwordRequest` parameter in `CollectionControllerProd`
- ContentRepository has ~9 unused query methods (identified in 7b review; keep if needed for Phase 6, else remove)
- Wildcard imports in ContentProcessingUtil and ContentService (style cleanup)

---

## Next Steps: Deploying Phase 7b (Flyway V4 Migration)

Phase 7b includes `V4__image_dedupe_fields.sql` which modifies the `content_image` table (adds columns, drops columns, creates index). This requires careful deployment.

### Pre-Deployment Checklist

1. **Backup the database** (CRITICAL -- this migration drops columns)
   ```bash
   ssh -i ~/key.pem ec2-user@<ec2-ip>
   docker exec portfolio-postgres pg_dump -U zedens edens_zac | gzip > ~/portfolio-backend/backups/pre_v4_$(date +%Y%m%d_%H%M%S).sql.gz
   ```

2. **Verify the backup is valid**
   ```bash
   ls -la ~/portfolio-backend/backups/pre_v4_*.sql.gz
   # Should be non-zero size (typically 50KB-5MB depending on data)
   ```

3. **Optional: Copy backup to S3 for extra safety**
   ```bash
   aws s3 cp ~/portfolio-backend/backups/pre_v4_*.sql.gz s3://<bucket>/db-backups/
   ```

### Deployment Steps

4. **Verify database is running** (DB is managed separately in ~/portfolio-db/)
   ```bash
   docker exec portfolio-postgres pg_isready -U zedens
   ```
   If not running: `cd ~/portfolio-db && docker compose up -d`

5. **Deploy the new code** (Flyway runs automatically on startup)
   ```bash
   cd ~/portfolio-backend/repo
   git fetch && git reset --hard origin/main
   cp ~/portfolio-backend/.env ~/portfolio-backend/repo/.env
   docker compose build
   docker compose down
   docker compose up -d
   ```
   Or simply: `bash ~/portfolio-backend/repo/deploy.sh`

6. **Watch the startup logs for Flyway output**
   ```bash
   docker compose logs -f backend 2>&1 | grep -i flyway
   ```
   Expected output:
   ```
   Flyway Community Edition ...
   Migrating schema "public" to version "4 - image dedupe fields"
   Successfully applied 1 migration
   ```

7. **Verify the migration succeeded**
   ```bash
   docker exec portfolio-postgres psql -U zedens edens_zac -c "\d content_image"
   ```
   Verify:
   - `capture_date` column exists (type: date)
   - `last_export_date` column exists (type: timestamp)
   - `original_filename` column exists (type: varchar(512))
   - `create_date` column does NOT exist
   - `file_identifier` column does NOT exist

8. **Verify the dedupe index exists**
   ```bash
   docker exec portfolio-postgres psql -U zedens edens_zac -c "\di idx_content_image_dedupe"
   ```

9. **Verify data migration**
   ```bash
   # Check that capture_date was populated from old create_date
   docker exec portfolio-postgres psql -U zedens edens_zac -c "SELECT COUNT(*) AS total, COUNT(capture_date) AS has_date, COUNT(original_filename) AS has_filename FROM content_image;"
   ```
   `has_filename` should equal `total`. `has_date` may be less than `total` (film scans without EXIF dates).

### Post-Deployment Verification

10. **Test the upload endpoint** -- upload a new image and verify it creates successfully
11. **Test dedupe** -- re-upload the same image and verify it returns SKIP
12. **Test the read endpoints** -- verify existing images render correctly (captureDate instead of createDate in JSON)

### Rollback Plan

If something goes wrong:
```bash
# Stop the app
docker compose down

# Restore the pre-migration backup
bash ~/portfolio-backend/repo/scripts/restore-postgres.sh ~/portfolio-backend/backups/pre_v4_<timestamp>.sql.gz

# Revert code to previous version
cd ~/portfolio-backend/repo
git checkout <previous-commit-hash>

# Redeploy old version
docker compose build
docker compose up -d
```

**Important:** After rollback, the `flyway_schema_history` table will still show V4 as applied but the schema will be reverted. You must manually fix:
```sql
DELETE FROM flyway_schema_history WHERE version = '4';
```

### Frontend Impact

The JSON response field `createDate` (String) has been replaced with `captureDate` (ISO date format: "2025-01-15"). If the frontend references `createDate`, it will need updating before or alongside this deployment.

---

## Architecture Notes

**What's working well:**
- JDBC over JPA (performance control, no N+1)
- Dev/Prod controller separation
- Constructor injection with `@RequiredArgsConstructor`
- Batch loading patterns
- Google Java Format via Spotless
- Smart dedupe with (original_filename, capture_date) identity
- Separate DB and app Docker Compose stacks (DB survives backend deploys)
- Terraform for infrastructure-as-code (new)

**Key problems remaining:**
- Service bloat (900+ line services with 8-9 dependencies)
- No GIF/MP4 upload path -- **Phase 5**

**Recommendation: stay with Java.** Modernize with records, sealed interfaces, and focused services.
