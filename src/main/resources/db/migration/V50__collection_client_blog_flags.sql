-- V50: introduce is_client / is_blog booleans as the storage truth for what
-- CollectionType.CLIENT_GALLERY / BLOG mean today.
--
-- Dual-compat window: the legacy `type` column is NOT dropped or altered here. The
-- application keeps `type` and the new booleans in sync on every write; a later
-- cleanup migration drops `type` after both deploys (BE then FE) verify.
--
-- Audit queries -- run manually before deploying (see V15 for the same convention):
--
-- -- 1. Tag seeding collisions: a tag already named 'Art Gallery'/'Portfolio' with a
-- --    hand-edited slug is repaired below; a *converted* tag (saved as a collection)
-- --    holding either slug would shadow the tag view entirely (TagViewResolver
-- --    returns empty for converted tags), which this migration cannot repair.
-- SELECT * FROM tag WHERE slug IN ('art-gallery','portfolio')
--                      OR tag_name IN ('Art Gallery','Portfolio');
--
-- -- 2. Scope of the display_mode backfill below (rows that would otherwise flip
-- --    ORDERED -> CHRONOLOGICAL on their next read):
-- SELECT count(*) FROM collection WHERE display_mode IS NULL AND type <> 'BLOG';

ALTER TABLE collection ADD COLUMN is_client BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE collection ADD COLUMN is_blog BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill from the legacy type column.
UPDATE collection
SET is_client = (type = 'CLIENT_GALLERY'),
    is_blog   = (type = 'BLOG');

-- Mutual exclusion is an invariant of the model, not just of CollectionTypeCompat.
-- The entity is @Data/@Builder with independently settable flags and the repository
-- full-row-writes whatever it holds, so the database is the backstop. Deliberately NOT
-- a full type/flag sync CHECK: integration fixtures and the phase-2 window both allow
-- type and flags to diverge.
ALTER TABLE collection
  ADD CONSTRAINT chk_collection_client_blog_excl CHECK (NOT (is_client AND is_blog));

-- display_mode backfill. The application fallback for a NULL display_mode is now
-- unconditionally CHRONOLOGICAL (previously BLOG ? CHRONOLOGICAL : ORDERED), so every
-- existing non-blog collection that never had one stored would silently reorder on its
-- next read. Writing ORDERED here keeps stored behaviour identical across the deploy;
-- the new fallback then applies only to rows created from now on.
UPDATE collection
SET display_mode = 'ORDERED'
WHERE display_mode IS NULL
  AND type <> 'BLOG';

-- Label-tag backfill: ART_GALLERY / PORTFOLIO grouping must survive the eventual
-- type drop, so each such collection gets a label tag. MISC gets nothing (untagged
-- IS its meaning); PARENT/HOME get nothing (the graph/slug carry them).
--
-- Idempotent + collision-safe without depending on any constraint of the pre-Flyway
-- `tag` / `collection_tags` tables (a missing unique index would make an ON CONFLICT
-- target abort V50 and block application boot), so no ON CONFLICT is used at all:
--   * the UPDATE repairs a tag that already carries the name but drifted off the slug
--     -- otherwise its slug guard passes, the name unique constraint rejects the
--     INSERT, and the slug-join below silently attaches zero rows;
--   * the INSERT fires only when neither the slug nor the name exists;
--   * the join-table inserts use NOT EXISTS instead of ON CONFLICT (collection_id, tag_id).

UPDATE tag SET slug = 'art-gallery'
WHERE tag_name = 'Art Gallery'
  AND slug IS DISTINCT FROM 'art-gallery'
  AND NOT EXISTS (SELECT 1 FROM tag t2 WHERE t2.slug = 'art-gallery');

INSERT INTO tag (tag_name, slug, created_at)
SELECT 'Art Gallery', 'art-gallery', NOW()
WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'art-gallery')
  AND NOT EXISTS (SELECT 1 FROM tag WHERE tag_name = 'Art Gallery');

UPDATE tag SET slug = 'portfolio'
WHERE tag_name = 'Portfolio'
  AND slug IS DISTINCT FROM 'portfolio'
  AND NOT EXISTS (SELECT 1 FROM tag t2 WHERE t2.slug = 'portfolio');

INSERT INTO tag (tag_name, slug, created_at)
SELECT 'Portfolio', 'portfolio', NOW()
WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'portfolio')
  AND NOT EXISTS (SELECT 1 FROM tag WHERE tag_name = 'Portfolio');

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
