#!/bin/bash

# Database backup script for Edens.Zac Portfolio Backend
# Usage: ./backup-database.sh [backup-name]

set -e

# Configuration - Use RDS environment variables first, fallback to local
DB_NAME="${RDS_TABLE_NAME:-${MYSQL_DATABASE:-edens_zac}}"
DB_USER="${RDS_USERNAME:-${MYSQL_USER:-admin}}"
DB_PASSWORD="${RDS_PASSWORD:-${MYSQL_PASSWORD:-password}}"
DB_HOST="${RDS_ENDPOINT:-${DB_HOST:-localhost}}"
DB_PORT="${DB_PORT:-3306}"

# Create backup directory if it doesn't exist
BACKUP_DIR="./backups"
mkdir -p "$BACKUP_DIR"

# Generate backup filename
BACKUP_NAME="${1:-$(date +%Y%m%d_%H%M%S)}"
BACKUP_FILE="$BACKUP_DIR/${DB_NAME}_backup_${BACKUP_NAME}.sql"

echo "Creating database backup..."
echo "Database: $DB_NAME"
echo "Output: $BACKUP_FILE"

# Create the backup
mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" \
    --single-transaction \
    --routines \
    --triggers \
    --add-drop-database \
    --databases "$DB_NAME" > "$BACKUP_FILE"

# Compress the backup
gzip "$BACKUP_FILE"
BACKUP_FILE="${BACKUP_FILE}.gz"

echo "Backup completed successfully: $BACKUP_FILE"
echo "Backup size: $(du -h "$BACKUP_FILE" | cut -f1)"

# Keep only the last 7 backups to save space
find "$BACKUP_DIR" -name "${DB_NAME}_backup_*.sql.gz" -type f -mtime +7 -delete

echo "Old backups (>7 days) cleaned up."