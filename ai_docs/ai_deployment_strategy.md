# Deployment Strategy (AI Reference)

## Overview

Single EC2 instance running Docker Compose with PostgreSQL 16 + Spring Boot 3.4.1 (Java 23) + optional Caddy reverse proxy for HTTPS. CI runs on GitHub Actions. Deployment is manual via SSH.

**Design goals**: Cheap (single EC2), simple (a friend could clone and deploy), reliable (health checks, memory limits, backups).

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│ EC2 Instance (t2.small+ recommended)                    │
│                                                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │  PostgreSQL   │  │  Spring Boot │  │    Caddy     │  │
│  │  (512M limit) │  │  (768M limit)│  │  (optional)  │  │
│  │  port 5432    │→ │  port 8080   │← │  ports 80/443│  │
│  │  (internal)   │  │  (internal)  │  │  (public)    │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
│         ↑                  ↑                            │
│    postgres_data      Named volume                      │
│    (persistent)       for Caddy TLS                     │
└─────────────────────────────────────────────────────────┘
         ↕                                    ↕
    SSH tunnel                           CloudFront
    (local dev)                          (CDN/frontend)
```

## File Map

| File | Purpose | When to edit |
|------|---------|-------------|
| `Dockerfile` | Multi-stage build: Maven → JRE-alpine runtime | Changing Java version, build args, JVM flags |
| `docker-compose.yml` | All services: database, backend, caddy | Adding services, changing ports, memory limits |
| `Caddyfile` | HTTPS reverse proxy config | Changing domain, adding routes |
| `deploy.sh` | Main deploy script (verbose, with health checks) | Changing deploy workflow |
| `scripts/deploy.sh` | Concise deploy script (same logic, less output) | Mirror changes from root deploy.sh |
| `scripts/setup-ec2.sh` | One-time EC2 instance setup | Adding setup steps |
| `scripts/backup-postgres.sh` | Automated PostgreSQL backup + S3 sync | Changing retention, backup format |
| `scripts/restore-postgres.sh` | Restore from backup with confirmation | Changing restore process |
| `.github/workflows/ci-cd.yml` | CI pipeline (lint, test, build, security) | Adding CI steps |
| `.env.example` | Template for environment variables | Adding new env vars |
| `ai_docs/ai_ec2.md` | EC2 infrastructure reference | EC2 config changes |
| `ai_docs/ai_cicd.md` | CI/CD pipeline reference | Pipeline changes |
| `config/InternalSecretFilter.java` | Servlet filter rejecting requests without `X-Internal-Secret` | Changing secret validation logic |

## Deployment Pipeline

### CI/CD Flow (Automatic - GitHub Actions)

```
Push to any branch
    │
    ├── lint (Checkstyle) ──────────────────────────┐
    │       │                                       │
    │       ├── test (PostgreSQL 16 service)         │
    │       │       │                               │
    │       │       └── build (Maven + Docker)       │
    │       │                                       │
    │       └── security-scan (OWASP, non-blocking) │
    │                                               │
    └───────────────────────────────────────────────┘
                        │
                  All pass? → PR mergeable
