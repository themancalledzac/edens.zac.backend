-- V36: Replace gallery_access with user_collection membership.
-- user_collection is the single source of per-collection access truth:
--   GENERAL = sees the collection even when private (no password), no client powers.
--   CLIENT  = view + download + tag + star (+ share, Phase 3).
-- Existing gallery_access rows migrate to GENERAL (client is never a default — it must be
-- granted explicitly via the /admin Users module). gallery_access is then dropped.
-- collection_people is untouched (it stays pure display metadata; it grants no access).

BEGIN;

CREATE TABLE user_collection (
  user_id       BIGINT      NOT NULL REFERENCES users(id)      ON DELETE CASCADE,
  collection_id BIGINT      NOT NULL REFERENCES collection(id) ON DELETE CASCADE,
  role          VARCHAR(16) NOT NULL DEFAULT 'GENERAL' CHECK (role IN ('GENERAL', 'CLIENT')),
  granted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  granted_by    BIGINT      REFERENCES users(id),
  PRIMARY KEY (user_id, collection_id)
);

CREATE INDEX idx_user_collection_collection ON user_collection(collection_id);

-- Migrate every existing grant to GENERAL membership (downgrade — re-grant CLIENT by hand).
INSERT INTO user_collection (user_id, collection_id, role, granted_at, granted_by)
SELECT user_id, collection_id, 'GENERAL', granted_at, granted_by
  FROM gallery_access;

DROP TABLE gallery_access;

COMMIT;
