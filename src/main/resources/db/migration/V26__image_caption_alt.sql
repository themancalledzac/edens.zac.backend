-- Add editable caption + alt-text columns to images.
-- The frontend already sends these keys on PATCH /api/admin/content/images; until now the
-- backend DTO and table had no such fields, so edits were silently dropped (see audit 4.2).

ALTER TABLE content_image ADD COLUMN caption TEXT;
ALTER TABLE content_image ADD COLUMN alt VARCHAR(500);
