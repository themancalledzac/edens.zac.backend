#!/bin/bash
set -e

echo "======================================"
echo "Portfolio Backend Deployment"
echo "======================================"

# Configuration
REPO_URL="https://github.com/themancalledzac/edens.zac.backend.git"
BRANCH="main"
APP_DIR="$HOME/portfolio-backend"

# Pull latest code
echo "Pulling latest code from $BRANCH..."
if [ -d "$APP_DIR/repo" ]; then
  cd "$APP_DIR/repo"
  git fetch
  git reset --hard origin/$BRANCH
  echo "Updated existing repository"
else
  echo "Cloning repository..."
  git clone --branch $BRANCH $REPO_URL "$APP_DIR/repo"
  cd "$APP_DIR/repo"
fi

# Copy environment variables
echo "Setting up environment variables..."
if [ -f "$APP_DIR/.env" ]; then
  cp "$APP_DIR/.env" "$APP_DIR/repo/.env"
  echo "Environment variables copied"
else
  echo "ERROR: .env file not found at $APP_DIR/.env"
  exit 1
fi

# Verify database is running (managed separately in ~/portfolio-db/)
echo "Checking database health..."
if ! docker exec portfolio-postgres pg_isready -U ${POSTGRES_USER:-zedens} -q 2>/dev/null; then
  echo "ERROR: PostgreSQL container 'portfolio-postgres' is not running or not healthy."
  echo "Start it first: cd ~/portfolio-db && docker compose up -d"
  exit 1
fi
echo "Database is healthy"

# Free disk space before building (old images, build cache).
#
# The build needs several GB of headroom: the maven builder stage carries a full ~/.m2 plus the
# target/ output, and the root volume also holds the postgres data dir and the nightly backups.
# A deploy that runs out of space fails inside `mvn package` with "No space left on device", which
# reads like a build error and is not one -- so check for the headroom first and say so plainly.
#
# The routine prune is deliberately gentle: keeping recent build cache and the maven base image is
# what makes a same-day redeploy fast. Only when headroom is actually short does it escalate to
# dropping all of it, because on a small instance re-pulling the base image and re-resolving every
# dependency costs minutes.
# Measured against the real box on 2026-08-24, not guessed. The 8GB root volume holds ~5.9GB that
# never comes back -- ~2.3GB of OS under /usr, the in-use backend and postgres images (~670MB), the
# postgres data volume, and the journal floor. A full reclaim tops out around 2.0GB free, so a
# threshold above that would abort every deploy on this instance. Peak build demand is roughly
# 1.8GB: the maven base image, a fresh ~355MB .m2, target/ output, and the new image layers.
#
# That leaves about 200MB of margin, which is thin. It is thin because the volume is 8GB and
# ai_docs/ai_ec2.md:410 asks for 20GB+; raise this number when the volume grows, or drop the
# on-box maven build (see the local-build path) and this stops mattering.
REQUIRED_FREE_MB=2000

free_mb() {
  df -Pm "$APP_DIR" | awk 'NR==2 {print $4}'
}

echo "Cleaning up old Docker resources..."
docker image prune -f
docker builder prune -f --filter "until=24h"

if [ "$(free_mb)" -lt "$REQUIRED_FREE_MB" ]; then
  echo "Only $(free_mb)MB free, below the ${REQUIRED_FREE_MB}MB the build needs -- escalating cleanup..."
  docker builder prune -af
  docker image prune -af
  # Journal logs routinely reclaim 500MB+ and are the largest non-Docker consumer on this box.
  if command -v journalctl > /dev/null 2>&1 && sudo -n true 2>/dev/null; then
    sudo journalctl --vacuum-size=100M || true
  fi
  echo "After escalated cleanup: $(free_mb)MB free"
fi

if [ "$(free_mb)" -lt "$REQUIRED_FREE_MB" ]; then
  echo ""
  echo "======================================"
  echo "DEPLOYMENT ABORTED -- not enough disk space to build"
  echo "======================================"
  echo ""
  echo "Free: $(free_mb)MB. Needed: ${REQUIRED_FREE_MB}MB."
  echo ""
  echo "Nothing was stopped, so the currently running backend is untouched and still serving."
  echo ""
  echo "Largest consumers to check:"
  echo "  df -h /"
  echo "  docker system df"
  echo "  du -sh ~/portfolio-backend/backups ~/portfolio-db/data 2>/dev/null"
  echo "  sudo du -xh / 2>/dev/null | sort -rh | head -20"
  echo ""
  echo "If the volume is simply too small, grow it -- ai_docs/ai_ec2.md calls for 20GB+ and an"
  echo "8GB root volume cannot hold Docker, the postgres data dir and the backups at once."
  echo ""
  exit 1
fi

# Build new image (bust source cache on new commits, keep dependency cache)
echo "Building images..."
cd "$APP_DIR/repo"
docker compose build --build-arg CACHE_BUST="$(git rev-parse HEAD)"

# Stop old containers and start new ones
echo "Restarting backend..."
docker compose down || true
docker compose up -d

# Wait for backend to be healthy (up to 60 seconds)
echo "Waiting for backend to be healthy..."
RETRIES=30
HEALTHY=false
for i in $(seq 1 $RETRIES); do
  if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
    HEALTHY=true
    echo "Backend is healthy (took ~$((i * 2))s)"
    break
  fi
  sleep 2
done

if [ "$HEALTHY" = false ]; then
  echo "ERROR: Backend did not become healthy within 60s"
  echo "Recent logs:"
  docker compose logs --tail=30 backend
fi
# NOTE: the non-zero exit for this case is deferred to the end of the script, so the container
# status and startup logs below still print. Those are what actually identify the cause (a failed
# Flyway migration, for instance), and they are worth more than exiting a few lines earlier.

# Check container status
echo ""
echo "Container Status:"
docker compose ps

# Show startup logs (Flyway migrations, errors, etc.)
echo ""
echo "Startup logs:"
docker compose logs --tail=20 backend

# Final cleanup
echo ""
echo "Cleaning up dangling Docker images..."
docker image prune -f

# Report the truth. Previously this script printed "Deployment completed successfully!" and exited
# 0 even when the health check never passed -- so a backend that was crash-looping on a failed
# Flyway migration looked like a clean deploy, and the outage was only discovered from the browser.
if [ "$HEALTHY" = false ]; then
  echo ""
  echo "======================================"
  echo "DEPLOYMENT FAILED -- backend never became healthy"
  echo "======================================"
  echo ""
  echo "The container started but never passed /actuator/health."
  echo "The startup logs above usually name the cause. For more:"
  echo "  docker compose logs --tail=100 backend"
  echo ""
  echo "The previous image is gone (docker compose down ran), so the site stays down"
  echo "until this is resolved."
  echo ""
  exit 1
fi

echo ""
echo "======================================"
echo "Deployment completed successfully!"
echo "======================================"
echo ""
echo "To view logs:"
echo "  docker compose logs -f"
echo ""
echo "To check health:"
echo "  curl http://localhost:8080/actuator/health"
echo ""
echo "If something went wrong, check logs with:"
echo "  docker compose logs --tail=100"
echo ""
