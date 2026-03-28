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

# Free disk space before building (old images, build cache)
echo "Cleaning up old Docker resources..."
docker image prune -f
docker builder prune -f --filter "until=24h"

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
  echo "WARNING: Backend did not become healthy within 60s"
  echo "Recent logs:"
  docker compose logs --tail=30 backend
fi

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
