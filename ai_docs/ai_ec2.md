# EC2 Infrastructure Documentation (AI Reference)

## Architecture Overview

**Single EC2 Instance** running two services:
1. PostgreSQL 16 database (standalone container in `~/portfolio-db/`)
2. Java Spring Boot application (container in `~/portfolio-backend/`)

**Database Connectivity**:
- EC2 Spring app → PostgreSQL via `localhost:5432` (same machine)
- Local dev Spring app → PostgreSQL via `<ec2-ip>:5432` (remote connection)
- **Shared database**: Both local and EC2 apps use the same PostgreSQL instance on EC2

## Directory Structure on EC2

```
~/portfolio-db/              # PostgreSQL service
├── docker compose.yml       # PostgreSQL container config
├── .env                     # Database credentials (not in git)
├── data/                    # PostgreSQL data volume (persistent)
└── scripts/                 # Initialization scripts
    └── postgres-init.sql

~/portfolio-backend/         # Application service
├── repo/                    # Git repository clone
│   ├── docker compose.yml   # Application container config
│   └── .env                 # App environment variables (copied from parent)
└── .env                     # Master environment file (not in git)
```

## PostgreSQL Service

### Configuration
**File**: `scripts/ec2-postgres/docker compose.yml`
**Image**: `postgres:16-alpine`
**Container name**: `portfolio-postgres`
**Port**: `5432` (exposed to host)
**Data volume**: `~/portfolio-db/data` (persistent)

**Environment variables** (from `~/portfolio-db/.env`):
```bash
POSTGRES_DB=edens_zac
POSTGRES_USER=zedens
POSTGRES_PASSWORD=<secure-password>
```

**PostgreSQL tuning**:
- `shared_buffers=256MB`
- `max_connections=100`
- `log_statement=all`

### Initial Deployment

**1. Copy files to EC2**:
```bash
scp -i "$EC2_PEM_FILE" scripts/ec2-postgres/docker compose.yml "$EC2_USER@$EC2_HOST:~/portfolio-db/"
scp -i "$EC2_PEM_FILE" scripts/ec2-postgres/env.template "$EC2_USER@$EC2_HOST:~/portfolio-db/"
scp -i "$EC2_PEM_FILE" scripts/postgres-init.sql "$EC2_USER@$EC2_HOST:~/portfolio-db/scripts/"
```

**2. Create `.env` file on EC2**:
```bash
ssh -i "$EC2_PEM_FILE" "$EC2_USER@$EC2_HOST"
cd ~/portfolio-db
cp env.template .env
nano .env  # Set POSTGRES_PASSWORD
```

**3. Start PostgreSQL**:
```bash
cd ~/portfolio-db
docker compose up -d
```

**4. Initialize database schema**:
```bash
docker compose exec postgres psql -U zedens -d edens_zac -f /scripts/postgres-init.sql
```

**5. Verify**:
```bash
docker compose ps
docker compose exec postgres psql -U zedens -d edens_zac -c "\dt"
```

### Database Management

**Access PostgreSQL shell**:
```bash
cd ~/portfolio-db
docker compose exec postgres psql -U zedens -d edens_zac
```

**Manual backup**:
```bash
cd ~/portfolio-db
docker compose exec postgres pg_dump -U zedens edens_zac > backup_$(date +%Y%m%d_%H%M%S).sql
gzip backup_*.sql
```

**Restore from backup**:
```bash
cd ~/portfolio-db
gunzip backup_YYYYMMDD_HHMMSS.sql.gz
docker compose exec -T postgres psql -U zedens -d edens_zac < backup_YYYYMMDD_HHMMSS.sql
```

**View logs**:
```bash
cd ~/portfolio-db
docker compose logs -f postgres
```

**Restart PostgreSQL**:
```bash
cd ~/portfolio-db
docker compose restart
```

## Application Service

### Configuration
**File**: `docker compose.yml` (root of repo)
**Image**: `edens.zac.backend:latest`
**Port**: `8080` (exposed to host)
**No local PostgreSQL**: Database service commented out, uses EC2 PostgreSQL

**Environment variables** (from `~/portfolio-backend/.env`):
```bash
# Database connection (EC2 PostgreSQL)
POSTGRES_HOST=<ec2-private-ip-or-localhost>
POSTGRES_PORT=5432
POSTGRES_DB=edens_zac
POSTGRES_USER=zedens
POSTGRES_PASSWORD=<same-as-db-password>

# AWS credentials
AWS_ACCESS_KEY_ID=<key>
AWS_SECRET_ACCESS_KEY=<secret>
AWS_REGION=us-west-2
AWS_PORTFOLIO_S3_BUCKET=<bucket-name>
AWS_CLOUDFRONT_DOMAIN=<domain>

# Spring profile
SPRING_PROFILES_ACTIVE=default
```

