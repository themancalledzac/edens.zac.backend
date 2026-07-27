-- V51: migration prep for the typeless collection (unit U0).

-- 1. Let the application stop writing `type` (U4) while the column still physically exists.
--    DEFAULT 'MISC' covers any INSERT that omits the column; DROP NOT NULL covers any INSERT
--    that passes it explicitly as NULL. Both directions of a U4 rollback stay safe.
ALTER TABLE collection
  ALTER COLUMN type DROP NOT NULL,
  ALTER COLUMN type SET DEFAULT 'MISC';

-- 2. The only rollback artifact that survives U5's `ALTER TABLE collection DROP COLUMN type`.
--    PORTFOLIO / ART_GALLERY / PARENT / HOME / MISC are all is_client = false, is_blog = false,
--    so they are NOT reconstructable from the flags once the column is gone. One row per
--    collection; keep this table indefinitely.
CREATE TABLE collection_type_archive AS
SELECT id, slug, type, is_client, is_blog, now() AS archived_at FROM collection;
