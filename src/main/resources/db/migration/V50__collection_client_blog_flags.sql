-- V50: introduce is_client / is_blog booleans as the storage truth for what
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
-- Idempotent + collision-safe, without depending on any constraint of the pre-Flyway
-- `collection_tags` table: the join-table inserts use NOT EXISTS rather than
-- ON CONFLICT (collection_id, tag_id), which would require a unique index that no
-- migration declares (a missing index would abort V50 and block application boot).
-- Tag inserts are guarded on slug existence (tag.slug has a unique index, idx_tag_slug
-- from V8) with ON CONFLICT DO NOTHING as a final guard against a unique-name collision.

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
  AND NOT EXISTS (SELECT 1 FROM collection_tags ct
                  WHERE ct.collection_id = c.id AND ct.tag_id = t.id);

INSERT INTO collection_tags (collection_id, tag_id)
SELECT c.id, t.id
FROM collection c
JOIN tag t ON t.slug = 'portfolio'
WHERE c.type = 'PORTFOLIO'
  AND NOT EXISTS (SELECT 1 FROM collection_tags ct
                  WHERE ct.collection_id = c.id AND ct.tag_id = t.id);
