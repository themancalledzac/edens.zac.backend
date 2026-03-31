-- V12: Convert decimal shutter speed values to human-readable fraction format.
-- XMP ExposureTime stores values as decimals (e.g., "0.01") but the display format
-- should be fractional (e.g., "1/100 sec") to match how photographers read shutter speed.

UPDATE content_image
SET shutter_speed = CASE
    WHEN shutter_speed::numeric >= 1 THEN ROUND(shutter_speed::numeric)::text || ' sec'
    ELSE '1/' || ROUND(1.0 / shutter_speed::numeric)::text || ' sec'
  END
WHERE shutter_speed IS NOT NULL
  AND shutter_speed NOT LIKE '%/%'
  AND shutter_speed ~ '^\d+\.?\d*$';
