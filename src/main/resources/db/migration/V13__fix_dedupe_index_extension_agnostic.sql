-- V13: Make dedupe index extension-agnostic.
-- V4 migration populated original_filename from file_identifier, which used
-- the WebP filename (e.g. "DSC_6247.webp"). Disk re-uploads send the source
-- JPEG filename (e.g. "DSC_6247.jpg"). The old exact-match index prevented
-- the dedup query from finding existing records, so captureDate was never
-- updated from midnight.
--
-- Fix: normalize original_filename to strip the extension in the index,
-- and update existing .webp values to .jpg so they match future uploads.

-- Step 1: Normalize existing webp original_filenames to jpg
-- (these were migrated from file_identifier in V4 and are not the true source filenames)
UPDATE content_image
SET original_filename = REGEXP_REPLACE(original_filename, '\.webp$', '.jpg')
WHERE original_filename LIKE '%.webp';

-- Step 2: Rebuild the unique index using base filename (no extension)
DROP INDEX IF EXISTS idx_content_image_dedupe;

CREATE UNIQUE INDEX idx_content_image_dedupe
    ON content_image (REGEXP_REPLACE(original_filename, '\.[^.]+$', ''), CAST(capture_date AS DATE))
    WHERE original_filename IS NOT NULL AND capture_date IS NOT NULL;
