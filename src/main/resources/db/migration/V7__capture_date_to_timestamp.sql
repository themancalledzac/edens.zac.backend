-- V7: Widen capture_date from DATE to TIMESTAMP to preserve EXIF time component.
-- Existing rows will retain their date with a midnight time (00:00:00), as the time component
-- was never stored. A re-import of existing images is needed to recover precise capture times.

-- Drop indexes that depend on the column type before altering
DROP INDEX IF EXISTS idx_content_image_dedupe;
DROP INDEX IF EXISTS idx_content_image_capture_date;

-- Change column type; PostgreSQL auto-casts existing DATE values to midnight TIMESTAMP
ALTER TABLE content_image
    ALTER COLUMN capture_date TYPE TIMESTAMP
    USING capture_date::TIMESTAMP;

-- Recreate deduplication index (now deduplicates per exact second, not per day)
CREATE UNIQUE INDEX IF NOT EXISTS idx_content_image_dedupe
    ON content_image (original_filename, capture_date)
    WHERE original_filename IS NOT NULL AND capture_date IS NOT NULL;

-- Recreate performance index
CREATE INDEX IF NOT EXISTS idx_content_image_capture_date
    ON content_image (capture_date);
