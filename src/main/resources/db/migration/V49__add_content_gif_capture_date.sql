-- Give GIF/MP4 content a real capture date so it can be sorted chronologically alongside images.
-- Nullable: existing gifs have no capture date and remain sorted to the end until an admin sets one.
ALTER TABLE content_gif ADD COLUMN capture_date TIMESTAMP NULL;
