#!/bin/bash
set -e

## On EC2 Instance

# Configuration
REPO_URL="https://github.com/themancalledzac/edens.zac.backend.git"
BRANCH="main"
APP_DIR="$HOME/portfolio-backend"

# Pull latest code
echo "Pulling latest code..."
if [ -d "$APP_DIR/repo" ]; then
  cd "$APP_DIR/repo"
  git fetch
  git reset --hard origin/$BRANCH
else
  git clone --branch $BRANCH $REPO_URL "$APP_DIR/repo"
  cd "$APP_DIR/repo"
fi

# Copy environment variables
echo "Setting up environment variables..."
cp "$APP_DIR/.env" "$APP_DIR/repo/.env"

# Build new image first while old containers still serve traffic
echo "Building and deploying..."
cd "$APP_DIR/repo"
docker compose build
docker compose --profile local-db down || true
docker compose --profile local-db up -d

# Cleanup dangling images
docker image prune -f

echo "Deployment completed successfully!"
echo "If something went wrong: docker compose --profile local-db logs --tail=100"
