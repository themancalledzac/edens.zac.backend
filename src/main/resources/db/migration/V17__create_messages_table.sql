CREATE TABLE messages (
  id            BIGSERIAL PRIMARY KEY,
  email         VARCHAR(320) NOT NULL,
  message       TEXT NOT NULL,
  created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
  email_sent_at TIMESTAMP NULL
);

CREATE INDEX idx_messages_created_at ON messages(created_at DESC);
CREATE INDEX idx_messages_unsent ON messages(email_sent_at) WHERE email_sent_at IS NULL;