```

**What CI validates:**
- Code style (Google Java Format via Checkstyle)
- All tests pass against real PostgreSQL
- Maven package builds successfully
- Docker image builds successfully
- No HIGH/CRITICAL CVEs in dependencies (advisory only)

**What CI does NOT do:**
- Deploy to EC2 (manual)
- Push Docker images to a registry
- Run integration/E2E tests against a live environment

### Deployment Flow (Manual - SSH to EC2)

```
1. Merge PR to main
2. SSH to EC2:  ssh -i ~/key.pem ec2-user@<ip>
3. Run deploy:  bash ~/portfolio-backend/repo/deploy.sh
4. Verify:      curl http://localhost:8080/actuator/health
```

**What deploy.sh does (in order):**
1. `git fetch` + `git reset --hard origin/main` (pull latest code)
2. Copy `.env` from `~/portfolio-backend/.env` to repo
3. `docker compose build` (builds new image while old containers still serve traffic)
4. `docker compose --profile local-db down` (stop old containers)
5. `docker compose --profile local-db up -d` (start new containers)
6. Wait 10s for health checks
7. `docker image prune -f` (clean dangling images)

**Key design decisions:**
- Build happens BEFORE down, minimizing downtime to ~10-30 seconds (container swap only)
- No `--no-cache` — Docker layer caching speeds up builds dramatically
- No `docker system prune -a` — avoids nuking base images and forcing full re-downloads
- Only dangling images pruned after deploy

## Docker Configuration

### Dockerfile (Multi-stage)

**Build stage:** `maven:3.9.9-eclipse-temurin-23`
- Copies `pom.xml` first for dependency caching
- `mvn dependency:go-offline` caches deps in a layer
- `mvn clean package -DskipTests` builds the JAR

**Runtime stage:** `eclipse-temurin:23-jre-alpine` (not JDK — smaller, more secure)
- Copies only the JAR
- JVM flags: `-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`
- These flags prevent OOM on small instances shared with PostgreSQL

### docker-compose.yml

**Services:**

| Service | Image | Memory Limit | Profile | Purpose |
|---------|-------|-------------|---------|---------|
| `database` | postgres:16-alpine | 512M | `local-db` | PostgreSQL with tuned shared_buffers |
| `backend` | edens.zac.backend:latest | 768M | (always) | Spring Boot application |
| `caddy` | caddy:2-alpine | (default) | `production` | HTTPS reverse proxy |

**Profile usage:**
- Local dev (no DB): `docker compose up` (backend only, connects to remote DB)
- Local dev (with DB): `docker compose --profile local-db up`
- EC2 production: `docker compose --profile local-db up -d`
- EC2 with HTTPS: `docker compose --profile local-db --profile production up -d`

**Volumes:**
- `postgres_data` — PostgreSQL data (persists across restarts and deploys)
- `caddy_data` — TLS certificates from Let's Encrypt
- `caddy_config` — Caddy configuration state

### Caddyfile

```
{$DOMAIN:localhost} {
    reverse_proxy backend:8080
}
```

Set `DOMAIN=api.yourdomain.com` in `.env` to enable automatic HTTPS via Let's Encrypt.

## EC2 Instance Setup

### Prerequisites
- AWS account with EC2 access
- Domain name (optional, for HTTPS)
- AWS credentials for S3/CloudFront (for image storage)

### First-Time Setup

Run `scripts/setup-ec2.sh` on a fresh Amazon Linux 2023 instance:
```bash
# SSH to instance
ssh -i ~/key.pem ec2-user@<ip>

# Download and run setup
curl -O https://raw.githubusercontent.com/themancalledzac/edens.zac.backend/main/scripts/setup-ec2.sh
bash setup-ec2.sh
```

**What setup-ec2.sh does:**
1. Updates system packages
2. Installs Docker + Docker Compose plugin
3. Clones the repository to `~/portfolio-backend/repo`
4. Copies `.env.example` to `~/portfolio-backend/.env`

**After setup, you must:**
1. Edit `~/portfolio-backend/.env` with real credentials
2. Log out and back in (Docker group membership)
3. Configure swap space (critical for instances with ≤2GB RAM):
   ```bash
   sudo dd if=/dev/zero of=/swapfile bs=128M count=8
   sudo chmod 600 /swapfile
   sudo mkswap /swapfile
   sudo swapon /swapfile
   echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab
   ```
4. Run `bash ~/portfolio-backend/repo/deploy.sh`

### EC2 Directory Structure

```
~/portfolio-backend/
├── .env                    # Master credentials (NOT in git)
├── backups/                # PostgreSQL backups
│   ├── postgres_edens_zac_20260314_030000.sql.gz
│   └── ...
└── repo/                   # Git clone of this repository
    ├── deploy.sh
    ├── docker-compose.yml
    ├── Dockerfile
    ├── .env                # Copied from parent by deploy.sh
    └── ...
