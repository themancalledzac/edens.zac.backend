-- V35: Merge content_people (Person) into app_user, renamed `users`.
-- A "person tag" and a "login account" become one identity row. Tagged-only people get
-- status='PERSON' (no account). Linked persons (content_people.user_id set, V31) fold into their
-- existing account. The two tag joins (content_image_people, collection_people) re-point from
-- content_people(id) to users(id). content_people is then dropped.
-- `role` is intentionally left in place (Phase 2 removes it once membership + perimeter replace it).
--
-- No explicit BEGIN/COMMIT: Flyway runs each migration inside its own transaction. An inner
-- BEGIN/COMMIT raised "there is already a transaction in progress" (SQLSTATE 25001) and committed
-- Flyway's transaction before it could record the migration.

-- 1. Rename the table. FKs from session/webauthn/gallery_access/invite/selects/rating_override
--    track by oid and remain valid.
ALTER TABLE app_user RENAME TO users;

-- 2. Unify the name column; relax account-only columns.
ALTER TABLE users RENAME COLUMN display_name TO name;
UPDATE users SET name = COALESCE(name, email, 'Unnamed') WHERE name IS NULL OR name = '';
ALTER TABLE users ALTER COLUMN name SET NOT NULL;
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
-- A tag-only PERSON row has no account, so it has no role. `role` stays for accounts (Phase 2
-- removes it) but must allow NULL for PERSON rows.
ALTER TABLE users ALTER COLUMN role DROP NOT NULL;

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

-- 4b. Unlinked persons become new PERSON rows. Correlate each new users.id back to its source
--     content_people.id through a transient column. person_name is NOT unique, so the previous
--     `JOIN ON users.name = content_people.person_name` could fan one tag out into several map
--     rows (and violate person_id_map's primary key) whenever two unlinked people share a name.
ALTER TABLE users ADD COLUMN tmp_old_person_id BIGINT;
INSERT INTO users (name, webauthn_user_handle, role, status, created_at, updated_at, tmp_old_person_id)
SELECT cp.person_name, gen_random_uuid(), NULL, 'PERSON', cp.created_at, now(), cp.id
  FROM content_people cp
 WHERE cp.user_id IS NULL;
INSERT INTO person_id_map (old_person_id, user_id)
SELECT tmp_old_person_id, id FROM users WHERE tmp_old_person_id IS NOT NULL;
ALTER TABLE users DROP COLUMN tmp_old_person_id;

-- 5. Re-point the tag joins onto users(id). Rebuild instead of UPDATE-in-place: a freshly assigned
--    users.id can reuse a number that is still a live content_people.id stored in these join tables,
--    so updating the (.., person_id) primary-key column in place hits transient duplicate-key
--    collisions (the PK is non-deferrable -- checked per row, not at commit). Staging the remapped
--    rows and reinserting into the emptied table sidesteps the collision; DISTINCT also folds any
--    identity merge (two persons -> one account, same content/collection) into a single tag.
--
--    The person FK is dropped via a dynamic pg_constraint lookup (by referenced table, not name):
--    the actual names are fk_image_people_person / fk_collection_people_person, not the
--    Postgres-default *_person_id_fkey, and auto-generated names are not stable across environments.
--    This mirrors the lookup V27 already used on these same tables.

-- content_image_people
DO $$
DECLARE fk_name text;
BEGIN
  SELECT conname INTO fk_name FROM pg_constraint
   WHERE conrelid = 'content_image_people'::regclass AND contype = 'f'
     AND confrelid = 'content_people'::regclass;
  IF fk_name IS NOT NULL THEN
    EXECUTE 'ALTER TABLE content_image_people DROP CONSTRAINT ' || quote_ident(fk_name);
  END IF;
END $$;
CREATE TEMP TABLE content_image_people_remap ON COMMIT DROP AS
SELECT DISTINCT cip.content_id, m.user_id AS person_id
  FROM content_image_people cip
  JOIN person_id_map m ON m.old_person_id = cip.person_id;
DELETE FROM content_image_people;
INSERT INTO content_image_people (content_id, person_id)
SELECT content_id, person_id FROM content_image_people_remap;
ALTER TABLE content_image_people ADD CONSTRAINT fk_image_people_person
  FOREIGN KEY (person_id) REFERENCES users(id) ON DELETE CASCADE;

-- collection_people
DO $$
DECLARE fk_name text;
BEGIN
  SELECT conname INTO fk_name FROM pg_constraint
   WHERE conrelid = 'collection_people'::regclass AND contype = 'f'
     AND confrelid = 'content_people'::regclass;
  IF fk_name IS NOT NULL THEN
    EXECUTE 'ALTER TABLE collection_people DROP CONSTRAINT ' || quote_ident(fk_name);
  END IF;
END $$;
CREATE TEMP TABLE collection_people_remap ON COMMIT DROP AS
SELECT DISTINCT clp.collection_id, m.user_id AS person_id
  FROM collection_people clp
  JOIN person_id_map m ON m.old_person_id = clp.person_id;
DELETE FROM collection_people;
INSERT INTO collection_people (collection_id, person_id)
SELECT collection_id, person_id FROM collection_people_remap;
ALTER TABLE collection_people ADD CONSTRAINT fk_collection_people_person
  FOREIGN KEY (person_id) REFERENCES users(id) ON DELETE CASCADE;

-- 6. Drop the retired table (its V31 user_id link goes with it).
DROP TABLE content_people;
