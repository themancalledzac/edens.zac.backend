-- V18: Replace BCrypt password_hash with plaintext gallery_password and add recipient_emails
-- Passwords for client galleries are no longer hashed; the manage page can display and update them directly.

ALTER TABLE collection RENAME COLUMN password_hash TO gallery_password;

-- Clear existing BCrypt hashes -- they are no longer valid since comparison is now plaintext.
-- Admins must re-set passwords for any gallery that had one.
UPDATE collection SET gallery_password = NULL WHERE gallery_password LIKE '$2%';

-- Store recipient emails as a native array so we can add/remove without a join table.
ALTER TABLE collection ADD COLUMN IF NOT EXISTS recipient_emails TEXT[] NOT NULL DEFAULT '{}';