```

### Security Group Rules

| Port | Protocol | Source | Purpose |
|------|----------|-------|---------|
| 22 | TCP | Your IP | SSH access |
| 80 | TCP | 0.0.0.0/0 | HTTP (Caddy redirect to HTTPS) |
| 443 | TCP | 0.0.0.0/0 | HTTPS (Caddy with Let's Encrypt) |
| 8080 | TCP | 0.0.0.0/0 | Required — Amplify (outside VPC) calls EC2 via public IP. InternalSecretFilter rejects unauthenticated requests. Can close if Caddy handles HTTPS on 443 instead. |

**IMPORTANT:** Do NOT expose port 5432. Use SSH tunnel for local dev database access:
```bash
ssh -L 5432:localhost:5432 -i ~/key.pem ec2-user@<ec2-ip>
# Then connect locally to localhost:5432
```

### Recommended Instance Size

| Instance | vCPUs | RAM | Monthly Cost | Notes |
|----------|-------|-----|-------------|-------|
| t2.micro | 1 | 1GB | ~$8 | **DO NOT USE** — confirmed OOM at 904MB with Postgres + Spring Boot |
| **t3.small** | **2** | **2GB** | **~$15** | **Minimum viable — requires swap file** |
| t3.medium | 2 | 4GB | ~$30 | Comfortable headroom, no swap needed |

**Lesson learned (2026-03-14):** A t2.micro instance with 904MB RAM and 8GB disk was completely exhausted running Postgres + Spring Boot. Docker commands hung, disk filled to 100%, and the instance required an AWS console reboot. A 1GB instance is not viable for this stack.

With memory limits set (512M DB + 768M app = 1.28GB), a 2GB instance leaves room for OS and Docker overhead.

## Database Backup & Recovery

### Automated Backups

**Script:** `scripts/backup-postgres.sh`
**Schedule:** Add to crontab: `0 3 * * * bash ~/portfolio-backend/repo/scripts/backup-postgres.sh`
**Retention:** 7 days local, synced to S3 if AWS CLI available

**What it does:**
1. `pg_dump` via Docker exec → gzipped SQL file
2. Saves to `~/portfolio-backend/backups/`
3. Deletes backups older than 7 days
4. Optionally uploads to S3 (STANDARD_IA storage class)

### Manual Backup

```bash
docker exec portfolio-postgres pg_dump -U zedens edens_zac | gzip > ~/portfolio-backend/backups/manual_$(date +%Y%m%d).sql.gz
```

### Restore from Backup

```bash
bash ~/portfolio-backend/repo/scripts/restore-postgres.sh ~/portfolio-backend/backups/postgres_edens_zac_20260314_030000.sql.gz
```

The script will:
1. Show a confirmation prompt (destructive operation)
2. Decompress and pipe to `psql` in the container
3. Overwrite current database contents

### Disaster Recovery

If the EC2 instance dies completely:
1. Launch new EC2 instance
2. Run `scripts/setup-ec2.sh`
3. Configure `~/portfolio-backend/.env`
4. Deploy: `bash ~/portfolio-backend/repo/deploy.sh`
5. Restore latest backup from S3:
   ```bash
   aws s3 cp s3://your-bucket/db-backups/latest.sql.gz ~/portfolio-backend/backups/
   bash ~/portfolio-backend/repo/scripts/restore-postgres.sh ~/portfolio-backend/backups/latest.sql.gz
   ```

**Recovery time:** ~15-20 minutes (assuming S3 backups exist)
**Data loss window:** Up to 24 hours (daily backup frequency)
**Minimum disk:** 20GB EBS volume (8GB is not enough — fills up with Docker images and journal logs)

## Environment Variables

### Required (in `~/portfolio-backend/.env` on EC2)

| Variable | Example | Purpose |
|----------|---------|---------|
| `SPRING_PROFILES_ACTIVE` | `prod` | Spring profile (`prod` enables InternalSecretFilter + WebConfigProd) |
| `POSTGRES_HOST` | `database` | Docker service name (use `database` on EC2) |
| `POSTGRES_PORT` | `5432` | PostgreSQL port |
| `POSTGRES_DB` | `edens_zac` | Database name |
| `POSTGRES_USER` | `zedens` | Database user |
| `POSTGRES_PASSWORD` | (secret) | Database password |
| `AWS_ACCESS_KEY_ID` | (secret) | AWS credentials for S3 |
| `AWS_SECRET_ACCESS_KEY` | (secret) | AWS credentials for S3 |
| `AWS_PORTFOLIO_S3_BUCKET` | `my-bucket` | S3 bucket for image storage |
| `AWS_CLOUDFRONT_DOMAIN` | `d1234.cloudfront.net` | CloudFront CDN domain |
| `INTERNAL_API_SECRET` | (generate with `openssl rand -hex 32`) | Shared secret — EC2 rejects any request missing this header |

### Optional (EC2 .env)

| Variable | Default | Purpose |
|----------|---------|---------|
| `DOMAIN` | `localhost` | Domain for Caddy HTTPS (e.g., `api.example.com`) |

### Amplify Environment Variables

| Variable | Action | Purpose |
|----------|--------|---------|
| `API_URL` | Add | EC2 backend URL — server-side only, never in JS bundle |
| `NEXT_PUBLIC_APP_URL` | Add | Amplify app URL (e.g. `https://yourapp.amplifyapp.com`) — used by Server Components to build absolute proxy URLs |
| `INTERNAL_API_SECRET` | Add | Same value as EC2 — injected by proxy into every backend request |
| `NEXT_PUBLIC_ENV` | Add | Set to `production` — used by `isProduction()` check |
| `NEXT_PUBLIC_API_URL` | **REMOVE** | Was the EC2 URL baked into the client JS bundle — must not exist |

