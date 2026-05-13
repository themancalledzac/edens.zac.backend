-- V24: Add additional known film camera bodies with their default film formats.
-- Uses UPSERT so it is safe whether or not the camera already exists in this
-- environment (cameras are auto-created when images are tagged, so some may
-- already be present).

INSERT INTO content_cameras (camera_name, is_film, default_film_format, created_at)
VALUES
    ('Nikon F4s',       TRUE, 'MM_35',  NOW()),
    ('Mamiya RZ67',     TRUE, 'MM_120', NOW()),
    ('Mamiya 645 Pro',  TRUE, 'MM_120', NOW()),
    ('Minolta x700',    TRUE, 'MM_35',  NOW())
ON CONFLICT (camera_name) DO UPDATE
    SET is_film             = TRUE,
        default_film_format = EXCLUDED.default_film_format;
