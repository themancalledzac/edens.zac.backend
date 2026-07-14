-- V43: Add an optional end date to collection so a collection can span a date RANGE
-- (e.g. a multi-day trip). collection_date remains the start; collection_end_date is
-- the inclusive end. NULL end date means a single-day / open collection (unchanged
-- behavior). No backfill — existing rows keep collection_end_date = NULL.
ALTER TABLE collection ADD COLUMN collection_end_date DATE NULL;
