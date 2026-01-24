# CI/CD Pipeline Documentation (AI Reference)

## Overview

GitHub Actions pipeline for automated code quality, testing, building, and security scanning. Deployment is manual via SSH to EC2.

**Workflow File**: `.github/workflows/ci-cd.yml`

## Pipeline Jobs

### 1. Lint Job (`lint`)
- **Runs on**: All pushes and PRs
- **Tools**: Checkstyle (enabled), SpotBugs (disabled - Java 23 compatibility issue)
- **Failure behavior**: Blocks pipeline
- **Artifacts**: checkstyle-results, spotbugs-results (14 days retention)

**Commands**:
```bash
mvn checkstyle:check
# mvn spotbugs:check  # Currently disabled
```

### 2. Test Job (`test`)
- **Runs on**: After lint passes
- **Dependencies**: PostgreSQL 16 service container
- **Test database**: `edens_zac_test` (user: zedens, pass: password)
- **Failure behavior**: Blocks pipeline
- **Artifacts**: surefire-test-results (14 days retention)

**Commands**:
```bash
mvn test -B
```

**Environment**:
- `SPRING_DATASOURCE_URL`: jdbc:postgresql://localhost:5432/edens_zac_test
- `SPRING_PROFILES_ACTIVE`: test

### 3. Build Job (`build`)
- **Runs on**: After test passes
- **Outputs**: JAR file, Docker image
- **Image tags**: `edens.zac.backend:{sha}`, `edens.zac.backend:latest`
- **Artifacts**: backend-docker-image (7 days retention)

**Commands**:
```bash
mvn clean package -DskipTests -B
docker build -t edens.zac.backend:${{ github.sha }} .
```

### 4. Security Scan Job (`security-scan`)
- **Runs on**: After lint passes (parallel with test)
- **Tool**: OWASP Dependency Check
- **Failure behavior**: Non-blocking (`continue-on-error: true`)
- **Threshold**: CVSS ≥ 7.0 (HIGH/CRITICAL)
- **Artifacts**: owasp-dependency-check-report (14 days retention)

**Configuration** (lines 192-209):
```yaml
- DdataDirectory=$HOME/.owasp/dependency-check-data
- DautoUpdate=true
- DcveValidForHours=720  # 30 days
- DfailBuildOnCVSS=7
- DsuppressionFile=dependency-check-suppressions.xml
- Dformats=HTML,JSON
```

## OWASP Dependency Check Caching

### Cache Configuration
**Location**: `~/.owasp/dependency-check-data`
**Key**: `Linux-owasp-data-v1`
**Strategy**: Stable key, reused across all runs
**File**: `.github/workflows/ci-cd.yml:192-198`

### Cache Behavior
- CVE database valid for 720 hours (30 days)
- Only attempts updates when data expires
- Falls back to any previous cache if current key missing
- Non-blocking failures allow pipeline to pass during cache population

### Cache Invalidation

**When to invalidate**:
- Corrupted H2 database (null connection pool errors)
- Major OWASP tool version upgrade
- Want to force fresh CVE database download

**How to invalidate**:
1. Edit `.github/workflows/ci-cd.yml` line 196
2. Change `key: ${{ runner.os }}-owasp-data-v1` to `v2` (increment version)
3. Commit and push
4. Delete old cache: Actions → Caches → Delete `Linux-owasp-data-v1`
5. First run may fail with 429 rate limit (expected)
6. Re-run workflow after 30 minutes

### Common Issues

**429 Too Many Requests**
- Cause: NVD API rate limiting
- Expected on: First run, after cache invalidation, when cache expired
- Solution: Wait 30+ minutes, re-run workflow
- Prevention: Add NVD API key as GitHub secret `NVD_API_KEY`

**Database connection is null**
- Cause: Corrupted H2 database from interrupted download
- Solution: Invalidate cache (bump version), delete old cache, re-run

**Scan takes 15+ minutes**
- Cause: Cache miss, downloading full CVE database (~500MB)
- Expected on: First run, after invalidation
- Normal runtime: 2-3 minutes with valid cache

