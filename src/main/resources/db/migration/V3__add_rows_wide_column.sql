-- Migration: V3__add_rows_wide_column
-- Description: Add rows_wide column to collection table
--
-- This column controls the number of items per row in the collection layout.
-- NULL value uses the default chunk size (4).

BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'collection' AND column_name = 'rows_wide'
    ) THEN
        ALTER TABLE collection ADD COLUMN rows_wide INT;
        COMMENT ON COLUMN collection.rows_wide IS 'Number of items per row (chunk size for layout). NULL uses default (4).';
    END IF;
END $$;

COMMIT;
