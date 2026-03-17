-- V5: Add password_hash to collections and performance indices for search
-- password_hash: enables client gallery password protection
-- indices: optimize multi-dimensional image search joins

-- Step 1: Add password_hash column for client gallery protection
ALTER TABLE collection ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

-- Step 2: Performance indices on join table foreign keys
-- PostgreSQL does NOT auto-create indices on FK columns
CREATE INDEX IF NOT EXISTS idx_content_tags_tag_id ON content_tags(tag_id);
CREATE INDEX IF NOT EXISTS idx_content_tags_content_id ON content_tags(content_id);
CREATE INDEX IF NOT EXISTS idx_content_image_people_person_id ON content_image_people(person_id);
CREATE INDEX IF NOT EXISTS idx_content_image_people_image_id ON content_image_people(image_id);

-- Step 3: Performance indices on content_image filter columns
CREATE INDEX IF NOT EXISTS idx_content_image_camera_id ON content_image(camera_id);
CREATE INDEX IF NOT EXISTS idx_content_image_lens_id ON content_image(lens_id);
CREATE INDEX IF NOT EXISTS idx_content_image_location_id ON content_image(location_id);
CREATE INDEX IF NOT EXISTS idx_content_image_rating ON content_image(rating);
CREATE INDEX IF NOT EXISTS idx_content_image_capture_date ON content_image(capture_date);

-- Step 4: Index on collection.location_id for location page queries
CREATE INDEX IF NOT EXISTS idx_collection_location_id ON collection(location_id);
