-- V51: migration prep for the typeless collection (unit U0).
--
-- No application change ships with this migration and no Java is touched by it. It is fully
-- reversible: `collection.type` keeps every value it holds today, it merely stops being
-- mandatory, and collection_type_archive preserves those values independently.
--
-- What this enables:
--   1. U4's application can stop writing collection.type while the column still physically
--      exists (nullable + DEFAULT 'MISC'), so U4 and a U4 rollback are both safe.
--   2. U5 can DROP the column without destroying data. PORTFOLIO / ART_GALLERY / PARENT /
--      HOME / MISC are all is_client = false, is_blog = false, so they are NOT reconstructable
--      from the flags. collection_type_archive is the only surviving rollback artifact -- one
--      row per collection, keep it indefinitely.
--   3. D1: /home stops taking the unpaginated COLLECTION-only read branch
--      (CollectionService.java:130-141), so every row needs a usable content_per_page.
--   4. D6: V50's PORTFOLIO / ART_GALLERY label tags are removed. That grouping is deleted,
--      not converted to tags, and TagViewResolver turns any tag slug with >=1 LISTED member
--      into a live public page -- so leaving them behind leaves orphaned public routes.
--
-- Deliberately NOT here: any DROP INDEX. V9 lines 8-10 created idx_collection_type_visible_date
-- with a `WHERE visible = true` predicate, and V20 dropped the `visible` column, which
-- auto-dropped that index. A bare DROP INDEX of that name would error with "index does not
-- exist", abort this migration mid-way, and (spring.flyway.enabled=true,
-- application.properties:36) block application boot on every subsequent start until Flyway is
-- manually repaired. U5's DROP COLUMN auto-drops anything still depending on the column.
--
-- Audit queries -- run manually before deploying (see V15 lines 5-12 and V50 lines 21-34 for
-- the same convention):
--
-- -- 1. Shape of the data being collapsed. Every row here is about to lose its `type` and keep
-- --    only is_client / is_blog. A CLIENT_GALLERY row with is_client = false, or a BLOG row
-- --    with is_blog = false, is a divergent row that U4 would silently demote -- fix it first.
-- SELECT type, is_client, is_blog, count(*) FROM collection GROUP BY 1,2,3 ORDER BY 1;
--
-- -- 2. The population that becomes ineligible for updateGalleryAccess under any isClient-only
-- --    re-key. If this returns rows, U2's derived eligibility
-- --    (isClient() || hasClientGalleryChildren(id)) MUST cover them before U4 ships.
-- SELECT id, slug, type FROM collection
-- WHERE gallery_password IS NOT NULL AND is_client = false;
--
-- -- 3. The rows whose structural role becomes derived rather than stored. Expect exactly one
-- --    row at slug = 'home' plus the staging wrapper; anything else is a row that silently
-- --    becomes an ordinary collection at U5, losing the slug='home' visibility bypass.
-- SELECT id, slug, type, visibility, content_per_page FROM collection
-- WHERE type IN ('HOME','PARENT');
--
-- -- 4. Scope of the content_per_page backfill in step 3 below.
-- SELECT count(*), type FROM collection WHERE content_per_page IS NULL GROUP BY type;
--
-- -- 5. Tag-deletion collisions (this is V50 lines 28-30, re-run). A row that is canonical in
-- --    BOTH tag_name and slug with converted_collection_id IS NULL is deleted by step 4b --
-- --    that is the shape V50 creates. A row matching on only one of the two was REUSED by V50:
-- --    step 4a detaches its collections and the tag survives. A converted tag is left alone.
-- --    A both-canonical tag that predates V50 is indistinguishable in SQL from a V50-created
-- --    one, so this query is the only thing that can tell you -- run it.
-- SELECT id, tag_name, slug, converted_collection_id FROM tag
-- WHERE slug IN ('art-gallery','portfolio')
--    OR tag_name IN ('Art Gallery','Portfolio');

-- 1. Let the application stop writing `type` (U4) while the column still physically exists.
--    DEFAULT 'MISC' covers any INSERT that omits the column; DROP NOT NULL covers any INSERT
--    that passes it explicitly as NULL. Both directions of a U4 rollback stay safe.
ALTER TABLE collection
  ALTER COLUMN type DROP NOT NULL,
  ALTER COLUMN type SET DEFAULT 'MISC';

-- 2. The only rollback artifact that survives U5's `ALTER TABLE collection DROP COLUMN type`.
--    PORTFOLIO / ART_GALLERY / PARENT / HOME / MISC are all is_client = false, is_blog = false,
--    so they are NOT reconstructable from the flags once the column is gone. One row per
--    collection; keep this table indefinitely.
CREATE TABLE collection_type_archive AS
SELECT id, slug, type, is_client, is_blog, now() AS archived_at FROM collection;

-- 3. D1 backfill. 30 is DefaultValues.default_content_per_page (config/DefaultValues.java:6),
--    the same value applyPaginationDefaults writes on the create path. The rows holding NULL
--    are exactly the parent-type ones (CollectionProcessingUtil.java:584-587) -- `home`,
--    `staging` and every PARENT -- and applyPaginationDefaults is reachable only from
--    toEntity (:594), so nothing repairs them until someone happens to re-save them.
--
--    rows_wide is deliberately NOT backfilled, and the asymmetry is structural, not stylistic:
--    applyPaginationDefaults (CollectionProcessingUtil.java:930-943) fills only contentPerPage,
--    and no backend read consumes rows_wide at all -- it is written and echoed back
--    (CollectionRepository.java:604, :649; CollectionEntity.java:87) and nothing else. A NULL
--    content_per_page, by contrast, makes CollectionEntity.getTotalPages() (:106-114) return 0
--    via its null arm at :109-111, on the LIMIT/OFFSET read D1 routes every collection onto.
UPDATE collection SET content_per_page = 30 WHERE content_per_page IS NULL;

-- 4a. D6: detach the label-tag memberships V50 seeded (V50 lines 97-115). A tag the operator
--     has converted into a collection (V39's converted_collection_id) is skipped entirely --
--     TagViewResolver returns empty for a converted tag, so it is not an orphaned route, and
--     its memberships are the operator's own data.
DELETE FROM collection_tags
WHERE tag_id IN (
    SELECT id FROM tag
    WHERE converted_collection_id IS NULL
      AND (slug IN ('art-gallery', 'portfolio')
           OR tag_name IN ('Art Gallery', 'Portfolio')));

-- 4b. D6: delete only the tags V50 itself created. V50 inserted a tag only when NEITHER the
--     canonical name NOR the canonical slug was taken (V50 lines 87-95), so a V50-created tag
--     is exactly one holding both. A tag matching on only one of the two was a pre-existing
--     operator tag that V50 reused: 4a has already removed the label grouping from it, and the
--     tag survives with whatever public route the operator chose. The NOT EXISTS guards make
--     this a no-op for any tag that still carries memberships of its own.
--
--     A tag that predates V50 while holding BOTH canonical values is indistinguishable from a
--     V50-created one; audit query 5 in the header is the only way to detect that case, which
--     is why it must be run before this migration ships.
DELETE FROM tag t
WHERE t.converted_collection_id IS NULL
  AND ((t.tag_name = 'Art Gallery' AND t.slug = 'art-gallery')
       OR (t.tag_name = 'Portfolio' AND t.slug = 'portfolio'))
  AND NOT EXISTS (SELECT 1 FROM collection_tags ct WHERE ct.tag_id = t.id)
  AND NOT EXISTS (SELECT 1 FROM content_tags ct WHERE ct.tag_id = t.id);
