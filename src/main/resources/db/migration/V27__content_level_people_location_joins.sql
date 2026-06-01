-- Generalize the people + location join tables from image-specific to content-level.
-- The tables are V16-managed (Flyway baseline); their image_id values are already the shared
-- content.id, so this is an in-place column rename + FK retarget, not a data move. The dynamic
-- pg_constraint lookup is required because the original FK name is auto-generated and not stable.
-- Retargeting the FK to content(id) lets GIF (and any content type) use these joins.

-- ---- content_image_locations ----
ALTER TABLE content_image_locations RENAME COLUMN image_id TO content_id;

DO $$
DECLARE fk_name text;
BEGIN
  SELECT conname INTO fk_name FROM pg_constraint
   WHERE conrelid = 'content_image_locations'::regclass AND contype = 'f'
     AND confrelid = 'content_image'::regclass;
  IF fk_name IS NOT NULL THEN
    EXECUTE 'ALTER TABLE content_image_locations DROP CONSTRAINT ' || quote_ident(fk_name);
  END IF;
END $$;

ALTER TABLE content_image_locations
  ADD CONSTRAINT content_image_locations_content_id_fkey
  FOREIGN KEY (content_id) REFERENCES content(id) ON DELETE CASCADE;

ALTER INDEX IF EXISTS idx_content_image_locations_image_id
  RENAME TO idx_content_image_locations_content_id;

-- ---- content_image_people ----
ALTER TABLE content_image_people RENAME COLUMN image_id TO content_id;

DO $$
DECLARE fk_name text;
BEGIN
  SELECT conname INTO fk_name FROM pg_constraint
   WHERE conrelid = 'content_image_people'::regclass AND contype = 'f'
     AND confrelid = 'content_image'::regclass;
  IF fk_name IS NOT NULL THEN
    EXECUTE 'ALTER TABLE content_image_people DROP CONSTRAINT ' || quote_ident(fk_name);
  END IF;
END $$;

ALTER TABLE content_image_people
  ADD CONSTRAINT content_image_people_content_id_fkey
  FOREIGN KEY (content_id) REFERENCES content(id) ON DELETE CASCADE;

ALTER INDEX IF EXISTS idx_content_image_people_image_id
  RENAME TO idx_content_image_people_content_id;
