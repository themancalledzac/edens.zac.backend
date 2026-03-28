-- Add slug columns to location, tag, and content_people tables
-- Slugs provide URL-friendly identifiers (e.g., "dolomites-italy" instead of "Dolomites, Italy")

-- 1. Add nullable slug columns
ALTER TABLE location ADD COLUMN slug VARCHAR(255);
ALTER TABLE tag ADD COLUMN slug VARCHAR(100);
ALTER TABLE content_people ADD COLUMN slug VARCHAR(150);

-- 2. Backfill slugs from existing names
-- Slug logic: lowercase, strip non-alphanumeric (except space/hyphen), spaces to hyphens, collapse hyphens, trim hyphens
UPDATE location SET slug = TRIM(BOTH '-' FROM
    REGEXP_REPLACE(
        REGEXP_REPLACE(
            REGEXP_REPLACE(
                LOWER(location_name),
                '[^a-z0-9\s-]', '', 'g'),
            '\s+', '-', 'g'),
        '-+', '-', 'g'))
WHERE slug IS NULL;

UPDATE tag SET slug = TRIM(BOTH '-' FROM
    REGEXP_REPLACE(
        REGEXP_REPLACE(
            REGEXP_REPLACE(
                LOWER(tag_name),
                '[^a-z0-9\s-]', '', 'g'),
            '\s+', '-', 'g'),
        '-+', '-', 'g'))
WHERE slug IS NULL;

UPDATE content_people SET slug = TRIM(BOTH '-' FROM
    REGEXP_REPLACE(
        REGEXP_REPLACE(
            REGEXP_REPLACE(
                LOWER(person_name),
                '[^a-z0-9\s-]', '', 'g'),
            '\s+', '-', 'g'),
        '-+', '-', 'g'))
WHERE slug IS NULL;

-- 3. Add NOT NULL constraint and unique index
ALTER TABLE location ALTER COLUMN slug SET NOT NULL;
ALTER TABLE tag ALTER COLUMN slug SET NOT NULL;
ALTER TABLE content_people ALTER COLUMN slug SET NOT NULL;

CREATE UNIQUE INDEX idx_location_slug ON location(slug);
CREATE UNIQUE INDEX idx_tag_slug ON tag(slug);
CREATE UNIQUE INDEX idx_content_people_slug ON content_people(slug);
