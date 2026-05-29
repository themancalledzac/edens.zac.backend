-- V25: Add gif_url_web to content_gif.
-- Stores the CloudFront URL of the 1080px-longest-side "web" display variant of an
-- animated MP4/MOV, served in the in-row layout. The existing gif_url remains the
-- 2000px "full" master used by the fullscreen viewer.
-- Nullable: pre-existing gifs (and actual image/gif uploads) have no web variant;
-- the frontend falls back to gif_url when this is NULL.
-- Idempotent: no-op if the column already exists.

ALTER TABLE content_gif
    ADD COLUMN IF NOT EXISTS gif_url_web VARCHAR(512);
