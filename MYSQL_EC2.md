# EC2 MySQL Database Management Guide

Complete guide for managing your MySQL database running on EC2, including migration from RDS, local development setup, and daily database operations using custom .zshrc functions.

## Overview

Your application architecture:
- **Production**: MySQL and backend both run as containers on your EC2 instance
- **Local Development**: Spring Boot app runs locally but connects to the production MySQL database on EC2
- **Single Database**: One production database that serves both your deployed backend and local development

## Quick Start

### 1. Initial Setup & Migration from RDS

```bash
# Setup EC2 with MySQL container and scripts
ec2DbSetup

# Migrate your existing RDS data to EC2 MySQL
ec2DbMigrate

# Verify migration was successful
ec2DbStatus
ec2DbQuery "SHOW TABLES;"
```

### 2. Configure Local Development

```bash
# Set up local environment to connect to EC2 database
ec2DevSetup

# Run your Spring Boot application locally (connects to production DB)
./mvnw spring-boot:run
```

## Migration Strategy

### Phase 1: Preparation
1. **Backup Current Data**
   ```bash
   # The ec2DbMigrate function will handle RDS backup automatically
   # But you can also create manual backups
   ec2DbBackup "pre-migration-$(date +%Y%m%d)"
   ```

2. **Deploy MySQL to EC2**
   ```bash
   ec2DbSetup
   ```

### Phase 2: Data Migration
1. **Set RDS Environment Variables** (locally)
   ```bash
   export RDS_ENDPOINT="your-rds-endpoint.region.rds.amazonaws.com"
   export RDS_TABLE_NAME="your_database_name"
   export RDS_PASSWORD="your_rds_password"
   ```

2. **Run Automated Migration**
   ```bash
   ec2DbMigrate
   ```

3. **Verify Data Migration**
   ```bash
   ec2DbStatus
   ec2DbQuery "SELECT COUNT(*) FROM content_collection_entity;"
   ec2DbQuery "SELECT COUNT(*) FROM image_entity;"
   ```

### Phase 3: Testing & Validation
1. **Test Local Development**
   ```bash
   ec2DevSetup
   ./mvnw spring-boot:run
   # Test all your endpoints and functionality
   ```

2. **Test Production Deployment**
   ```bash
   ec2Deploy
   ec2Status
   ec2Logs
   ```

### Phase 4: Go Live
1. **Update DNS/Load Balancer** (if applicable)
2. **Terminate RDS Instance** (after confirming everything works)
3. **Update billing alerts** to reflect cost savings

## Connection Configuration

### Local Development Connection
Your application automatically connects to the EC2 database when you run:
```bash
ec2DevSetup
./mvnw spring-boot:run
```

**How it works:**
- `ec2DevSetup` sets `EC2_HOST_FOR_DB` environment variable
- `application.properties` uses: `jdbc:mysql://${EC2_HOST:localhost}:3306/edens_zac`
- Your local app connects directly to port 3306 on your EC2 instance

### Production Container Connection
When deployed via `docker-compose`, containers connect using:
- Service name: `mysql` (internal Docker network)
- URL: `jdbc:mysql://mysql:3306/edens_zac`
- No external network access needed between containers

### Connection Security
- MySQL container accepts external connections for local development
- Credentials stored in EC2 `.env` file
- Port 3306 exposed only for development access
- Consider VPN or IP restrictions for additional security

## .zshrc Function Reference

### Database Management Functions

#### `ec2DbStatus`
Check MySQL database health and connectivity
```bash
ec2DbStatus
```

#### `ec2DbLogs [lines]`
View MySQL container logs
```bash
ec2DbLogs 50      # Last 50 lines
ec2DbLogs         # Last 100 lines (default)
```

#### `ec2DbQuery '<SQL>'`
Execute SQL queries directly
```bash
ec2DbQuery "SELECT COUNT(*) FROM content_collection_entity;"
ec2DbQuery "SHOW TABLES;"
ec2DbQuery "DESC image_entity;"
```

#### `ec2DbShell`
Open interactive MySQL shell
```bash
ec2DbShell
# Then run SQL commands interactively
# Exit with 'quit' or Ctrl+D
```

### Backup & Restore Functions

