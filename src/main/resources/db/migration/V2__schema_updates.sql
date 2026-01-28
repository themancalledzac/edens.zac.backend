-- Migration: V2__schema_updates
-- Description: V2 schema updates - table structure changes only
-- 
-- This file contains all V2 schema changes. When adding new V2 changes:
-- 1. Add a comment describing the change above
-- 2. Add the SQL statements within the BEGIN/COMMIT block
-- 3. Update the "Changes included in V2" list below
-- 
-- Note: This migration is for a clean database with no existing data.
-- No data migration is included - only schema structure changes.
-- 
-- Changes included in V2:
-- 1. Ensure location table exists (may already exist in production)
-- 2. Add location_id column to collection table (if it doesn't exist)
-- 3. Add location_id column to content_image table (if it doesn't exist)
-- 4. Add foreign key constraint from collection.location_id to location.id
-- 5. Add foreign key constraint from content_image.location_id to location.id
-- 6. Remove legacy location VARCHAR column from collection table
-- 7. Remove legacy location TEXT column from content_image table
-- 8. Add body_serial_number column to content_cameras table (if it doesn't exist)
-- 9. Add lens_serial_number column to content_lenses table (if it doesn't exist)

BEGIN;

-- ============================================================================
-- SCHEMA CHANGES
-- ============================================================================

-- Step 1: Ensure location table exists (may already exist in production)
-- This is idempotent - will not fail if table already exists
CREATE SEQUENCE IF NOT EXISTS location_id_seq;

CREATE TABLE IF NOT EXISTS location (
    id BIGINT PRIMARY KEY DEFAULT nextval('location_id_seq'),
    location_name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_location_name ON location(location_name);

COMMENT ON TABLE location IS 'Geographic locations that can be associated with collections and content images';

-- Step 2: Add location_id column to collection table (if it doesn't exist)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'collection' AND column_name = 'location_id'
    ) THEN
        ALTER TABLE collection ADD COLUMN location_id BIGINT;
    END IF;
END $$;

-- Step 3: Add location_id column to content_image table (if it doesn't exist)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'content_image' AND column_name = 'location_id'
    ) THEN
        ALTER TABLE content_image ADD COLUMN location_id BIGINT;
    END IF;
END $$;

-- Step 4: Add foreign key constraint from collection.location_id to location.id
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_collection_location'
    ) THEN
        ALTER TABLE collection
            ADD CONSTRAINT fk_collection_location
            FOREIGN KEY (location_id) REFERENCES location(id);
    END IF;
END $$;

-- Step 5: Add foreign key constraint from content_image.location_id to location.id
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_content_image_location'
    ) THEN
        ALTER TABLE content_image
            ADD CONSTRAINT fk_content_image_location
            FOREIGN KEY (location_id) REFERENCES location(id);
    END IF;
END $$;

-- Step 6: Remove legacy location VARCHAR column from collection table
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'collection' AND column_name = 'location'
    ) THEN
        ALTER TABLE collection DROP COLUMN location;
    END IF;
END $$;

-- Step 7: Remove legacy location TEXT column from content_image table
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'content_image' AND column_name = 'location'
    ) THEN
        ALTER TABLE content_image DROP COLUMN location;
    END IF;
END $$;

-- Step 8: Add body_serial_number column to content_cameras table (if it doesn't exist)
-- This column is used internally for camera deduplication during image uploads
-- but is NOT exposed in API responses
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'content_cameras' AND column_name = 'body_serial_number'
    ) THEN
        ALTER TABLE content_cameras ADD COLUMN body_serial_number VARCHAR(50);
    END IF;
END $$;

-- Step 9: Add lens_serial_number column to content_lenses table (if it doesn't exist)
-- This column is used internally for lens deduplication during image uploads
-- but is NOT exposed in API responses
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'content_lenses' AND column_name = 'lens_serial_number'
    ) THEN
        ALTER TABLE content_lenses ADD COLUMN lens_serial_number VARCHAR(50);
    END IF;
END $$;

-- Step 10: Remove offset_time column from content_image table (if it exists)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'content_image' AND column_name = 'offset_time'
    ) THEN
        ALTER TABLE content_image DROP COLUMN offset_time;
    END IF;
END $$;

COMMIT;
