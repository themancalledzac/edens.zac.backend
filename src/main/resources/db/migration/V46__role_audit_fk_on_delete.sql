-- V46: the audit FKs (role.created_by, role_member.added_by, role_collection.granted_by) are
-- informational only. Recreate them as ON DELETE SET NULL so a hard-delete of a user (e.g. the
-- identity merge in UserMergeService) never RESTRICT-fails on a stale actor reference; the row
-- survives with a null actor. (The deferred DROP TABLE user_collection lands in a later migration.)
BEGIN;

ALTER TABLE role DROP CONSTRAINT role_created_by_fkey;
ALTER TABLE role ADD CONSTRAINT role_created_by_fkey
  FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE role_member DROP CONSTRAINT role_member_added_by_fkey;
ALTER TABLE role_member ADD CONSTRAINT role_member_added_by_fkey
  FOREIGN KEY (added_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE role_collection DROP CONSTRAINT role_collection_granted_by_fkey;
ALTER TABLE role_collection ADD CONSTRAINT role_collection_granted_by_fkey
  FOREIGN KEY (granted_by) REFERENCES users(id) ON DELETE SET NULL;

COMMIT;
