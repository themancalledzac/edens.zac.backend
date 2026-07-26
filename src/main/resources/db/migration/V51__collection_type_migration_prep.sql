-- V51: migration prep for the typeless collection (unit U0).

-- 1. Let the application stop writing `type` (U4) while the column still physically exists.
--    DEFAULT 'MISC' covers any INSERT that omits the column; DROP NOT NULL covers any INSERT
--    that passes it explicitly as NULL. Both directions of a U4 rollback stay safe.
ALTER TABLE collection
  ALTER COLUMN type DROP NOT NULL,
  ALTER COLUMN type SET DEFAULT 'MISC';
