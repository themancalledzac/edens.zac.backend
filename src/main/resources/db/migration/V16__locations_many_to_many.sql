-- Migration: V16__locations_many_to_many
-- Description: Convert locations from single FK to many-to-many relationship
-- on both collections and content_image tables.
-- Creates join tables, migrates existing data, and drops old FK columns.

BEGIN;

-- Step 1: Create join tables

CREATE TABLE collection_locations (
    collection_id BIGINT NOT NULL REFERENCES collection(id) ON DELETE CASCADE,
    location_id BIGINT NOT NULL REFERENCES location(id) ON DELETE CASCADE,
    PRIMARY KEY (collection_id, location_id)
);

CREATE TABLE content_image_locations (
    image_id BIGINT NOT NULL REFERENCES content_image(id) ON DELETE CASCADE,
    location_id BIGINT NOT NULL REFERENCES location(id) ON DELETE CASCADE,
    PRIMARY KEY (image_id, location_id)
);

-- Step 2: Create indexes (mirrors content_image_people / collection_people pattern)

CREATE INDEX idx_collection_locations_collection_id ON collection_locations(collection_id);
CREATE INDEX idx_collection_locations_location_id ON collection_locations(location_id);
CREATE INDEX idx_content_image_locations_image_id ON content_image_locations(image_id);
CREATE INDEX idx_content_image_locations_location_id ON content_image_locations(location_id);

-- Step 3: Migrate existing FK data into join tables

INSERT INTO collection_locations (collection_id, location_id)
SELECT id, location_id FROM collection WHERE location_id IS NOT NULL;

INSERT INTO content_image_locations (image_id, location_id)
SELECT id, location_id FROM content_image WHERE location_id IS NOT NULL;

-- Step 4: Drop old FK constraints and columns

ALTER TABLE collection DROP CONSTRAINT IF EXISTS fk_collection_location;
ALTER TABLE collection DROP COLUMN location_id;

ALTER TABLE content_image DROP CONSTRAINT IF EXISTS fk_content_image_location;
ALTER TABLE content_image DROP COLUMN location_id;

COMMIT;