## Troubleshooting

### Deploy fails — out of disk space
```bash
df -h                             # Check disk usage
sudo journalctl --vacuum-size=100M  # Free journal logs (can reclaim 500MB+)
docker system prune -f            # Remove stopped containers, dangling images
docker volume prune -f            # Remove unused volumes (CAUTION: not postgres_data)
docker builder prune -f           # Remove build cache
```

### Instance unresponsive — RAM exhausted
If SSH hangs or Docker commands freeze:
1. Reboot via AWS EC2 console (Instances → select → Instance State → Reboot)
2. SSH back in after ~2 minutes
3. Add swap if not already configured (see EC2 Setup section)
4. Clean Docker: `docker system prune -a -f`
5. Then deploy: `bash ~/portfolio-backend/repo/deploy.sh`

### App won't start — OOM killed
```bash
docker compose --profile local-db logs backend | tail -50
docker stats --no-stream          # Check memory usage
# If consistently hitting limits, increase instance size or tune -Xmx in Dockerfile
```

### Database connection refused
```bash
docker compose --profile local-db ps    # Is database container healthy?
docker compose --profile local-db logs database | tail -20
docker exec portfolio-postgres pg_isready -U zedens
```

### Old version still running after deploy
```bash
docker images | grep edens.zac    # Check image timestamps
docker compose --profile local-db down
docker compose build              # Rebuild (uses layer cache, fast)
docker compose --profile local-db up -d
```

### Caddy won't get HTTPS certificate
- Ensure port 80 AND 443 are open in security group
- Ensure `DOMAIN` in `.env` points to this EC2's public IP via DNS
- Check Caddy logs: `docker compose --profile production logs caddy`

## Remaining Manual Steps (Not Automated Yet)

These require manual action on the EC2 instance or AWS console:

### P0 — Security
- [x] **Port 5432 not exposed** — confirmed not in security group
- [x] **Removed "All TCP 0-65535 from 0.0.0.0/0" security group rule** — was opening every port to the internet
- [ ] **Change database password** if it's still a weak/default value

### P1 — Reliability
- [x] **Backup cron job running:** `0 3 * * * bash ~/portfolio-backend/repo/scripts/backup-postgres.sh >> ~/portfolio-backend/backups/backup.log 2>&1`
- [x] **AWS CLI installed and working** — S3 sync confirmed working
- [x] **Log rotation configured** — `/etc/logrotate.d/portfolio-backup` (weekly, 4 rotations, compressed)
- [ ] **Add `cronie` to `setup-ec2.sh`** — not installed by default on Amazon Linux 2023; needed for cron jobs

### P2 — BFF Proxy Security Hardening (IN PROGRESS — 2026-03-14)

**Problem solved**: The browser was calling EC2 directly, and the EC2 URL was baked into the client JS bundle via `NEXT_PUBLIC_API_URL`. Anyone could discover the EC2 address and bypass the frontend entirely.

**Solution**: Next.js BFF (Backend-For-Frontend) proxy pattern. All API calls route through `/api/proxy/` on the Amplify app. EC2 rejects any request that doesn't carry a shared secret header.

**What was changed:**

Frontend (`edens.zac.frontend`):
- `app/api/proxy/[...path]/route.ts` — reads `API_URL` (server-only env var, never in bundle); injects `X-Internal-Secret` header; removed EC2 URL from 502 error body
- `app/lib/api/core.ts` — production calls now route to `/api/proxy/api/{endpointType}`, never directly to EC2
- `app/utils/environment.ts` — `isProduction()` no longer depends on `NEXT_PUBLIC_API_URL`

