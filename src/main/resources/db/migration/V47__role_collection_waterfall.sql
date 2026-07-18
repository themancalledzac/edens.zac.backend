-- V47: waterfall role grants. Adds provenance to role_collection so a grant on a PARENT
-- collection can be materialized onto every descendant as a real row:
--   inherited_from_collection_id IS NULL -> direct grant (the unchanged meaning of every
--                                           pre-V47 row; this migration leaves them all NULL);
--   inherited_from_collection_id = X     -> inherited copy whose ORIGIN (the collection that
--                                           holds the direct grant) is X. Storing the origin --
--                                           not the immediate parent -- makes removal a single
--                                           DELETE ... WHERE inherited_from_collection_id = X.
-- The resolution queries (RoleRepository.canView/isClient/memberCollectionIdsForUser/
-- effectiveGrants) are untouched by design: inherited grants are real rows, so reads stay flat.
BEGIN;

ALTER TABLE role_collection
  ADD COLUMN inherited_from_collection_id BIGINT NULL
    REFERENCES collection(id) ON DELETE CASCADE;
CREATE INDEX idx_role_collection_inherited_from
  ON role_collection(inherited_from_collection_id);

-- One-time backfill: materialize every existing direct grant down its collection tree.
-- The hierarchy is a content graph: parent -> collection_content (visible links only, matching
-- runtime propagation) -> content_collection -> child collection. UNION (not UNION ALL) drops
-- revisited rows, so accidental cycles terminate. When a descendant is reachable from origins
-- at different levels, CLIENT wins; a descendant that already has its own direct grant for the
-- role is left untouched (direct wins).
WITH RECURSIVE descendant AS (
  SELECT rc.role_id,
         rc.collection_id AS origin_id,
         rc.level,
         cct.referenced_collection_id AS descendant_id
    FROM role_collection rc
    JOIN collection_content cc ON cc.collection_id = rc.collection_id AND cc.visible = true
    JOIN content_collection cct ON cct.id = cc.content_id
   WHERE rc.inherited_from_collection_id IS NULL
     AND cct.referenced_collection_id IS NOT NULL
  UNION
  SELECT d.role_id, d.origin_id, d.level, cct.referenced_collection_id
    FROM descendant d
    JOIN collection_content cc ON cc.collection_id = d.descendant_id AND cc.visible = true
    JOIN content_collection cct ON cct.id = cc.content_id
   WHERE cct.referenced_collection_id IS NOT NULL
)
INSERT INTO role_collection (role_id, collection_id, level, inherited_from_collection_id)
SELECT DISTINCT ON (d.role_id, d.descendant_id)
       d.role_id, d.descendant_id, d.level, d.origin_id
  FROM descendant d
 WHERE NOT EXISTS (SELECT 1 FROM role_collection rc
                    WHERE rc.role_id = d.role_id AND rc.collection_id = d.descendant_id)
 ORDER BY d.role_id, d.descendant_id, (d.level = 'CLIENT') DESC
ON CONFLICT (role_id, collection_id) DO NOTHING;

COMMIT;
