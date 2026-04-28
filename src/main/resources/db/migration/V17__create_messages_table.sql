CREATE TABLE messages (
  id         BIGSERIAL PRIMARY KEY,
  email      VARCHAR(320) NOT NULL,
  message    TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_messages_created_at ON messages(created_at DESC);
