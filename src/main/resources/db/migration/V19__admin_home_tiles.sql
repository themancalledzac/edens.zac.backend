-- V19: Create admin_home_tile table mapping tile keys to cover images.
-- cover_image_id is nullable; frontend renders a placeholder when null.
-- All 10 tile keys are seeded with NULL cover images; they are assigned via admin UI.

CREATE TABLE admin_home_tile (
    id             BIGSERIAL   PRIMARY KEY,
    tile_key       VARCHAR(64) NOT NULL UNIQUE,
    cover_image_id BIGINT      REFERENCES content_image(id) ON DELETE SET NULL,
    display_order  INT         NOT NULL
);

INSERT INTO admin_home_tile (tile_key, cover_image_id, display_order) VALUES
('home',             NULL, 0),
('all-collections',  NULL, 1),
('all-images',       NULL, 2),
('metadata',         NULL, 3),
('comments',         NULL, 4),
('blogs',            NULL, 5),
('client-galleries', NULL, 6),
('create',           NULL, 7),
('manage',           NULL, 8),
('about',            NULL, 9);
