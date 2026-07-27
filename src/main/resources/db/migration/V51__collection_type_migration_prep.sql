-- V51: migration prep for the typeless collection (unit U0).

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
