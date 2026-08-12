-- V55: widen role_collection.level for the collaborator tier. COLLABORATOR sits between CLIENT
-- and admin: curation edits on a collection (/api/edit/**) without membership, visibility, link,
-- or image-set control. ADMIN is deliberately absent -- it is a computed sentinel derived from
-- users.is_admin, never stored; this CHECK is the database backstop behind the API-level
-- rejection on the grant endpoint. No data migration; existing rows are untouched.
-- V45 declared the CHECK inline on the column, so Postgres auto-named it
-- role_collection_level_check (<table>_<column>_check).
BEGIN;

ALTER TABLE role_collection DROP CONSTRAINT IF EXISTS role_collection_level_check;
ALTER TABLE role_collection ADD CONSTRAINT role_collection_level_check
  CHECK (level IN ('GENERAL', 'CLIENT', 'COLLABORATOR'));

COMMIT;
