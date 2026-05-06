-- V20: Replace boolean collection.visible with a 3-state visibility column.
-- LISTED   = appears in list endpoints, searchable, allowed as visible child collection.
-- UNLISTED = direct slug access only (still requires password if password_hash set).
-- HIDDEN   = dev environment only; returns 404 in non-local requests.

ALTER TABLE collection
    ADD COLUMN visibility VARCHAR(16);

-- Migrate existing data:
--   visible=true  AND type=CLIENT_GALLERY -> UNLISTED  (clients should not be in public lists)
--   visible=true  AND any other type      -> LISTED
--   visible=false                          -> UNLISTED (preserves "still reachable by slug" behavior)
UPDATE collection
SET visibility = CASE
    WHEN visible = TRUE  AND type = 'CLIENT_GALLERY' THEN 'UNLISTED'
    WHEN visible = TRUE                              THEN 'LISTED'
    ELSE 'UNLISTED'
END;

ALTER TABLE collection
    ALTER COLUMN visibility SET NOT NULL;

ALTER TABLE collection
    ADD CONSTRAINT collection_visibility_chk
    CHECK (visibility IN ('LISTED','UNLISTED','HIDDEN'));

CREATE INDEX idx_collection_visibility ON collection(visibility);

-- Drop the legacy boolean.
ALTER TABLE collection DROP COLUMN visible;
