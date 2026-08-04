-- V54: fold any remaining duplicate user names, and guarantee UNIQUE (LOWER(name)).
--
-- Why this is a new migration rather than a fix to V53: V53 was already applied to the live
-- database. Editing an applied migration changes its checksum and Flyway then refuses to boot
-- ("Migration checksum mismatch for migration version 53"). V53 stays byte-identical to what ran;
-- the correction lands here.
--
-- What V53 got wrong: it moved tags with DELETE-the-collisions-then-UPDATE, copied from
-- PersonRepository.repointTags, which moves ONE source onto ONE target. A fold is N-to-one once a
-- name group holds 3+ rows, and V53's collision DELETE only removed rows colliding with the
-- WINNER -- never two losers colliding with EACH OTHER. Those both survived, and the UPDATE then
-- violated the non-deferrable composite primary key.
--
-- This version sidesteps collision analysis entirely: INSERT the target rows with
-- ON CONFLICT DO NOTHING, then DELETE the source rows. Every collision shape is handled by the
-- conflict clause, and DISTINCT covers two losers contributing the same target row.
--
-- No-op against the live database: V53 completed there (only 2-row groups existed), so v54_fold is
-- empty, every statement matches zero rows, and the index already exists. It matters for a restore
-- from a pre-V53 dump.

-- 1. Abort rather than destroy an account: if a group holds two real accounts there is no safe
--    automatic winner. Without this, step 5 would fail with an opaque "could not create unique
--    index" instead.
DO $$
DECLARE
  offenders text;
BEGIN
  SELECT string_agg(key, ', ') INTO offenders
  FROM (
    SELECT LOWER(name) AS key
    FROM users
    GROUP BY LOWER(name)
    HAVING count(*) > 1 AND count(*) FILTER (WHERE status <> 'PERSON') > 1
  ) g;

  IF offenders IS NOT NULL THEN
    RAISE EXCEPTION
      'V54: cannot collapse duplicate user names -- these have more than one real account: %. '
      'Rename or merge them by hand (POST /api/admin/users/{targetId}/merge), then redeploy.',
      offenders;
  END IF;
END $$;

-- 2. Survivor per group: a real account outranks a tag-only PERSON, then lowest id. Boolean
--    ordering puts false first, so (status = 'PERSON') ranks accounts ahead of tag rows.
CREATE TEMP TABLE v54_fold ON COMMIT DROP AS
WITH ranked AS (
  SELECT
    id,
    LOWER(name) AS key,
    row_number() OVER (
      PARTITION BY LOWER(name)
      ORDER BY (status = 'PERSON'), id
    ) AS rn
  FROM users
)
SELECT loser.id AS source_id, winner.id AS target_id
FROM ranked loser
JOIN ranked winner ON winner.key = loser.key AND winner.rn = 1
WHERE loser.rn > 1;

-- 3. Copy each loser's joins onto the survivor, then drop the loser's. ON CONFLICT DO NOTHING
--    absorbs every duplicate -- whether the survivor already held the row or two losers both did.
INSERT INTO content_image_people (content_id, person_id)
SELECT DISTINCT s.content_id, f.target_id
FROM content_image_people s JOIN v54_fold f ON s.person_id = f.source_id
ON CONFLICT DO NOTHING;

DELETE FROM content_image_people s USING v54_fold f WHERE s.person_id = f.source_id;

INSERT INTO collection_people (collection_id, person_id)
SELECT DISTINCT s.collection_id, f.target_id
FROM collection_people s JOIN v54_fold f ON s.person_id = f.source_id
ON CONFLICT DO NOTHING;

DELETE FROM collection_people s USING v54_fold f WHERE s.person_id = f.source_id;

INSERT INTO role_member (role_id, user_id)
SELECT DISTINCT s.role_id, f.target_id
FROM role_member s JOIN v54_fold f ON s.user_id = f.source_id
ON CONFLICT DO NOTHING;

DELETE FROM role_member s USING v54_fold f WHERE s.user_id = f.source_id;

-- 4. Drop the folded rows. The status guard mirrors PersonRepository.deletePersonById; step 1 has
--    already proved every loser is a tag-only PERSON.
DELETE FROM users u
USING v54_fold f
WHERE u.id = f.source_id AND u.status = 'PERSON';

-- 5. The invariant. IF NOT EXISTS because V53 already created it wherever V53 succeeded.
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_name_lower ON users (LOWER(name));
