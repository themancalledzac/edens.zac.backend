-- V4: Replace file_identifier with smart deduplication fields
-- Adds capture_date, last_export_date, original_filename for dedupe logic.
-- Drops file_identifier (superseded by original_filename + capture_date).
-- Drops create_date VARCHAR (superseded by capture_date DATE).

-- Step 1: Add new columns
ALTER TABLE content_image ADD COLUMN IF NOT EXISTS capture_date DATE;
ALTER TABLE content_image ADD COLUMN IF NOT EXISTS last_export_date TIMESTAMP;
ALTER TABLE content_image ADD COLUMN IF NOT EXISTS original_filename VARCHAR(512);

-- Step 2: Migrate existing data
-- Populate capture_date from create_date (EXIF string format "YYYY:MM:DD HH:MM:SS" or "YYYY-MM-DD")
UPDATE content_image
SET capture_date = CASE
    WHEN create_date IS NOT NULL AND create_date ~ '^\d{4}[:\-]\d{2}[:\-]\d{2}'
        THEN TO_DATE(REPLACE(SUBSTRING(create_date FROM 1 FOR 10), ':', '-'), 'YYYY-MM-DD')
    ELSE NULL
END
WHERE capture_date IS NULL;

-- Populate original_filename from file_identifier (format: "YYYY-MM/filename.jpg" or deeper paths)
UPDATE content_image
SET original_filename = CASE
    WHEN file_identifier IS NOT NULL AND file_identifier LIKE '%/%'
        THEN SUBSTRING(file_identifier FROM '([^/]+)$')
    ELSE file_identifier
END
WHERE original_filename IS NULL;

-- Step 3: Drop superseded columns
ALTER TABLE content_image DROP COLUMN IF EXISTS create_date;
ALTER TABLE content_image DROP COLUMN IF EXISTS file_identifier;

-- Step 4: Create partial unique index for deduplication
-- Only enforces uniqueness where both fields are non-null
CREATE UNIQUE INDEX IF NOT EXISTS idx_content_image_dedupe
    ON content_image (original_filename, capture_date)
    WHERE original_filename IS NOT NULL AND capture_date IS NOT NULL;
