-- V33: Per-user "Selects" (favorites / shortlist).
-- A select is one (user, content image) pair scoped to the collection it was selected in.
-- A user can hold at most one select per image (PK on (user_id, content_id)); collection_id
-- records the gallery the select belongs to so the pinned "Your Selects" section and the
-- /user page can group/scope without a second lookup. v1 is images-only: content_id
-- references content_image(id), mirroring V16 content_image_locations and V19 cover_image_id.

BEGIN;

CREATE TABLE user_selects (
    user_id       BIGINT      NOT NULL REFERENCES app_user(id)      ON DELETE CASCADE,
    content_id    BIGINT      NOT NULL REFERENCES content_image(id) ON DELETE CASCADE,
    collection_id BIGINT      NOT NULL REFERENCES collection(id)    ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, content_id)
);

-- user_id is the leading column of the PK, so (user_id) lookups are already served. The pinned
-- "Your Selects" section queries by (user_id, collection_id), and the /user page groups by
-- collection_id — this composite index backs both without scanning all of a user's selects.
CREATE INDEX idx_user_selects_user_collection ON user_selects (user_id, collection_id);

COMMIT;
