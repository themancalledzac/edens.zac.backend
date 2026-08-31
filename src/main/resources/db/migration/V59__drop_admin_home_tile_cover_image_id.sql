-- V59: Drop admin_home_tile.cover_image_id.
--
-- The column has never held a value. V19 created it and seeded all ten tile rows with an explicit
-- NULL, and no Java has ever written it: AdminHomeTileRepository is the only class that touches
-- admin_home_tile and its sole statement selects tile_key and display_order. The frontend has
-- always rendered its own placeholder rather than reading a cover.
--
-- To restore it, re-add the column with the V19 definition; there is no data to recover.
--   ALTER TABLE admin_home_tile
--     ADD COLUMN cover_image_id BIGINT REFERENCES content_image(id) ON DELETE SET NULL;

ALTER TABLE admin_home_tile DROP COLUMN cover_image_id;
