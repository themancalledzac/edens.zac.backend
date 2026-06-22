CREATE TABLE webauthn_credential (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  credential_id BYTEA NOT NULL UNIQUE,
  public_key    BYTEA NOT NULL,
  sign_count    BIGINT NOT NULL DEFAULT 0,
  transports    VARCHAR(64),
  label         VARCHAR(120),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_used_at  TIMESTAMPTZ
);
CREATE INDEX idx_webauthn_credential_user_id ON webauthn_credential(user_id);