### Deployment Process

**Automated deployment script**: `scripts/deploy.sh`

**What it does**:
1. Pulls latest code from `main` branch (`git fetch` + `git reset --hard`)
2. Copies `.env` from `~/portfolio-backend/.env` to repo
3. Builds new Docker image (with layer caching, while old containers still serve)
4. Stops old containers and starts new ones
5. Waits for health checks
6. Cleans up dangling images

**Manual deployment**:
```bash
ssh -i ~/path/to/key.pem ec2-user@<ec2-ip>
bash ~/portfolio-backend/repo/deploy.sh
```

**Deployment verification**:
```bash
# Check container status
cd ~/portfolio-backend/repo
docker compose ps

# Check application logs
docker compose logs -f backend

# Test health endpoint
curl http://localhost:8080/actuator/health

# Test specific endpoint
curl http://localhost:8080/api/read/home
```

### Application Management

**View application logs**:
```bash
cd ~/portfolio-backend/repo
docker compose logs -f backend
```

**Restart application** (without rebuild):
```bash
cd ~/portfolio-backend/repo
docker compose restart backend
```

**Full restart** (with rebuild):
```bash
cd ~/portfolio-backend/repo
docker compose down
docker compose up -d --build
```

**Access application shell**:
```bash
cd ~/portfolio-backend/repo
docker compose exec backend /bin/sh
```

## Local Development Setup

**Connecting local Spring app to EC2 PostgreSQL**:

Create `.env` file in local repo root:
```bash
POSTGRES_HOST=<ec2-public-ip>
POSTGRES_PORT=5432
POSTGRES_DB=edens_zac
POSTGRES_USER=zedens
POSTGRES_PASSWORD=<same-as-ec2-db-password>

# AWS credentials (same as EC2)
AWS_ACCESS_KEY_ID=<key>
AWS_SECRET_ACCESS_KEY=<secret>
AWS_REGION=us-west-2
AWS_PORTFOLIO_S3_BUCKET=<bucket-name>
AWS_CLOUDFRONT_DOMAIN=<domain>

SPRING_PROFILES_ACTIVE=default
```

**Security group requirement**: EC2 security group must allow inbound PostgreSQL (port 5432) from your local IP.

**Connection string in Spring**:
```
jdbc:postgresql://<ec2-public-ip>:5432/edens_zac
```

## Scripts Reference

| Script | Location | Purpose |
|--------|----------|---------|
| `deploy.sh` | Root + `scripts/deploy.sh` | Deploy application to EC2 (run on EC2) |
| `setup-ec2.sh` | `scripts/setup-ec2.sh` | One-time EC2 instance setup |
| `backup-postgres.sh` | `scripts/backup-postgres.sh` | PostgreSQL backup with S3 sync |
| `restore-postgres.sh` | `scripts/restore-postgres.sh` | Restore PostgreSQL from backup |
| `backup-database.sh` | `scripts/backup-database.sh` | **DEPRECATED** — Legacy MySQL backup |
| `restore-database.sh` | `scripts/restore-database.sh` | **DEPRECATED** — Legacy MySQL restore |
| `migrate-from-rds.sh` | `scripts/migrate-from-rds.sh` | **DEPRECATED** — RDS → EC2 MySQL migration |
| `postgres-init.sql` | `scripts/postgres-init.sql` | PostgreSQL schema initialization |

**Note**: Backup/restore/migrate scripts are legacy MySQL scripts. PostgreSQL backups use `pg_dump` manually.

## File Locations

| File | Location | Purpose |
|------|----------|---------|
| `scripts/ec2-postgres/docker compose.yml` | Repo | PostgreSQL container config (copy to EC2) |
| `scripts/ec2-postgres/env.template` | Repo | Template for PostgreSQL `.env` file |
| `scripts/postgres-init.sql` | Repo | Database schema and initial data |
| `docker compose.yml` | Repo root | Application container config (backend only, no local DB) |
| `~/portfolio-db/.env` | EC2 only | PostgreSQL credentials (not in git) |
| `~/portfolio-backend/.env` | EC2 only | Application environment variables (not in git) |

## Troubleshooting

### PostgreSQL Issues

**Container won't start**:
```bash
cd ~/portfolio-db
docker compose logs postgres
docker compose restart postgres
```

**Connection refused**:
```bash
# Check if PostgreSQL is running
docker compose ps

# Check if port is listening
netstat -tlnp | grep 5432

# Check health
docker compose exec postgres pg_isready -U zedens
```

