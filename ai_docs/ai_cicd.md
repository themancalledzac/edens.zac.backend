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
- **Outputs**: JAR file, Docker image (build validation only, not saved as artifact)
- **Image tags**: `edens.zac.backend:{sha}`, `edens.zac.backend:latest`
- **Artifacts**: None — image is rebuilt on EC2 during deploy

**Commands**:
```bash
mvn clean package -DskipTests -B
docker build -t edens.zac.backend:${{ github.sha }} .
```

## Deployment

**Method**: Manual SSH deployment to EC2
**No GitHub secrets required** - No automatic deployment configured

**Manual deployment steps**:
```bash
ssh -i ~/path/to/key.pem ec2-user@<ec2-ip>
bash ~/portfolio-backend/repo/deploy.sh
```

## Branch Protection

**Not currently configured.** `main` has no branch protection rule and no ruleset -- verified
2026-09-09 via `gh api repos/:owner/:repo/branches/main/protection` (404 Branch not protected).
The jobs below run on every PR but none of them gates a merge.

**Jobs that run**:
- Code Linting & Style Check
- Run Unit & Integration Tests
- Build Application & Docker Image

## Caching Strategy

### Maven Dependencies
**Path**: `~/.m2/repository`
**Key**: `Linux-maven-{hash(pom.xml)}`
**Invalidation**: Automatic when pom.xml changes

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

```

## File Locations

| File | Purpose |
|------|---------|
| `.github/workflows/ci-cd.yml` | Main workflow configuration |
| `pom.xml` | Maven dependencies and plugin config |
| `checkstyle.xml` | Checkstyle rules (Google Java Style Guide) |
| `checkstyle-suppressions.xml` | Checkstyle rule suppressions |
| `spotbugs-exclude.xml` | SpotBugs exclusions (currently unused) |
| `Dockerfile` | Container build instructions |

## Troubleshooting Workflow

1. **Lint failure**: Run `mvn checkstyle:check` locally, fix violations
2. **Test failure**: Run `mvn test`, check `target/surefire-reports/`
3. **Build failure**: Run `mvn clean package -DskipTests`
4. **Cache issues**: Check Actions → Caches, verify the Maven cache exists

## Pipeline Execution Flow

```
Push/PR → lint (checkstyle) → test (postgres) → build (docker, validation only) ✓

Merge to main → Same flow → Manual SSH deploy (bash ~/portfolio-backend/repo/deploy.sh)

Note: CI builds the Docker image as validation but does NOT save/push it.
EC2 deploy.sh rebuilds on the instance with Docker layer caching.
```

## Future: Automated Deployment

To add auto-deploy on merge to main, add this job to `.github/workflows/ci-cd.yml`:

```yaml
deploy:
  name: Deploy to EC2
  runs-on: ubuntu-latest
  needs: build
  if: github.ref == 'refs/heads/main' && github.event_name == 'push'
  steps:
    - name: Deploy via SSH
      uses: appleboy/ssh-action@v1
      with:
        host: ${{ secrets.EC2_HOST }}
        username: ${{ secrets.EC2_USER }}
        key: ${{ secrets.EC2_SSH_KEY }}
        script: bash ~/portfolio-backend/repo/deploy.sh
```

**Required GitHub secrets**: `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`

## Future: Container Registry

Instead of rebuilding on EC2, push to a registry and pull:
- **ECR** (AWS): 500MB free tier, integrates with IAM
- **GHCR** (GitHub): Free for public repos
- Benefit: Faster deploys (pull vs. build), less EC2 CPU pressure

## Notes for AI Agents

- **Code style**: Google Java Style Guide enforced via `mvn spotless:apply`
- **SpotBugs is disabled**: Java 23 compatibility issue (lines 37-41 in ci-cd.yml)
- **Security scan is non-blocking**: Allows pipeline to pass during rate limits
- **No automatic deployment**: Manual SSH required, no secrets in GitHub
- **Cache keys are stable**: Don't use `github.run_number` or timestamps in cache keys
- **CVE data is 30-day old**: Acceptable trade-off for rate limit avoidance
- **Known test issues**: If Mockito stubbing tests fail after formatting, check
  `AdminControllerCollectionsTest.java` (renamed from `CollectionControllerDevTest`). The other two
  files this list used to name, `ContentControllerDevTest` and `ContentProcessingUtilTest`, no
  longer exist.
