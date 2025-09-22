#!/bin/bash

# RDS to Local MySQL Migration Script
# This script helps migrate data from your existing RDS instance to local MySQL
# Usage: ./migrate-from-rds.sh

set -e

echo "=== RDS to Local MySQL Migration ==="
echo ""

# Check if required environment variables are set
if [ -z "$RDS_ENDPOINT" ] || [ -z "$RDS_TABLE_NAME" ] || [ -z "$RDS_PASSWORD" ]; then
    echo "Error: Please set the following environment variables:"
    echo "  RDS_ENDPOINT"
    echo "  RDS_TABLE_NAME" 
    echo "  RDS_PASSWORD"
    echo ""
    echo "You can set them by running:"
    echo "  export RDS_ENDPOINT='your-rds-endpoint'"
    echo "  export RDS_TABLE_NAME='your-db-name'"
    echo "  export RDS_PASSWORD='your-rds-password'"
    exit 1
fi

# Configuration
RDS_USER="admin"
RDS_TIMEZONE="${RDS_TIMEZONE:-UTC}"
LOCAL_DB_NAME="${MYSQL_DATABASE:-edens_zac}"
LOCAL_DB_USER="${MYSQL_USER:-zedens}"
LOCAL_DB_PASSWORD="${MYSQL_PASSWORD:-password}"

echo "RDS Configuration:"
echo "  Endpoint: $RDS_ENDPOINT"
echo "  Database: $RDS_TABLE_NAME"
echo "  User: $RDS_USER"
echo ""
echo "Local MySQL Configuration:"
echo "  Database: $LOCAL_DB_NAME"
echo "  User: $LOCAL_DB_USER"
echo ""

read -p "Proceed with migration? (y/N): " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Migration cancelled."
    exit 0
fi

# Create backup directory
mkdir -p ./backups/migration

# Step 1: Export from RDS
echo "Step 1: Exporting data from RDS..."
EXPORT_FILE="./backups/migration/rds_export_$(date +%Y%m%d_%H%M%S).sql"

mysqldump -h "$RDS_ENDPOINT" -u "$RDS_USER" -p"$RDS_PASSWORD" \
    --single-transaction \
    --routines \
    --triggers \
    --databases "$RDS_TABLE_NAME" > "$EXPORT_FILE"

echo "RDS data exported to: $EXPORT_FILE"

# Step 2: Start local MySQL if not running
echo "Step 2: Ensuring local MySQL is running..."
if ! docker-compose ps mysql | grep -q "Up"; then
    echo "Starting MySQL container..."
    docker-compose up -d mysql
    echo "Waiting for MySQL to be ready..."
    sleep 10
fi

# Wait for MySQL to be healthy
echo "Waiting for MySQL to be ready..."
for i in {1..30}; do
    if docker-compose exec mysql mysqladmin ping -h localhost -u root -p"${MYSQL_ROOT_PASSWORD:-rootpassword}" --silent; then
        echo "MySQL is ready!"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "Error: MySQL failed to start after 30 attempts"
        exit 1
    fi
    sleep 2
done

# Step 3: Import to local MySQL
echo "Step 3: Importing data to local MySQL..."

# Replace database name in the export file for local import
sed "s/\`$RDS_TABLE_NAME\`/\`$LOCAL_DB_NAME\`/g" "$EXPORT_FILE" > "${EXPORT_FILE}.local"

# Import the data
mysql -h localhost -u "$LOCAL_DB_USER" -p"$LOCAL_DB_PASSWORD" < "${EXPORT_FILE}.local"

echo ""
echo "=== Migration Complete! ==="
echo ""
echo "Your data has been successfully migrated from RDS to local MySQL."
echo ""
echo "Next steps:"
echo "1. Test your application with: docker-compose up"
echo "2. Verify all data is present and working correctly"
echo "3. Update your deployment configuration to use local MySQL"
echo "4. Once confirmed working, you can safely terminate your RDS instance"
echo ""
echo "Backup files saved in: ./backups/migration/"