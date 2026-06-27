-- V34__create_user_rating_override
-- Description: Per-user, per-image rating override scoped to a client's own view of a
-- gallery. A row says "for this user, this image's rating is N in this collection's view"
-- and NEVER changes the canonical content.rating (admins use the canonical path). PK is
-- (user_id, content_id) so each user has at most one override per image; collection_id is
-- carried for scoped GET-by-collection and the (user_id, collection_id) index. Both FKs
-- cascade so deleting a user or an image cleans up its overrides.

BEGIN;

CREATE TABLE user_rating_override (
    user_id       BIGINT  NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    content_id    BIGINT  NOT NULL REFERENCES content(id)  ON DELETE CASCADE,
    collection_id BIGINT  NOT NULL,
    rating        INTEGER NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, content_id),
    CONSTRAINT chk_user_rating_override_range CHECK (rating BETWEEN 0 AND 5)
);

-- Backs the scoped read "all of this user's overrides in this collection's view".
CREATE INDEX idx_user_rating_override_user_collection
    ON user_rating_override(user_id, collection_id);

COMMIT;
