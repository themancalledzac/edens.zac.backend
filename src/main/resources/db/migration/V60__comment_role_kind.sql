-- V60: Record what role.kind is for, so it is not mistaken for a dead column again.
--
-- No Java reads or writes kind other than RoleRepository, which always writes the 'SHARED' default.
-- That made it look droppable. It is not: V45's backfill writes 'PERSONAL' and joins on it, so any
-- database that ran V45 against a non-empty user_collection carries both values, and kind is the
-- only surviving marker of which roles that migration created versus which an admin made since.

COMMENT ON COLUMN role.kind IS
  'Provenance, not behaviour. PERSONAL marks a role created by the V45 backfill, one per '
  'grant-holder in the pre-V45 user_collection table. SHARED marks every role created since, '
  'which is RoleRepository''s only value. Nothing in the Java layer reads this column; it exists '
  'so the V45 backfill stays distinguishable from deliberate admin roles.';