#### `ec2DbBackup [name]`
Create database backup
```bash
ec2DbBackup "before-feature-x"
ec2DbBackup                      # Auto-named with timestamp
```

#### `ec2DbBackups`
List all available backups
```bash
ec2DbBackups
```

#### `ec2DbRestore <backup_filename>`
Restore from backup (with confirmation prompt)
```bash
ec2DbRestore "edens_zac_backup_20240825_143022.sql.gz"
```

#### `ec2DbDownload <backup_filename>`
Download backup file to local machine
```bash
ec2DbDownload "edens_zac_backup_20240825_143022.sql.gz"
# Downloads to ./backups/downloaded/
```

### Migration Functions

#### `ec2DbSetup`
Initial setup: deploy MySQL container and copy scripts to EC2
```bash
ec2DbSetup
```

#### `ec2DbMigrate`
Automated RDS to EC2 MySQL migration
```bash
# First set RDS environment variables locally:
export RDS_ENDPOINT="your-endpoint"
export RDS_TABLE_NAME="your-db"
export RDS_PASSWORD="your-password"

# Then run migration:
ec2DbMigrate
```

### Development Functions

#### `ec2DevSetup`
Configure local environment to connect to EC2 database
```bash
ec2DevSetup
```

#### `ec2DbTestConnection`
Test local MySQL client connection to EC2
```bash
ec2DbTestConnection
# Requires: brew install mysql
```

## Daily Development Workflow

### Starting Development
```bash
# 1. Configure environment
ec2DevSetup

# 2. Check database status
ec2DbStatus

# 3. Run your application
./mvnw spring-boot:run
```

### During Development
```bash
# Quick database queries
ec2DbQuery "SELECT * FROM content_collection_entity LIMIT 5;"

# Check recent data changes
ec2DbQuery "SELECT created_date FROM content_collection_entity ORDER BY created_date DESC LIMIT 10;"

# Open full MySQL shell for complex operations
ec2DbShell
```

### Before Major Changes
```bash
# Create backup before significant database changes
ec2DbBackup "before-$(date +%Y%m%d)-feature"
```

### Deployment
```bash
# 1. Create backup
ec2DbBackup "pre-deploy-$(date +%Y%m%d_%H%M)"

# 2. Deploy application
ec2Deploy

# 3. Check status
ec2Status
ec2DbStatus

# 4. Monitor logs
ec2Logs
ec2DbLogs
```

## Database Operations

### Schema Management
Your application uses Hibernate with `ddl-auto=update`, so schema changes happen automatically. Monitor with:
```bash
ec2DbLogs 50  # Check for schema update messages
```

### Performance Monitoring
```bash
# Check database performance
ec2DbQuery "SHOW PROCESSLIST;"
ec2DbQuery "SHOW ENGINE INNODB STATUS\\G"

# Monitor container resources
ssh -i "$EC2_PEM_FILE" "$EC2_USER@$EC2_HOST" "docker stats --no-stream"
```

### Data Maintenance
```bash
# Check database size
ec2DbQuery "SELECT table_schema AS 'Database', ROUND(SUM(data_length + index_length) / 1024 / 1024, 2) AS 'Size (MB)' FROM information_schema.tables WHERE table_schema='edens_zac';"

# Optimize tables (run occasionally)
ec2DbQuery "OPTIMIZE TABLE content_collection_entity, image_entity;"
```

## Backup Strategy

### Automated Backups
Set up cron jobs on your EC2 instance:
```bash
ssh -i "$EC2_PEM_FILE" "$EC2_USER@$EC2_HOST"
crontab -e

# Add these lines:
# Daily backup at 2 AM
0 2 * * * /home/ubuntu/portfolio-backend/repo/scripts/backup-database.sh daily
# Weekly backup on Sunday at 3 AM
0 3 * * 0 /home/ubuntu/portfolio-backend/repo/scripts/backup-database.sh weekly
```

### Manual Backups
```bash
# Before major changes
ec2DbBackup "before-schema-change"

# Before deployments
ec2DbBackup "pre-deploy-$(date +%Y%m%d)"

# Download critical backups locally
ec2DbDownload "critical-backup.sql.gz"
```

### Backup Retention
The backup script automatically:
- Compresses backups with gzip
- Keeps last 7 daily backups
- Stores backups in `backups/` directory on EC2

