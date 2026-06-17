-- V29: Auth foundation spine (Phase F1).
-- Three tables: app_user (the principal), user_session (DB-backed opaque sessions),
-- gallery_access (per-person collection scope — created now, read by no controller until Phase C).
-- webauthn_credential is V30 (F2), not here.

-- The principal. role distinguishes admin from client.
CREATE TABLE app_user (
  id                   BIGSERIAL PRIMARY KEY,
  email                VARCHAR(255) UNIQUE NOT NULL,
  role                 VARCHAR(16)  NOT NULL,         -- 'ADMIN' | 'CLIENT'
  password_hash        VARCHAR(255),                  -- DelegatingPasswordEncoder ({bcrypt}...); null until set
  webauthn_user_handle UUID UNIQUE NOT NULL,          -- random handle WebAuthn binds to (NOT the email)
  display_name         VARCHAR(120),
  status               VARCHAR(16)  NOT NULL,         -- 'INVITED' | 'ACTIVE' | 'DISABLED'
  created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE user_session (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  token_hash    VARCHAR(255) NOT NULL UNIQUE,         -- SHA-256 hex of the raw cookie value
  mfa_satisfied BOOLEAN NOT NULL DEFAULT FALSE,        -- true=passkey(user-verified), false=break-glass password
  ip            VARCHAR(64),
  user_agent    VARCHAR(255),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at    TIMESTAMPTZ NOT NULL,
  revoked_at    TIMESTAMPTZ
);
CREATE INDEX idx_user_session_user_id ON user_session(user_id);

CREATE TABLE gallery_access (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  collection_id BIGINT NOT NULL REFERENCES collection(id) ON DELETE CASCADE,
  can_download  BOOLEAN NOT NULL DEFAULT TRUE,
  can_tag       BOOLEAN NOT NULL DEFAULT FALSE,
  granted_by    BIGINT REFERENCES app_user(id),
  granted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at    TIMESTAMPTZ,
  UNIQUE (user_id, collection_id)
);
