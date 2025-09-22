-- MySQL Database Initialization Script for Edens.Zac Portfolio Backend
-- This script runs when the MySQL container is first created

-- Create the main database if it doesn't exist
CREATE DATABASE IF NOT EXISTS edens_zac;

-- Use the database
USE edens_zac;

-- Grant all privileges to the application user
-- Allow connections from anywhere (for local development access)
GRANT ALL PRIVILEGES ON edens_zac.* TO 'zedens'@'%';
GRANT ALL PRIVILEGES ON edens_zac.* TO 'zedens'@'localhost';
FLUSH PRIVILEGES;

-- Basic optimization settings for small to medium workloads
SET GLOBAL innodb_buffer_pool_size = 128M;
SET GLOBAL max_connections = 100;

-- Create a simple health check table for monitoring
CREATE TABLE IF NOT EXISTS health_check (
    id INT PRIMARY KEY,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO health_check (id) VALUES (1) ON DUPLICATE KEY UPDATE last_updated = NOW();