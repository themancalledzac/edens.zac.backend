#!/bin/bash
#
# Deploys the backend on the EC2 instance. Run it there: bash ~/portfolio-backend/repo/deploy.sh
#
# Order: pull main, copy ~/portfolio-backend/.env, check that .env satisfies the prod startup
# guards (config/Prod*Guard.java), check postgres, free disk, build, swap the container, wait for
# /actuator/health. If the new container never gets healthy, the previous image is put back and
# started again, so a bad deploy does not take the site down.
#
# When a new Prod*Guard requires an env var, add that check to check_prod_env below and the var to
# .env.example. Keys in .env.example that are missing from the server .env print as a warning.
#
# Disk: the 8GB root volume tops out near 2.3GB free after a full prune, and the build peaks near
# 1.8GB, so REQUIRED_FREE_MB defaults to 2000 and can be overridden (REQUIRED_FREE_MB=1500).
# The escalated prune only runs when headroom is short, because re-pulling the base image costs
# minutes. Never pass --remove-orphans: portfolio-postgres shows up as an orphan of this project.
set -e

echo "======================================"
echo "Portfolio Backend Deployment"
echo "======================================"

REPO_URL="https://github.com/themancalledzac/edens.zac.backend.git"
BRANCH="main"
APP_DIR="$HOME/portfolio-backend"
IMAGE="edens.zac.backend"
REQUIRED_FREE_MB="${REQUIRED_FREE_MB:-2000}"
HEALTH_RETRIES=30

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

echo "Setting up environment variables..."
if [ -f "$APP_DIR/.env" ]; then
  cp "$APP_DIR/.env" "$APP_DIR/repo/.env"
  echo "Environment variables copied"
else
  echo "ERROR: .env file not found at $APP_DIR/.env"
  exit 1
fi

env_value() {
  grep -E "^[[:space:]]*(export[[:space:]]+)?$1=" "$APP_DIR/repo/.env" | tail -n1 \
    | cut -d= -f2- | sed -E 's/^[[:space:]]+|[[:space:]]+$//g; s/^"(.*)"$/\1/; s/^'"'"'(.*)'"'"'$/\1/'
}

