#!/bin/bash

# Database restore script for Edens.Zac Portfolio Backend
# Usage: ./restore-database.sh <backup-file>

set -e

if [ -z "$1" ]; then
    echo "Usage: $0 <backup-file>"
    echo "Example: $0 backups/edens_zac_backup_20240101_120000.sql.gz"
    exit 1
fi

BACKUP_FILE="$1"

if [ ! -f "$BACKUP_FILE" ]; then
    echo "Error: Backup file '$BACKUP_FILE' not found!"
    exit 1
fi

# Configuration
DB_USER="${MYSQL_USER:-zedens}"
DB_PASSWORD="${MYSQL_PASSWORD:-password}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"

echo "Restoring database from: $BACKUP_FILE"

# Decompress and restore based on file extension
if [[ "$BACKUP_FILE" == *.gz ]]; then
    echo "Decompressing and restoring..."
    gunzip -c "$BACKUP_FILE" | mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD"
else
    echo "Restoring..."
    mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" < "$BACKUP_FILE"
fi

echo "Database restored successfully!"