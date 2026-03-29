-- ============================================================================
-- V9: Read Performance Indexes
-- Adds missing indexes identified during read performance audit (2026-03-28)
-- ============================================================================

-- Composite index for collection listings by type (most frequent read query)
-- Covers: findByTypeAndVisibleTrueOrderByCollectionDateDesc
CREATE INDEX IF NOT EXISTS idx_collection_type_visible_date
  ON collection (type, collection_date DESC NULLS LAST)
  WHERE visible = true;

-- Reverse lookup on collection_content for orphan exclusion and membership checks
-- Covers: findContentByContentIdsIn, orphan image NOT EXISTS subquery
CREATE INDEX IF NOT EXISTS idx_collection_content_content_id
  ON collection_content (content_id);

-- Join table indexes (currently missing entirely)
CREATE INDEX IF NOT EXISTS idx_collection_tags_collection_id ON collection_tags (collection_id);
CREATE INDEX IF NOT EXISTS idx_collection_tags_tag_id ON collection_tags (tag_id);
CREATE INDEX IF NOT EXISTS idx_collection_people_collection_id ON collection_people (collection_id);
CREATE INDEX IF NOT EXISTS idx_collection_people_person_id ON collection_people (person_id);

-- Partial index for visible collection slug lookups
CREATE INDEX IF NOT EXISTS idx_collection_slug_visible
  ON collection (slug)
  WHERE visible = true;

-- Functional indexes for case-insensitive lookups (used during uploads)
CREATE INDEX IF NOT EXISTS idx_location_name_lower ON location (LOWER(location_name));
CREATE INDEX IF NOT EXISTS idx_tag_name_lower ON tag (LOWER(tag_name));
CREATE INDEX IF NOT EXISTS idx_person_name_lower ON content_people (LOWER(person_name));
