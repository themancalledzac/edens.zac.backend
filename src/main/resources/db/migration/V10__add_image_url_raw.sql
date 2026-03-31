-- Add column for RAW file S3 URL (nullable - only populated for Lightroom exports)
ALTER TABLE content_image ADD COLUMN image_url_raw VARCHAR(512);
