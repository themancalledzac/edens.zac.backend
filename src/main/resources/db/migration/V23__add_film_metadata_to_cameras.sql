-- V23: Add film-photography defaults to content_cameras so that
-- selecting a known film body in the metadata editor can auto-toggle
-- isFilm + filmFormat on the frontend.
--
-- is_film defaults to FALSE for back-compat (all existing cameras).
-- default_film_format is nullable and holds FilmFormat enum names
-- (currently MM_35 or MM_120).

ALTER TABLE content_cameras
    ADD COLUMN IF NOT EXISTS is_film             BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS default_film_format VARCHAR(20);

-- Backfill known film bodies. Case-insensitive match; safe no-op if the
-- camera doesn't yet exist in this environment.
UPDATE content_cameras
   SET is_film = TRUE,
       default_film_format = 'MM_120'
 WHERE LOWER(camera_name) IN ('hasselblad 500cm', 'hasselblad 500c/m', 'hasselblad 500 c/m');

UPDATE content_cameras
   SET is_film = TRUE,
       default_film_format = 'MM_35'
 WHERE LOWER(camera_name) IN ('nikon fm3a', 'nikon fm-3a');