**Data corruption**:
```bash
# Stop PostgreSQL
cd ~/portfolio-db
docker compose down

# Remove corrupted data (WARNING: data loss)
rm -rf data/

# Restart and re-initialize
docker compose up -d
docker compose exec postgres psql -U zedens -d edens_zac -f /scripts/postgres-init.sql
```

### Application Issues

**Application won't connect to database**:
```bash
# Check PostgreSQL is accessible
cd ~/portfolio-db
docker compose exec postgres psql -U zedens -d edens_zac -c "SELECT 1;"

# Check environment variables
cd ~/portfolio-backend/repo
docker compose exec backend env | grep POSTGRES

# Verify connection string
# EC2 app should use: POSTGRES_HOST=localhost or <ec2-private-ip>
```

**Application container crashes**:
```bash
# Check logs for errors
cd ~/portfolio-backend/repo
docker compose logs backend

# Check health endpoint
curl http://localhost:8080/actuator/health

# Restart application
docker compose restart backend
```

**Old version still running**:
```bash
# Verify image tag
docker images | grep edens.zac.backend

# Force recreate with new build
cd ~/portfolio-backend/repo
docker compose down
docker compose build --no-cache
docker compose up -d
```

### Deployment Issues

**Deploy script fails**:
```bash
# Check disk space
df -h

# Clear Docker space
docker system prune -a -f

# Check git status
cd ~/portfolio-backend/repo
git status
git fetch
git reset --hard origin/main
```

**Environment variables missing**:
```bash
# Verify .env exists and has correct permissions
ls -la ~/portfolio-backend/.env
cat ~/portfolio-backend/.env  # Check contents

# deploy.sh copies this to repo
ls -la ~/portfolio-backend/repo/.env
```

## Security Notes

- **Never commit `.env` files** - Keep credentials out of git
- **Use same credentials** - Local dev and EC2 share same PostgreSQL password
- **SSH key security** - Store EC2 PEM files securely, never commit
- **Security groups** - Limit PostgreSQL port 5432 access to trusted IPs only
- **AWS credentials** - Use IAM users with minimal required permissions
- **Port 5432 should NOT be in the security group** — Use SSH tunnel: `ssh -L 5432:localhost:5432 ec2-user@<ip>`
- **Swap file is mandatory** on instances with ≤2GB RAM — without it, OOM kills Postgres or the app with no recovery path except reboot

## CI/CD Integration

**Pipeline**: GitHub Actions runs CI checks (lint, test, build, security scan)
**Deployment**: Manual SSH deployment via `deploy.sh`
**No automation**: No automatic deployment configured (by design)

**Post-merge workflow**:
1. CI pipeline passes on merge to `main`
2. SSH to EC2: `ssh -i ~/key.pem ubuntu@<ec2-ip>`
3. Run deployment: `cd ~/edens.zac.backend && bash scripts/deploy.sh`
4. Verify: `curl http://localhost:8080/actuator/health`

## EC2 Instance Requirements

**Minimum specifications**:
- Instance type: t3.small or larger (2GB+ RAM required — t2.micro confirmed insufficient)
- Storage: 20GB+ EBS volume (8GB fills up with Docker images and logs)
- OS: Amazon Linux 2023 or Ubuntu 22.04 LTS
- Docker installed
- Docker Compose plugin installed
- Swap file configured (1GB, critical for ≤2GB RAM instances)

**Ports to expose**:
- `22` - SSH access
- `80` - HTTP (Caddy redirect to HTTPS)
- `443` - HTTPS (Caddy with Let's Encrypt)
- `8080` - Application HTTP (direct, if no Caddy)
- ~~`5432`~~ - **Do NOT expose** — Use SSH tunnel instead

**Security group inbound rules**:
- SSH (22) from your IP
- HTTP (8080) from 0.0.0.0/0 (if public API) or specific IPs
- PostgreSQL (5432) from your local dev IP (for remote dev access)

## Notes for AI Agents

- **Architecture**: Single EC2 instance, two separate Docker Compose services
- **Database sharing**: Local dev connects to EC2 PostgreSQL remotely (not localhost)
- **No local database**: `docker compose.yml` has PostgreSQL service commented out
- **Deployment is manual**: `deploy.sh` must be run via SSH, no GitHub Actions automation
- **Environment files**: `.env` files exist only on EC2 and local dev, never in git
- **Legacy scripts**: MySQL backup/restore/migration scripts are deprecated (PostgreSQL now)
- **PostgreSQL backups**: Automated via `scripts/backup-postgres.sh`, restore via `scripts/restore-postgres.sh`
- **Connection patterns**:
  - EC2 app uses `POSTGRES_HOST=localhost` or EC2 private IP
  - Local dev uses `POSTGRES_HOST=<ec2-public-ip>`