**Build fails on vulnerabilities**
- Cause: Dependencies have CVE with CVSS ≥ 7.0
- Solutions:
  1. Update vulnerable dependency in `pom.xml`
  2. Add suppression to `dependency-check-suppressions.xml`:
     ```xml
     <suppress>
       <notes>Reason for suppression</notes>
       <cve>CVE-2024-XXXXX</cve>
     </suppress>
     ```

## Deployment

**Method**: Manual SSH deployment to EC2
**No GitHub secrets required** - No automatic deployment configured

**Manual deployment steps**:
```bash
ssh -i ~/path/to/key.pem ubuntu@<ec2-ip>
cd ~/edens.zac.backend
git pull origin main
bash scripts/deploy.sh
```

## Branch Protection

**Protected branch**: `main`
**Required status checks**:
- Code Linting & Style Check
- Run Unit & Integration Tests
- Build Application & Docker Image
- Security Vulnerability Scan

**Rules**:
- Require PR before merging
- Require all status checks to pass
- Direct commits to main blocked

## Caching Strategy

### Maven Dependencies
**Path**: `~/.m2/repository`
**Key**: `Linux-maven-{hash(pom.xml)}`
**Invalidation**: Automatic when pom.xml changes

### OWASP CVE Database
**Path**: `~/.owasp/dependency-check-data`
**Key**: `Linux-owasp-data-v1` (manual version bump)
**Size**: ~500MB
**Update frequency**: Every 30 days

## Local Testing Commands

```bash
# Format code (Google Java Style Guide)
mvn spotless:apply

# Linting
mvn checkstyle:check

# Tests
mvn test

# Full build
mvn clean package

# Docker build
docker build -t edens.zac.backend:test .

# Security scan (local)
mvn org.owasp:dependency-check-maven:check
open target/dependency-check-report.html
```

## File Locations

| File | Purpose |
|------|---------|
| `.github/workflows/ci-cd.yml` | Main workflow configuration |
| `pom.xml` | Maven dependencies and plugin config |
| `dependency-check-suppressions.xml` | CVE suppressions for false positives |
| `checkstyle.xml` | Checkstyle rules (Google Java Style Guide) |
| `checkstyle-suppressions.xml` | Checkstyle rule suppressions |
| `spotbugs-exclude.xml` | SpotBugs exclusions (currently unused) |
| `Dockerfile` | Container build instructions |

## Troubleshooting Workflow

1. **Lint failure**: Run `mvn checkstyle:check` locally, fix violations
2. **Test failure**: Run `mvn test`, check `target/surefire-reports/`
3. **Build failure**: Run `mvn clean package -DskipTests`
4. **Security scan failure**: Download report artifact, update dependencies or add suppressions
5. **Cache issues**: Check Actions → Caches, verify cache exists and size is ~500MB

## Adding NVD API Key (Optional)

Eliminates rate limiting for OWASP scans.

1. Get key: https://nvd.nist.gov/developers/request-an-api-key
2. Add GitHub secret: `NVD_API_KEY`
3. Update workflow line 200-209:
   ```yaml
   - name: Run OWASP Dependency Check
     env:
       NVD_API_KEY: ${{ secrets.NVD_API_KEY }}
     run: |
       mvn org.owasp:dependency-check-maven:check \
         -DnvdApiKey=$NVD_API_KEY \
         ...
   ```

## Pipeline Execution Flow

```
Push/PR → lint (checkstyle) → test (postgres) → build (docker) ✓
                ↓
         security-scan (owasp, non-blocking) ✓

Merge to main → Same flow → Manual SSH deployment to EC2
```

## Notes for AI Agents

- **Code style**: Google Java Style Guide enforced via `mvn spotless:apply`
- **SpotBugs is disabled**: Java 23 compatibility issue (lines 37-41 in ci-cd.yml)
- **Security scan is non-blocking**: Allows pipeline to pass during rate limits
- **No automatic deployment**: Manual SSH required, no secrets in GitHub
- **Cache keys are stable**: Don't use `github.run_number` or timestamps in cache keys
- **CVE data is 30-day old**: Acceptable trade-off for rate limit avoidance
- **Known test issues**: If Mockito stubbing tests fail after formatting, check:
  - `ContentControllerDevTest.java`
  - `CollectionControllerDevTest.java`
  - `ContentProcessingUtilTest.java`
