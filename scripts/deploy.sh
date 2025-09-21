#!/bin/bash
set -e

## On EC2 Instance

# Configuration
REPO_URL="https://github.com/themancalledzac/edens.zac.backend.git"
BRANCH="main"
APP_DIR="$HOME/portfolio-backend"

# Clear Docker system
docker system prune -a -f

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

# Build and deploy with Docker Compose
echo "Building and deploying with Docker Compose..."
cd "$APP_DIR/repo"
docker-compose down || true
docker-compose build --no-cache
docker-compose up -d

# Cleanup old images to save disk space
echo "Cleaning up old Docker images..."
docker image prune -f

echo "Deployment completed successfully!"