## Troubleshooting

### Connection Issues

#### Local Development Can't Connect
```bash
# 1. Check database status
ec2DbStatus

# 2. Test basic connectivity
ec2DbTestConnection

# 3. Verify environment setup
echo $EC2_HOST_FOR_DB

# 4. Check EC2 security group allows port 3306
# 5. Check MySQL logs
ec2DbLogs 50
```

#### Container Connection Issues
```bash
# 1. Check container status
ec2Status

# 2. Check MySQL container specifically
ssh -i "$EC2_PEM_FILE" "$EC2_USER@$EC2_HOST" "docker-compose ps mysql"

# 3. Check container logs
ec2DbLogs

# 4. Restart containers if needed
ec2Restart
```

### Database Performance Issues
```bash
# Check slow queries
ec2DbQuery "SELECT * FROM mysql.slow_log ORDER BY start_time DESC LIMIT 10;"

# Check database locks
ec2DbQuery "SHOW OPEN TABLES WHERE In_use > 0;"

# Monitor memory usage
ssh -i "$EC2_PEM_FILE" "$EC2_USER@$EC2_HOST" "docker stats mysql --no-stream"
```

### Migration Issues

#### Migration Script Fails
```bash
# Check RDS connectivity first
mysql -h $RDS_ENDPOINT -u admin -p$RDS_PASSWORD -e "SELECT 1;"

# Check EC2 disk space
ssh -i "$EC2_PEM_FILE" "$EC2_USER@$EC2_HOST" "df -h"

# Check migration logs
ec2DbLogs 100
```

#### Data Validation After Migration
```bash
# Compare record counts
ec2DbQuery "SELECT 'content_collections', COUNT(*) FROM content_collection_entity UNION SELECT 'images', COUNT(*) FROM image_entity;"

# Check for recent data
ec2DbQuery "SELECT MAX(created_date) as latest_content FROM content_collection_entity;"
```

### Data Recovery

#### Restore from Backup
```bash
# 1. List available backups
ec2DbBackups

# 2. Create current state backup first
ec2DbBackup "before-restore-$(date +%Y%m%d_%H%M)"

# 3. Restore from backup
ec2DbRestore "backup_filename.sql.gz"

# 4. Verify restoration
ec2DbStatus
ec2DbQuery "SELECT COUNT(*) FROM content_collection_entity;"
```

#### Partial Data Recovery
```bash
# Download backup locally for examination
ec2DbDownload "backup_filename.sql.gz"

# Extract specific tables (locally)
gunzip -c backups/downloaded/backup_filename.sql.gz | grep -A 1000 "CREATE TABLE content_collection_entity" > partial_restore.sql

# Upload and restore specific data
scp -i "$EC2_PEM_FILE" partial_restore.sql "$EC2_USER@$EC2_HOST:~/portfolio-backend/repo/"
ec2DbQuery "source partial_restore.sql;"
```

## Security Considerations

### Database Access
- MySQL root password stored in EC2 `.env` file
- Application user `zedens` has full access to `edens_zac` database only
- Port 3306 exposed for local development (consider VPN for production)

### Backup Security
- Backups stored on EC2 instance (same security as your application)
- Consider encrypting sensitive backups
- Download critical backups to secure local storage

### Network Security
```bash
# Check EC2 security group settings
# Ensure port 3306 is restricted to your IP or VPN range
# Consider using SSH tunneling for additional security:
ssh -i "$EC2_PEM_FILE" -L 3306:localhost:3306 "$EC2_USER@$EC2_HOST"
```

## Monitoring & Maintenance

### Health Checks
```bash
# Daily health check routine
ec2DbStatus           # Database health
ec2Status            # Container status
ec2DbQuery "SELECT 1;" # Quick connectivity test
```

### Performance Monitoring
```bash
# Weekly performance check
ec2DbQuery "SHOW ENGINE INNODB STATUS\\G" | grep -A 5 "BACKGROUND THREAD"
ssh -i "$EC2_PEM_FILE" "$EC2_USER@$EC2_HOST" "docker stats mysql --no-stream"
```

