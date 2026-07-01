-- Links a tag to the collection it was promoted into; nulled if that collection is deleted.
ALTER TABLE tag ADD COLUMN converted_collection_id BIGINT REFERENCES collection(id) ON DELETE SET NULL;