check_prod_env() {
  local problems=()
  local profile rp_id origins
  profile="$(env_value SPRING_PROFILES_ACTIVE)"
  if [ -z "$profile" ]; then
    problems+=("SPRING_PROFILES_ACTIVE is not set")
  fi
  if [[ ",$profile," == *",prod,"* ]]; then
    case "$(env_value INTERNAL_API_SECRET)" in
      "" | dev-internal-secret) problems+=("INTERNAL_API_SECRET is unset or the dev default") ;;
    esac
    case "$(env_value ACCESS_TOKEN_SECRET)" in
      "" | dev-access-token-secret) problems+=("ACCESS_TOKEN_SECRET is unset or the dev default") ;;
    esac
    rp_id="$(env_value WEBAUTHN_RP_ID)"
    if [ -z "$rp_id" ] || [ "$rp_id" = "localhost" ]; then
      problems+=("WEBAUTHN_RP_ID must be the production domain (e.g. zacedens.com)")
    fi
    origins="$(env_value WEBAUTHN_ALLOWED_ORIGINS)"
    if ! awk -F, '{for (i = 1; i <= NF; i++) {gsub(/[[:space:]]/, "", $i); if ($i != "" && $i !~ /localhost/) ok = 1}} END {exit !ok}' <<< "$origins"; then
      problems+=("WEBAUTHN_ALLOWED_ORIGINS must include the production origin (e.g. https://zacedens.com)")
    fi
  fi

  if [ -f "$APP_DIR/repo/.env.example" ]; then
    local missing
    missing="$(grep -oE '^[A-Z][A-Z0-9_]*=' "$APP_DIR/repo/.env.example" | tr -d '=' | sort -u \
      | while read -r key; do
          grep -qE "^[[:space:]]*(export[[:space:]]+)?$key=" "$APP_DIR/repo/.env" || echo "$key"
        done)"
    if [ -n "$missing" ]; then
      echo "WARNING: keys in .env.example that $APP_DIR/.env does not set (compose defaults apply):"
      sed 's/^/  /' <<< "$missing"
    fi
  fi

  if [ ${#problems[@]} -gt 0 ]; then
    echo ""
    echo "======================================"
    echo "DEPLOYMENT ABORTED -- $APP_DIR/.env fails the prod startup checks"
    echo "======================================"
    printf '  - %s\n' "${problems[@]}"
    echo ""
    echo "The backend would refuse to start with this .env. Fix $APP_DIR/.env and re-run."
    echo "Nothing was built or stopped; the running backend is untouched."
    exit 1
  fi
  echo "Environment passes the prod startup checks"
}

echo "Checking environment..."
check_prod_env

echo "Checking database health..."
if ! docker exec portfolio-postgres pg_isready -U ${POSTGRES_USER:-zedens} -q 2>/dev/null; then
  echo "ERROR: PostgreSQL container 'portfolio-postgres' is not running or not healthy."
  echo "Start it first: cd ~/portfolio-db && docker compose up -d"
  exit 1
fi
echo "Database is healthy"

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

cd "$APP_DIR/repo"

HAS_PREVIOUS=false
RUNNING_ID="$(docker compose ps -q backend 2>/dev/null || true)"
if [ -n "$RUNNING_ID" ]; then
  docker tag "$(docker inspect --format '{{.Image}}' "$RUNNING_ID")" "$IMAGE:previous"
  HAS_PREVIOUS=true
  echo "Kept the running image as $IMAGE:previous for rollback"
else
  docker rmi "$IMAGE:previous" > /dev/null 2>&1 || true
  echo "No running backend container; there is nothing to roll back to"
fi

echo "Building images..."
docker compose build --build-arg CACHE_BUST="$(git rev-parse HEAD)"

wait_for_health() {
  local id restarts
  for i in $(seq 1 $HEALTH_RETRIES); do
    if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
      echo "Backend is healthy (took ~$((i * 2))s)"
      return 0
    fi
    id="$(docker compose ps -q backend 2>/dev/null || true)"
    restarts="$(docker inspect --format '{{.RestartCount}}' "$id" 2>/dev/null || echo 0)"
    if [ "${restarts:-0}" -gt 0 ]; then
      echo "Backend container crashed during startup (restart count $restarts)"
      return 1
    fi
    sleep 2
  done
  echo "Backend did not become healthy within $((HEALTH_RETRIES * 2))s"
  return 1
}

echo "Restarting backend..."
docker compose up -d --no-build --force-recreate backend

echo "Waiting for backend to be healthy..."
HEALTHY=true
wait_for_health || HEALTHY=false

if [ "$HEALTHY" = true ]; then
  docker rmi "$IMAGE:previous" > /dev/null 2>&1 || true
  echo ""
  echo "Container Status:"
  docker compose ps
  echo ""
  echo "Startup logs:"
  docker compose logs --tail=20 backend
  echo ""
  echo "Cleaning up dangling Docker images..."
  docker image prune -f
  echo ""
  echo "======================================"
  echo "Deployment completed successfully!"
  echo "======================================"
  echo ""
  echo "To view logs:     docker compose logs -f"
  echo "To check health:  curl http://localhost:8080/actuator/health"
  echo ""
  exit 0
fi

echo ""
echo "Startup logs from the failed version:"
docker compose logs --tail=60 backend
FAILED_LOGS_HINT="the failed version's logs are printed above"

if [ "$HAS_PREVIOUS" = false ]; then
  echo ""
  echo "======================================"
  echo "DEPLOYMENT FAILED -- backend never became healthy, and there was no previous image"
  echo "======================================"
  echo "The site is down until this is fixed; $FAILED_LOGS_HINT."
  exit 1
fi

echo ""
echo "Rolling back to the previous image..."
docker tag "$IMAGE:latest" "$IMAGE:failed"
docker tag "$IMAGE:previous" "$IMAGE:latest"
docker compose up -d --no-build --force-recreate backend

if wait_for_health; then
  echo ""
  echo "======================================"
  echo "DEPLOYMENT FAILED -- rolled back, the previous version is serving"
  echo "======================================"
  echo "The new build is kept as $IMAGE:failed; $FAILED_LOGS_HINT."
  echo "If the new version ran a Flyway migration, confirm the previous version still works with it."
  echo "Remove the failed image once done: docker rmi $IMAGE:failed"
  exit 1
fi

echo ""
docker compose logs --tail=40 backend
echo ""
echo "======================================"
echo "DEPLOYMENT FAILED -- rollback did not become healthy either, the site is down"
echo "======================================"
echo "Both versions fail to start, which points at the environment or the database"
echo "(a .env change, postgres, or a migration the previous version cannot run against)."
exit 1
