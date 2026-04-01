-- V14: Ensure capture_date is TIMESTAMP, not DATE.
-- V7 was supposed to widen this column, but Flyway's baseline-version=7 caused
-- it to be skipped.  The column has remained DATE, silently truncating all
-- timestamps to midnight.
-- This ALTER is idempotent: if the column is already TIMESTAMP it is a no-op.

ALTER TABLE content_image
    ALTER COLUMN capture_date TYPE TIMESTAMP
    USING capture_date::TIMESTAMP;
