-- V11: Fix dedupe index to compare on DATE only, not full TIMESTAMP.
-- Old records from pre-V7 have midnight timestamps; new EXIF parsing includes time.
-- Using DATE() ensures same-day captures with the same filename are treated as duplicates.

DROP INDEX IF EXISTS idx_content_image_dedupe;

CREATE UNIQUE INDEX idx_content_image_dedupe
    ON content_image (original_filename, CAST(capture_date AS DATE))
    WHERE original_filename IS NOT NULL AND capture_date IS NOT NULL;
