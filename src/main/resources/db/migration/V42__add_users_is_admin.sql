-- V42: Restore an admin capability flag on users, dropped in V37 when "admin = perimeter".
-- Reintroduced as a plain boolean (not the old role enum): a SPECIFIC user is admin,
-- separate from being a logged-in USER. No user is seeded here — the PUBLIC repo carries
-- no identity. AdminBootstrap (env-driven) designates the admin on startup.
ALTER TABLE users ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT false;
