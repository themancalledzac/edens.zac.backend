-- V50: introduce is_client / is_blog booleans as the storage truth for what
-- CollectionType.CLIENT_GALLERY / BLOG mean today.
--
-- Dual-compat window: the legacy `type` column is NOT dropped or altered here. The
-- application keeps `type` and the new booleans in sync on every write.
--
-- PHASE 2 (this comment is currently the only record of it -- a tracking issue is
-- still to be opened). After the backend and then the frontend deploy and verify:
--   DROP:   collection.type (this migration's dual-compat reason for existing)
--   DELETE: CollectionTypeCompat and its test; the legacy `type` field on
--           CollectionModel / CollectionRequests.Create / CollectionRequests.Update /
--           SaveAsCollectionRequest / Records.CollectionList / Records.SiblingRow /
--           ContentModels.Collection; the `type` multipart param on the admin
--           create-collection endpoint.
--   RE-KEY: CollectionRepository.findNonEmptyOrderedByVisibilityIn's typeFilter and
--           the SyntheticCollectionResolver catalog entries that still pass a
--           CollectionType (notably /all-blogs, which must move to is_blog).
--   MOVE:   PORTFOLIO / ART_GALLERY grouping onto the label tags seeded below;
--           PARENT / HOME structure onto the collection graph and the home slug.
--
-- Audit queries -- run manually before deploying (see V15 for the same convention):
--
-- -- 1. Tag seeding collisions. A tag already named 'Art Gallery'/'Portfolio' with a
-- --    hand-edited slug is reused as-is (its slug is NOT rewritten -- see the note
-- --    above the seed below). A *converted* tag (saved as a collection) holding either
-- --    name or slug would shadow the tag view entirely (TagViewResolver returns empty
-- --    for converted tags); this migration cannot repair that, so check for it here.
-- SELECT id, tag_name, slug, converted_collection_id FROM tag
-- WHERE slug IN ('art-gallery','portfolio')
--    OR tag_name IN ('Art Gallery','Portfolio');
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
-- target abort V50 and block application boot), so no ON CONFLICT is used at all, and
-- WITHOUT mutating any existing row:
--   * the INSERT fires only when neither the name nor the canonical slug is taken, so
--     it can never violate either unique constraint;
--   * the attach resolves the label tag by tag_name FIRST, falling back to whichever
--     tag holds the canonical slug. Resolving by slug alone was the bug: a tag already
--     named 'Portfolio' with an operator-edited slug passes the slug guard, the
--     tag_name unique constraint rejects the INSERT, and the slug-join then attaches
--     zero rows while the migration reports success. Both subqueries return at most one
--     row (tag_name and slug are each UNIQUE), so there is no fan-out;
--   * the join-table inserts use NOT EXISTS instead of ON CONFLICT (collection_id, tag_id).
--
-- This migration deliberately does NOT rewrite an existing tag's slug: that would break
-- whatever public /{slug} tag-view URL the operator chose. Consequence for the tag-view
-- routes (amends 132-C11): the canonical /art-gallery and /portfolio routes only appear
-- when V50 creates the tag itself. If a differently-slugged tag already carries the name,
-- the label grouping is attached to it and its existing route keeps working. Moving it to
-- the canonical slug is a deliberate manual change, not a migration side effect.

INSERT INTO tag (tag_name, slug, created_at)
SELECT 'Art Gallery', 'art-gallery', NOW()
WHERE NOT EXISTS (SELECT 1 FROM tag WHERE tag_name = 'Art Gallery')
  AND NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'art-gallery');

INSERT INTO tag (tag_name, slug, created_at)
SELECT 'Portfolio', 'portfolio', NOW()
WHERE NOT EXISTS (SELECT 1 FROM tag WHERE tag_name = 'Portfolio')
  AND NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'portfolio');

INSERT INTO collection_tags (collection_id, tag_id)
SELECT c.id, t.id
FROM collection c
JOIN tag t ON t.id = COALESCE(
    (SELECT id FROM tag WHERE tag_name = 'Art Gallery'),
    (SELECT id FROM tag WHERE slug = 'art-gallery'))
WHERE c.type = 'ART_GALLERY'
  AND NOT EXISTS (SELECT 1 FROM collection_tags ct
                  WHERE ct.collection_id = c.id AND ct.tag_id = t.id);

INSERT INTO collection_tags (collection_id, tag_id)
SELECT c.id, t.id
FROM collection c
JOIN tag t ON t.id = COALESCE(
    (SELECT id FROM tag WHERE tag_name = 'Portfolio'),
    (SELECT id FROM tag WHERE slug = 'portfolio'))
WHERE c.type = 'PORTFOLIO'
  AND NOT EXISTS (SELECT 1 FROM collection_tags ct
                  WHERE ct.collection_id = c.id AND ct.tag_id = t.id);