Backend (`edens.zac.backend`):
- `config/InternalSecretFilter.java` — `@Order(1)` filter; returns 403 on any request missing/wrong `X-Internal-Secret`; `/actuator/health` is exempt
- `application.properties` — added `internal.api.secret=${INTERNAL_API_SECRET}`

**Remaining steps to complete:**
- [ ] Generate secret: `openssl rand -hex 32`
- [ ] Add `INTERNAL_API_SECRET` to EC2 `~/portfolio-backend/.env` and deploy backend
- [ ] Verify EC2 rejects unauthenticated requests: `curl http://ec2-ip:8080/api/read/collections` → 403
- [ ] Add `API_URL`, `NEXT_PUBLIC_APP_URL`, `INTERNAL_API_SECRET`, `NEXT_PUBLIC_ENV=production` to Amplify env vars
- [ ] Remove `NEXT_PUBLIC_API_URL` from Amplify env vars
- [ ] Redeploy frontend; confirm site loads and all data renders
- [ ] Check built JS bundle — EC2 IP/domain must not appear
- [ ] Close port 8080 in EC2 security group (final step, after everything verified)

**Verification checklist:**
1. Browser devtools Network tab — no direct requests to EC2 IP/domain
2. `curl http://ec2-ip:8080/api/read/collections` → 403
3. `curl http://ec2-ip:8080/api/read/collections -H "X-Internal-Secret: wrongvalue"` → 403
4. `curl http://ec2-ip:8080/actuator/health` → 200
5. Site loads, all collections and images render normally
6. Search built JS bundle for EC2 IP — must not appear

## Future Improvements (Not Started)

These are enhancements to consider when the project grows. None are needed now.

### Automated Deployment via CI
Add a `deploy` job to `.github/workflows/ci-cd.yml` that SSHs to EC2 after merge to main:
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
**Requires:** GitHub secrets for EC2_HOST, EC2_USER, EC2_SSH_KEY.
**Trade-off:** Convenience vs. manual control. Manual is safer for a solo project.

### Container Registry
Push Docker images to ECR or GitHub Container Registry so EC2 pulls pre-built images instead of building on the instance:
- Faster deploys (pull vs. build)
- Less CPU/memory pressure on EC2 during deploy
- ECR free tier: 500MB storage

### Blue-Green Deployments
Run two backend containers and swap traffic between them for zero-downtime deploys. Overkill for a portfolio site but interesting if traffic grows.

### Terraform / Infrastructure as Code
Not recommended at current scale. A single EC2 instance with Docker Compose doesn't benefit from Terraform's complexity. Revisit if you add:
- Separate staging environment
- Load balancer (ALB)
- RDS instead of local PostgreSQL
- Multiple services or microservices

### Monitoring & Alerting
- Spring Boot Actuator metrics are already exposed
- Add Prometheus + Grafana (Docker containers) for dashboards
- Or use CloudWatch agent for simpler AWS-native monitoring
- Set up alerts for disk space, memory, and health check failures

### Log Aggregation
Currently logs are in Docker json-file driver (10MB max, 3 files). For searchable logs:
- CloudWatch Logs agent (AWS-native, ~$0.50/GB)
- Loki + Grafana (self-hosted, free)

## Notes for AI Agents

- **Primary deploy script**: `deploy.sh` at repo root (the verbose one with health checks)
- **`scripts/deploy.sh`**: Concise version, same logic, less output
- **Profile `local-db`**: Starts PostgreSQL alongside the app (used on EC2)
- **Profile `production`**: Adds Caddy reverse proxy for HTTPS
- **Memory limits are enforced**: 512M for DB, 768M for backend, via Docker deploy.resources
- **JVM is capped**: `-Xmx512m` hard limit even without Docker memory enforcement
- **No automatic deployment**: Merge to main does NOT deploy. Manual SSH required.
- **Database is co-located**: PostgreSQL runs on same EC2 instance as the app
- **Backups go to S3**: If AWS CLI is installed and `AWS_PORTFOLIO_S3_BUCKET` is set
- **Legacy MySQL scripts exist**: `scripts/backup-database.sh`, `scripts/restore-database.sh`, `scripts/migrate-from-rds.sh` — these are deprecated, PostgreSQL equivalents are `scripts/backup-postgres.sh` and `scripts/restore-postgres.sh`
- **Security**: Port 5432 should NOT be in the security group. Use SSH tunnel.
