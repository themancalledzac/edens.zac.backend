#!/bin/bash
# chmod +x scripts/backup-postgres.sh
set -e

BACKUP_DIR="$HOME/portfolio-backend/backups"
CONTAINER_NAME="portfolio-postgres"
DB_NAME="${POSTGRES_DB:-edens_zac}"
DB_USER="${POSTGRES_USER:-zedens}"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/postgres_${DB_NAME}_${DATE}.sql.gz"
KEEP_DAYS=7

# Load env vars for S3 sync (needed when run from cron)
ENV_FILE="$HOME/portfolio-backend/.env"
if [ -f "$ENV_FILE" ]; then
  set -a
  source "$ENV_FILE"
  set +a
fi

mkdir -p "$BACKUP_DIR"

echo "Backing up PostgreSQL database: $DB_NAME"
docker exec "$CONTAINER_NAME" pg_dump -U "$DB_USER" "$DB_NAME" | gzip > "$BACKUP_FILE"

echo "Backup saved: $BACKUP_FILE ($(du -h "$BACKUP_FILE" | cut -f1))"

# Cleanup old backups
echo "Removing backups older than $KEEP_DAYS days..."
find "$BACKUP_DIR" -name "postgres_*.sql.gz" -mtime +$KEEP_DAYS -delete

# Optional: sync to S3 if AWS CLI is available
if command -v aws &> /dev/null && [ -n "$AWS_PORTFOLIO_S3_BUCKET" ]; then
  echo "Syncing backups to S3..."
  aws s3 cp "$BACKUP_FILE" "s3://$AWS_PORTFOLIO_S3_BUCKET/db-backups/" --storage-class STANDARD_IA
  echo "S3 sync complete"
fi

echo "Backup complete"
