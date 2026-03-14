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

# Build new image first (old containers keep serving traffic)
echo "Building images..."
cd "$APP_DIR/repo"
docker compose build

# Stop old containers and start new ones
echo "Restarting containers (database + backend)..."
docker compose --profile local-db down || true
docker compose --profile local-db up -d

# Wait for services to be healthy
echo "Waiting for services to be healthy..."
sleep 10

# Check container status
echo ""
echo "Container Status:"
docker compose --profile local-db ps

# Cleanup dangling images to save disk space
echo ""
echo "Cleaning up dangling Docker images..."
docker image prune -f

echo ""
echo "======================================"
echo "Deployment completed successfully!"
echo "======================================"
echo ""
echo "To view logs:"
echo "  docker compose --profile local-db logs -f"
echo ""
echo "To check health:"
echo "  curl http://localhost:8080/actuator/health"
echo ""
echo "If something went wrong, check logs with:"
echo "  docker compose --profile local-db logs --tail=100"
echo ""
