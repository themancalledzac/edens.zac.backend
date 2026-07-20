-- V49: introduce is_client / is_blog booleans as the storage truth for what
-- CollectionType.CLIENT_GALLERY / BLOG mean today.
--
-- Dual-compat window: the legacy `type` column is NOT dropped or altered here. The
-- application keeps `type` and the new booleans in sync on every write; a later
-- cleanup migration drops `type` after both deploys (BE then FE) verify.

ALTER TABLE collection ADD COLUMN is_client BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE collection ADD COLUMN is_blog BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill from the legacy type column.
UPDATE collection
SET is_client = (type = 'CLIENT_GALLERY'),
    is_blog   = (type = 'BLOG');

-- Label-tag backfill: ART_GALLERY / PORTFOLIO grouping must survive the eventual
-- type drop, so each such collection gets a label tag. MISC gets nothing (untagged
-- IS its meaning); PARENT/HOME get nothing (the graph/slug carry them).
--
-- Idempotent + collision-safe: tag inserts are guarded on slug existence (tag.slug
-- has a unique index, idx_tag_slug from V8) with ON CONFLICT DO NOTHING as a final
-- guard against any concurrent/unique-name collision; join-table inserts rely on
-- the (collection_id, tag_id) primary key of collection_tags.

INSERT INTO tag (tag_name, slug, created_at)
SELECT 'Art Gallery', 'art-gallery', NOW()
WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'art-gallery')
ON CONFLICT DO NOTHING;

INSERT INTO tag (tag_name, slug, created_at)
SELECT 'Portfolio', 'portfolio', NOW()
WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'portfolio')
ON CONFLICT DO NOTHING;

INSERT INTO collection_tags (collection_id, tag_id)
SELECT c.id, t.id
FROM collection c
JOIN tag t ON t.slug = 'art-gallery'
WHERE c.type = 'ART_GALLERY'
ON CONFLICT (collection_id, tag_id) DO NOTHING;

INSERT INTO collection_tags (collection_id, tag_id)
SELECT c.id, t.id
FROM collection c
JOIN tag t ON t.slug = 'portfolio'
WHERE c.type = 'PORTFOLIO'
ON CONFLICT (collection_id, tag_id) DO NOTHING;