### Maintenance Tasks
```bash
# Monthly maintenance
ec2DbBackup "monthly-$(date +%Y%m)"
ec2DbQuery "OPTIMIZE TABLE content_collection_entity, image_entity, catalog_entity;"
ec2DbQuery "ANALYZE TABLE content_collection_entity, image_entity, catalog_entity;"
```

## Cost Savings

### Before Migration
- **RDS db.t3.micro**: ~$12-25/month
- **Data transfer**: ~$1-3/month
- **Backup storage**: ~$1-2/month
- **Total**: ~$14-30/month

### After Migration
- **Additional EC2 resources**: ~$0-5/month (using existing instance)
- **Backup storage**: $0 (local to EC2)
- **Data transfer**: $0 (local connections)
- **Total**: ~$0-5/month

### **Annual Savings**: $168-300+

## Emergency Procedures

### Database Corruption
```bash
# 1. Stop application
ec2Restart

# 2. Check MySQL error logs
ec2DbLogs 200

# 3. Attempt repair
ec2DbShell
# Then: CHECK TABLE content_collection_entity;
# Then: REPAIR TABLE content_collection_entity;

# 4. If repair fails, restore from backup
ec2DbRestore "latest_good_backup.sql.gz"
```

### EC2 Instance Issues
```bash
# 1. Create immediate backup if possible
ec2DbBackup "emergency-$(date +%Y%m%d_%H%M%S)"

# 2. Download critical backups
ec2DbDownload "latest_backup.sql.gz"

# 3. If EC2 is lost, deploy to new instance:
# - Launch new EC2
# - Install Docker & Docker Compose
# - Deploy application with ec2DbSetup
# - Restore from downloaded backup
```

This guide provides comprehensive management of your EC2 MySQL database using the custom .zshrc functions, ensuring efficient development workflow and robust database operations.


aws ec2 describe-instances --query 'Reservations[].Instances[].[InstanceId,PublicIpAddress,ImageId,KeyName,State.Name]' --output table
--------------------------------------------------------------------------------------------------------
|                                           DescribeInstances                                          |
+---------------------+-----------------+------------------------+-------------------------+-----------+
|  InstanceId         | PublicIpAddress |  Image Id              |  KeyName                |  State.name  |
+---------------------+-----------------+------------------------+-------------------------+-----------+
|  i-0a125f53892b6f138|  35.164.195.142 |  ami-027951e78de46a00e |  portfolio-backend-key  |  running  |
+---------------------+-----------------+------------------------+-------------------------+-----------+

aws ec2-instance-connect send-ssh-public-key --instance-id i-0a125f53892b6f138 --instance-os-user ec2-user --ssh-public-key "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDWCt8jfXfF8J2iQH/6j7EjipDNP00bTfRAxoh5PJQjt1GS3SofO/5Erm9B4TDz4l5aQRYK249awb6oQkVMK6mvNn0EDl1YRYusuR7jRHQVkppJl9hABFAmCuDinq9g/33tGs3+XiQDKeK0rWmSWD9J/7Wo1PFDbsmph8vdLrEkaFEk2LQvk35CAFXH6/AavyEnhFHRkT4wsgn2rul73s6ulb6dqDxEDXHKadh9YOoyazO/2m2OE4RXnFa6YWbsgPE4DlDVJ51reCBk2MAJw4mULlpHgvOp3DfC1LaPGQMBwMdc0Pv8zPunhy7f0eJy6IFBMOlKvNvnJLmDFKMiZ6I/"

echo "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDWCt8jfXfF8J2iQH/6j7EjipDNP00bTfRAxoh5PJQjt1GS3SofO/5Erm9B4TDz4l5aQRYK249awb6oQkVMK6mvNn0EDl1YRYusuR7jRHQVkppJl9hABFAmCuDinq9g/33tGs3+XiQDKeK0rWmSWD9J/7Wo1PFDbsmph8vdLrEkaFEk2LQvk35CAFXH6/AavyEnhFHRkT4wsgn2rul73s6ulb6dqDxEDXHKadh9YOoyazO/2m2OE4RXnFa6YWbsgPE4DlDVJ51reCBk2MAJw4mULlpHgvOp3DfC1LaPGQMBwMdc0Pv8zPunhy7f0eJy6IFBMOlKvNvnJLmDFKMiZ6I/" >> ~/.ssh/authorized_keys chmod 600 ~/.ssh/authorized_keys
