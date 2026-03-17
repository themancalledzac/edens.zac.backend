-- V6: Add missing indices for high-traffic query patterns

-- collection_content(collection_id) - hit on every collection page load
-- PostgreSQL does NOT auto-create indices on FK columns
CREATE INDEX IF NOT EXISTS idx_collection_content_collection_id ON collection_content(collection_id);

-- collection(slug) - primary public lookup key for collections
-- Using regular index (not UNIQUE) to avoid migration failure if duplicates exist
-- A UNIQUE constraint should be added separately after verifying data integrity
CREATE INDEX IF NOT EXISTS idx_collection_slug ON collection(slug);
