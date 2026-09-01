-- V62: Repair is_film on images that predate the rules which now set it at ingest.
--
-- ImageProcessingService derives is_film two ways: the metadata key, and resolveFilmCameraDefaults,
-- which forces TRUE when the body is a known film camera. Images ingested before V23 flagged those
-- bodies never got the second rule, so they sit at FALSE with a film camera attached.
--
-- Both statements below restate a rule the application already enforces, so they are corrections
-- rather than guesses. Neither infers film from a collection slug: "-film" in a name is a naming
-- habit, not data, and a backfill keyed on it would be wrong the first time someone names a
-- collection differently.
--
-- Scope limit, stated because the numbers that motivated this item are not fully addressed by it:
-- chamonix-film (0/5), vienna-film (0/5) and gorge-50km-film (0/7) are only repaired if their
-- images carry a flagged film body or a film stock. V23 flags exactly two bodies (Hasselblad 500cm,
-- Nikon FM3A), which is why dolomites-film reads 33/33 and these three read zero. If they were shot
-- on a third body, the remaining fix is to flag that body -- a data call for the owner, not a
-- migration that can guess it.

-- `IS DISTINCT FROM TRUE`, not `= FALSE`: content_image.is_film is nullable (ContentRepository
-- reads it through getBoolean, which returns null on wasNull), and "unset" on these rows means
-- NULL as often as FALSE. `= FALSE` would silently skip every NULL row -- the exact rows this
-- migration exists to repair.

-- A film stock on an image is only meaningful for film.
UPDATE content_image
   SET is_film = TRUE
 WHERE film_type_id IS NOT NULL
   AND is_film IS DISTINCT FROM TRUE;

-- The camera rule ImageProcessingService applies at ingest, applied to rows that predate it.
UPDATE content_image ci
   SET is_film = TRUE
  FROM content_cameras cc
 WHERE ci.camera_id = cc.id
   AND cc.is_film = TRUE
   AND ci.is_film IS DISTINCT FROM TRUE;
