-- V37: Remove the temporary `role` column from users.
-- Per-collection access is now user_collection membership; admin is the perimeter
-- (InternalSecretFilter / localhost), not a user attribute.
ALTER TABLE users DROP COLUMN role;
