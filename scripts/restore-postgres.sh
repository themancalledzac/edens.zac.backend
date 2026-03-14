#!/bin/bash
# chmod +x scripts/restore-postgres.sh
set -e

CONTAINER_NAME="portfolio-postgres"
DB_NAME="${POSTGRES_DB:-edens_zac}"
DB_USER="${POSTGRES_USER:-zedens}"

if [ -z "$1" ]; then
  echo "Usage: $0 <backup-file.sql.gz>"
  echo ""
  echo "Available backups:"
  ls -lh ~/portfolio-backend/backups/postgres_*.sql.gz 2>/dev/null || echo "  No backups found"
  exit 1
fi

BACKUP_FILE="$1"

if [ ! -f "$BACKUP_FILE" ]; then
  echo "ERROR: Backup file not found: $BACKUP_FILE"
  exit 1
fi

echo "WARNING: This will overwrite the current database '$DB_NAME'"
read -p "Are you sure? (y/N): " confirm
if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
  echo "Cancelled"
  exit 0
fi

echo "Restoring from: $BACKUP_FILE"
gunzip -c "$BACKUP_FILE" | docker exec -i "$CONTAINER_NAME" psql -U "$DB_USER" -d "$DB_NAME"

echo "Restore complete"
