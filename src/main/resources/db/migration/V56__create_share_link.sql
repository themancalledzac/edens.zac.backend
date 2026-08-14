-- V56: user share links. A share is a VIRTUAL ROLE -- "holds the token" replaces role_member,
-- and a computed set replaces role_collection. Only the OPT-IN exceptions are stored here; the
-- tagged-in half of a share's scope is resolved live at read time via
-- CollectionRepository.findCollectionIdsByPersonId.
--
-- Not snapshotting that half is deliberate. The parent->child gallery password propagation
-- (V-era 9dc4f75) copies at save time and carries a known staleness bug where children silently
-- miss later updates; a frozen share list would reproduce that bug class exactly -- newly tagged
-- collections would never appear in a live share.
BEGIN;

CREATE TABLE share_link (
  id            BIGSERIAL    PRIMARY KEY,
  user_id       BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  -- Only the SHA-256 hash is stored, so a DB leak yields no usable link (same treatment as
  -- user_invite.token_hash). "Reset link" rotates this value in place, which kills the old URL
  -- and every live cookie at once while preserving the share_link_collection opt-ins below.
  token_hash    VARCHAR(255) NOT NULL UNIQUE,
  -- GENERAL is the only level any code path writes today. CLIENT is admitted by the CHECK so a
  -- future "this link may download" toggle is a data change rather than another migration.
  level         VARCHAR(16)  NOT NULL DEFAULT 'GENERAL'
                  CHECK (level IN ('GENERAL', 'CLIENT')),
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  rotated_at    TIMESTAMPTZ,
  last_used_at  TIMESTAMPTZ
);

-- No expires_at by design: a link lives until its owner resets it. A time-limited link would
-- create a dead-link cliff with no recovery path for the recipient, which is the exact failure
-- this feature exists to avoid.

-- One live link per user. The table can carry several rows per user later, when per-share
-- curation and alternate layouts arrive; v1 does not need it.
CREATE UNIQUE INDEX uq_share_link_user ON share_link(user_id);

-- The stored half of a share's scope: collections the sharer holds a role grant on and has
-- deliberately opted in. Off by default -- a fresh link exposes only what its owner is tagged
-- in, so nobody can re-share a gallery merely because they were given access to it.
CREATE TABLE share_link_collection (
  share_link_id BIGINT NOT NULL REFERENCES share_link(id) ON DELETE CASCADE,
  collection_id BIGINT NOT NULL REFERENCES collection(id) ON DELETE CASCADE,
  added_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (share_link_id, collection_id)
);
CREATE INDEX idx_share_link_collection_collection
  ON share_link_collection(collection_id);

COMMIT;
