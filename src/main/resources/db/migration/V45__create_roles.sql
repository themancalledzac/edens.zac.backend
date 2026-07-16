-- V45: role-based access control (EXPAND phase). Additive only; user_collection is kept
-- intact as a rollback reference and dropped later in the contract release (V46).
BEGIN;

CREATE TABLE role (
  id          BIGSERIAL    PRIMARY KEY,
  name        VARCHAR(128) NOT NULL,
  kind        VARCHAR(16)  NOT NULL DEFAULT 'SHARED'
                CHECK (kind IN ('PERSONAL', 'SHARED')),
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by  BIGINT       REFERENCES users(id),
  UNIQUE (name)
);

CREATE TABLE role_member (
  role_id  BIGINT      NOT NULL REFERENCES role(id)  ON DELETE CASCADE,
  user_id  BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  added_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  added_by BIGINT      REFERENCES users(id),
  PRIMARY KEY (role_id, user_id)
);
CREATE INDEX idx_role_member_user ON role_member(user_id);

CREATE TABLE role_collection (
  role_id       BIGINT      NOT NULL REFERENCES role(id)       ON DELETE CASCADE,
  collection_id BIGINT      NOT NULL REFERENCES collection(id) ON DELETE CASCADE,
  level         VARCHAR(16) NOT NULL DEFAULT 'GENERAL'
                  CHECK (level IN ('GENERAL', 'CLIENT')),
  granted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  granted_by    BIGINT      REFERENCES users(id),
  PRIMARY KEY (role_id, collection_id)
);
CREATE INDEX idx_role_collection_collection ON role_collection(collection_id);

-- Behavior-preserving backfill: one PERSONAL role per existing grant-holder, carrying that
-- user's current grants at their EXISTING level.
INSERT INTO role (name, kind, created_at)
SELECT DISTINCT 'user:' || uc.user_id, 'PERSONAL', now()
  FROM user_collection uc;

INSERT INTO role_member (role_id, user_id, added_at)
SELECT r.id, uc.user_id, now()
  FROM user_collection uc
  JOIN role r ON r.name = 'user:' || uc.user_id AND r.kind = 'PERSONAL'
 GROUP BY r.id, uc.user_id;

INSERT INTO role_collection (role_id, collection_id, level, granted_at, granted_by)
SELECT r.id, uc.collection_id, uc.role, uc.granted_at, uc.granted_by
  FROM user_collection uc
  JOIN role r ON r.name = 'user:' || uc.user_id AND r.kind = 'PERSONAL';

-- user_collection intentionally NOT dropped here (see V46, contract release).
COMMIT;
