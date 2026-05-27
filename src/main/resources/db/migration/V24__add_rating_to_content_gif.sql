-- V24: Add rating column to content_gif (mirrors content_image.rating).
-- Range 0-5 (NULL means unrated). Used by the layout algorithm to size GIF/MP4
-- content blocks in the row grid — without a rating, GIFs fall back to a
-- 1-slot wrapper, which leaves empty row space around them.
--
-- Backfill existing rows to 4 so prior uploads render as feature content
-- (full row for horizontal, half row for vertical) by default.

ALTER TABLE content_gif
    ADD COLUMN rating INT;

UPDATE content_gif SET rating = 4 WHERE rating IS NULL;

ALTER TABLE content_gif
    ADD CONSTRAINT content_gif_rating_chk
    CHECK (rating IS NULL OR (rating >= 0 AND rating <= 5));

CREATE INDEX idx_content_gif_rating ON content_gif(rating);
