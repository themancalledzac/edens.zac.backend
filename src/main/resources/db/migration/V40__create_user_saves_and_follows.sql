-- V40: "Your Space" — per-user saved images and followed collections.
-- Two user-scoped join tables mirroring V33 user_selects, but with no per-collection access
-- gate: any logged-in user may bookmark any image or follow any collection. FKs point at
-- `users` (V35 renamed app_user -> users; V36 user_collection is the current precedent).

BEGIN;

CREATE TABLE user_saved_image (
  user_id    BIGINT      NOT NULL REFERENCES users(id)         ON DELETE CASCADE,
  image_id   BIGINT      NOT NULL REFERENCES content_image(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, image_id)
);

-- Backs the "Saved" list: a user's saves newest-first.
CREATE INDEX idx_user_saved_image_user ON user_saved_image (user_id, created_at DESC);

CREATE TABLE user_followed_collection (
  user_id       BIGINT      NOT NULL REFERENCES users(id)      ON DELETE CASCADE,
  collection_id BIGINT      NOT NULL REFERENCES collection(id) ON DELETE CASCADE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, collection_id)
);

-- Backs the "Following" list: a user's follows newest-first.
CREATE INDEX idx_user_followed_collection_user ON user_followed_collection (user_id, created_at DESC);

COMMIT;
