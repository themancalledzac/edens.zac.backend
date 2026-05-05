-- V21: Add rating column to collection (mirrors content_image.rating).
-- Range 0-5 (NULL means unrated). Used to order multi-collection list views.

ALTER TABLE collection
    ADD COLUMN rating INT;

ALTER TABLE collection
    ADD CONSTRAINT collection_rating_chk
    CHECK (rating IS NULL OR (rating >= 0 AND rating <= 5));

CREATE INDEX idx_collection_rating ON collection(rating);
