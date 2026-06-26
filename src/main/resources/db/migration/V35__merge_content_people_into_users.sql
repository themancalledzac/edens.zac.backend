-- V35: Merge content_people (Person) into app_user, renamed `users`.
-- A "person tag" and a "login account" become one identity row. Tagged-only people get
-- status='PERSON' (no account). Linked persons (content_people.user_id set, V31) fold into their
-- existing account. The two tag joins (content_image_people, collection_people) re-point from
-- content_people(id) to users(id). content_people is then dropped.
-- `role` is intentionally left in place (Phase 2 removes it once membership + perimeter replace it).

BEGIN;

-- 1. Rename the table. FKs from session/webauthn/gallery_access/invite/selects/rating_override
--    track by oid and remain valid.
ALTER TABLE app_user RENAME TO users;

-- 2. Unify the name column; relax account-only columns.
ALTER TABLE users RENAME COLUMN display_name TO name;
UPDATE users SET name = COALESCE(name, email, 'Unnamed') WHERE name IS NULL OR name = '';
ALTER TABLE users ALTER COLUMN name SET NOT NULL;
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;

-- 3. Allow PERSON status (tagged-only, no account). Existing accounts keep their lifecycle value.
--    app_user.status is a VARCHAR(16) with app-level enum; no DB check constraint to widen here.

-- 4. Map each content_people row to a users id.
CREATE TEMP TABLE person_id_map (old_person_id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL) ON COMMIT DROP;

-- 4a. Linked persons fold into their existing account; copy the name only when the account had none.
UPDATE users u
   SET name = cp.person_name
  FROM content_people cp
 WHERE cp.user_id = u.id AND (u.name IS NULL OR u.name = '');
INSERT INTO person_id_map (old_person_id, user_id)
SELECT cp.id, cp.user_id FROM content_people cp WHERE cp.user_id IS NOT NULL;

-- 4b. Unlinked persons become new PERSON rows.
WITH inserted AS (
  INSERT INTO users (name, webauthn_user_handle, role, status, created_at, updated_at)
  SELECT cp.person_name, gen_random_uuid(), NULL, 'PERSON', cp.created_at, now()
    FROM content_people cp
   WHERE cp.user_id IS NULL
  RETURNING id, name
)
INSERT INTO person_id_map (old_person_id, user_id)
SELECT cp.id, i.id
  FROM content_people cp
  JOIN inserted i ON i.name = cp.person_name
 WHERE cp.user_id IS NULL;

-- 5. Re-point the tag joins onto users(id).
ALTER TABLE content_image_people DROP CONSTRAINT IF EXISTS content_image_people_person_id_fkey;
UPDATE content_image_people cip SET person_id = m.user_id FROM person_id_map m WHERE cip.person_id = m.old_person_id;
ALTER TABLE content_image_people ADD CONSTRAINT content_image_people_person_id_fkey
  FOREIGN KEY (person_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE collection_people DROP CONSTRAINT IF EXISTS collection_people_person_id_fkey;
UPDATE collection_people cp SET person_id = m.user_id FROM person_id_map m WHERE cp.person_id = m.old_person_id;
ALTER TABLE collection_people ADD CONSTRAINT collection_people_person_id_fkey
  FOREIGN KEY (person_id) REFERENCES users(id) ON DELETE CASCADE;

-- 6. Drop the retired table (its V31 user_id link goes with it).
DROP TABLE content_people;

COMMIT;
